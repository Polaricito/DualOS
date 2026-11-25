package com.fahadali.oneos.core.model

import org.junit.Assert.assertTrue
import org.junit.Test

class PatchManifestTest {
    private val trusted = setOf("Polaricito/DualOS", "thatjoshguy67/OneOS-files")
    private val set = PackageSets.LINEAGE23
    private val image = Images.LINEAGE23
    /** The app's own list: every file "a..." except the ones a test changes. */
    private val base = set.names.associateWith { "a".repeat(64) }
    private val valid = PatchManifest(1, set.id, image.rawSha256, "test-1", set.names.map {
        PatchManifest.File(it, "a".repeat(64), 1024, "gh:Polaricito/DualOS:sets-1")
    })
    private fun PatchManifest.ok() = check(set, image, trusted, base = base).isEmpty()
    private fun replaced(name: String) = valid.copy(files = valid.files.map { if (it.name == name) it.copy(sha256 = "c".repeat(64)) else it })

    @Test fun acceptsSupportedImagesAndTrustedRepository() {
        assertTrue(valid.ok())
    }
    @Test fun protectsGuardsPayloadsAndGuestApp() {
        for (name in listOf("guards.txt", "services_patched.jar", "vold_patched", "service.incremental_patched.so",
                "telephony-common_patched.jar", "framework_patched.jar", "art_payload.img", "samcam_a.tar", "OneOSGuest.apk")) {
            if (name in set.names) assertTrue(name, !replaced(name).ok())
        }
        // A snapshot made for another app version (older payloads) is refused, not staged.
        assertTrue(!valid.check(set, image, trusted, base = base + ("guards.txt" to "b".repeat(64))).isEmpty())
        assertTrue(!valid.check(set, image, trusted, base = base - "guards.txt").isEmpty())
    }
    @Test fun rejectsOtherImagesSetsAndUnknownSchemas() {
        assertTrue(!valid.copy(imageSha256 = "b".repeat(64)).ok())
        assertTrue(!valid.copy(setId = "other-set").ok())
        assertTrue(!valid.copy(schema = 2).ok())
    }
    @Test fun rejectsScriptsMissingFilesAndDuplicates() {
        assertTrue(!valid.copy(files = valid.files + valid.files.first().copy(name = "guest_start.sh")).ok())
        assertTrue(!valid.copy(files = valid.files.drop(1)).ok())
        assertTrue(!valid.copy(files = valid.files + valid.files.first()).ok())
    }
    @Test fun rejectsUntrustedSourcesInvalidHashesAndOversizeFiles() {
        for (file in listOf(valid.files.first().copy(source = "gh:someone/Other:latest"),
            valid.files.first().copy(sha256 = "invalid"), valid.files.first().copy(bytes = 0),
            valid.files.first().copy(bytes = 268435457))) {
            assertTrue(!valid.copy(files = listOf(file) + valid.files.drop(1)).ok())
        }
    }
}
