package com.fahadali.oneos.core.model

import java.util.Locale

/**
 * The system an image is a build of. Guest data stays with its own system family
 * (StageStatus.beginModes, root's `begin keep`).
 */
enum class SystemFamily { LINEAGE, INFINITYX, EVOX }

/**
 * One OneOS OS image as friends download it (a gzip of the ext4 system
 * image) and as root stages it (`$STAGE/system.raw`). Both hashes are pinned:
 * the app checks the .gz, root checks the unpacked bytes (AA4, XP2).
 */
data class ImageRef(
    val id: String,
    /** "ColorOS 17, Find X9 Ultra build": the technical label (All checks, the findings). */
    val label: String,
    /** "Find X9 Ultra": tells the builds of one system apart in technical messages. */
    val shortName: String,
    /**
     * The system's name as the user sees it, `{system}` in every plain text:
     * "ColorOS 17", "Pixel 11 Fold". Brand names live only here, in the
     * credits and in the legal line.
     */
    val name: String,
    /** Tells two images of the same [name] apart on the cards: "Find N6 global build", "Find X9 Ultra build"; null = none needed. */
    val variant: String?,
    /** One line for its card: "Google's Pixel software." */
    val blurb: String,
    /** The shared link "Download" opens in the browser (no in-app download yet); null = none. */
    val downloadUrl: String?,
    /** Guest SDK; must equal the host's (a guest shares One UI's system services). */
    val sdk: Int,
    val buildId: String,
    /** File name of the upload. */
    val gzName: String,
    val gzBytes: Long,
    val gzSha256: String,
    /** Unpacked size and SHA-256, as staged in `$STAGE/system.raw`. */
    val rawBytes: Long,
    val rawSha256: String,
    /** Where a session has run with it; null = untested. */
    val testedOn: String?,
    /** CPU features the image may not start without (missing = WARN). */
    val cpuRequired: List<String>,
    /** CPU features only some OPPO app and media libraries use (missing = WARN that those features may crash). */
    val cpuUsedByApps: List<String>,
    /**
     * Play services and the Play Store are in the image (the Pixel image; the
     * ColorOS CN builds have none). The image's card says so; a launch may hide
     * them only when [gmsHideable].
     */
    val googleServices: Boolean,
    /**
     * Sessions present the image's own GmsCore: its file sets stage
     * `gmssystem_payload.img`, which guest_prepare.sh mounts and presents
     * (docs/pixel-guest.md, "Google services"). Then Google services cannot be
     * hidden for a launch: oneos_switch.sh refuses `ONEOS_GUEST_HIDE_GMS=1`
     * next to that payload while `ONEOS_GUEST_GMS` is on, and session.env
     * cannot turn that off. [PackageSetsTest] keeps this equal to the sets.
     */
    val presentsGms: Boolean = false,
    /** Whose system it is: its guest data is kept only for an image of the same family. */
    val family: SystemFamily,
) {
    /**
     * The image's own download on the SourceForge mirror (0.4.59, project oneos-mirror, folder
     * images/): one file, resumable, from a mirror near the user. [downloadUrl] (the Drive
     * folder) stays the fallback.
     */
    val mirrorUrl: String get() = "${Images.MIRROR_FILES_URL}$gzName/download"

    /**
     * A launch may hide Google services (`ONEOS_GUEST_HIDE_GMS=1`, [SessionEnv],
     * root's `IMAGES_GMS`): the image has them and its sessions do not present them.
     */
    val gmsHideable: Boolean get() = googleServices && !presentsGms

    /** "Pixel 11 Fold (Android 17)", "ColorOS 17 · Find N6 global build (Android 17)": unique on a phone ([ImagesTest]). */
    val displayName: String
        get() = "$nameWithVariant (Android ${AndroidRelease.name(sdk)})"

    /** "ColorOS 17 · Find N6 global build", or just [name] when there is no [variant]. */
    val nameWithVariant: String get() = listOfNotNull(name, variant).joinToString(" · ")

    /** The gzip ISIZE trailer: the unpacked size mod 2^32 (RFC 1952), an early check before the hash. */
    val gzIsize: Long get() = rawBytes and 0xffff_ffffL

    fun gzMatches(bytes: Long, sha256: String): Boolean =
        bytes == gzBytes && sha256.lowercase(Locale.ROOT) == gzSha256

    fun rawMatches(bytes: Long, sha256: String): Boolean =
        bytes == rawBytes && sha256.lowercase(Locale.ROOT) == rawSha256
}

