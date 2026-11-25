package com.fahadali.oneos.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Share
import com.fahadali.oneos.shared.DualOsIcons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fahadali.oneos.BuildConfig
import com.fahadali.oneos.R
import com.fahadali.oneos.shared.ExpressiveGroupedCard
import com.fahadali.oneos.shared.ExpressiveListDivider
import com.fahadali.oneos.shared.ExpressiveListItem
import com.fahadali.oneos.shared.ExpressiveSectionHeader
import com.fahadali.oneos.shared.ExpressiveStatusPill

/**
 * The probe edition (DualOS Probe) stays read-only: Check this phone, Run tests,
 * Before you start, About. Zero switching or session code.
 */
internal object EditionHome {
    fun onboarding(a: MainActivity) = Intent(a, BeforeYouStartActivity::class.java)

    fun redirect(a: MainActivity): Intent? = null

    fun started(a: MainActivity) = Unit

    @Composable
    fun Content(a: MainActivity) {
        DualOsScaffold(
            title = stringResource(R.string.app_name),
            subtitle = stringResource(R.string.home_subtitle),
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ExpressiveStatusPill(
                                text = "Read-only · v${BuildConfig.VERSION_NAME}",
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            )
                            Text(
                                text = stringResource(R.string.home_send_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Text(
                                text = stringResource(R.string.home_send_summary),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }

                item { ExpressiveSectionHeader(stringResource(R.string.home_cat_phone)) }
                item {
                    ExpressiveGroupedCard {
                        ExpressiveListItem(
                            title = stringResource(R.string.home_check_title),
                            subtitle = stringResource(R.string.home_check_summary),
                            icon = DualOsIcons.PhoneAndroid,
                            showChevron = true,
                            onClick = { a.startActivity(Intent(a, DeviceReportActivity::class.java)) },
                        )
                        ExpressiveListDivider()
                        ExpressiveListItem(
                            title = stringResource(R.string.home_tests_title),
                            subtitle = stringResource(R.string.home_tests_summary),
                            icon = DualOsIcons.Science,
                            showChevron = true,
                            onClick = { a.startActivity(Intent(a, RunTestsActivity::class.java)) },
                        )
                    }
                }

                item { ExpressiveSectionHeader(stringResource(R.string.home_cat_app)) }
                item {
                    ExpressiveGroupedCard {
                        ExpressiveListItem(
                            title = stringResource(R.string.before_title),
                            subtitle = stringResource(R.string.before_link_summary),
                            icon = DualOsIcons.Shield,
                            showChevron = true,
                            onClick = { a.startActivity(Intent(a, BeforeYouStartActivity::class.java)) },
                        )
                        ExpressiveListDivider()
                        ExpressiveListItem(
                            title = stringResource(R.string.home_about_title),
                            subtitle = stringResource(R.string.home_about_summary),
                            icon = Icons.Rounded.Info,
                            showChevron = true,
                            onClick = { a.startActivity(Intent(a, AboutActivity::class.java)) },
                        )
                    }
                }
            }
        }
    }
}

internal object EditionBeforeYouStart {
    val ackRes: Int = R.string.before_ack

    fun items(): List<BeforeItem> = listOf(
        BeforeItem(R.string.before_probe_reads_title, R.string.before_probe_reads_summary, DualOsIcons.Science),
        BeforeItem(R.string.before_probe_root_title, R.string.before_probe_root_summary, DualOsIcons.Security),
        BeforeItem(R.string.before_probe_privacy_title, R.string.before_probe_privacy_summary, DualOsIcons.Shield),
        BeforeItem(R.string.before_probe_share_title, R.string.before_probe_share_summary, Icons.Rounded.Share),
        BeforeItem(R.string.before_probe_switch_title, R.string.before_probe_switch_summary, Icons.Rounded.Info),
    )
}
