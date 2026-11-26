package com.fahadali.oneos.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fahadali.oneos.R
import com.fahadali.oneos.core.model.Images
import com.fahadali.oneos.core.model.Level
import com.fahadali.oneos.core.model.Profiles
import com.fahadali.oneos.core.root.OutcomeKind
import com.fahadali.oneos.report.ReportSender
import com.fahadali.oneos.session.LogJob
import com.fahadali.oneos.session.SessionPrefs
import com.fahadali.oneos.session.StageCheck
import com.fahadali.oneos.shared.DualOsIcons
import com.fahadali.oneos.shared.DualOsTheme
import com.fahadali.oneos.shared.ExpressiveGroupedCard
import com.fahadali.oneos.shared.ExpressiveListDivider
import com.fahadali.oneos.shared.ExpressiveListItem
import com.fahadali.oneos.shared.ExpressiveSectionHeader

/** Last session: outcome summary, read-only redacted log collection, share/send, and One UI settings restore. */
class LastSessionActivity : AppCompatActivity() {
    private var unsubscribe: (() -> Unit)? = null
    private var privacyOpen by mutableStateOf(false)
    private var detailsOpen by mutableStateOf(false)
    private var tick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DualOsTheme {
                Screen()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        unsubscribe = LogJob.observe { tick++ }
    }

    override fun onStop() {
        unsubscribe?.invoke()
        unsubscribe = null
        super.onStop()
    }

