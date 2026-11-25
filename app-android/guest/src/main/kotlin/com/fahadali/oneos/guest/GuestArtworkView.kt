package com.fahadali.oneos.guest

import androidx.annotation.DrawableRes
import com.fahadali.oneos.core.model.SystemFamily

/** Maps the active Galaxy A53 5G guest system name to its DualOS artwork resource. */
object GuestArtworkView {
    fun familyFor(systemName: String?): SystemFamily = when {
        systemName?.contains("Infinity", ignoreCase = true) == true -> SystemFamily.INFINITYX
        systemName?.contains("Evo", ignoreCase = true) == true -> SystemFamily.EVOX
        else -> SystemFamily.LINEAGE
    }

    @DrawableRes
    fun headerDrawable(systemName: String?): Int = when (familyFor(systemName)) {
        SystemFamily.INFINITYX -> R.drawable.os_infinity_header
        SystemFamily.LINEAGE, SystemFamily.EVOX -> R.drawable.os_lineage_header
    }

    @DrawableRes
    fun cardDrawable(systemName: String?): Int = when (familyFor(systemName)) {
        SystemFamily.INFINITYX -> R.drawable.os_infinity
        SystemFamily.LINEAGE, SystemFamily.EVOX -> R.drawable.os_lineage
    }
}
