package com.fahadali.oneos.core.model

import java.util.Locale

/**
 * One image a profile may run, with the file set staged next to it (built
 * from that image by scripts/make-overlays.sh). The staged image decides the
 * set: never another set with that image, never this set with another image.
 */
data class ImageChoice(
    /** [ImageRef.id]. */
    val imageId: String,
    /** [PackageSet.id] staged next to that image on this phone. */
    val packageSetId: String,
    /** A session has run on this phone with this image; false = the untested tick at Switch. */
    val tested: Boolean,
)

/**
 * The phone profile DualOS supports (Galaxy A53 5G exclusive). Matches only when
 * model starts with [modelPrefix] AND `ro.product.device` is [codename] AND SoC is [socModel].
 */
data class PhoneProfile(
    /** Stable id: `ONEOS_PROFILE` in session.env, the stage marker, logs. */
    val id: String,
    /** "Galaxy A53 5G". */
    val label: String,
    /** `SM-A536`: regional variants (B, E, U, U1, N, W, 0) start with it. */
    val modelPrefix: String,
    /** `ro.product.device`, e.g. `a53x`. */
    val codename: String,
    /** `ro.soc.model`, e.g. `s5e8825` (Exynos 1280). */
    val socModel: String,
    /** BAR profile gets the phone form in session.env and no fold steps. */
    val form: FormFactor,
    /** The images this phone may run, each with its set; the first is its usual one. */
    val choices: List<ImageChoice>,
    /** False: known, but this build refuses to switch ([disabledReason] is the BLOCK). */
    val enabled: Boolean = true,
    val disabledReason: String? = null,
    /** Extra switch-time warnings for this phone (no tick needed). */
    val notes: List<Finding> = emptyList(),
) {
    fun matches(model: String, device: String, soc: String): Boolean =
        model.trim().uppercase(Locale.ROOT).startsWith(modelPrefix) &&
            device.trim().equals(codename, ignoreCase = true) &&
            soc.trim().equals(socModel, ignoreCase = true)

    fun matches(f: DeviceFacts): Boolean = matches(f.model, f.device, f.soc.model)

    /** The choice for [imageId]; null = this phone does not take that image. */
    fun choice(imageId: String): ImageChoice? = choices.firstOrNull { it.imageId == imageId }

    /** The images of [choices] this build has, in order. */
    fun images(all: List<ImageRef> = Images.ALL): List<ImageRef> = choices.mapNotNull { Images.byId(it.imageId, all) }

    /** The choice "Choose a system" preselects: the first one that has run on this phone, else the first. */
    val recommended: ImageChoice? get() = choices.firstOrNull { it.tested } ?: choices.firstOrNull()

    /** Some image has run on this phone (the device report's "Tested phone"). */
    val testedWithAnyImage: Boolean get() = choices.any { it.tested }

    /** "SM-A536* / a53x / s5e8825". */
    val ids: String get() = "$modelPrefix* / $codename / $socModel"
}

/** [profile] is the one profile all three ids match; null when none (or, fail closed, more than one) does. */
data class ProfileResolution(
    val profile: PhoneProfile?,
    /** When nothing matched: the profile with the same model prefix (else codename), for the message. */
    val nearest: PhoneProfile?,
    /** What differs from [nearest], e.g. "codename pa3q (profile: a53x)". */
    val mismatches: List<String>,
)

/** Galaxy A53 5G profile built into DualOS. */
object Profiles {
    const val ID_A53 = "a53"

    /** Kept for Gate / Plain rule exhaustiveness. */
    const val ID_NOTE_OLDER_VENDOR = "profile-vendor-older"
    const val R_OLDER_VENDOR = "older-vendor"
    const val ID_NOTE_EXPECT_NO_START = "profile-expect-no-start"
    const val R_EXPECT_NO_START = "expect-no-start"

    /** LineageOS 23 (the Galaxy A53 5G's own Android 16 QPR0 GSI), verified on real A53 hardware. */
    private val LINEAGE23_CHOICE = ImageChoice(Images.ID_LINEAGE23, PackageSets.ID_LINEAGE23, tested = true)

    /** Project Infinity-X 3.5 (Android 16 QPR0 / BP2A VANILLA EXT4 GSI). */
    private val INFINITYX35_CHOICE = ImageChoice(Images.ID_INFINITYX35, PackageSets.ID_INFINITYX35, tested = false)

    /** The Switch tick for [ID_NOTE_EXPECT_NO_START]. */
    fun expectNoStartAck(system: String = Images.OTHER_SYSTEM): String =
        "I understand $system will most likely not start on this phone, and I'll share the session logs after"

    val A53 = PhoneProfile(
        id = ID_A53,
        label = "Galaxy A53 5G",
        modelPrefix = "SM-A536",
        codename = "a53x",
        socModel = "s5e8825",
        form = FormFactor.BAR,
        choices = listOf(LINEAGE23_CHOICE, INFINITYX35_CHOICE),
        notes = emptyList(),
    )

    val BUILT_IN: List<PhoneProfile> = listOf(A53)

    /** `ro.build.version.oneui` values seen on the Galaxy A53 5G (One UI 8.0). */
    val ONE_UI_SEEN: Set<Int> = setOf(80000)

    /** One UI versions whose installd tables the app carries for Android 16 systems (One UI 8.0 and 8.5, [InstalldExpected.accepted]). */
    val ONE_UI_SEEN_A16: Set<Int> = setOf(80000, 80500)

    fun byId(id: String, profiles: List<PhoneProfile> = BUILT_IN): PhoneProfile? = profiles.firstOrNull { it.id == id }

    fun resolve(f: DeviceFacts, profiles: List<PhoneProfile> = BUILT_IN): ProfileResolution =
        resolve(f.model, f.device, f.soc.model, profiles)

    fun resolve(model: String, device: String, soc: String, profiles: List<PhoneProfile> = BUILT_IN): ProfileResolution {
        val hits = profiles.filter { it.matches(model, device, soc) }
        if (hits.size == 1) return ProfileResolution(hits.single(), null, emptyList())
        if (hits.size > 1) {
            return ProfileResolution(null, null, listOf("matches ${hits.size} profiles (${hits.joinToString { it.id }})"))
        }
        val m = model.trim().uppercase(Locale.ROOT)
        val nearest = profiles.firstOrNull { m.startsWith(it.modelPrefix) }
            ?: profiles.firstOrNull { device.trim().equals(it.codename, ignoreCase = true) }
            ?: return ProfileResolution(null, null, emptyList())
        val diff = mutableListOf<String>()
        if (!m.startsWith(nearest.modelPrefix)) diff += "model ${model.trim()} (profile: ${nearest.modelPrefix}*)"
        if (!device.trim().equals(nearest.codename, ignoreCase = true)) {
            diff += "codename ${device.trim().ifEmpty { "unknown" }} (profile: ${nearest.codename})"
        }
        if (!soc.trim().equals(nearest.socModel, ignoreCase = true)) {
            diff += "chip ${soc.trim().ifEmpty { "unknown" }} (profile: ${nearest.socModel})"
        }
        return ProfileResolution(null, nearest, diff)
    }
}

/** Android release names for the SDK levels DualOS meets. */
object AndroidRelease {
    fun name(sdk: Int): String = when (sdk) {
        31 -> "12"
        32 -> "12L"
        33 -> "13"
        34 -> "14"
        35 -> "15"
        36 -> "16"
        37 -> "17"
        else -> "SDK $sdk"
    }
}
