package com.fahadali.oneos.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import com.fahadali.oneos.shared.DualOsIcons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fahadali.oneos.BuildConfig
import com.fahadali.oneos.R
import com.fahadali.oneos.shared.DualOsTheme
import com.fahadali.oneos.shared.ExpressiveGroupedCard
import com.fahadali.oneos.shared.ExpressiveListDivider
import com.fahadali.oneos.shared.ExpressiveListItem
import com.fahadali.oneos.shared.ExpressiveSectionHeader
import com.fahadali.oneos.shared.ExpressiveStatusPill

/** About: what this version does, the tested scope, safety, credits. */
class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DualOsTheme {
                DualOsScaffold(
                    title = stringResource(R.string.about_title),
                    onBack = { finish() },
                ) { padding ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = padding.calculateTopPadding() + 8.dp,
                            bottom = padding.calculateBottomPadding() + 32.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(28.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                ),
                            ) {
                                Column(
                                    modifier = Modifier.padding(22.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.app_name),
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                        ExpressiveStatusPill(
                                            text = "v${BuildConfig.VERSION_NAME}",
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                        )
                                    }
                                    Text(
                                        text = stringResource(R.string.about_what_summary),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        }

                        item { ExpressiveSectionHeader(stringResource(R.string.about_cat_app)) }
                        item {
                            ExpressiveGroupedCard {
                                ExpressiveListItem(
                                    title = stringResource(R.string.about_version_title),
                                    subtitle = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                    icon = Icons.Rounded.Info,
                                )
                                ExpressiveListDivider()
                                ExpressiveListItem(
                                    title = stringResource(R.string.about_version_scope_title),
                                    subtitle = stringResource(R.string.about_version_scope_summary),
                                    icon = DualOsIcons.Sparkles,
                                )
                                ExpressiveListDivider()
                                ExpressiveListItem(
                                    title = stringResource(R.string.about_tested_title),
                                    subtitle = stringResource(R.string.about_tested_summary),
                                    icon = DualOsIcons.PhoneAndroid,
                                )
                                ExpressiveListDivider()
                                ExpressiveListItem(
                                    title = stringResource(R.string.about_safety_title),
                                    subtitle = stringResource(R.string.about_safety_summary),
                                    icon = DualOsIcons.Security,
                                )
                                ExpressiveListDivider()
                                ExpressiveListItem(
                                    title = stringResource(R.string.before_title),
                                    subtitle = stringResource(R.string.before_link_summary),
                                    icon = DualOsIcons.Shield,
                                    showChevron = true,
                                    onClick = { startActivity(Intent(this@AboutActivity, BeforeYouStartActivity::class.java)) },
                                )
                            }
                        }

                        item { ExpressiveSectionHeader(stringResource(R.string.about_cat_credits)) }
                        item {
                            val onelineageUrl = stringResource(R.string.about_credit_onelineage_url)
                            val lineageUrl = stringResource(R.string.about_credit_lineageos_url)
                            val infinityUrl = stringResource(R.string.about_credit_infinityx_url)
                            ExpressiveGroupedCard {
                                ExpressiveListItem(
                                    title = stringResource(R.string.about_credit_oneos_title),
                                    subtitle = stringResource(R.string.about_credit_oneos_summary),
                                    icon = DualOsIcons.Code,
                                )
                                ExpressiveListDivider()
                                ExpressiveListItem(
                                    title = stringResource(R.string.about_credit_onelineage_title),
                                    subtitle = onelineageUrl,
                                    icon = DualOsIcons.OpenInNew,
                                    showChevron = true,
                                    onClick = { openInBrowser(onelineageUrl) },
                                )
                                ExpressiveListDivider()
                                ExpressiveListItem(
                                    title = stringResource(R.string.about_credit_lineageos_title),
                                    subtitle = stringResource(R.string.about_credit_lineageos_summary),
                                    icon = DualOsIcons.OpenInNew,
                                    showChevron = true,
                                    onClick = { openInBrowser(lineageUrl) },
                                )
                                ExpressiveListDivider()
                                ExpressiveListItem(
                                    title = stringResource(R.string.about_credit_infinityx_title),
                                    subtitle = stringResource(R.string.about_credit_infinityx_summary),
                                    icon = DualOsIcons.OpenInNew,
                                    showChevron = true,
                                    onClick = { openInBrowser(infinityUrl) },
                                )
                                ExpressiveListDivider()
                                ExpressiveListItem(
                                    title = stringResource(R.string.about_credit_oneui_title),
                                    subtitle = stringResource(R.string.about_credit_oneui_summary),
                                    icon = DualOsIcons.Sparkles,
                                )
                            }
                        }

                        item { ExpressiveSectionHeader(stringResource(R.string.about_cat_legal)) }
                        item {
                            ExpressiveGroupedCard {
                                ExpressiveListItem(
                                    title = stringResource(R.string.about_legal_title),
                                    subtitle = stringResource(R.string.about_legal_summary),
                                    icon = Icons.Rounded.Info,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun openInBrowser(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.about_no_browser, Toast.LENGTH_SHORT).show()
        }
    }
}
