package com.fahadali.oneos.core.model

import java.util.Locale

/** What a staged file is for; decides its mode and how the scripts use it (guest_prepare.sh). */
enum class FileRole {
    /** services / framework / telephony-common: carry the storage, installd, sysrq and other guards; pinned by guards.txt. */
    GUARD_JAR,
    /**
     * `vold_patched`: the guest's own vold registered as "gvld" (docs/inplace-return.md),
     * bound over the guest's /system/bin/vold; pinned by guards.txt together with [GUARD_LIBRARY]
     * and the guarded services.jar (both binaries or none); executable.
     */
    GUARD_EXECUTABLE,
    /** `service.incremental_patched.so`: looks up "gvld"; pinned by guards.txt with [GUARD_EXECUTABLE]. */
    GUARD_LIBRARY,
    /** Other patched framework jars (bound over the guest's). */
    FRAMEWORK_JAR,
    /** `guards.txt`: SHA-256 of the guard files, checked by guest_prepare.sh before anything is bound. */
    GUARDS,
    /** The guest surfaceflinger with the composer skip; executable. */
    SURFACEFLINGER,
    /** An APEX payload loop-mounted read-only for the guest. */
    APEX_PAYLOAD,
    /** A resource overlay copied into the guest (its /oem/overlay). */
    RRO,
    /** `OneOSGuest.apk` (app-android/guest): DualOS inside the guest, Back to One UI; copied into the guest's /oem/app. */
    GUEST_APP,
    /**
     * `oneos-telephony.jar`: the signal poll TeleService's own
     * TelephonyComponentFactory injection loads, from a read-only overlay layer
     * on the guest's system_ext.
     */
    INJECTION_JAR,
    /**
     * `oneos-telephony.sha256`: the guest's telephony-common.jar and
     * TeleService.apk the jar was built and checked against; guest_prepare.sh
     * uses the jar only while both still match.
     */
    INJECTION_SHA256,
    /**
     * Guest fixes shipped beside the framework jars (camera bundles, APEX requirements, etc.).
     * Never run in One UI.
     */
    GUEST_FIX,
}

/** One file of a set, by its flat name in `$STAGE`. */
data class PackageFile(val name: String, val role: FileRole) {
    /**
     * Mode in `$STAGE`: surfaceflinger_patched and vold_patched are executable;
     * everything else 0644. Root's `file_mode` agrees.
     */
    val mode: String get() = if (role in EXECUTABLE) "0755" else "0644"

    companion object {
        private val EXECUTABLE = setOf(FileRole.SURFACEFLINGER, FileRole.GUARD_EXECUTABLE)

        /** What guards.txt pins: the guard jars and the guest vold's two binaries. */
        val GUARDED: Set<FileRole> = setOf(FileRole.GUARD_JAR, FileRole.GUARD_EXECUTABLE, FileRole.GUARD_LIBRARY)
    }
}

/**
 * The patched files staged next to one image (AA6), as data: flat names and
 * roles only. The SHA-256 of each file is in the checked-in
 * `app-android/packages/<id>.sha256` (the packaging task fails when a built
 * file differs), so this list and that file must name the same files; see
 * [check].
 */