/**
 * The built-in image table. Sizes and hashes verified on the server on
 * 2026-10-22 (the Pixel 11 Fold image on 2026-10-24): the gunzip of each
 * upload is byte-identical to the server image. 0.3.7 dropped the Find X9
 * Ultra IN build (`coloros17-findx9u-in`); 0.4.11 takes it again.
 */
object Images {
    /**
     * The owner's shared folder the images are downloaded from (owner, 2026-10-24: no in-app download yet).
     * 0.4.41: the owner's Google Drive ("safer and easier for users"); 0.4.42: every image is there
     * (scripts/gofile-to-drive.py checked each against its pinned size and SHA-256).
     */
    const val SHARED_FOLDER_URL = "https://drive.google.com/drive/folders/1KuyNegquzVGxX3WfqyjQW5NErAYNEsUv"
    /** The SourceForge mirror's image folder (0.4.59); [ImageRef.mirrorUrl] adds `<gzName>/download`. */
    const val MIRROR_FILES_URL = "https://sourceforge.net/projects/oneos-mirror/files/images/"

    /** The gofile folder every image up to 0.4.40 was uploaded to; a manual mirror now, not linked from the app. */
    const val GOFILE_FOLDER_URL = "https://github.com/Polaricito/DualOS#supported-systems"

    /** What fixed texts say when no image is known: they never name a system. */
    const val OTHER_SYSTEM = "the other system"

    /** Starts with "lineage": switcher/assets/boot_status.sh names the boot splash from it. */
    const val ID_LINEAGE23 = "lineage23-gsi"

    /** Starts with "infinityx35": switcher/assets/boot_status.sh names the boot splash from it. */
    const val ID_INFINITYX35 = "infinityx35-gsi"

    /** Where root stages the unpacked image. */
    const val STAGE_NAME = "system.raw"

    /** The ext4 superblock magic 0xEF53, little endian at byte 1080 of the unpacked image. */
    const val EXT4_MAGIC_OFFSET = 1080L
    const val EXT4_MAGIC = 0xEF53

    /**
     * LineageOS 23 (0.5.18, experimental): LineageOS's Android 16 QPR0 GSI of 2026-01-09 for the Galaxy A53 5G
     * (MisterZtr's VANILLA EXT4 build, ro.lineage.version 23.0-20260109-VANILLA-EXT4-GSI; BP2A.250805.005,
     * SDK 36.0, security patch 2026-01-01): the A53's own QPR0, so the camera and installd tables need no
     * translation. Like LineageOS 24 it has no Google apps: a session with Google services takes One UI's Play
     * services, Play Store and Services Framework as overlays on the guest's product partition (guest_google.sh,
     * set id "lineage23"), and googleServices is true with presentsGms false.
     */
    val LINEAGE23 = ImageRef(
        id = ID_LINEAGE23,
        label = "LineageOS 23, Galaxy A53 5G GSI",
        shortName = "Galaxy A53 5G",
        name = "LineageOS 23",
        variant = null,
        blurb = "LineageOS for the Galaxy A53 5G (Android 16). Experimental. Google apps come from One UI.",
        downloadUrl = SHARED_FOLDER_URL,
        sdk = 36,
        buildId = "BP2A.250805.005",
        gzName = "OneOS_LineageOS23_GalaxyA53.img.gz",
        gzBytes = 1_250_952_887L,
        gzSha256 = "b6159d3055c66ae2ab5578010a094f2aab96fec35917d837b698a6ec5edaf6e7",
        rawBytes = 2_685_145_088L,
        rawSha256 = "c92d7d5f9962ebc2a93ff4ef4cd155aca254399687f6907593fbf6931c9991fe",
        testedOn = "Galaxy A53 5G",
        cpuRequired = emptyList(),
        cpuUsedByApps = emptyList(),
        googleServices = true,
        presentsGms = false,
        family = SystemFamily.LINEAGE,
    )

