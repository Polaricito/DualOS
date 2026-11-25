package com.fahadali.oneos.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImagesTest {
    private val lineage23 = Images.LINEAGE23
    private val infinityx35 = Images.INFINITYX35

    private val old = RetiredImage(
        id = "coloros16-findx8u",
        shortName = "Find X8 Ultra",
        gzName = "OneOS_ColorOS16_FindX8Ultra.img.gz",
        gzBytes = 4_000_000_001L,
        rawBytes = 9_000_000_000L,
    )

    @Test
    fun builtInTable() {
        assertEquals("lineage23-gsi", Images.ID_LINEAGE23)
        assertEquals("infinityx35-gsi", Images.ID_INFINITYX35)
        assertTrue(Images.ID_LINEAGE23.startsWith("lineage"))
        assertEquals(listOf(lineage23, infinityx35), Images.ALL)
        assertEquals(emptyList<RetiredImage>(), Images.RETIRED)
        assertEquals(emptyMap<String, String>(), Images.DATA_UPGRADES)

        assertEquals("LineageOS 23", lineage23.name)
        assertEquals("LineageOS 23, Galaxy A53 5G GSI", lineage23.label)
        assertEquals("Galaxy A53 5G", lineage23.shortName)
        assertEquals("Galaxy A53 5G", lineage23.testedOn)
        assertEquals(36, lineage23.sdk)
        assertEquals("BP2A.250805.005", lineage23.buildId)
        assertEquals(2_685_145_088L, lineage23.rawBytes)
        assertEquals(1_250_952_887L, lineage23.gzBytes)
        assertEquals("OneOS_LineageOS23_GalaxyA53.img.gz", lineage23.gzName)
        assertTrue(lineage23.googleServices)
        assertFalse(lineage23.presentsGms)
        assertTrue(lineage23.gmsHideable)
        assertEquals(SystemFamily.LINEAGE, lineage23.family)
        assertEquals(emptyList<String>(), lineage23.cpuRequired)
        assertEquals(lineage23, Images.byId(Images.ID_LINEAGE23))
        assertNull(Images.byId("unknown"))
        assertEquals(lineage23, Images.byGzSize(lineage23.gzBytes))
        assertEquals(lineage23, Images.byRawSize(lineage23.rawBytes))

        assertEquals("Infinity-X 3.5", infinityx35.name)
        assertEquals("Project Infinity-X 3.5, Galaxy A53 5G GSI", infinityx35.label)
        assertEquals("Infinity-X 3.5", infinityx35.shortName)
        assertNull(infinityx35.testedOn)
        assertEquals(36, infinityx35.sdk)
        assertEquals("BP2A.250805.005", infinityx35.buildId)
        assertEquals(3_027_066_880L, infinityx35.rawBytes)
        assertEquals(1_520_656_210L, infinityx35.gzBytes)
        assertEquals("DualOS_InfinityX35_GalaxyA53.img.gz", infinityx35.gzName)
        assertTrue(infinityx35.googleServices)
        assertFalse(infinityx35.presentsGms)
        assertTrue(infinityx35.gmsHideable)
        assertEquals(SystemFamily.INFINITYX, infinityx35.family)
        assertEquals(infinityx35, Images.byId(Images.ID_INFINITYX35))
        assertEquals(infinityx35, Images.byGzSize(infinityx35.gzBytes))
        assertEquals(infinityx35, Images.byRawSize(infinityx35.rawBytes))
    }

    @Test
    fun systemNameAndShortName() {
        assertEquals("LineageOS 23", Images.systemName(lineage23, Profiles.A53))
        assertEquals(Images.OTHER_SYSTEM, Images.systemName(null, Profiles.A53))
        assertEquals("Galaxy A53 5G", Images.shortName(Images.ID_LINEAGE23))
        assertEquals("Find X8 Ultra", Images.shortName(old.id, retired = listOf(old)))
        assertEquals("unknown-id", Images.shortName("unknown-id"))
    }

    @Test
    fun identifyGzOutcomes() {
        val profile = Profiles.A53
        val ok = ImageIdentifier.identify(
            ImageFileCheck(lineage23.gzName, lineage23.gzBytes, lineage23.gzSha256, lineage23.gzBytes),
            profile,
        )
        assertEquals(ImageState.VERIFIED, ok.state)
        assertEquals(lineage23, ok.image)
        assertEquals(Level.OK, ok.finding.level)

        val unhashed = ImageIdentifier.identify(
            ImageFileCheck(lineage23.gzName, lineage23.gzBytes, null, null),
            profile,
        )
        assertEquals(ImageState.NOT_CHECKED, unhashed.state)
        assertEquals(ImageIdentifier.R_NOT_CHECKED, unhashed.finding.reason)

        val damaged = ImageIdentifier.identify(
            ImageFileCheck(lineage23.gzName, lineage23.gzBytes, "0".repeat(64), lineage23.gzBytes),
            profile,
        )
        assertEquals(ImageState.DAMAGED, damaged.state)
        assertEquals(ImageIdentifier.R_DAMAGED, damaged.finding.reason)

        val rawPicked = ImageIdentifier.identify(
            ImageFileCheck("lineage23.img", lineage23.rawBytes),
            profile,
        )
        assertEquals(ImageState.UNCOMPRESSED, rawPicked.state)
        assertEquals(ImageIdentifier.R_UNCOMPRESSED, rawPicked.finding.reason)

        val notOurs = ImageIdentifier.identify(
            ImageFileCheck("random.img.gz", 123_456L),
            profile,
        )
        assertEquals(ImageState.UNKNOWN, notOurs.state)
        assertEquals(ImageIdentifier.R_NOT_OURS, notOurs.finding.reason)

        val unknownSize = ImageIdentifier.identify(ImageFileCheck("unknown.img.gz", null), profile)
        assertEquals(ImageState.UNKNOWN, unknownSize.state)
        assertEquals(ImageIdentifier.R_SIZE_UNKNOWN, unknownSize.finding.reason)

        val retired = ImageIdentifier.identify(
            ImageFileCheck(old.gzName, old.gzBytes),
            profile,
            retiredImages = listOf(old),
        )
        assertEquals(ImageState.UNKNOWN, retired.state)
        assertEquals(ImageIdentifier.R_RETIRED, retired.finding.reason)

        val other = lineage23.copy(id = "other-os", gzBytes = 1_111_222_333L, gzSha256 = "f".repeat(64))
        val wrongPhone = ImageIdentifier.identify(
            ImageFileCheck("other.img.gz", other.gzBytes, other.gzSha256, other.gzBytes),
            profile,
            images = listOf(lineage23, other),
        )
        assertEquals(ImageState.WRONG_FOR_PHONE, wrongPhone.state)
        assertEquals(ImageIdentifier.R_WRONG_PHONE, wrongPhone.finding.reason)
    }

    @Test
    fun identifyRawOutcomes() {
        val profile = Profiles.A53
        val ok = ImageIdentifier.identifyStaged(StagedRaw(lineage23.rawBytes, lineage23.rawSha256), profile)
        assertEquals(ImageState.VERIFIED, ok.state)
        assertEquals(lineage23, ok.image)

        val notChecked = ImageIdentifier.identifyStaged(StagedRaw(lineage23.rawBytes, null), profile)
        assertEquals(ImageState.NOT_CHECKED, notChecked.state)
        assertEquals(Level.OK, notChecked.finding.level)
        assertNull(notChecked.finding.reason)

        val damaged = ImageIdentifier.identifyStaged(StagedRaw(lineage23.rawBytes, "0".repeat(64)), profile)
        assertEquals(ImageState.DAMAGED, damaged.state)
        assertEquals(ImageIdentifier.R_RAW_DAMAGED, damaged.finding.reason)

        val missing = ImageIdentifier.identifyStaged(StagedRaw(null, null), profile)
        assertEquals(ImageState.UNKNOWN, missing.state)
        assertEquals(ImageIdentifier.R_NO_RAW, missing.finding.reason)

        val wrongSize = ImageIdentifier.identifyStaged(StagedRaw(999L, null), profile)
        assertEquals(ImageState.UNKNOWN, wrongSize.state)
        assertEquals(ImageIdentifier.R_RAW_NOT_OURS, wrongSize.finding.reason)
    }
}
