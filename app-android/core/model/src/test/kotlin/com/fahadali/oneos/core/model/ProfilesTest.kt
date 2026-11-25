package com.fahadali.oneos.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilesTest {
    @Test
    fun a53ResolvesAndOtherModelsDoNot() {
        val res = Profiles.resolve(Fixtures.a53())
        assertEquals(Profiles.A53, res.profile)
        assertEquals(FormFactor.BAR, Fixtures.a53().form.form)

        for (other in listOf(
            Bundles.F8U.facts,
            Bundles.S26U.facts,
            Bundles.S25U.facts,
            Bundles.S25.facts,
            Fixtures.a53(model = "SM-A546B"),
            Fixtures.a53(device = "a54x"),
            Fixtures.a53(soc = SocProps("Samsung", "s5e8835", "s5e8835")),
        )) {
            assertNull(other.model, Profiles.resolve(other).profile)
        }
    }

    @Test
    fun builtInTable() {
        assertEquals(listOf(Profiles.A53), Profiles.BUILT_IN)
        val a53 = Profiles.A53
        assertEquals("a53", a53.id)
        assertEquals("Galaxy A53 5G", a53.label)
        assertEquals(listOf("SM-A536", "a53x", "s5e8825"), listOf(a53.modelPrefix, a53.codename, a53.socModel))
        assertEquals(FormFactor.BAR, a53.form)
        assertEquals(
            listOf(
                ImageChoice(Images.ID_LINEAGE23, PackageSets.ID_LINEAGE23, tested = true),
                ImageChoice(Images.ID_INFINITYX35, PackageSets.ID_INFINITYX35, tested = false),
            ),
            a53.choices,
        )
        assertTrue(a53.testedWithAnyImage && a53.enabled)
        assertEquals(setOf(80000), Profiles.ONE_UI_SEEN)
        assertTrue(a53.notes.isEmpty())
    }
}
