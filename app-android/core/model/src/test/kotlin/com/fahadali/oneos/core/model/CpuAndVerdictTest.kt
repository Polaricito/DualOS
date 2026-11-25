package com.fahadali.oneos.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CpuAndVerdictTest {
    private fun CompatReport.level(id: String): Level? = find(id)?.level

    @Test
    fun parsesCoresFeaturesAndIds() {
        val cpu = CpuInfoParser.parse(Fixtures.SM8850_CPUINFO)
        assertEquals(8, cpu.cores.size)
        assertEquals("Qualcomm Technologies, Inc", cpu.hardware)
        // The tested Fold has every reported feature but SME2 and MOPS.
        for (f in CpuRules.REPORTED - setOf("sme2", "mops")) assertTrue(f, cpu.has(f))
        assertFalse(cpu.has("sme2") || cpu.has("mops"))
        assertTrue(cpu.has("SVE2")) // case-insensitive
        assertFalse(cpu.has("sve3"))
        val c0 = cpu.cores[0]
        assertEquals(0, c0.index)
        assertEquals("0x51", c0.implementer)
        assertEquals("0x002", c0.part)
        assertEquals("0x3", c0.variant)
        assertEquals("2", c0.revision)
        assertEquals("Qualcomm 0x002 r3p2", c0.label)
        assertEquals("8 cores: 2 x Qualcomm 0x002 r3p2, 6 x Qualcomm 0x001 r3p2", cpu.summary)
        assertFalse(cpu.heterogeneous)
        assertTrue(CpuRules.missingForColorOs(cpu).isEmpty())
    }

    @Test
    fun featuresAreTheOnesEveryCoreHas() {
        val text = Fixtures.cpuinfo("fp asimd sve sve2 sme mops", cores = 2) +
            Fixtures.cpuinfo("fp asimd sve2 mops", cores = 1).replace("processor\t: 0", "processor\t: 2")
        val cpu = CpuInfoParser.parse(text)
        assertEquals(3, cpu.cores.size)
        assertTrue(cpu.heterogeneous)
        assertEquals(setOf("fp", "asimd", "sve2", "mops"), cpu.features)
        assertEquals(listOf("sme"), CpuRules.missingForColorOs(cpu))
    }

    @Test
    fun armPartNamesAndEmptyInput() {
        val cpu = CpuInfoParser.parse(Fixtures.cpuinfo("fp asimd", cores = 1, implementer = "0x41", parts = listOf("0xd81")))
        assertEquals("Arm Cortex-A720 r3p2", cpu.cores[0].label)
        val empty = CpuInfoParser.parse("")
        assertTrue(empty.cores.isEmpty())
        assertTrue(empty.features.isEmpty())
        assertEquals(CpuRules.ID_UNKNOWN, CpuRules.finding(empty).id)
    }

    @Test
    fun cpuWithoutSveSmeWarnsWithTheExactTitle() {
        val cpu = CpuInfoParser.parse(Fixtures.NO_SVE_CPUINFO)
        assertEquals(listOf("sve2", "sme"), CpuRules.missingForColorOs(cpu))
        val f = CpuRules.finding(cpu)
        assertEquals(CpuRules.ID_MISSING, f.id)
        assertEquals(Level.WARN, f.level)
        assertEquals("CPU lacks SVE2, SME used by the system images", f.title)
        assertEquals(CpuRules.R_MISSING, f.reason)
        assertTrue(f.detail.contains("SVE NO") || f.detail.contains("SVE no"))

        val onlySme = CpuInfoParser.parse(Fixtures.cpuinfo("fp asimd sve sve2", cores = 1))
        assertEquals("CPU lacks SME used by the system images", CpuRules.finding(onlySme).title)

        // The tested Fold has no MOPS or SME2 and runs ColorOS: no warning.
        val fold = CpuInfoParser.parse(Fixtures.SM8850_CPUINFO)
        assertEquals(false, fold.has("mops"))
        assertEquals(CpuRules.ID_OK, CpuRules.finding(fold).id)
    }

    @Test
    fun cpuFindingInTheDeviceReport() {
        val ok = CompatEvaluator.evaluate(Fixtures.a53())
        assertEquals(Level.OK, ok.level(CpuRules.ID_OK))
        assertEquals(Verdict.COMPATIBLE, ok.verdict)

        val noSve = CompatEvaluator.evaluate(Fixtures.a53(cpu = CpuInfoParser.parse(Fixtures.NO_SVE_CPUINFO)))
        assertEquals(Level.OK, noSve.level(CpuRules.ID_OK))
        assertEquals(Verdict.COMPATIBLE, noSve.verdict)

        // Unreadable /proc/cpuinfo is a note, not a warning.
        val unknown = CompatEvaluator.evaluate(Fixtures.a53(cpu = null))
        assertEquals(Level.INFO, unknown.level(CpuRules.ID_UNKNOWN))
        assertEquals(Verdict.COMPATIBLE, unknown.verdict)
    }

    @Test
    fun androidVersionOtherThan16Warns() {
        val noProfile = SocProps(manufacturer = "Samsung", model = "s5e9955")
        val r = CompatEvaluator.evaluate(Fixtures.facts(model = "SM-S948B", device = "m3s", soc = noProfile, sdk = 35))
        val f = r.find(CompatEvaluator.ID_ANDROID_DIFFERS)!!
        assertEquals(Level.WARN, f.level)
        assertEquals("Android version differs from the DualOS images (16); switching may not work", f.title)
        assertNull(r.find(CompatEvaluator.ID_ANDROID_MATCHES))
        assertEquals(Level.OK, CompatEvaluator.evaluate(Fixtures.facts(model = "SM-S948B", device = "m3s", soc = noProfile, sdk = 36)).level(CompatEvaluator.ID_ANDROID_MATCHES))

        for (sdk in listOf(35, 37)) {
            val mismatch = CompatEvaluator.evaluate(Fixtures.a53(sdk = sdk))
            assertEquals(Level.WARN, mismatch.find(CompatEvaluator.ID_ANDROID_DIFFERS)!!.level)
            assertEquals(Verdict.WARNING, mismatch.verdict)
            assertNull(mismatch.find(CompatEvaluator.ID_ANDROID_MATCHES))
        }

        val same = CompatEvaluator.evaluate(Fixtures.a53(sdk = 36))
        assertNull(same.find(CompatEvaluator.ID_ANDROID_DIFFERS))
        assertEquals(Level.OK, same.level(CompatEvaluator.ID_ANDROID_MATCHES))

        assertEquals("17", AndroidRelease.name(37))
        assertEquals("16", AndroidRelease.name(36))
        assertEquals("SDK 40", AndroidRelease.name(40))
    }

    @Test
    fun oneUi80IsTheTestedVersion() {
        assertNull(CompatEvaluator.evaluate(Fixtures.a53(oneUi = "80000")).find("oneui-unseen"))
        assertEquals(Level.WARN, CompatEvaluator.evaluate(Fixtures.a53(oneUi = "90000")).level("oneui-unseen"))
    }

    @Test
    fun a53IsTestedAndFlagshipsAreOutOfScope() {
        val a53 = CompatEvaluator.evaluate(Fixtures.a53())
        assertEquals(Verdict.COMPATIBLE, a53.verdict)
        assertNull(a53.find(CompatEvaluator.ID_PROFILE_DISABLED))
        assertEquals(Level.OK, a53.level("tested-model"))
        assertNull(a53.find("untested-model"))
        assertEquals(Level.OK, a53.level("soc-exynos"))
        assertEquals(0, a53.count(Level.BLOCK))

        for (f in listOf(Fixtures.s25Ultra(), Fixtures.s26Ultra())) {
            val r = CompatEvaluator.evaluate(f)
            assertEquals(Verdict.NOT_SUPPORTED, r.verdict)
            assertEquals(Level.BLOCK, r.level(CompatEvaluator.ID_OUT_OF_SCOPE))
        }
    }
}
