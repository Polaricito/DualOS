package com.fahadali.oneos.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ApkSeriesTest {
    @Test
    fun everyProfileIsInExactlyOneSeries() {
        for (p in Profiles.BUILT_IN) {
            assertEquals(p.id, 1, ApkSeries.entries.count { p.id in it.profileIds })
        }
        val known = Profiles.BUILT_IN.map { it.id }.toSet()
        for (s in ApkSeries.entries) assertTrue("${s.id} names an unknown profile", known.containsAll(s.profileIds))
    }

    @Test
    fun everySetIsInASeriesDownload() {
        assertEquals(PackageSets.ALL.map { it.id }.toSet(), ApkSeries.entries.flatMap { it.setIds() }.toSet())
    }

    /** app-android/packages/series.txt (what app/build.gradle.kts packs) lists exactly [ApkSeries.setIds]. */
    @Test
    fun theListBuildsUseIsTheModels() {
        val dir = File(System.getProperty("oneos.packagesDir") ?: error("oneos.packagesDir not set"))
        val lines = File(dir, "series.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        val listed = lines.associate { l -> l.split(Regex("\\s+")).let { it.first() to it.drop(1).toSet() } }
        assertEquals(ApkSeries.entries.map { it.id }.toSet(), listed.keys)
        for (s in ApkSeries.entries) assertEquals(s.id, s.setIds(), listed.getValue(s.id))
    }

    @Test
    fun a53SeriesCarriesLineage23AndInfinityX35() {
        assertEquals(listOf(ApkSeries.A53), ApkSeries.entries)
        assertEquals(setOf(PackageSets.ID_LINEAGE23, PackageSets.ID_INFINITYX35), ApkSeries.A53.setIds())
    }

    @Test
    fun a53SeriesDoesNotBlockA53Image() {
        val a53Facts = Fixtures.a53()
        val profile = Profiles.resolve(a53Facts).profile!!
        val image = Images.byId(profile.choices.first().imageId)!!
        val installd = Fixtures.A53_INSTALLD
        fun gate(series: ApkSeries?) = SwitchGates.evaluate(
            GatePhase.PREPARE,
            GateInput(
                facts = a53Facts,
                screenLockSet = false,
                oldSwitcherInstalled = false,
                installd = installd,
                stage = StageState(emptyList(), emptyList(), false, null, false),
                imageFile = ImageFileCheck(image.gzName, image.gzBytes, image.gzSha256, image.gzBytes),
                series = series,
            ),
        ).find(SwitchGates.ID_IMAGE)!!.finding

        assertFalse(gate(ApkSeries.A53).reason == SwitchGates.R_IMAGE_OTHER_SERIES)
        assertFalse(gate(null).reason == SwitchGates.R_IMAGE_OTHER_SERIES)
    }
}
