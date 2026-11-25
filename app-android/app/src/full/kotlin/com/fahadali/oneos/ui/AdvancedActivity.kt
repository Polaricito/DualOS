package com.fahadali.oneos.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fahadali.oneos.R
import com.fahadali.oneos.shared.DualOsTheme

/** Standalone Settings screen reusing the shared Material 3 Expressive Settings tab content. */
class AdvancedActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DualOsTheme {
                DualOsScaffold(
                    title = stringResource(R.string.advanced_title),
                    onBack = { finish() },
                ) { padding ->
                    SettingsTabContent(
                        activity = this@AdvancedActivity,
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = padding.calculateTopPadding() + 8.dp,
                            bottom = padding.calculateBottomPadding() + 32.dp,
                        ),
                    )
                }
            }
        }
    }
}
