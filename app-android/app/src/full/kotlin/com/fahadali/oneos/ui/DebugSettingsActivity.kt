package com.fahadali.oneos.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fahadali.oneos.BuildConfig
import com.fahadali.oneos.R
import com.fahadali.oneos.session.AppUpdater
import com.fahadali.oneos.shared.DualOsIcons
import com.fahadali.oneos.shared.DualOsTheme
import com.fahadali.oneos.shared.ExpressiveGroupedCard
import com.fahadali.oneos.shared.ExpressiveListDivider
import com.fahadali.oneos.shared.ExpressiveListItem
import com.fahadali.oneos.shared.ExpressiveSectionHeader
import com.fahadali.oneos.shared.ExpressiveSwitchItem

/** Technical tools & team screens in Material 3 Expressive Compose. */
class DebugSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DualOsTheme {
                Screen()
            }
        }
    }

    @Composable
    private fun Screen() {
        var previewOn by remember { mutableStateOf(DebugPreview.enabled(this)) }
        var testChannelOn by remember { mutableStateOf(AppUpdater.testChannel(this)) }
        var showCodeDialog by remember { mutableStateOf(false) }

        DualOsScaffold(
            title = stringResource(R.string.team_title),
            onBack = { finish() },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (BuildConfig.DEBUG) {
                    item {
                        ExpressiveGroupedCard {
                            ExpressiveSwitchItem(
                                title = stringResource(R.string.setup_preview_setting),
                                subtitle = stringResource(R.string.setup_preview_setting_summary),
                                icon = Icons.Rounded.Build,
                                checked = previewOn,
                                onCheckedChange = {
                                    previewOn = it
                                    DebugPreview.setEnabled(this@DebugSettingsActivity, it)
                                },
                            )
                        }
                    }
                }

                item { ExpressiveSectionHeader(stringResource(R.string.team_card_checks)) }
                item {
                    ExpressiveGroupedCard {
                        ExpressiveListItem(
                            title = stringResource(R.string.switch_title),
                            subtitle = stringResource(R.string.team_switch_summary),
                            icon = DualOsIcons.Power,
                            showChevron = true,
                            onClick = { startActivity(Intent(this@DebugSettingsActivity, SwitchActivity::class.java)) },
                        )
                        ExpressiveListDivider()
                        ExpressiveListItem(
                            title = stringResource(R.string.report_title),
                            subtitle = stringResource(R.string.team_phone_summary),
                            icon = DualOsIcons.PhoneAndroid,
                            showChevron = true,
                            onClick = { startActivity(Intent(this@DebugSettingsActivity, DeviceReportActivity::class.java)) },
                        )
                        ExpressiveListDivider()
                        ExpressiveListItem(
                            title = stringResource(R.string.tests_title),
                            subtitle = stringResource(R.string.team_tests_summary),
                            icon = DualOsIcons.Science,
                            showChevron = true,
                            onClick = { startActivity(Intent(this@DebugSettingsActivity, RunTestsActivity::class.java)) },
                        )
                    }
                }

                item { ExpressiveSectionHeader(stringResource(R.string.team_card_setup)) }
                item {
                    ExpressiveGroupedCard {
                        ExpressiveListItem(
                            title = stringResource(R.string.prepare_title_team),
                            subtitle = stringResource(R.string.team_setup_summary),
                            icon = DualOsIcons.Tune,
                            showChevron = true,
                            onClick = { startActivity(Intent(this@DebugSettingsActivity, PrepareActivity::class.java).putExtra(PrepareActivity.EXTRA_TEAM, true)) },
                        )
                        ExpressiveListDivider()
                        ExpressiveListItem(
                            title = stringResource(R.string.image_title_team),
                            subtitle = stringResource(R.string.team_file_summary),
                            icon = DualOsIcons.FolderOpen,
                            showChevron = true,
                            onClick = { startActivity(Intent(this@DebugSettingsActivity, ImageActivity::class.java).putExtra(ImageActivity.EXTRA_TEAM, true)) },
                        )
                        ExpressiveListDivider()
                        ExpressiveListItem(
                            title = stringResource(R.string.team_update_test),
                            subtitle = stringResource(if (testChannelOn) R.string.team_update_test_on else R.string.team_update_test_summary),
                            icon = Icons.Rounded.Edit,
                            showChevron = true,
                            onClick = { showCodeDialog = true },
                        )
                    }
                }
            }

            if (showCodeDialog) {
                var code by remember { mutableStateOf(AppUpdater.testCode(this@DebugSettingsActivity).orEmpty()) }
                AlertDialog(
                    onDismissRequest = { showCodeDialog = false },
                    title = { Text(stringResource(R.string.team_update_test)) },
                    text = {
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it },
                            label = { Text(stringResource(R.string.team_update_test_dialog)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val ok = AppUpdater.setTestCode(this@DebugSettingsActivity, code)
                            if (!ok) Toast.makeText(this@DebugSettingsActivity, R.string.team_update_test_invalid, Toast.LENGTH_LONG).show()
                            testChannelOn = AppUpdater.testChannel(this@DebugSettingsActivity)
                            showCodeDialog = false
                        }) { Text(stringResource(android.R.string.ok)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCodeDialog = false }) { Text(stringResource(android.R.string.cancel)) }
                    },
                )
            }
        }
    }
}
