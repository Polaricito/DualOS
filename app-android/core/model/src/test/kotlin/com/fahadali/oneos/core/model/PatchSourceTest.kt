package com.fahadali.oneos.core.model

import org.junit.Assert.*
import org.junit.Test

class PatchSourceTest {
    private val target = PatchSource.Target(Profiles.A53.id, Images.ID_LINEAGE23, PackageSets.ID_LINEAGE23)
    private val source = PatchSource("another-contributor", "Another contributor's patches", "another-contributor/patches", "testing", listOf(target))

    @Test fun targetMatchingAndStableChannelWork() {
        assertTrue(PatchSource.STABLE.check().isEmpty())
        assertTrue(PatchSource.STABLE.supports(Profiles.A53.id, Images.ID_LINEAGE23, PackageSets.ID_LINEAGE23))
        assertTrue(source.supports(target.profile, target.image, target.set))
        assertFalse(source.supports("other", target.image, target.set))
        assertFalse(source.supports(target.profile, "other-image", target.set))
        assertFalse(source.supports(target.profile, target.image, "other-set"))
    }
    @Test fun rejectsEmptyOrUnsupportedTargetsAndNonReplaceableSets() {
        assertTrue(source.copy(targets = emptyList()).check().isNotEmpty())
        assertTrue(source.copy(targets = listOf(target.copy(profile = "unknown"))).check().isNotEmpty())
        assertTrue(source.copy(targets = listOf(target.copy(set = "unknown"))).check().isNotEmpty())
        assertTrue(source.copy(targets = listOf(target, target)).check().isNotEmpty())
        // lineage23 carries no REPLACEABLE contributor target.
        assertTrue(source.check().any { "replace" in it })
    }
    @Test fun repositoriesAndReleaseTagsCannotInjectUrlsOrPaths() {
        assertTrue(source.copy(repository = "https://example.com/patches").check().isNotEmpty())
        assertTrue(source.copy(repository = "../patches").check().isNotEmpty())
        assertTrue(source.copy(tag = "../../other").check().isNotEmpty())
    }
    @Test fun manifestTrustBelongsToTheSelectedContributor() {
        val set = PackageSets.LINEAGE23
        val manifest = PatchManifest(1, set.id, Images.LINEAGE23.rawSha256, "test", set.names.map {
            PatchManifest.File(it, "a".repeat(64), 1, "gh:${source.repository}:${source.tag}")
        })
        val base = set.names.associateWith { "a".repeat(64) }
        assertTrue(manifest.check(set, Images.LINEAGE23, base = base).isNotEmpty())
        assertTrue(manifest.check(set, Images.LINEAGE23, setOf(source.repository), base = base).isEmpty())
        val scoped = manifest.copy(profileId = Profiles.A53.id)
        assertTrue(scoped.check(set, Images.LINEAGE23, setOf(source.repository), Profiles.A53.id, base = base).isEmpty())
        assertTrue(scoped.check(set, Images.LINEAGE23, setOf(source.repository), "other", base = base).isNotEmpty())
    }
}