    /**
     * Project Infinity-X 3.5 (Android 16 QPR0 / BP2A.250805.005, SDK 36.0, security patch 2026-04-01;
     * Doze-off VANILLA EXT4 GSI of 2026-04-04): ships no Google apps, so One UI's Play services, Play Store
     * and Services Framework are staged over the guest's product partition (guest_google.sh, set id "infinityx35").
     */
    val INFINITYX35 = ImageRef(
        id = ID_INFINITYX35,
        label = "Project Infinity-X 3.5, Galaxy A53 5G GSI",
        shortName = "Infinity-X 3.5",
        name = "Infinity-X 3.5",
        variant = null,
        blurb = "Project Infinity-X 3.5 for the Galaxy A53 5G (Android 16 QPR0). Experimental. Google apps come from One UI.",
        downloadUrl = SHARED_FOLDER_URL,
        sdk = 36,
        buildId = "BP2A.250805.005",
        gzName = "DualOS_InfinityX35_GalaxyA53.img.gz",
        gzBytes = 1_520_656_210L,
        gzSha256 = "09b1b93e24ad5d76ecbb269bdfd03031c735603a368a84aa87a2399bccdf29e3",
        rawBytes = 3_027_066_880L,
        rawSha256 = "98e3b7afada0145326ad9ba8eaf412fefd8e081ad715d621196feafa2adad23f",
        testedOn = null,
        cpuRequired = emptyList(),
        cpuUsedByApps = emptyList(),
        googleServices = true,
        presentsGms = false,
        family = SystemFamily.INFINITYX,
    )

    /**
     * 0.5.10: a stage whose guest data an image (key) made may keep it when its phone is set up with the
     * newer build of the same system (value), as an over-the-air update would: the first Find X9 Ultra IN
     * build is retired for the 1105 build (owner), and nothing else moves. Never the other way.
     */
    val DATA_UPGRADES: Map<String, String> = emptyMap()

    val ALL: List<ImageRef> = listOf(LINEAGE23, INFINITYX35)

    fun byId(id: String, images: List<ImageRef> = ALL): ImageRef? = images.firstOrNull { it.id == id }

    val RETIRED: List<RetiredImage> = emptyList()

    /** "Find N6", a retired image's name, or the id itself when no build knew it. */
    fun shortName(id: String, images: List<ImageRef> = ALL, retired: List<RetiredImage> = RETIRED): String =
        byId(id, images)?.shortName ?: retired.firstOrNull { it.id == id }?.shortName ?: id

    /** The image whose .gz has exactly this size (`OpenableColumns.SIZE`). */
    fun byGzSize(bytes: Long, images: List<ImageRef> = ALL): ImageRef? = images.firstOrNull { it.gzBytes == bytes }

    fun byRawSize(bytes: Long, images: List<ImageRef> = ALL): ImageRef? = images.firstOrNull { it.rawBytes == bytes }

    /**
     * `{system}` in a plain text: [image]'s name ("ColorOS 17", "Pixel 11
     * Fold"), with its variant when [profile] takes another image of the same
     * name, so the two are never confused ("ColorOS 17 · Find N6 global build"); "the
     * other system" when no image is known.
     */
    fun systemName(image: ImageRef?, profile: PhoneProfile? = null, images: List<ImageRef> = ALL): String {
        if (image == null) return OTHER_SYSTEM
        val twin = profile?.images(images).orEmpty().any { it.id != image.id && it.name == image.name }
        return if (twin) image.nameWithVariant else image.name
    }
}

/** An image an earlier build took ([Images.RETIRED]): its name and sizes, so it is told apart from a file that is no OneOS image. */
data class RetiredImage(val id: String, val shortName: String, val gzName: String, val gzBytes: Long, val rawBytes: Long)

/** What the app knows about the file the user picked (ImageActivity, AA1). */
data class ImageFileCheck(
    /** `OpenableColumns.DISPLAY_NAME`, for messages only. */
    val displayName: String?,
    /** `OpenableColumns.SIZE`; null when the provider does not say. */
    val bytes: Long?,
    /** SHA-256 of the whole stream; null until "Check file" has run. */
    val sha256: String? = null,
    /** Bytes read while hashing; null until hashed. */
    val hashedBytes: Long? = null,
)

/** The stage's `system.raw` as root's read-only check found it ("Use the image already on this phone"). */
data class StagedRaw(
    /** Its size now; null = there is none. */
    val bytes: Long?,
    /**
     * The SHA-256 root checked when it staged the file (`system.raw.id`), only
     * while the file is unchanged since (same size and dev:inode:size:mtime);
     * null = not known.
     */
    val verifiedSha256: String? = null,
)

enum class ImageState {
    /** Size and SHA-256 are an image this phone accepts. */
    VERIFIED,
    /** Size matches an image this phone accepts; not hashed yet. */
    NOT_CHECKED,
    /** A OneOS image, but not one this phone accepts. */
    WRONG_FOR_PHONE,
    /** Size or SHA-256 differ: damaged or incomplete download. */
    DAMAGED,
    /** The unpacked image; the app stages only the .img.gz. */
    UNCOMPRESSED,
    /** Not a OneOS image, or the size is unknown. */
    UNKNOWN,
}

data class ImageIdentity(val state: ImageState, val image: ImageRef?, val finding: Finding)

