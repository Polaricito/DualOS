package com.fahadali.oneos.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class PackageSetsTest {
    private val lineage23 = PackageSets.LINEAGE23
    private val infinityx35 = PackageSets.INFINITYX35

    private fun shas(set: PackageSet) = Bundles.text("packages/${set.id}.sha256")
    private fun guards(set: PackageSet) = Bundles.text("packages/${set.id}.guards.txt")

    private val commonPayloads = listOf(
        "art", "i18n", "runtime", "sdkext", "crashrecovery", "extservices", "permission", "wifi",
        "appsearch", "mediaprovider", "adservices", "rkpd", "healthfitness", "ondevicepersonalization",
        "configinfrastructure",
    ).map { "${it}_payload.img" }

    @Test
    fun lineage23AndInfinityX35SetsAsData() {
        assertEquals(listOf(PackageSets.LINEAGE23, PackageSets.INFINITYX35), PackageSets.ALL)
        assertEquals("lineage23", lineage23.id)
        assertEquals(Images.ID_LINEAGE23, lineage23.imageId)
        assertEquals("out/overlays_lineage23_r1", lineage23.sourceDir)
        assertEquals(37, lineage23.files.size)
        assertEquals(19, lineage23.files.count { it.role == FileRole.APEX_PAYLOAD })
        assertEquals(listOf(PackageSets.TETHERING, PackageSets.CELLBROADCAST), lineage23.omitted)
        assertTrue(lineage23.file(PackageSets.GMS_PAYLOAD) == null)
        assertTrue(lineage23.file(PackageSets.CELLBROADCAST) == null)
        assertTrue(lineage23.file(PackageSets.TETHERING) == null)
        assertEquals(
            listOf("services_patched.jar", "framework_patched.jar", "vold_patched", "service.incremental_patched.so",
                "libcamera_client_patched.so", "telephony-common_patched.jar"),
            lineage23.files.filter { it.role in PackageFile.GUARDED }.map { it.name },
        )
        assertTrue(lineage23.hasReturnApp && lineage23.file(PackageSets.GUEST_APP)?.role == FileRole.GUEST_APP)
        assertEquals(549_716_536L, lineage23.approxBytes)

        assertEquals("infinityx35", infinityx35.id)
        assertEquals(Images.ID_INFINITYX35, infinityx35.imageId)
        assertEquals("out/overlays_infinityx35_r1", infinityx35.sourceDir)
        assertEquals(37, infinityx35.files.size)
        assertEquals(19, infinityx35.files.count { it.role == FileRole.APEX_PAYLOAD })
        assertEquals(listOf(PackageSets.TETHERING, PackageSets.CELLBROADCAST), infinityx35.omitted)
        assertTrue(infinityx35.file(PackageSets.GMS_PAYLOAD) == null)
        assertTrue(infinityx35.hasReturnApp && infinityx35.file(PackageSets.GUEST_APP)?.role == FileRole.GUEST_APP)
        assertEquals(556_814_105L, infinityx35.approxBytes)
        for (s in PackageSets.ALL) {
            assertEquals(s.names.size, s.names.toSet().size)
            assertTrue(s.omitted.none { it in s.names })
            assertTrue(s.names.none { it == Images.STAGE_NAME })
            assertTrue(s.id, s.names.containsAll(commonPayloads + "guards.txt" + "services_patched.jar"))
            assertEquals(s.id, FileRole.GUARDS, s.file("guards.txt")!!.role)
            for (f in s.files) assertEquals("${s.id} ${f.name}", if (f.name == "vold_patched") "0755" else "0644", f.mode)
            assertEquals(s.id, s.file("vold_patched") != null, s.file("service.incremental_patched.so") != null)
            assertEquals(s.id, PackageSets.GUEST_APP in s.names, s.hasReturnApp)
        }
        for (p in Profiles.BUILT_IN) {
            for (choice in p.choices) {
                val image = Images.byId(choice.imageId)!!
                val set = PackageSets.byId(choice.packageSetId)!!
                assertEquals("${p.id} ${set.id}", image.presentsGms, set.file(PackageSets.GMS_PAYLOAD) != null)
            }
        }
    }

    @Test
    fun shaListAndGuardsValidation() {
        for (s in PackageSets.ALL) {
            val sums = shas(s)
            val g = guards(s)
            assertEquals(s.id, emptyList<String>(), s.check(sums))
            assertEquals(s.id, emptyList<String>(), s.checkGuards(g, sums))
            for (t in PackageSets.GMS_GUARDS) assertTrue("${s.id} $t", Regex("\\b$t\\b").containsMatchIn(g))
        }
        val sums = shas(lineage23)
        val g = guards(lineage23)

        val withOmitted = sums + "${"a".repeat(64)}  tethering_payload.img\n"
        assertEquals(listOf("lineage23: tethering_payload.img is left out of this set"), lineage23.check(withOmitted))
        val extra = sums + "${"a".repeat(64)}  unknown_extra.apk\n"
        assertEquals(listOf("lineage23: unknown_extra.apk is not in this set"), lineage23.check(extra))
        val dup = sums + sums.lines().first { it.isNotBlank() && !it.startsWith("#") } + "\n"
        val firstFile = sums.lines().first { it.isNotBlank() && !it.startsWith("#") }.substringAfter("  ")
        assertEquals(listOf("$firstFile listed twice"), lineage23.check(dup))

        for (n in listOf("vold_patched", "service.incremental_patched.so", "telephony-common_patched.jar")) {
            val missing = g.lines().filterNot { it.endsWith("  $n") }.joinToString("\n")
            assertEquals(n, listOf("guards.txt: $n missing"), lineage23.checkGuards(missing, sums))
        }
        val wrongVold = g.replace(ShaList.parse(g).entries.getValue("vold_patched"), "c".repeat(64))
        assertTrue(lineage23.checkGuards(wrongVold, sums).single().startsWith("guards.txt: vold_patched is cccccccccccc..., the set has "))
        assertTrue(lineage23.checkGuards(g + "${"b".repeat(64)}  OneOSGuest.apk\n", sums).single().contains("not a guard jar"))
    }

    @Test
    fun shaListParsing() {
        val h = "0123456789abcdef".repeat(4)
        val ok = ShaList.parse("# comment\n$h  a.jar\n${h.uppercase()} *b_payload.img\n\n")
        assertEquals(mapOf("a.jar" to h, "b_payload.img" to h), ok.entries)
        assertTrue(ok.problems.isEmpty())
        assertEquals("$h  a.jar\n$h  b_payload.img\n", ShaList.render(ok.entries))
        for (bad in listOf("$h a.jar b", "$h  ../x", "$h  dir/x", "$h  .hidden", "${h.dropLast(1)}  a.jar", "$h\ta.jar")) {
            assertFalse(bad, ShaList.parse(bad).problems.isEmpty())
            assertTrue(bad, ShaList.parse(bad).entries.isEmpty())
        }
    }

    @Test
    fun checkedInPackageListsMatchTheSets() {
        val dir = System.getProperty("oneos.packagesDir")?.let(::File)
        assumeTrue("app-android/packages not there yet", dir != null && dir.isDirectory)
        for (s in PackageSets.ALL) {
            val f = File(dir, "${s.id}.sha256")
            assumeTrue("${f.name} not there yet", f.isFile)
            assertEquals(s.id, emptyList<String>(), s.check(f.readText()))
        }
        assertEquals(PackageSets.ALL.map { "${it.id}.sha256" }.toSet(), dir!!.list()!!.filter { it.endsWith(".sha256") }.toSet())
    }

    @Test
    fun spaceToStage() {
        val need = SpaceRule.toStage(Images.LINEAGE23, lineage23)
        assertEquals(2_685_145_088L + 549_716_536L + 11L * SpaceRule.GIB, need)
        assertEquals(11L * SpaceRule.GIB, SpaceRule.LAUNCH_BYTES)
        assertEquals(Images.LINEAGE23.rawBytes + SpaceRule.LAUNCH_BYTES, SpaceRule.toStage(Images.LINEAGE23, null))
    }
}
