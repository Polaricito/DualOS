package com.fahadali.oneos.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.fahadali.oneos.shared.DualOsTheme

/**
 * Entry point for both editions (DualOS and DualOS Probe).
 * Until Before you start is acknowledged, every launch goes to onboarding first.
 * Each edition provides its own Material 3 Expressive [EditionHome] hub.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        EditionHome.started(this)
        val next = if (BeforeYouStart.isAcknowledged(this)) {
            EditionHome.redirect(this)
        } else {
            EditionHome.onboarding(this)
        }
        if (next != null) {
            startActivity(next)
            finish()
            return
        }
        setContent {
            DualOsTheme {
                EditionHome.Content(this)
            }
        }
    }
}
