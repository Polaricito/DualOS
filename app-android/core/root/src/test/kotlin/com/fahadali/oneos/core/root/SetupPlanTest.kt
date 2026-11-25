package com.fahadali.oneos.core.root

import com.fahadali.oneos.core.model.Images
import com.fahadali.oneos.core.model.PackageSets
import com.fahadali.oneos.core.model.Profiles
import com.fahadali.oneos.core.model.StageMarker
import com.fahadali.oneos.core.model.SystemFamily
import com.fahadali.oneos.core.root.RootScript.BeginMode
import com.fahadali.oneos.core.root.StageFixtures.prepared
import com.fahadali.oneos.core.root.StageFixtures.status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Setup picks what to do with the stage itself; nothing is ever deleted, and another system's data is never kept. */
class SetupPlanTest {
    private val a53 = Profiles.A53
    private val lineage = Images.LINEAGE23
    private val set = PackageSets.LINEAGE23
    private val other = Images.LINEAGE23.copy(id = "infinityx-a53", name = "Infinity-X", family = SystemFamily.INFINITYX)
    private val app = 148

    @Test
    fun selfConsistentOldPayloadsStillRequireTheNewApksUpdate() {
        val st = status(prepared(a53, lineage, set, versionCode = app))
        assertTrue(st.stageState(a53, st.hashes)!!.filesVerified)
        val currentApk = st.hashes + ("OneOSBootStatus.dex" to "e".repeat(64))
        assertFalse(st.stageState(a53, currentApk)!!.filesVerified)
        assertFalse(st.stageState(a53, emptyMap())!!.filesVerified)
    }

    @Test
    fun interruptedUpdateRemainsAnUpdateWithoutTheRawVerificationRecord() {
        val complete = prepared(a53, lineage, set, versionCode = app)
        val interrupted = status(complete + ("stage.marker" to "updating\n${complete.getValue("stage.marker")}"))
            .copy(rawId = null)
        assertFalse(interrupted.ours)
        assertTrue(interrupted.partial)
        assertNull(interrupted.marker)
        assertEquals(lineage.id, interrupted.pendingUpdate?.imageId)
        assertFalse(interrupted.stageState(a53)!!.filesVerified)
        val retry = SetupPlan.decide(interrupted, lineage, a53, app, hasDownload = true)!!
        assertEquals(SetupPlan.Kind.UPDATE, retry.kind)
        assertEquals(BeginMode.KEEP, retry.mode)
        assertEquals(SetupPlan.Source.ON_PHONE, retry.source)
        assertTrue(SetupPlan.updateWithoutDownload(interrupted, lineage, a53, app))
        assertFalse(SetupPlan.reusesInstalled(interrupted.copy(rawSize = 1), lineage, a53, app))
        val aside = SetupPlan.decide(interrupted, other, a53, app, false)!!
        assertEquals(BeginMode.ASIDE, aside.mode)
        assertTrue(aside.setsAsideData)
    }

    @Test
    fun invalidUpdateCheckpointDoesNotSupplyAnInstalledIdentity() {
        val complete = prepared(a53, lineage, set, versionCode = app)
        val broken = status(complete + ("stage.marker" to "updating\nnot a stage marker"))
        assertNull(broken.marker)
        assertNull(broken.pendingUpdate)
        assertFalse(broken.stageState(a53)!!.filesVerified)
    }

    @Test
    fun nothingThereStartsNew() {
        val p = SetupPlan.decide(StageFixtures.absent, lineage, a53, app, hasDownload = true)!!
        assertEquals(BeginMode.NEW, p.mode)
        assertEquals(SetupPlan.Kind.NEW, p.kind)
        assertEquals(SetupPlan.Source.DOWNLOAD, p.source)
        assertFalse(p.setsAsideData)
    }

    @Test
    fun theSameSystemKeepsItsAppsAndData() {
        val st = status(prepared(a53, lineage, set, versionCode = app))
        val p = SetupPlan.decide(st, lineage, a53, app, hasDownload = false)!!
        assertEquals(BeginMode.KEEP, p.mode)
        assertTrue(p.keepsData)
        assertEquals(SetupPlan.Kind.REPAIR, p.kind)
        assertEquals(SetupPlan.Source.ON_PHONE, p.source)
        assertFalse(p.needsDownload)
        assertEquals(SetupPlan.Source.ON_PHONE, SetupPlan.decide(st, lineage, a53, app, hasDownload = true)!!.source)
    }

    @Test
    fun anAppUpdateNeedsNoDownload() {
        val st = status(prepared(a53, lineage, set, versionCode = app - 1))
        val p = SetupPlan.decide(st, lineage, a53, app, hasDownload = false)!!
        assertEquals(SetupPlan.Kind.UPDATE, p.kind)
        assertEquals(BeginMode.KEEP, p.mode)
        assertEquals(SetupPlan.Source.ON_PHONE, p.source)
        assertTrue(SetupPlan.updateWithoutDownload(st, lineage, a53, app))
        assertFalse(SetupPlan.updateWithoutDownload(status(prepared(a53, lineage, set, versionCode = app)), lineage, a53, app))
        assertTrue(SetupPlan.reusesInstalled(status(prepared(a53, lineage, set, versionCode = app)), lineage, a53, app))
        assertEquals(SetupPlan.Kind.REPAIR, SetupPlan.decide(status(prepared(a53, lineage, set, versionCode = app)), lineage, a53, app, false)!!.kind)
    }

