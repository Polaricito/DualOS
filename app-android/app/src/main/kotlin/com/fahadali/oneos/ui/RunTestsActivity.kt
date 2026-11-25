package com.fahadali.oneos.ui

import android.content.Intent
import android.os.Bundle
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fahadali.oneos.R
import com.fahadali.oneos.core.model.Level
import com.fahadali.oneos.core.model.ProbeCommand
import com.fahadali.oneos.core.model.ProbeCommandList
import com.fahadali.oneos.core.model.ProbeKind
import com.fahadali.oneos.core.model.ProbeStatus
import com.fahadali.oneos.probe.ProbeSession
import com.fahadali.oneos.probe.ProbeSession.Phase
import com.fahadali.oneos.shared.DualOsIcons
import com.fahadali.oneos.shared.DualOsTheme
import com.fahadali.oneos.shared.ExpressiveGroupedCard
import com.fahadali.oneos.shared.ExpressiveListDivider
import com.fahadali.oneos.shared.ExpressiveListItem
import com.fahadali.oneos.shared.ExpressiveSectionHeader
import com.fahadali.oneos.shared.LocalDualOsStatusColors

/** Run tests: read-only probes as root, then the findings to share. */
class RunTestsActivity : AppCompatActivity() {
    private var unsubscribe: (() -> Unit)? = null
    private lateinit var bundled: ProbeSession.Bundled
    private var sessionState: ProbeSession.State by mutableStateOf(ProbeSession.state)
    private var showConfirmDialog: Boolean by mutableStateOf(false)
    private var inspectedCommand: ProbeCommand? by mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        bundled = ProbeSession.bundled(this)
        setContent {
            DualOsTheme {
                Screen()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        unsubscribe = ProbeSession.observe { sessionState = it }
    }

    override fun onStop() {
        unsubscribe?.invoke()
        unsubscribe = null
        super.onStop()
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun Screen() {
        val s = sessionState
        val statusColors = LocalDualOsStatusColors.current
        val canRun = bundled.problems.isEmpty() && !s.busy
        val zip = if (s.busy) null else (s.zip ?: ProbeSession.latestZip(this))

        val statusSummary = when (s.phase) {
            Phase.IDLE -> s.message ?: stringResource(R.string.tests_run_summary)
            Phase.ROOT_CHECK -> stringResource(R.string.tests_checking_root)
            Phase.ROOT_FAILED, Phase.FAILED -> s.message.orEmpty()
            Phase.RUNNING -> "Running ${minOf(s.finished + 1, s.total)} of ${s.total}" + (s.current?.let { ": $it" } ?: "")
            Phase.BUILDING -> stringResource(R.string.tests_building)
            Phase.DONE -> s.message.orEmpty()
        }

        DualOsScaffold(
            title = stringResource(R.string.tests_title),
            subtitle = stringResource(R.string.tests_subtitle),
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
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = stringResource(if (s.phase == Phase.DONE) R.string.tests_run_again else R.string.tests_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = statusSummary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (s.busy) {
                                if (s.phase == Phase.RUNNING && s.total > 0) {
                                    LinearWavyProgressIndicator(
                                        progress = { (s.finished.toFloat() / s.total.toFloat()).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                } else {
                                    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                if (s.phase == Phase.ROOT_CHECK || s.phase == Phase.RUNNING) {
                                    OutlinedButton(
                                        onClick = { ProbeSession.cancel() },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(16.dp),
                                    ) {
                                        Text(stringResource(R.string.tests_stop_title))
                                    }
                                } else {
                                    Button(
                                        onClick = { showConfirmDialog = true },
                                        enabled = canRun,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(16.dp),
                                    ) {
                                        Text(stringResource(if (s.phase == Phase.DONE) R.string.tests_run_again else R.string.tests_run))
                                    }
                                }
                                if (zip != null) {
                                    FilledTonalButton(
                                        onClick = { startActivity(Intent(this@RunTestsActivity, FindingsActivity::class.java)) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(16.dp),
                                    ) {
                                        Text(stringResource(R.string.findings_title))
                                    }
                                }
                            }
                        }
                    }
                }

                if (bundled.problems.isNotEmpty()) {
                    item {
                        ExpressiveGroupedCard {
                            ExpressiveListItem(
                                title = stringResource(R.string.tests_list_broken_title),
                                subtitle = bundled.problems.take(5).joinToString("\n"),
                                icon = levelIcon(Level.BLOCK),
                                iconTint = levelColor(Level.BLOCK),
                            )
                        }
                    }
                }

                item { ExpressiveSectionHeader(stringResource(R.string.tests_cat_about)) }
                item {
                    ExpressiveGroupedCard {
                        ExpressiveListItem(
                            title = stringResource(R.string.tests_what_title),
                            subtitle = stringResource(R.string.tests_what_summary),
                            icon = DualOsIcons.Science,
                        )
                        ExpressiveListDivider()
                        ExpressiveListItem(
                            title = stringResource(R.string.tests_readonly_title),
                            subtitle = stringResource(R.string.tests_readonly_summary),
                            icon = DualOsIcons.Security,
                        )
                        ExpressiveListDivider()
                        ExpressiveListItem(
                            title = stringResource(R.string.tests_privacy_title),
                            subtitle = stringResource(R.string.tests_privacy_summary),
                            icon = DualOsIcons.Shield,
                        )
                    }
                }

                val groups = ProbeCommandList.groups(bundled.commands)
                for (group in groups) {
                    val cmds = bundled.commands.filter { it.group == group }
                    item(key = "hdr_$group") { ExpressiveSectionHeader(group) }
                    item(key = "grp_$group") {
                        ExpressiveGroupedCard {
                            cmds.forEachIndexed { idx, c ->
                                if (idx > 0) ExpressiveListDivider()
                                val st = s.statuses[c.id] ?: ProbeStatus.PENDING
                                val code = s.exitCodes[c.id]
                                val sub = buildString {
                                    append(c.id)
                                    if (s.statuses.isNotEmpty()) {
                                        append(" · ").append(st.label)
                                        if (st == ProbeStatus.FAILED && code != null) append(" (exit $code)")
                                    }
                                }
                                val (ico, tint) = if (s.statuses.isEmpty()) {
                                    DualOsIcons.Terminal to MaterialTheme.colorScheme.onSurfaceVariant
                                } else when (st) {
                                    ProbeStatus.PENDING -> Icons.Rounded.Info to MaterialTheme.colorScheme.onSurfaceVariant
                                    ProbeStatus.RUNNING -> Icons.Rounded.Refresh to MaterialTheme.colorScheme.primary
                                    ProbeStatus.OK -> Icons.Rounded.CheckCircle to statusColors.ok
                                    ProbeStatus.NOT_PRESENT -> Icons.Rounded.Close to MaterialTheme.colorScheme.onSurfaceVariant
                                    ProbeStatus.TIMEOUT -> Icons.Rounded.Warning to statusColors.warn
                                    ProbeStatus.FAILED -> DualOsIcons.Block to statusColors.block
                                }
                                ExpressiveListItem(
                                    title = c.description,
                                    subtitle = sub,
                                    icon = ico,
                                    iconTint = tint,
                                    onClick = { inspectedCommand = c },
                                )
                            }
                        }
                    }
                }
            }

            if (showConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showConfirmDialog = false },
                    title = { Text(stringResource(R.string.tests_dialog_title)) },
                    text = { Text(stringResource(R.string.tests_dialog_message)) },
                    confirmButton = {
                        TextButton(onClick = {
                            showConfirmDialog = false
                            ProbeSession.start(this@RunTestsActivity)
                        }) {
                            Text(stringResource(R.string.tests_run))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showConfirmDialog = false }) {
                            Text(stringResource(android.R.string.cancel))
                        }
                    },
                )
            }

            inspectedCommand?.let { c ->
                val header = "Runs as root, read only, for at most ${c.timeoutSec} s. Output: ${c.target}" +
                    (if (c.kind == ProbeKind.FILES) " (one file per path it prints)" else "") + "\n"
                MonospaceViewerDialog(
                    title = c.id,
                    header = header,
                    body = c.command.trim(),
                    onDismiss = { inspectedCommand = null },
                )
            }
        }
    }
}