/**
 * Identifies the picked file against the images the phone's profile accepts
 * ([PhoneProfile.choices]) and says which one it is: by size first (cheap,
 * before any hashing), then by SHA-256 after "Check file". Only
 * [ImageState.VERIFIED] is OK; everything else is a BLOCK, and an image the
 * phone does not accept names the ones it does. [profile] null skips the
 * "right image for this phone" check (the profile gate refuses that phone anyway).
 */
object ImageIdentifier {
    const val ID = "gate-image"

    /** [Finding.reason]s of the picked .gz ([identify]). */
    const val R_SIZE_UNKNOWN = "size-unknown"
    const val R_UNCOMPRESSED = "uncompressed"
    const val R_NOT_OURS = "not-ours"
    const val R_WRONG_PHONE = "wrong-phone"
    const val R_NOT_CHECKED = "not-checked"
    const val R_DAMAGED = "damaged"

    /** The .gz of an image an earlier build took ([Images.RETIRED]). */
    const val R_RETIRED = "retired"

    /** [Finding.reason]s of the stage's system.raw ([identifyStaged]). */
    const val R_NO_RAW = "no-raw"
    const val R_RAW_NOT_OURS = "raw-not-ours"
    const val R_RAW_WRONG_PHONE = "raw-wrong-phone"
    const val R_RAW_DAMAGED = "raw-damaged"

    /** "Z Fold (F8U) takes the Find N6 image (….gz) or the Pixel 11 Fold image (….gz)". */
    fun accepts(profile: PhoneProfile?, images: List<ImageRef> = Images.ALL): String {
        val want = profile?.images(images).orEmpty()
        return if (profile != null && want.isNotEmpty()) {
            "${profile.label} takes " + want.joinToString(" or ") { "the ${it.shortName} image (${it.gzName})" }
        } else {
            "the DualOS images are ${images.joinToString(" and ") { it.gzName }}"
        }
    }

    fun identify(
        check: ImageFileCheck,
        profile: PhoneProfile?,
        images: List<ImageRef> = Images.ALL,
        retiredImages: List<RetiredImage> = Images.RETIRED,
    ): ImageIdentity {
        val name = check.displayName?.takeIf { it.isNotBlank() }?.let { "'${it.take(120)}'" } ?: "This file"
        val size = check.hashedBytes ?: check.bytes
        val wantText = accepts(profile, images)

        if (size == null) {
            return ImageIdentity(
                ImageState.UNKNOWN, null,
                block("Image size unknown", "$name has no size. Tap Check file to read it; $wantText.", R_SIZE_UNKNOWN),
            )
        }
        val bySize = images.filter { it.gzBytes == size }
        if (bySize.isEmpty()) {
            val raw = Images.byRawSize(size, images)
            val retired = retiredImages.firstOrNull { it.gzBytes == size }
            return if (raw == null && retired != null) {
                ImageIdentity(
                    ImageState.UNKNOWN, null,
                    block(
                        "Image no longer supported",
                        "$name has the size of the ${retired.shortName} image (${retired.gzName}), which this version of DualOS no longer sets up: $wantText.",
                        R_RETIRED,
                    ),
                )
            } else if (raw != null) {
                val pick = if (profile == null || profile.choice(raw.id) != null) "pick ${raw.gzName}" else wantText
                ImageIdentity(
                    ImageState.UNCOMPRESSED, raw,
                    block("Pick the .img.gz file", "$name is the unpacked ${raw.shortName} image. DualOS unpacks the image itself: $pick.", R_UNCOMPRESSED),
                )
            } else {
                ImageIdentity(
                    ImageState.UNKNOWN, null,
                    block("Not a DualOS image", "$name (${Units.bytes(size)}) is not a DualOS image: $wantText.", R_NOT_OURS),
                )
            }
        }
        val accepted = if (profile == null) bySize else bySize.filter { profile.choice(it.id) != null }
        val sha = check.sha256?.lowercase(Locale.ROOT)
        // Hashed: the image whose size and SHA-256 both match, if any.
        val exact = sha?.let { h -> bySize.firstOrNull { it.gzMatches(size, h) } }
        if (accepted.isEmpty() || (exact != null && exact !in accepted)) {
            val other = exact ?: bySize.first()
            return ImageIdentity(
                ImageState.WRONG_FOR_PHONE, other,
                block("Wrong image for this phone", "This is the ${other.shortName} image; $wantText.", R_WRONG_PHONE),
            )
        }
        val image = exact ?: accepted.first()
        if (sha == null) {
            return ImageIdentity(
                ImageState.NOT_CHECKED, image,
                block("Check the file first", "$name has the size of the ${image.shortName} image. Tap Check file to verify it.", R_NOT_CHECKED),
            )
        }
        if (exact == null || (check.bytes != null && check.bytes != size)) {
            return ImageIdentity(
                ImageState.DAMAGED, image,
                block(
                    "The file is damaged or incomplete",
                    "$name does not match the ${image.shortName} image (SHA-256 ${sha.take(12)}..., expected " +
                        "${image.gzSha256.take(12)}...). Download it again.",
                    R_DAMAGED,
                ),
            )
        }
        return ImageIdentity(
            ImageState.VERIFIED, image,
            Finding(
                ID, Level.OK, "${image.shortName} image verified",
                "${image.label}: ${image.gzName}, ${Units.bytes(image.gzBytes)}, SHA-256 matches.",
            ),
        )
    }

