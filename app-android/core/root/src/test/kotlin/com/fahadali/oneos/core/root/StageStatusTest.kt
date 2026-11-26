package com.fahadali.oneos.core.root

import com.fahadali.oneos.core.model.Hashes
import com.fahadali.oneos.core.model.ImageRef
import com.fahadali.oneos.core.model.Images
import com.fahadali.oneos.core.model.PackageSet
import com.fahadali.oneos.core.model.PackageSets
import com.fahadali.oneos.core.model.PhoneProfile
import com.fahadali.oneos.core.model.ProbeStatus
import com.fahadali.oneos.core.model.Profiles
import com.fahadali.oneos.core.model.SessionEnv
import com.fahadali.oneos.core.model.ShaList
import com.fahadali.oneos.core.model.StageMarker
import com.fahadali.oneos.core.model.StagedRaw
import com.fahadali.oneos.core.model.SystemFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StageStatusTest {
    private val s = RootScript.STAGE
    private val profile = Profiles.A53
    private val image = Images.LINEAGE23
    private val set = PackageSets.LINEAGE23

    private fun status(out: Map<String, String>, missing: Set<String> = emptySet(), failed: Set<String> = emptySet()): StageStatus {
        val statuses = StageStatus.OUTPUTS.keys.associateWith { id ->
            when (id) {
                in failed -> ProbeStatus.FAILED
                in missing -> ProbeStatus.NOT_PRESENT
                else -> ProbeStatus.OK
            }
        }
        return StageStatus.parse(statuses) { path -> out[StageStatus.OUTPUTS.entries.first { it.value == path }.key] }
    }

    private val names = RootScript.SCRIPTS + set.names
    private val shas = names.associateWith { Hashes.sha256(it) }

    private fun prepared(
        extra: Map<String, String> = emptyMap(),
        image: ImageRef = this.image,
        set: PackageSet = this.set,
        profile: PhoneProfile = this.profile,
    ): Map<String, String> = preparedFor(image, set, profile) + extra

    private fun preparedFor(image: ImageRef, set: PackageSet, profile: PhoneProfile): Map<String, String> {
        val names = RootScript.SCRIPTS + set.names
        val shas = names.associateWith { Hashes.sha256(it) }
        return mapOf(
            "stage.dir" to "dir 0 0 755\n",
            "stage.list" to (names.map { "f 0 644 10 $it" } + listOf("f 0 644 ${image.rawBytes} system.raw", "d 0 755 4096 data", "d 0 755 4096 bin", "f 0 755 30000 bin/oneos_root.sh"))
                .joinToString("\n", postfix = "\n"),
            "stage.marker" to StageMarker(148, profile.id, image.id, set.id, 1_792_000_000).toJson(),
            "stage.sums" to ShaList.render(shas),
            "stage.hashes" to (shas.entries.joinToString("") { "${it.value}  $s/${it.key}\n" } + "${"c".repeat(64)}  $s/bin/oneos_root.sh\n"),
            "stage.raw" to "now ${image.rawBytes} 64773:1234:${image.rawBytes}:1792000000\nid ${image.rawBytes} ${image.rawSha256} 64773:1234:${image.rawBytes}:1792000000\n",
            "stage.env" to SessionEnv.generate(profile),
            "stage.table" to "${"d".repeat(64)}  $s/host_installd.table\n",
            "stage.mounts" to "root ok\n",
            "stage.bridges" to "",
            "stage.loops" to "",
            "stage.session" to "boot=11111111-2222\n",
            "stage.guest" to "packages org.lineageos.\n",
            "stage.old" to "",
            "stage.tools" to "/data/adb/ksu/bin/busybox\n/data/adb/ksu/bin/resetprop\n",
            "stage.props" to "ro.csc.sales_code=ATT\nro.boot.sales_code=ATT\nro.csc.countryiso_code=US\ngsm.sim.operator.iso-country=us\n",
        )
    }

    @Test
    fun preparedStageVerifies() {
        val st = status(prepared())
        assertTrue(st.complete)
        assertTrue(st.ours)
        assertEquals(GuestData.LINEAGEOS, st.guest)
        assertEquals("c".repeat(64), st.rootScriptSha)
        assertEquals(emptyList<String>(), st.verify(profile, image, set))
        val gate = st.stageState(profile)!!
        assertTrue(gate.filesVerified)
        assertFalse(gate.sessionActive)
        assertEquals(profile.id, gate.marker!!.profileId)
        assertEquals(true, com.fahadali.oneos.core.model.CarrierRules.voLteOnly(st.carrier))
    }

    @Test
    fun aStageAtItsOldPathReadsTheSame() {
        val old = prepared().mapValues { (k, v) -> if (k == "stage.hashes" || k == "stage.table") v.replace("$s/", "${RootScript.LEGACY_STAGE}/") else v }
        assertTrue(old.getValue("stage.hashes").contains("/data/local/tmp/lineage_stage/"))
        val st = status(old)
        assertEquals("c".repeat(64), st.rootScriptSha)
        assertEquals(emptyList<String>(), st.verify(profile, image, set))
        val other = status(prepared(mapOf("stage.hashes" to prepared().getValue("stage.hashes").replace("$s/", "/data/local/tmp/oneos_x/"))))
        assertTrue(other.hashes.isEmpty())
    }

    @Test
    fun changesAreFound() {
        fun problems(extra: Map<String, String>) = status(prepared(extra)).verify(profile, image, set)
        val changed = prepared()["stage.hashes"]!!.replaceFirst(shas.getValue("art_payload.img"), "e".repeat(64))
        assertTrue(problems(mapOf("stage.hashes" to changed)).contains("art_payload.img changed since it was prepared"))
        val leftover = prepared()["stage.list"] + "f 0 644 10 oplus-services_patched.jar\n"
        assertTrue(problems(mapOf("stage.list" to leftover)).any { it.startsWith("oplus-services_patched.jar") })
        val moved = "now ${image.rawBytes} 64773:9999:${image.rawBytes}:1792000001\nid ${image.rawBytes} ${image.rawSha256} 64773:1234:${image.rawBytes}:1792000000\n"
        assertTrue(problems(mapOf("stage.raw" to moved)).contains("system.raw changed since it was verified"))
        val wrongRaw = "now 1 x\nid 123 ${"0".repeat(64)} x\n"
        assertTrue(problems(mapOf("stage.raw" to wrongRaw)).contains("system.raw is not the Galaxy A53 5G image"))
        val env = SessionEnv.generate(profile) + "ONEOS_RETURN_MAX=0\n"
        assertTrue(problems(mapOf("stage.env" to env)).any { it.contains("forbidden key ONEOS_RETURN_MAX") })
    }

    @Test
    fun foreignAndUnsafeStages() {
        val unsafe = status(prepared(mapOf("stage.dir" to "dir 2000 2000 777\n", "stage.guest" to "packages org.lineageos.\n")), missing = setOf("stage.marker"))
        assertFalse(unsafe.safe)
        assertTrue(unsafe.foreign)
        assertEquals(GuestData.LINEAGEOS, unsafe.guest)
        val withSession = status(
            prepared(mapOf("stage.dir" to "dir 0 0 777\n", "stage.session" to "boot=b\nactive\nformat=1\n")),
            missing = setOf("stage.marker"),
        )
        assertFalse(withSession.sessionActive)
        val foreignSafe = status(prepared(), missing = setOf("stage.marker"))
        assertTrue(foreignSafe.safe)
        assertFalse(foreignSafe.ours)
        assertNull(foreignSafe.stageState(profile)!!.marker)
    }

    @Test
    fun beginModes() {
        val k = RootScript.BeginMode.KEEP
        val a = RootScript.BeginMode.ASIDE
        assertEquals(listOf(k, a), status(prepared()).beginModes(null))
        assertEquals(listOf(k, a), status(prepared(), missing = setOf("stage.marker")).beginModes(null))
        assertEquals(listOf(a), status(prepared(mapOf("stage.dir" to "dir 2000 2000 777\n", "stage.guest" to "packages org.lineageos.\n")), missing = setOf("stage.marker")).beginModes(null))
        assertEquals(listOf(a), status(prepared(mapOf("stage.guest" to "packages none\n", "stage.list" to "f 0 644 1 x.log\n")), missing = setOf("stage.marker")).beginModes(null))
        val half = status(prepared(mapOf("stage.guest" to "packages none\n")), missing = setOf("stage.marker"))
        assertTrue(half.partial)
        assertEquals(listOf(k, a), half.beginModes(null))
        assertEquals(emptyList<RootScript.BeginMode>(), status(prepared(mapOf("stage.dir" to "link\n"))).beginModes(null))
        val fresh = status(mapOf("stage.dir" to "dir 0 0 755\n", "stage.list" to "d 0 755 4096 bin\nf 0 755 1 bin/oneos_root.sh\n", "stage.mounts" to "root ok\n"))
        assertEquals(listOf(RootScript.BeginMode.NEW), fresh.beginModes(null))
    }

    @Test
    fun anotherImagesGuestDataIsOnlyMovedAside() {
        val k = RootScript.BeginMode.KEEP
        val a = RootScript.BeginMode.ASIDE
        val otherId = "infinityx-a53"
        val st = status(prepared())
        assertEquals(listOf(k, a), st.beginModes(image.id))
        assertNull(st.otherImageData(image.id))
        assertEquals(listOf(a), st.beginModes(otherId))
        assertEquals(image.id, st.otherImageData(otherId))

        assertEquals(listOf(a), status(prepared(mapOf("stage.guest" to "packages none\n"))).beginModes(otherId))
        val noData = status(prepared(), missing = setOf("stage.guest"))
        assertEquals(GuestData.NONE, noData.guest)
        assertEquals(listOf(k, a), noData.beginModes(otherId))
        assertNull(noData.otherImageData(otherId))

        val v1 = StageMarker(6, profile.id, image.id, null, 1_792_000_000).toJson()
        assertEquals(listOf(a), status(prepared(mapOf("stage.marker" to v1))).beginModes(otherId))
        assertEquals(listOf(k, a), st.beginModes(null))
    }

    @Test
    fun guestDataParsesLineageInfinityXAndLegacyOther() {
        assertEquals(GuestData.LINEAGEOS, status(prepared(mapOf("stage.guest" to "packages org.lineageos.\n"))).guest)
        assertEquals(GuestData.INFINITYX, status(prepared(mapOf("stage.guest" to "packages ${StageStatus.INFINITYX_WORD}\n"))).guest)
        assertEquals(GuestData.OTHER, status(prepared(mapOf("stage.guest" to "packages ${StageStatus.PIXEL_WORD}\n"))).guest)
        assertEquals(GuestData.OTHER, status(prepared(mapOf("stage.guest" to "packages com.oplus.\n"))).guest)
        assertEquals(GuestData.OTHER, status(prepared(mapOf("stage.guest" to ""))).guest)

        assertEquals(SystemFamily.LINEAGE, GuestData.LINEAGEOS.family)
        assertEquals(SystemFamily.INFINITYX, GuestData.INFINITYX.family)
        assertEquals(SystemFamily.EVOX, GuestData.EVOX.family)
        for (g in listOf(GuestData.NONE, GuestData.EMPTY, GuestData.OTHER)) assertNull(g.family)

        val inf = image.copy(id = "infinityx", family = SystemFamily.INFINITYX)
        assertFalse(GuestData.LINEAGEOS.otherSystemThan(image))
        assertTrue(GuestData.LINEAGEOS.otherSystemThan(inf))
        assertTrue(GuestData.INFINITYX.otherSystemThan(image))
        assertFalse(GuestData.INFINITYX.otherSystemThan(inf))
    }

    @Test
    fun runningOrUnfinishedSessions() {
        val mounted = status(prepared(mapOf("stage.mounts" to "root ok\ninit $s/rootfs\nself $s/rootfs\n")))
        assertEquals(listOf("$s/rootfs"), mounted.mounts)
        assertTrue(mounted.sessionActive)
        val hard = status(prepared(mapOf("stage.session" to "boot=new\nactive\nformat=2\nboot_id=old\nepoch=1\n")))
        assertTrue(hard.unfinishedSession)
        assertTrue(hard.sessionActive)
        val same = status(prepared(mapOf("stage.session" to "boot=b\nactive\nformat=2\nboot_id=b\n")))
        assertFalse(same.unfinishedSession)
        assertTrue(same.sessionActive)
    }

    @Test
    fun incompleteChecksFailClosed() {
        assertNull(status(prepared(), failed = setOf("stage.mounts")).stageState(profile))
        assertNull(status(prepared(mapOf("stage.mounts" to ""))).stageState(profile))
        val absent = status(emptyMap(), missing = StageStatus.OUTPUTS.keys - "stage.mounts" - "stage.session" - "stage.bridges" - "stage.loops" - "stage.old" - "stage.tools" - "stage.props")
        assertFalse(absent.exists)
        assertTrue(absent.empty)
    }

    @Test
    fun theMarkersImageAndSetDecide() {
        val st = status(prepared())
        assertEquals(image to set, st.staged(profile))
        assertEquals(emptyList<String>(), st.verify(profile, image, set))
        assertTrue(st.stageState(profile)!!.filesVerified)

        val wrongSet = status(prepared(mapOf("stage.marker" to StageMarker(148, profile.id, image.id, "other-set", 1).toJson())))
        assertTrue(wrongSet.ours)
        assertNull(wrongSet.staged(profile))
        assertFalse(wrongSet.stageState(profile)!!.filesVerified)
        assertTrue(wrongSet.verify(profile, image, set).any { it.contains("the marker names ${image.id} with other-set for ${profile.id}") })
    }

    @Test
    fun anotherSetsFilesAreLeftovers() {
        fun problems(extra: String) =
            status(prepared(mapOf("stage.list" to preparedFor(image, set, profile).getValue("stage.list") + extra)))
                .verify(profile, image, set)
        for (n in listOf("OneOSWallpapersFoldGrid.apk", "gmssystem_payload.img", "surfaceflinger_patched", "oplus-services_patched.jar")) {
            assertTrue(n, problems("f 0 644 10 $n\n").contains("$n is in the stage but was not prepared"))
        }
        for (runtime in listOf("oneos_wdog_feeder.sh", "bring_watch.sh", "guest_panel_fit.sh")) {
            assertFalse(runtime, problems("f 0 700 10 $runtime\n").any { runtime in it })
        }
        assertEquals(emptyList<String>(), problems("f 0 644 10 takeover_app.log\nf 0 644 10 payload_loops\n"))
    }

    @Test
    fun anOlderStageStaysOursButDoesNotSwitch() {
        val v1 = StageMarker(6, profile.id, image.id, null, 1_792_000_000).toJson()
        val st = status(prepared(mapOf("stage.marker" to v1, "stage.session" to "boot=new\nactive\nformat=2\nboot_id=old\n")))
        assertTrue(st.ours)
        assertTrue(st.unfinishedSession)
        assertEquals(listOf(RootScript.BeginMode.KEEP, RootScript.BeginMode.ASIDE), st.beginModes(image.id))
        assertNull(st.staged(profile))
        assertFalse(st.stageState(profile)!!.filesVerified)
        assertTrue(st.verify(profile, image, set).any { it.contains("with no file set") })
    }

    @Test
    fun hiddenGoogleServicesOnlyWhereASessionCanHideThem() {
        val hidden = SessionEnv.generate(profile).replace("ONEOS_GUEST_HIDE_GMS=0", "ONEOS_GUEST_HIDE_GMS=1")
        assertEquals(emptyList<String>(), status(prepared(mapOf("stage.env" to hidden))).verify(profile, image, set))

        val noGms = image.copy(googleServices = false, presentsGms = false)
        assertTrue(status(prepared(mapOf("stage.env" to hidden))).verify(profile, noGms, set).any { it.contains("has no Google services") })

        val presentGms = image.copy(googleServices = true, presentsGms = true)
        assertTrue(status(prepared(mapOf("stage.env" to hidden))).verify(profile, presentGms, set).any { it.contains("cannot be hidden") })
    }

    @Test
    fun rawVerifiedOnlyWhileUnchanged() {
        val st = status(prepared())
        assertEquals(image.rawSha256, st.rawVerifiedSha256)
        assertEquals(StagedRaw(image.rawBytes, image.rawSha256), st.stagedRaw)
        val touched = "now ${image.rawBytes} 64773:1234:${image.rawBytes}:1792000999\nid ${image.rawBytes} ${image.rawSha256} 64773:1234:${image.rawBytes}:1792000000\n"
        assertNull(status(prepared(mapOf("stage.raw" to touched))).rawVerifiedSha256)
        assertEquals(StagedRaw(image.rawBytes, null), status(prepared(mapOf("stage.raw" to touched))).stagedRaw)
        val noId = "now ${image.rawBytes} 64773:1234:${image.rawBytes}:1792000000\n"
        assertNull(status(prepared(mapOf("stage.raw" to noId))).rawVerifiedSha256)
        assertEquals(StagedRaw(null, null), status(prepared(), missing = setOf("stage.raw")).stagedRaw)
    }
}
