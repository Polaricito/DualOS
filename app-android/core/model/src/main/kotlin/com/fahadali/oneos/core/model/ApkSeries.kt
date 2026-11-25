package com.fahadali.oneos.core.model

/**
 * DualOS download series: Galaxy A53 5G exclusive.
 * app/build.gradle.kts builds with -Poneos.series=<id> from app-android/packages/series.txt,
 * which lists the same sets as [setIds] (ApkSeriesTest).
 */
enum class ApkSeries(val id: String, val label: String, val profileIds: List<String>) {
    A53("a53", "Galaxy A53 5G", listOf(Profiles.ID_A53)),
    ;

    /** The sets this series' APK carries: every set its phones' systems use. */
    fun setIds(profiles: List<PhoneProfile> = Profiles.BUILT_IN): Set<String> =
        profileIds.mapNotNull { id -> profiles.firstOrNull { it.id == id } }
            .flatMap { p -> p.choices.map { it.packageSetId } }
            .toSortedSet()

    companion object {
        fun byId(id: String?): ApkSeries? = entries.firstOrNull { it.id == id }

        /** The series a profile's phones download. */
        fun of(profileId: String): ApkSeries? = entries.firstOrNull { profileId in it.profileIds }
    }
}
