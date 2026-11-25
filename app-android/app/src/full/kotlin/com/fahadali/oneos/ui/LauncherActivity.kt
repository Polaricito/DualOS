package com.fahadali.oneos.ui

import android.content.Intent
import android.os.Bundle

/**
 * Legacy launcher activity alias kept for deep links and manifest stability;
 * delegates cleanly to the unified Material 3 Expressive hub in [MainActivity]
 * or opens the standalone [SystemsActivity] when browsing systems explicitly.
 */
class LauncherActivity : SystemsActivity() {
    override val isRootLauncher: Boolean get() = true

    override fun onCreate(savedInstanceState: Bundle?) {
        if (!intent.getBooleanExtra("browse_systems", false)) {
            super.onCreate(savedInstanceState)
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
            finish()
            return
        }
        super.onCreate(savedInstanceState)
    }
}
