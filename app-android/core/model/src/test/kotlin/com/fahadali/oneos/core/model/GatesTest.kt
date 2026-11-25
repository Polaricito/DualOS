package com.fahadali.oneos.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GatesTest {
    private val lineage = Images.LINEAGE23
    private val set = PackageSets.LINEAGE23
    private val a53 = Profiles.A53

    private fun stage(
        p: PhoneProfile = a53,
        image: String = lineage.id,
        verified: Boolean = true,
        setId: String? = p.choice(image)?.packageSetId,
        versionCode: Int = 148,
    ) = StageState(emptyList(), emptyList(), false, StageMarker(versionCode, p.id, image, setId, 1_792_000_000), verified)

    private fun gz(i: ImageRef = lineage) = ImageFileCheck(i.gzName, i.gzBytes, i.gzSha256, i.gzBytes)

    private fun input(
        facts: DeviceFacts = Fixtures.a53(
            root = RootSignals(
                listOf("me.weishu.kernelsu"),
                probe = SuProbeResult(SuState.GRANTED, 0, "u:r:ksu:s0", "uid=0(root)"),
            ),
        ),
        profile: PhoneProfile? = Profiles.resolve(facts).profile,
        image: ImageRef? = lineage,
    ) = GateInput(
        facts = facts,
        screenLockSet = false,
        oldSwitcherInstalled = false,
        installd = Fixtures.A53_INSTALLD,
        stage = if (profile != null && image != null) stage(profile, image.id) else StageState(emptyList(), emptyList(), false, null, false),
        imageFile = image?.let(::gz),
        appVersionCode = 148,
    )

    private fun eval(phase: GatePhase, i: GateInput) = SwitchGates.evaluate(phase, i)
    private fun GateReport.level(id: String): Level? = find(id)?.level

    @Test
    fun a53SwitchAllowsWithoutCarrierOrUntestedBlock() {
        val r = eval(GatePhase.SWITCH, input())
        assertEquals(a53, r.profile)
        assertEquals(lineage, r.image)
        assertEquals(set, r.set)
        assertFalse(r.blocked)
        for (id in listOf(
            SwitchGates.ID_ROOT, SwitchGates.ID_ANDROID, SwitchGates.ID_FORM, SwitchGates.ID_IMAGE,
            SwitchGates.ID_INSTALLD, SwitchGates.ID_OLD_SWITCHER, SwitchGates.ID_STAGE,
            SwitchGates.ID_SPACE, SwitchGates.ID_SCREEN_LOCK, SwitchGates.ID_BATTERY, SwitchGates.ID_PROFILE,
        )) {
            assertEquals(id, Level.OK, r.level(id))
        }
        assertEquals(r.gates.size, r.gates.map { it.id }.toSet().size)
        assertFalse(eval(GatePhase.PREPARE, input()).blocked)
    }

    @Test
    fun otherPhonesAreBlocked() {
        for (other in listOf(Bundles.S26U.facts, Bundles.S25U.facts, Bundles.S25.facts, Bundles.F8U.facts)) {
            val r = eval(GatePhase.PREPARE, input(facts = other, profile = null))
            assertTrue(other.model, r.blocked)
            assertEquals(other.model, SwitchGates.R_PROFILE_NONE, r.find(SwitchGates.ID_PROFILE)?.finding?.reason)
        }
    }

    @Test
    fun screenLockBlocksSwitchButOnlyWarnsOnPrepare() {
        val swLocked = eval(GatePhase.SWITCH, input().copy(screenLockSet = true))
        assertTrue(swLocked.blocked)
        assertEquals(ScreenLockRule.R_SET, swLocked.find(SwitchGates.ID_SCREEN_LOCK)?.finding?.reason)

        val prepLocked = eval(GatePhase.PREPARE, input().copy(screenLockSet = true))
        assertFalse(prepLocked.blocked)
        assertEquals(Level.WARN, prepLocked.level(SwitchGates.ID_SCREEN_LOCK))

        val swUnknown = eval(GatePhase.SWITCH, input().copy(screenLockSet = null))
        assertTrue(swUnknown.blocked)
        assertEquals(ScreenLockRule.R_UNKNOWN, swUnknown.find(SwitchGates.ID_SCREEN_LOCK)?.finding?.reason)
    }

    @Test
    fun batteryAndRootAndStageChecks() {
        val lowBat = eval(GatePhase.SWITCH, input(facts = Fixtures.a53(battery = BatteryInfo(15, false, 30f))))
        assertEquals(SwitchGates.R_BATTERY_LOW, lowBat.find(SwitchGates.ID_BATTERY)?.finding?.reason)

        val hotBat = eval(GatePhase.SWITCH, input(facts = Fixtures.a53(battery = BatteryInfo(80, true, 43f))))
        assertEquals(SwitchGates.R_BATTERY_HOT, hotBat.find(SwitchGates.ID_BATTERY)?.finding?.reason)

        val denied = eval(GatePhase.SWITCH, input(facts = Fixtures.a53(root = RootSignals(listOf("me.weishu.kernelsu"), probe = SuProbeResult(SuState.DENIED, null, null, "")))))
        assertEquals(SwitchGates.R_ROOT_DENIED, denied.find(SwitchGates.ID_ROOT)?.finding?.reason)

        val mounted = eval(GatePhase.SWITCH, input().copy(stage = stage().copy(mounts = listOf("/data/local/tmp/oneos/root"))))
        assertEquals(SwitchGates.R_STAGE_MOUNTED, mounted.find(SwitchGates.ID_STAGE)?.finding?.reason)

        val unfinished = eval(GatePhase.SWITCH, input().copy(stage = stage().copy(unfinishedSession = true)))
        assertEquals(SwitchGates.R_STAGE_UNFINISHED, unfinished.find(SwitchGates.ID_STAGE)?.finding?.reason)
    }

    @Test
    fun stageImageAndVersionAndFilesChecks() {
        val noMarker = eval(GatePhase.SWITCH, input().copy(stage = StageState(emptyList(), emptyList(), false, null, false)))
        assertEquals(SwitchGates.R_IMAGE_NOTHING, noMarker.find(SwitchGates.ID_IMAGE)?.finding?.reason)

        val oldVersion = eval(GatePhase.SWITCH, input().copy(stage = stage(versionCode = 147)))
        assertEquals(SwitchGates.R_IMAGE_OTHER_VERSION, oldVersion.find(SwitchGates.ID_IMAGE)?.finding?.reason)

        val otherSet = eval(GatePhase.SWITCH, input().copy(stage = stage(setId = "wrong-set")))
        assertEquals(SwitchGates.R_IMAGE_OTHER_SET, otherSet.find(SwitchGates.ID_IMAGE)?.finding?.reason)

        val changed = eval(GatePhase.SWITCH, input().copy(stage = stage(verified = false)))
        assertEquals(SwitchGates.R_IMAGE_FILES_CHANGED, changed.find(SwitchGates.ID_IMAGE)?.finding?.reason)

        val noGz = eval(GatePhase.PREPARE, input().copy(imageFile = null))
        assertEquals(SwitchGates.R_IMAGE_NONE, noGz.find(SwitchGates.ID_IMAGE)?.finding?.reason)

        val stagedRaw = eval(GatePhase.PREPARE, input().copy(imageFile = null, onPhone = StagedRaw(lineage.rawBytes, lineage.rawSha256)))
        assertEquals(Level.OK, stagedRaw.level(SwitchGates.ID_IMAGE))
    }

    @Test
    fun logsAndZygiskAndSpaceGates() {
        val logsWarn = eval(GatePhase.SWITCH, input().copy(uncollectedLogs = true))
        assertEquals(Level.WARN, logsWarn.level(SwitchGates.ID_LOGS))
        assertEquals(SwitchGates.R_LOGS, logsWarn.find(SwitchGates.ID_LOGS)?.finding?.reason)

        val oldZygisk = eval(GatePhase.SWITCH, input().copy(zygiskNextVersionCode = 864))
        assertEquals(Level.WARN, oldZygisk.level(SwitchGates.ID_ZYGISK))
        assertEquals(SwitchGates.R_ZYGISK_OLD, oldZygisk.find(SwitchGates.ID_ZYGISK)?.finding?.reason)
        assertNull(eval(GatePhase.SWITCH, input().copy(zygiskNextVersionCode = 880)).find(SwitchGates.ID_ZYGISK))

        val need = SpaceRule.toStage(lineage, set)
        assertEquals(Level.OK, eval(GatePhase.PREPARE, input().copy(freeBytes = need)).level(SwitchGates.ID_SPACE))
        assertEquals(SwitchGates.R_SPACE_SETUP, eval(GatePhase.PREPARE, input().copy(freeBytes = need - 1)).find(SwitchGates.ID_SPACE)?.finding?.reason)
        assertEquals(SwitchGates.R_SPACE_SWITCH, eval(GatePhase.SWITCH, input().copy(freeBytes = SpaceRule.LAUNCH_BYTES - 1)).find(SwitchGates.ID_SPACE)?.finding?.reason)
    }

    @Test
    fun untestedAndCarrierAckWorkWhenSynthesized() {
        val untested = a53.copy(choices = listOf(ImageChoice(lineage.id, set.id, tested = false)))
        val r = SwitchGates.evaluate(GatePhase.SWITCH, input().copy(stage = stage(p = untested)), listOf(untested))
        assertFalse(r.blocked)
        assertEquals(Level.WARN, r.level(SwitchGates.ID_PROFILE))
        val ack = SwitchGates.untestedAck(untested, lineage)
        assertEquals(ack, r.find(SwitchGates.ID_PROFILE)?.ack)
        assertFalse(r.allows(emptySet()))
        assertTrue(r.allows(r.toTick.mapNotNull { it.tickKey }.toSet()))
        assertNotNull(SwitchGates.untestedAck(null))
    }
}