    @Composable
    private fun Screen() {
        @Suppress("UNUSED_VARIABLE") val t = tick
        val launch = SessionPrefs.lastLaunch(this)
        val job = LogJob.state
        val image = launch?.imageId?.let(Images::byId)
        val system = Images.systemName(image)
        val outcome = job.outcome?.kind ?: launch?.outcome?.let { o -> OutcomeKind.entries.firstOrNull { it.name == o } }
        val afterSeconds = job.outcome?.afterSeconds ?: launch?.afterSeconds
        val test = launch?.dryRun == true || outcome == OutcomeKind.DRY_RUN

        val headline = when {
            launch == null -> stringResource(R.string.last_none)
            outcome != null -> outcome.title(system).let { if (it.endsWith('.')) it.dropLast(1) else it }
            test -> stringResource(R.string.last_dry)
            launch.endedSince() -> stringResource(R.string.last_time_in, system)
            else -> stringResource(R.string.last_switched_to, system)
        }

        DualOsScaffold(
            title = stringResource(R.string.last_title),
            subtitle = launch?.let { formatDateTime(this, it.wallMs) },
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
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(headline, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            when {
                                launch == null -> Text(stringResource(R.string.last_none_line), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                outcome != null && !test -> {
                                    val timeLabel = afterSeconds?.takeIf { it > 0 }?.let(::formatSessionTime)
                                    val tiles = buildList {
                                        if (timeLabel != null) add(timeLabel to getString(R.string.home_welcome_in, system))
                                        if (!titleSaysHow(outcome)) add(getString(howCameBack(outcome)) to getString(R.string.home_welcome_how))
                                    }
                                    if (tiles.isNotEmpty()) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            tiles.forEach { (v, l) ->
                                                Surface(
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(18.dp),
                                                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                                ) {
                                                    Column(modifier = Modifier.padding(14.dp)) {
                                                        Text(v, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                                        Text(l, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                outcome != null -> Text(stringResource(R.string.last_test_line, system), style = MaterialTheme.typography.bodyMedium)
                                launch.endedSince() -> Text(stringResource(R.string.last_rebooted_summary), style = MaterialTheme.typography.bodyMedium)
                                else -> Text(stringResource(R.string.last_not_rebooted_summary), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                item { ExpressiveSectionHeader(stringResource(R.string.last_cat_logs)) }
                item {
                    val zip = job.zip ?: LogJob.latestZip(this@LastSessionActivity)
                    ExpressiveGroupedCard {
                        if (job.busy && !job.recovering) {
                            ExpressiveListItem(
                                title = stringResource(R.string.last_collecting),
                                subtitle = job.detail ?: stringResource(R.string.last_collecting_summary),
                                icon = Icons.Rounded.Refresh,
                            )
                        } else {
                            ExpressiveListItem(
                                title = stringResource(if (zip == null) R.string.last_collect_title else R.string.last_collect_again),
                                subtitle = stringResource(R.string.last_collect_summary),
                                icon = DualOsIcons.Description,
                                enabled = !job.busy,
                                showChevron = !job.busy,
                                onClick = { confirmCollect() },
                            )
                        }
                        if (!job.busy && job.phase == LogJob.Phase.FAILED && !job.restoreFailed) {
                            ExpressiveListDivider()
                            ExpressiveListItem(
                                title = stringResource(R.string.last_collect_failed),
                                subtitle = stringResource(R.string.last_collect_failed_line),
                                icon = levelIcon(Level.WARN),
                                iconTint = levelColor(Level.WARN),
                            )
                        } else if (!job.busy && job.phase == LogJob.Phase.DONE && job.zip != null && job.recovered == null) {
                            ExpressiveListDivider()
                            ExpressiveListItem(
                                title = stringResource(R.string.last_collected),
                                subtitle = stringResource(R.string.last_collected_summary),
                                icon = levelIcon(Level.OK),
                                iconTint = levelColor(Level.OK),
                            )
                        }
                        if (zip != null && !job.busy) {
                            if (ReportSender.offered) {
                                val code = SendReportActivity.sentCode(this@LastSessionActivity, zip)
                                ExpressiveListDivider()
                                ExpressiveListItem(
                                    title = stringResource(R.string.send_title),
                                    subtitle = if (code != null) stringResource(R.string.last_send_sent, code) else stringResource(R.string.last_send_summary),
                                    icon = Icons.AutoMirrored.Rounded.Send,
                                    showChevron = true,
                                    onClick = { startActivity(SendReportActivity.intent(this@LastSessionActivity, zip)) },
                                )
                            }
                            ExpressiveListDivider()
                            ExpressiveListItem(
                                title = stringResource(R.string.last_view_title),
                                subtitle = stringResource(R.string.last_view_summary),
                                icon = Icons.Rounded.Share,
                                showChevron = true,
                                onClick = { startActivity(Intent(this@LastSessionActivity, FindingsActivity::class.java).putExtra(FindingsActivity.EXTRA_ZIP, zip.path)) },
                            )
                        }
                        ExpressiveListDivider()
                        ExpressiveListItem(
                            title = stringResource(R.string.last_privacy_title),
                            icon = DualOsIcons.Shield,
                            onClick = { privacyOpen = !privacyOpen },
                        )
                        if (privacyOpen) {
                            Text(
                                text = stringResource(R.string.last_privacy_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                            )
                        }
                    }
                }

                val maybe = SessionRestore.maybe(this@LastSessionActivity)
                val unfinished = job.status?.unfinishedSession == true || maybe
                if (job.busy && job.recovering) {
                    item { ExpressiveSectionHeader(stringResource(R.string.last_cat_restore)) }
                    item {
                        ExpressiveGroupedCard {
                            ExpressiveListItem(title = stringResource(R.string.home_restoring), icon = DualOsIcons.RestartAlt)
                        }
                    }
                } else if (!job.busy && (unfinished || job.recovered != null || job.restoreFailed)) {
                    item { ExpressiveSectionHeader(stringResource(R.string.last_cat_restore)) }
                    item {
                        ExpressiveGroupedCard {
                            var first = true
                            @Composable
                            fun sep() { if (!first) ExpressiveListDivider(); first = false }
                            if (job.recovered != null) {
                                sep(); ExpressiveListItem(title = stringResource(R.string.last_restored), icon = levelIcon(Level.OK), iconTint = levelColor(Level.OK))
                            }
                            if (job.restoreFailed) {
                                sep(); ExpressiveListItem(title = stringResource(R.string.last_restore_failed), subtitle = stringResource(R.string.last_restore_failed_line), icon = levelIcon(Level.WARN), iconTint = levelColor(Level.WARN))
                            }
                            if (unfinished) {
                                sep(); ExpressiveListItem(
                                    title = stringResource(R.string.last_restore_title),
                                    subtitle = stringResource(if (maybe) R.string.last_restore_maybe else R.string.last_restore_summary),
                                    icon = DualOsIcons.RestartAlt,
                                    showChevron = true,
                                    onClick = { SessionRestore.confirm(this@LastSessionActivity) },
                                )
                            }
                        }
                    }
                }

                if (launch != null && !job.busy && !unfinished) {
                    item {
                        ExpressiveGroupedCard {
                            ExpressiveListItem(
                                title = stringResource(R.string.last_forget_title),
                                subtitle = stringResource(R.string.last_forget_summary),
                                icon = Icons.Rounded.CheckCircle,
                                showChevron = true,
                                onClick = {
                                    SessionPrefs.forgetLaunch(this@LastSessionActivity)
                                    finish()
                                },
                            )
                        }
                    }
                }

                item {
                    val zip = job.zip ?: LogJob.latestZip(this@LastSessionActivity)
                    ExpressiveGroupedCard {
                        ExpressiveListItem(
                            title = stringResource(R.string.details_title),
                            subtitle = stringResource(R.string.last_details_summary),
                            icon = if (detailsOpen) DualOsIcons.ArrowDropUp else DualOsIcons.ArrowDropDown,
                            onClick = { detailsOpen = !detailsOpen },
                        )
                        if (detailsOpen) {
                            if (launch != null) {
                                ExpressiveListDivider()
                                ExpressiveListItem(title = stringResource(R.string.last_id, launch.sessionId), subtitle = formatDateTime(this@LastSessionActivity, launch.wallMs))
                                ExpressiveListDivider()
                                val phone = Profiles.byId(launch.profileId)?.label ?: launch.profileId
                                ExpressiveListItem(title = stringResource(R.string.last_details_image, image?.shortName ?: launch.imageId), subtitle = phone)
                            }
                            job.outcome?.let { o ->
                                ExpressiveListDivider()
                                ExpressiveListItem(title = o.title, subtitle = o.detail(system))
                            }
                            job.detail?.takeIf { job.busy }?.let {
                                ExpressiveListDivider()
                                ExpressiveListItem(title = it)
                            }
                            job.message?.let {
                                ExpressiveListDivider()
                                ExpressiveListItem(title = stringResource(R.string.setup_details_result), subtitle = it)
                            }
                            job.recovered?.let {
                                ExpressiveListDivider()
                                ExpressiveListItem(title = stringResource(R.string.last_restored), subtitle = it)
                            }
                            zip?.let {
                                ExpressiveListDivider()
                                ExpressiveListItem(title = it.name)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun formatSessionTime(seconds: Int): String = when {
        seconds >= 3600 -> "${seconds / 3600} h ${(seconds % 3600) / 60} min"
        seconds >= 60 -> getString(R.string.home_minutes, (seconds + 59) / 60)
        else -> "< 1 min"
    }

    private fun titleSaysHow(o: OutcomeKind): Boolean = when (o) {
        OutcomeKind.SESSION_LIMIT, OutcomeKind.PREPARE_FAILED -> false
        else -> true
    }

    private fun howCameBack(o: OutcomeKind): Int = when (o) {
        OutcomeKind.USER_RETURN -> R.string.home_how_keys
        OutcomeKind.APP_RETURN -> R.string.home_how_app
        OutcomeKind.GUEST_REBOOT, OutcomeKind.GUEST_DEAD, OutcomeKind.BLACK_SCREEN, OutcomeKind.SESSION_LIMIT, OutcomeKind.BOOT_TIMEOUT -> R.string.home_how_auto
        OutcomeKind.HARD_RESET -> R.string.home_how_restart
        OutcomeKind.GUEST_SHUTDOWN -> R.string.home_how_off
        OutcomeKind.PREPARE_FAILED, OutcomeKind.REFUSED -> R.string.home_how_none
        OutcomeKind.DRY_RUN -> R.string.home_how_test
        OutcomeKind.UNKNOWN -> R.string.home_how_other
    }

    private fun confirmCollect() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.last_collect_dialog_title)
            .setMessage(R.string.last_collect_dialog_message)
            .setPositiveButton(R.string.last_collect_run) { _, _ -> LogJob.start(this) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}

/** Restore One UI's settings after an interrupted session. */
internal object SessionRestore {
    private fun currentStatus() = LogJob.state.status ?: StageCheck.state.result?.status

    fun maybe(ctx: Context): Boolean {
        val launch = SessionPrefs.lastLaunch(ctx)
        return currentStatus() == null && launch != null && !launch.dryRun && launch.endedSince()
    }

    fun offered(ctx: Context): Boolean = currentStatus()?.unfinishedSession == true || maybe(ctx)

    fun confirm(a: Activity) {
        MaterialAlertDialogBuilder(a)
            .setTitle(R.string.last_restore_title)
            .setMessage(R.string.last_restore_dialog_message)
            .setPositiveButton(R.string.last_restore_run) { _, _ -> LogJob.recover(a) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
