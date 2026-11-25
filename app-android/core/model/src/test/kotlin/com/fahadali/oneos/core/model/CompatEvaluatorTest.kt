package com.fahadali.oneos.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatEvaluatorTest {
    private fun eval(f: DeviceFacts) = CompatEvaluator.evaluate(f)

    private fun CompatReport.level(id: String): Level? = find(id)?.level

    @Test
    fun a53WithKernelSuUncheckedIsCompatible() {
        val r = eval(Fixtures.a53())
        assertEquals(Verdict.COMPATIBLE, r.verdict)
        assertEquals(Level.OK, r.level("tested-model"))
        assertEquals("Has run on the Galaxy A53 5G: LineageOS 23.", r.find("tested-model")!!.detail)
        assertEquals("Has run on: LineageOS 23 on the Galaxy A53 5G.", CompatEvaluator.TESTED_SCOPE)
        assertEquals(Level.OK, r.level("soc-exynos"))
        assertEquals(Level.OK, r.level("sdk-package"))
        assertEquals(Level.OK, r.level("free-space"))
        assertEquals(Level.INFO, r.level("root-unchecked"))
        assertEquals(0, r.count(Level.WARN))
        assertEquals(0, r.count(Level.BLOCK))
    }

    @Test
    fun rootProbeOutcomes() {
        fun withProbe(state: SuState, uid: Int?) = eval(
            Fixtures.a53(
                root = RootSignals(listOf("me.weishu.kernelsu"), probe = SuProbeResult(state, uid, "u:r:su:s0", "")),
            ),
        )
        val ok = withProbe(SuState.GRANTED, 0)
        assertEquals(Verdict.COMPATIBLE, ok.verdict)
        assertEquals(Level.OK, ok.level("root-ok"))

        val timeout = withProbe(SuState.TIMEOUT, null)
        assertEquals(Verdict.WARNING, timeout.verdict)
        assertEquals(Level.WARN, timeout.level("root-timeout"))

        for (state in listOf(SuState.DENIED, SuState.NOT_FOUND, SuState.ERROR)) {
            val r = withProbe(state, null)
            assertEquals(Verdict.NOT_SUPPORTED, r.verdict)
            assertEquals(Level.BLOCK, r.level("root-missing"))
        }
    }

    /**
     * DualOS Probe installs next to DualOS and needs its own root grant: every
     * root finding names the app that asks, never the other one.
     */
    @Test
    fun rootFindingsNameTheAppThatAsks() {
        val ids = listOf("root-timeout", "root-missing", "root-unchecked", "root-not-found")
        val facts = listOf(
            Fixtures.a53(root = RootSignals(listOf("me.weishu.kernelsu"), probe = SuProbeResult(SuState.TIMEOUT, null, null, ""))),
            Fixtures.a53(root = RootSignals(listOf("me.weishu.kernelsu"), probe = SuProbeResult(SuState.NOT_FOUND, null, null, ""))),
            Fixtures.a53(root = RootSignals(listOf("me.weishu.kernelsu"))),
            Fixtures.a53(root = RootSignals()),
        )
        for ((id, f) in ids.zip(facts)) {
            val probe = CompatEvaluator.evaluate(f, app = "DualOS Probe").find(id)
            assertNotNull(id, probe)
            val text = "${probe!!.title} ${probe.detail}"
            assertTrue("$id: $text", text.contains("DualOS Probe"))
            assertFalse("$id names DualOS: $text", Regex("""DualOS(?! Probe)""").containsMatchIn(text))
            val full = eval(f).find(id)!!
            assertEquals(text.replace("DualOS Probe", "DualOS"), "${full.title} ${full.detail}")
        }
    }

    @Test
    fun noRootManagerFoundIsWarn() {
        val r = eval(Fixtures.a53(root = RootSignals()))
        assertEquals(Verdict.WARNING, r.verdict)
        assertEquals(Level.WARN, r.level("root-not-found"))
    }

    @Test
    fun exynosWithoutProfileIsBlocked() {
        val r = eval(
            Fixtures.facts(
                model = "SM-S948B", device = "m3s",
                soc = SocProps(manufacturer = "Samsung", model = "s5e9965", platform = "s5e9965"),
            ),
        )
        assertEquals(Verdict.NOT_SUPPORTED, r.verdict)
        assertEquals(Level.BLOCK, r.level("soc-exynos"))
    }

    @Test
    fun a53ProfileResolvesAndExynosIsOk() {
        val f = Fixtures.a53()
        assertEquals(Profiles.A53, Profiles.resolve(f).profile)
        assertTrue(CompatEvaluator.inScope(f.family, Profiles.A53))
        val r = eval(f)
        assertEquals(Level.OK, r.level("soc-exynos"))
        assertNull(r.find(CompatEvaluator.ID_OUT_OF_SCOPE))
    }

    @Test
    fun notSamsungIsBlocked() {
        val r = eval(Fixtures.facts(brand = "google", model = "Pixel 10 Pro Fold", device = "rango"))
        assertEquals(Verdict.NOT_SUPPORTED, r.verdict)
        assertEquals(Level.BLOCK, r.level("not-samsung"))
        assertNull(r.find("model-out-of-scope"))
        assertNull(r.find("oneui-missing"))
    }

    @Test
    fun nonA53SamsungModelsAreOutOfScope() {
        for (f in listOf(
            Bundles.S25.facts,
            Bundles.F8U.facts,
            Fixtures.facts(model = "SM-A566B", device = "a56x", hinge = false, inputs = emptyList()),
        )) {
            assertNull(f.model, Profiles.resolve(f).profile)
            assertFalse(CompatEvaluator.inScope(f.family, null))
            val r = eval(f)
            assertEquals(f.model, Verdict.NOT_SUPPORTED, r.verdict)
            assertEquals(f.model, Level.BLOCK, r.level(CompatEvaluator.ID_OUT_OF_SCOPE))
            assertTrue(r.find(CompatEvaluator.ID_OUT_OF_SCOPE)!!.detail.contains("Galaxy A53 5G"))
        }
    }

    @Test
    fun noArm64IsBlocked() {
        val r = eval(Fixtures.a53(abis64 = emptyList()))
        assertEquals(Level.BLOCK, r.level("not-arm64"))
        assertEquals(Verdict.NOT_SUPPORTED, r.verdict)
    }

    @Test
    fun freeSpace() {
        val short = eval(Fixtures.a53(storage = StorageInfo(10_000_000_000, 128_000_000_000)))
        assertEquals(Level.WARN, short.level("free-space-short"))
        assertEquals(Verdict.WARNING, short.verdict)
        assertTrue(short.find("free-space-short")!!.detail.endsWith("A system that is already set up keeps its image: switching to it needs about 11.8 GB free."))

        val unknown = eval(Fixtures.a53(storage = null))
        assertEquals(Level.WARN, unknown.level("free-space-unknown"))

        val need = SpaceRule.toStage(Images.LINEAGE23, PackageSets.LINEAGE23)
        val exact = eval(Fixtures.a53(storage = StorageInfo(need, 128_000_000_000)))
        assertEquals(Level.OK, exact.level("free-space"))
        assertEquals(Level.WARN, eval(Fixtures.a53(storage = StorageInfo(need - 1, 128_000_000_000))).level("free-space-short"))
        assertEquals(2_685_145_088L + 549_716_536L + 11L * SpaceRule.GIB, need)
    }

    @Test
    fun a53Sdk36MatchesAndOlderOneUiDiffers() {
        val ok = eval(Fixtures.a53())
        assertNull(ok.find("oneui-unseen"))
        assertNull(ok.find(CompatEvaluator.ID_ANDROID_DIFFERS))
        assertTrue(ok.find(CompatEvaluator.ID_ANDROID_MATCHES)!!.detail.startsWith(Images.LINEAGE23.label))

        val older = eval(Fixtures.a53(oneUi = "70000", sdk = 35))
        assertEquals(Level.WARN, older.level("oneui-unseen"))
        assertEquals(Level.WARN, older.level(CompatEvaluator.ID_ANDROID_DIFFERS))
        assertEquals(Verdict.WARNING, older.verdict)

        val missing = eval(Fixtures.a53(oneUi = ""))
        assertEquals(Level.WARN, missing.level("oneui-missing"))
    }

    @Test
    fun formMismatchWarns() {
        val r = eval(Fixtures.a53(panels = listOf(Fixtures.F8U_INNER, Fixtures.F8U_COVER), hinge = true, inputs = listOf("flip", "sec_touchscreen")))
        assertEquals(Level.WARN, r.level("profile-form-mismatch"))
        assertEquals(Verdict.WARNING, r.verdict)
    }

    @Test
    fun infoNeverChangesTheVerdict() {
        val r = eval(Fixtures.a53(battery = BatteryInfo(10, false, 45f)))
        assertEquals(Level.INFO, r.level("battery-now"))
        assertEquals(Verdict.COMPATIBLE, r.verdict)
    }

    @Test
    fun sortedPutsWorstFirst() {
        val r = eval(Fixtures.a53(abis64 = emptyList(), oneUi = "70000", sdk = 35))
        val levels = r.sorted.map { it.level }
        assertEquals(Level.BLOCK, levels.first())
        assertTrue(levels.indexOf(Level.WARN) < levels.indexOf(Level.OK))
        assertTrue(levels.lastIndexOf(Level.OK) < levels.indexOf(Level.INFO))
        assertNotNull(r.find("not-arm64"))
    }

    @Test
    fun findingIdsAreUnique() {
        for (f in listOf(Fixtures.a53(), Bundles.S25.facts, Fixtures.facts(brand = "google"))) {
            val ids = eval(f).findings.map { it.id }
            assertEquals(ids.size, ids.toSet().size)
        }
    }
}