    @Test
    fun anotherFileSetIsAnUpdate() {
        val st = status(prepared(a53, lineage, set, versionCode = app).let {
            it + ("stage.marker" to StageMarker(app, a53.id, lineage.id, "old-set", 1).toJson())
        })
        assertEquals(SetupPlan.Kind.UPDATE, SetupPlan.decide(st, lineage, a53, app, false)!!.kind)
        val wrongData = SetupPlan.decide(
            status(prepared(a53, lineage, set, versionCode = app, guest = "packages ${StageStatus.INFINITYX_WORD}")),
            lineage, a53, app, false,
        )!!
        assertEquals(BeginMode.ASIDE, wrongData.mode)
        assertTrue(wrongData.setsAsideData)
    }

    @Test
    fun aSystemRootNoLongerVouchesForNeedsTheDownload() {
        val st = status(prepared(a53, lineage, set, versionCode = app - 1, rawVerified = false))
        assertNull(st.rawVerifiedSha256)
        assertEquals(SetupPlan.Source.DOWNLOAD, SetupPlan.decide(st, lineage, a53, app, hasDownload = true)!!.source)
        assertEquals(SetupPlan.Source.ON_PHONE, SetupPlan.decide(st, lineage, a53, app, hasDownload = false)!!.source)
        assertFalse(SetupPlan.updateWithoutDownload(st, lineage, a53, app))
    }

    @Test
    fun anotherSystemIsSetAsideNeverKept() {
        val st = status(prepared(a53, lineage, set, versionCode = app))
        val p = SetupPlan.decide(st, other, a53, app, hasDownload = true)!!
        assertEquals(BeginMode.ASIDE, p.mode)
        assertEquals(SetupPlan.Kind.REPLACE, p.kind)
        assertEquals(SetupPlan.Source.DOWNLOAD, p.source)
        assertTrue(p.setsAsideData)
        assertEquals(lineage.id, p.asideImageId)
        assertFalse(SetupPlan.updateWithoutDownload(st, other, a53, app))

        val empty = status(prepared(a53, lineage, set, versionCode = app, guest = "packages none"))
        val q = SetupPlan.decide(empty, other, a53, app, hasDownload = true)!!
        assertEquals(BeginMode.ASIDE, q.mode)
        assertFalse(q.setsAsideData)
        assertNull(q.asideImageId)
    }

    @Test
    fun aStageWeDidNotPrepareIsSetAside() {
        val unsafe = status(
            prepared(a53, lineage, set, versionCode = app, extra = mapOf("stage.dir" to "dir 2000 2000 777\n")),
            missing = setOf("stage.marker"),
        )
        val p = SetupPlan.decide(unsafe, lineage, a53, app, hasDownload = true)!!
        assertEquals(BeginMode.ASIDE, p.mode)
        assertTrue(p.setsAsideData)
        assertNull(p.asideImageId)

        val foreign = status(
            prepared(a53, lineage, set, versionCode = app, guest = "packages ${StageStatus.INFINITYX_WORD}", extra = mapOf("stage.list" to "f 0 644 10 x.log\nd 0 755 4096 data\n")),
            missing = setOf("stage.marker"),
        )
        assertTrue(foreign.foreign)
        assertEquals(listOf(BeginMode.ASIDE), foreign.beginModes(lineage.id))
        val toOther = SetupPlan.decide(foreign, lineage, a53, app, true)!!
        assertEquals(BeginMode.ASIDE, toOther.mode)
        assertTrue(toOther.setsAsideData)
    }

    @Test
    fun aSetupThatStoppedIsKeptOnlyWhenItCanBeThisSystem() {
        val half = status(prepared(a53, lineage, set, versionCode = app, guest = "packages none"), missing = setOf("stage.marker"))
        assertTrue(half.partial)
        val p = SetupPlan.decide(half, lineage, a53, app, hasDownload = true)!!
        assertEquals(BeginMode.KEEP, p.mode)
        assertEquals(SetupPlan.Kind.REPAIR, p.kind)

        val withData = status(prepared(a53, lineage, set, versionCode = app), missing = setOf("stage.marker"))
        assertEquals(BeginMode.KEEP, SetupPlan.decide(withData, lineage, a53, app, true)!!.mode)
        assertEquals(BeginMode.ASIDE, SetupPlan.decide(withData, other, a53, app, true)!!.mode)

        val unverified = status(prepared(a53, lineage, set, versionCode = app, rawVerified = false), missing = setOf("stage.marker"))
        assertEquals(BeginMode.ASIDE, SetupPlan.decide(unverified, lineage, a53, app, true)!!.mode)
    }

    @Test
    fun aLinkInTheStagesPlaceIsRefused() {
        val link = status(prepared(a53, lineage, set, versionCode = app, extra = mapOf("stage.dir" to "link\n")))
        assertNull(SetupPlan.decide(link, lineage, a53, app, true))
    }
}