data class PackageSet(
    val id: String,
    /** [ImageRef.id] the overlays were built from; never mix sets and images. */
    val imageId: String,
    /** Server/build folder the packaging task imports, relative to the repo root. */
    val sourceDir: String,
    val files: List<PackageFile>,
    /** Files in [sourceDir] this set leaves out on purpose. */
    val omitted: List<String>,
    /** Total bytes of [files], for the free-space gate. */
    val approxBytes: Long,
    /**
     * The set stages the in-guest app (Back to One UI in the other
     * system), so the Switch dialog may say so: guest_prepare.sh copies a
     * staged `OneOSGuest.apk` into the guest's /oem/app.
     */
    val hasReturnApp: Boolean = false,
) {
    val names: List<String> get() = files.map { it.name }

    fun file(name: String): PackageFile? = files.firstOrNull { it.name == name }

    /**
     * Problems with a `<sha256>  <name>` list for this set (the checked-in
     * packages/<id>.sha256 or a built folder's sha256sum); empty = it names
     * exactly [files], once each, with well-formed hashes.
     */
    fun check(shaList: String): List<String> {
        val parsed = ShaList.parse(shaList)
        val p = parsed.problems.toMutableList()
        val listed = parsed.entries.keys
        for (n in names) if (n !in listed) p += "$id: $n missing"
        for (n in listed) {
            when {
                n in omitted -> p += "$id: $n is left out of this set"
                file(n) == null -> p += "$id: $n is not in this set"
            }
        }
        return p
    }

    /**
     * Problems with this set's guards.txt against the set's own hashes: it
     * must list every guard file ([PackageFile.GUARDED]: the guard jars and
     * the guest vold's vold_patched and service.incremental_patched.so),
     * nothing else, with the file's SHA-256
     * (what oneos_switch.sh and guest_prepare.sh check before binding them).
     * A set with the GmsCore payload also needs guards.txt's comment lines to
     * name the guards Google services need ([PackageSets.GMS_GUARDS]), or
     * every switch with it would be refused.
     */
    fun checkGuards(guardsText: String, shaList: String): List<String> {
        val guards = ShaList.parse(guardsText)
        val shas = ShaList.parse(shaList).entries
        val p = guards.problems.map { "guards.txt: $it" }.toMutableList()
        val jars = files.filter { it.role in PackageFile.GUARDED }.map { it.name }
        for (j in jars) {
            val g = guards.entries[j]
            when {
                g == null -> p += "guards.txt: $j missing"
                shas[j] == null -> p += "$j has no hash in the set list"
                g != shas[j] -> p += "guards.txt: $j is ${g.take(12)}..., the set has ${shas[j]!!.take(12)}..."
            }
        }
        for (n in guards.entries.keys) if (n !in jars) p += "guards.txt: $n is not a guard jar of $id"
        if (file(PackageSets.GMS_PAYLOAD) != null) {
            // As the scripts read them: any comment line, whole words (grep -w).
            val words = guardsText.lineSequence().map { it.removeSuffix("\r") }.filter { it.startsWith("#") }
                .flatMap { it.split(Regex("[^A-Za-z0-9_]+")) }.toSet()
            for (t in PackageSets.GMS_GUARDS) if (t !in words) p += "guards.txt does not name the $t guard Google services need"
        }
        return p
    }
}

object PackageSets {
    /** LineageOS 23, the Galaxy A53 5G's Android 16 QPR0 GSI. */
    const val ID_LINEAGE23 = "lineage23"

    /** Project Infinity-X 3.5, Android 16 QPR0 / BP2A VANILLA EXT4 GSI. */
    const val ID_INFINITYX35 = "infinityx35"

    /** Never mounted (guest_prepare.sh only keeps the host module); left out of every set. */
    const val TETHERING = "tethering_payload.img"

    /** Source-built cellbroadcast stays on One UI's side. */
    const val CELLBROADCAST = "cellbroadcast_payload.img"

    /** GmsCore product APEX payload if an image ships its own GApps. */
    const val GMS_PAYLOAD = "gmssystem_payload.img"

    /** DualOS inside the guest (app-android/guest): Back to One UI from its screen or tile ([PackageSet.hasReturnApp]). */
    const val GUEST_APP = "OneOSGuest.apk"

    /**
     * The guards guards.txt's comment lines must name before a session
     * presents Google services (guest_prepare.sh and oneos_switch.sh refuse
     * otherwise): the phone's gatekeeper, FRP partition and apexd stay One
     * UI's, and guest apps get app ids from 15000.
     */
    val GMS_GUARDS: List<String> = listOf("gatekeeper", "pdb", "apexd", "appid15000")

    /** The APEX payloads every set carries (guest_prepare.sh mounts each one). */
    private val COMMON_PAYLOADS: List<String> = listOf(
        "art", "i18n", "runtime", "sdkext", "crashrecovery", "extservices", "permission", "wifi",
        "appsearch", "mediaprovider", "adservices", "rkpd", "healthfitness", "ondevicepersonalization",
        "configinfrastructure",
    )