    /**
     * "Use the image already on this phone": the stage's system.raw against the
     * images [profile] accepts, by size and, while root still vouches for the
     * file (system.raw.id), by SHA-256. Its size is enough to prepare (OK,
     * [ImageState.NOT_CHECKED]): root hashes it in keep-raw before anything
     * uses it, and moves it to the attic when it differs.
     */
    fun identifyStaged(
        raw: StagedRaw,
        profile: PhoneProfile?,
        images: List<ImageRef> = Images.ALL,
        retiredImages: List<RetiredImage> = Images.RETIRED,
    ): ImageIdentity {
        val wantText = accepts(profile, images)
        val size = raw.bytes ?: return ImageIdentity(
            ImageState.UNKNOWN, null,
            block("No image on this phone", "The stage has no system.raw. Pick the downloaded file instead: $wantText.", R_NO_RAW),
        )
        val bySize = images.filter { it.rawBytes == size }
        if (bySize.isEmpty()) {
            val retired = retiredImages.firstOrNull { it.rawBytes == size }
            return ImageIdentity(
                ImageState.UNKNOWN, null,
                if (retired != null) {
                    block(
                        "Image no longer supported",
                        "system.raw in the stage has the size of the ${retired.shortName} image, which this version of DualOS no longer sets up. " +
                            "Pick the downloaded file instead: $wantText.",
                        R_RAW_NOT_OURS,
                    )
                } else {
                    block(
                        "Not a DualOS image on this phone",
                        "system.raw in the stage (${Units.bytes(size)}) is not a DualOS image. Pick the downloaded file instead: $wantText.",
                        R_RAW_NOT_OURS,
                    )
                },
            )
        }
        val accepted = if (profile == null) bySize else bySize.filter { profile.choice(it.id) != null }
        val sha = raw.verifiedSha256?.lowercase(Locale.ROOT)
        val exact = sha?.let { h -> bySize.firstOrNull { it.rawMatches(size, h) } }
        if (accepted.isEmpty() || (exact != null && exact !in accepted)) {
            val other = exact ?: bySize.first()
            return ImageIdentity(
                ImageState.WRONG_FOR_PHONE, other,
                block(
                    "Wrong image on this phone",
                    "system.raw in the stage is the ${other.shortName} image; $wantText. Pick the downloaded file instead.",
                    R_RAW_WRONG_PHONE,
                ),
            )
        }
        val image = exact ?: accepted.first()
        return when {
            sha != null && exact == null -> ImageIdentity(
                ImageState.DAMAGED, image,
                block(
                    "Not the ${image.shortName} image",
                    "system.raw has the ${image.shortName} image's size, but root staged it with another SHA-256 " +
                        "(${sha.take(12)}...). Pick ${image.gzName} instead.",
                    R_RAW_DAMAGED,
                ),
            )
            exact != null -> ImageIdentity(
                ImageState.VERIFIED, image,
                Finding(
                    ID, Level.OK, "${image.shortName} image already on this phone",
                    "system.raw is the ${image.shortName} image (${Units.bytes(size)}), checked by root when it was staged. " +
                        "Prepare has root check its SHA-256 again (about a minute).",
                ),
            )
            else -> ImageIdentity(
                ImageState.NOT_CHECKED, image,
                Finding(
                    ID, Level.OK, "${image.shortName} image already on this phone",
                    "system.raw has the size of the ${image.shortName} image (${Units.bytes(size)}). Prepare has root " +
                        "check its SHA-256 (about a minute); if it differs, it is moved to the attic and you pick the .gz.",
                ),
            )
        }
    }

    private fun block(title: String, detail: String, reason: String) = Finding(ID, Level.BLOCK, title, detail, reason)
}
