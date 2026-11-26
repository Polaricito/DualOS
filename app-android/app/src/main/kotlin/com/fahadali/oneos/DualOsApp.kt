package com.fahadali.oneos

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors

/** Applies system night mode and Material 3 Dynamic Color (Monet) to Activities on Android 12+. */
class DualOsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