    /** Shared file layout for A53 Android 16 QPR0 VANILLA GSIs (`lineage23`, `infinityx35`). */
    private fun a16VanillaFiles(): List<PackageFile> = listOf(
        PackageFile("services_patched.jar", FileRole.GUARD_JAR),
        PackageFile("framework_patched.jar", FileRole.GUARD_JAR),
        PackageFile("vold_patched", FileRole.GUARD_EXECUTABLE),
        PackageFile("service.incremental_patched.so", FileRole.GUARD_LIBRARY),
        PackageFile("libcamera_client_patched.so", FileRole.GUARD_LIBRARY),
        PackageFile("telephony-common_patched.jar", FileRole.GUARD_JAR),
        PackageFile("guards.txt", FileRole.GUARDS),
        PackageFile("oneos-telephony.jar", FileRole.INJECTION_JAR),
        PackageFile("oneos-telephony.sha256", FileRole.INJECTION_SHA256),
        PackageFile("OneOSPhoneInjection.apk", FileRole.RRO),
        PackageFile(GUEST_APP, FileRole.GUEST_APP),
        PackageFile("OneOSPixelStatusBar.apk", FileRole.RRO),
        PackageFile("OneOSFoldCutout.apk", FileRole.RRO),
        PackageFile("OneOSTabletQs.apk", FileRole.RRO),
        PackageFile("oneos-apex-requires.txt", FileRole.GUEST_FIX),
    ) + (COMMON_PAYLOADS + listOf("bt", "nfcservices", "conscrypt", "profiling"))
        .map { PackageFile("${it}_payload.img", FileRole.APEX_PAYLOAD) } +
        PackageFile("OneOSPixelDoze.apk", FileRole.RRO) +
        PackageFile("samcam_a.tar", FileRole.GUEST_FIX) +
        PackageFile("samcam_bar.tar", FileRole.GUEST_FIX)

    /**
     * LineageOS 23, the Galaxy A53 5G's Android 16 QPR0 GSI of 2026-01-09, from
     * out/overlays_lineage23_r1: its camera and installd tables are the phone's own.
     * No gmssystem payload: guest_google.sh lays One UI's Google apps over the guest's
     * product partition (set id "lineage23").
     */
    val LINEAGE23 = PackageSet(
        id = ID_LINEAGE23,
        imageId = Images.ID_LINEAGE23,
        sourceDir = "out/overlays_lineage23_r1",
        files = a16VanillaFiles(),
        omitted = listOf(TETHERING, CELLBROADCAST),
        approxBytes = 549_716_536L,
        hasReturnApp = true,
    )

    /**
     * Project Infinity-X 3.5 (BP2A.250805.005, Android 16 QPR0 VANILLA EXT4 GSI of 2026-04-04),
     * from out/overlays_infinityx35_r1: PHH ISehRadio stripped in telephony-common_patched.jar,
     * One UI Google apps staged over product partition via guest_google.sh (set id "infinityx35").
     */
    val INFINITYX35 = PackageSet(
        id = ID_INFINITYX35,
        imageId = Images.ID_INFINITYX35,
        sourceDir = "out/overlays_infinityx35_r1",
        files = a16VanillaFiles(),
        omitted = listOf(TETHERING, CELLBROADCAST),
        approxBytes = 556_814_105L,
        hasReturnApp = true,
    )

    val ALL: List<PackageSet> = listOf(LINEAGE23, INFINITYX35)

    fun byId(id: String, sets: List<PackageSet> = ALL): PackageSet? = sets.firstOrNull { it.id == id }
}

/**
 * `<sha256>  <name>` lines, as sha256sum prints them (a `*` before the name,
 * binary mode, is accepted). Used for packages/<set>.sha256, guards.txt and
 * `$STAGE/files.sha256`. Lines starting with `#` are comments; names are flat
 * (no `/`, no leading `.`, no spaces).
 */
object ShaList {
    data class Parsed(val entries: Map<String, String>, val problems: List<String>)

    private val LINE = Regex("""^([0-9a-fA-F]{64}) [ *](\S+)$""")
    private val NAME = Regex("""^[A-Za-z0-9_][A-Za-z0-9._+-]*$""")

    fun parse(text: String): Parsed {
        val entries = linkedMapOf<String, String>()
        val problems = mutableListOf<String>()
        text.lineSequence().forEachIndexed { i, raw ->
            val line = raw.removeSuffix("\r")
            if (line.isBlank() || line.startsWith("#")) return@forEachIndexed
            val m = LINE.matchEntire(line)
            if (m == null) {
                problems += "line ${i + 1} is not '<sha256>  <name>'"
                return@forEachIndexed
            }
            val name = m.groupValues[2]
            if (!NAME.matches(name)) {
                problems += "line ${i + 1}: '$name' is not a flat file name"
                return@forEachIndexed
            }
            if (name in entries) problems += "$name listed twice"
            entries[name] = m.groupValues[1].lowercase(Locale.ROOT)
        }
        return Parsed(entries, problems)
    }

    /** sha256sum layout, sorted by name, one trailing newline. */
    fun render(entries: Map<String, String>): String =
        entries.entries.sortedBy { it.key }.joinToString("") { "${it.value}  ${it.key}\n" }
}
