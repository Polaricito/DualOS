package com.fahadali.oneos.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fahadali.oneos.R
import com.fahadali.oneos.core.model.GatePhase
import com.fahadali.oneos.core.model.Images
import com.fahadali.oneos.core.model.Level
import com.fahadali.oneos.session.LaunchJob
import com.fahadali.oneos.session.SessionPrefs
import com.fahadali.oneos.session.StageCheck
import com.fahadali.oneos.shared.DualOsIcons
import com.fahadali.oneos.shared.DualOsTheme
import com.fahadali.oneos.shared.ExpressiveGroupedCard
import com.fahadali.oneos.shared.ExpressiveListDivider
import com.fahadali.oneos.shared.ExpressiveListItem
import com.fahadali.oneos.shared.ExpressiveSectionHeader
import com.fahadali.oneos.shared.ExpressiveSwitchItem
import com.fahadali.oneos.shared.LocalDualOsStatusColors

/** All checks and Test run: ways back, full switch gate list, warnings, hide-GMS option, dry-run/switch actions. */
class SwitchActivity : AppCompatActivity() {
    private val subs = mutableListOf<() -> Unit>()
    private var ticked: Set<String> by mutableStateOf(emptySet())
    private var hideGms by mutableStateOf(false)
    private var tick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        savedInstanceState?.getStringArrayList(KEY_TICKED)?.let { ticked = it.toSet() }
        hideGms = savedInstanceState?.getBoolean(KEY_HIDE_GMS) == true
        setContent {
            DualOsTheme {
                Screen()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(KEY_TICKED, ArrayList(ticked))
        outState.putBoolean(KEY_HIDE_GMS, hideGms)
    }

    override fun onStart() {
        super.onStart()
        subs += StageCheck.observe { tick++ }
        subs += LaunchJob.observe { tick++ }
    }

    override fun onResume() {
        super.onResume()
        tick++
    }

    override fun onStop() {
        subs.forEach { it() }
        subs.clear()
        super.onStop()
    }

    @Composable
    private fun Screen() {
        @Suppress("UNUSED_VARIABLE") val t = tick
        val launch = LaunchJob.state
        val check = StageCheck.state
        val r = check.result
        val report = r?.takeIf { it.installd != null }?.let { StageCheck.gates(this, it, GatePhase.SWITCH) }
        val system = Images.systemName(report?.image, report?.profile)
        val statusColors = LocalDualOsStatusColors.current

        DualOsScaffold(
            title = stringResource(R.string.switch_title),
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
                item { ExpressiveSectionHeader(stringResource(R.string.switch_cat_back)) }
                item {
                    ExpressiveGroupedCard {
                        var hasTop = false
                        @Composable
                        fun sep() { if (hasTop) ExpressiveListDivider(); hasTop = true }
                        if (report?.set?.hasReturnApp == true) {
                            sep(); ExpressiveListItem(title = stringResource(R.string.home_sheet_back_app), subtitle = stringResource(R.string.home_sheet_back_app_line, system), icon = Icons.AutoMirrored.Rounded.ExitToApp)
                        }
                        sep(); ExpressiveListItem(title = stringResource(R.string.switch_back_keys_title), subtitle = stringResource(R.string.switch_back_keys_summary, system), icon = DualOsIcons.VolumeUp)
                        sep(); ExpressiveListItem(title = stringResource(R.string.switch_back_auto_title), subtitle = stringResource(R.string.switch_back_auto_summary, system), icon = DualOsIcons.History)
                        sep(); ExpressiveListItem(title = stringResource(R.string.switch_back_force_title), subtitle = stringResource(R.string.switch_back_force_summary), icon = DualOsIcons.Power)
                        sep(); ExpressiveListItem(title = stringResource(R.string.switch_back_root_title), subtitle = stringResource(R.string.switch_back_root_summary), icon = DualOsIcons.Security)
                    }
                }

                item { ExpressiveSectionHeader(stringResource(R.string.switch_cat_checks)) }
                item {
                    ExpressiveGroupedCard {
                        if (check.busy) {
                            ExpressiveListItem(title = check.step ?: stringResource(R.string.prepare_checking), icon = Icons.Rounded.Refresh)
                        } else {
                            ExpressiveListItem(
                                title = stringResource(if (r == null) R.string.prepare_check_title else R.string.prepare_check_again),
                                subtitle = check.message ?: stringResource(R.string.switch_check_summary),
                                icon = Icons.Rounded.Refresh,
                                enabled = !launch.busy,
                                showChevron = !launch.busy,
                                onClick = { if (!launch.busy) StageCheck.start(this@SwitchActivity, StageCheck.Mode.FULL) },
                            )
                        }
                        if (r != null && report == null) {
                            ExpressiveListDivider()
                            ExpressiveListItem(title = stringResource(R.string.switch_need_full), icon = levelIcon(Level.BLOCK), iconTint = levelColor(Level.BLOCK))
                        }
                        report?.report?.sorted?.forEach { f ->
                            ExpressiveListDivider()
                            ExpressiveListItem(title = f.title, subtitle = f.detail.ifEmpty { null }, icon = levelIcon(f.level), iconTint = levelColor(f.level))
                        }
                    }
                }

                if (r != null && report != null) {
                    val stale = r.ageMs > StageCheck.FRESH_MS
                    val toTick = report.toTick
                    val validTicked = ticked.intersect(toTick.mapNotNull { it.tickKey }.toSet())
                    if (toTick.isNotEmpty()) {
                        item { ExpressiveSectionHeader(stringResource(R.string.switch_cat_tick)) }
                        item {
                            ExpressiveGroupedCard {
                                toTick.forEachIndexed { idx, g ->
                                    if (idx > 0) ExpressiveListDivider()
                                    val key = g.tickKey
                                    val isChecked = key in validTicked
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (key != null) {
                                                    ticked = if (isChecked) validTicked - key else validTicked + key
                                                }
                                            }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { on ->
                                                if (key != null) ticked = if (on) validTicked + key else validTicked - key
                                            },
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(g.ack.orEmpty(), style = MaterialTheme.typography.titleSmall)
                                            Text(g.finding.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item { ExpressiveSectionHeader(stringResource(R.string.switch_cat_go)) }
                    item {
                        val effectiveHideGms = if (report.image?.gmsHideable == true) hideGms else false
                        val allowed = !stale && report.allows(validTicked) && !launch.busy && launch.phase != LaunchJob.Phase.LAUNCHED
                        val why = when {
                            stale -> stringResource(R.string.prepare_run_stale)
                            report.blocked -> stringResource(R.string.switch_blocked)
                            !report.allows(validTicked) -> stringResource(R.string.switch_tick_first)
                            else -> null
                        }
                        val setupNote = report.image?.presentsGms == true
                        val backApp = report.set?.hasReturnApp == true

                        ExpressiveGroupedCard {
                            var hasTop = false
                            @Composable
                            fun sep() { if (hasTop) ExpressiveListDivider(); hasTop = true }
                            if (report.image?.gmsHideable == true) {
                                sep(); ExpressiveSwitchItem(
                                    title = stringResource(R.string.switch_hide_gms_title),
                                    subtitle = stringResource(R.string.switch_hide_gms_summary, system),
                                    checked = hideGms,
                                    enabled = !launch.busy && launch.phase != LaunchJob.Phase.LAUNCHED,
                                    onCheckedChange = { hideGms = it },
                                )
                            }
                            sep(); ExpressiveListItem(
                                title = stringResource(R.string.switch_dry_title),
                                subtitle = why ?: stringResource(R.string.switch_dry_summary, system),
                                icon = DualOsIcons.Science,
                                enabled = allowed,
                                showChevron = allowed,
                                onClick = { if (allowed) confirm(true, system, setupNote, backApp, validTicked, effectiveHideGms) },
                            )
                            sep(); ExpressiveListItem(
                                title = stringResource(R.string.switch_go_title, system),
                                subtitle = why ?: stringResource(R.string.switch_go_summary, system),
                                icon = DualOsIcons.Power,
                                iconTint = if (allowed) statusColors.ok else MaterialTheme.colorScheme.onSurfaceVariant,
                                enabled = allowed,
                                showChevron = allowed,
                                onClick = { if (allowed) confirm(false, system, setupNote, backApp, validTicked, effectiveHideGms) },
                            )
                            when (launch.phase) {
                                LaunchJob.Phase.RUNNING -> {
                                    sep(); ExpressiveListItem(title = launch.detail ?: stringResource(R.string.switch_starting), icon = Icons.Rounded.Refresh)
                                }
                                LaunchJob.Phase.LAUNCHED -> {
                                    sep(); ExpressiveListItem(title = stringResource(R.string.switch_launched), subtitle = launch.message, icon = levelIcon(Level.OK), iconTint = levelColor(Level.OK))
                                }
                                LaunchJob.Phase.FAILED -> {
                                    sep(); ExpressiveListItem(title = stringResource(R.string.switch_not_started), subtitle = launch.message, icon = levelIcon(Level.BLOCK), iconTint = levelColor(Level.BLOCK))
                                }
                                LaunchJob.Phase.IDLE -> Unit
                            }
                        }
                    }
                }
            }
        }
    }

    private fun confirm(dry: Boolean, system: String, setupNote: Boolean, backApp: Boolean, validTicked: Set<String>, hide: Boolean) {
        val back = if (backApp) getString(R.string.switch_go_dialog_back_app, system) else getString(R.string.switch_go_dialog_back_keys)
        val message = (if (dry) getString(R.string.switch_dry_dialog_message, system) else getString(R.string.switch_go_dialog_message, system, back)) +
            (if (!dry && setupNote) "\n\n" + getString(R.string.confirm_setup_wizard, system) else "") +
            (if (hide) "\n\n" + getString(R.string.switch_dialog_gms_hidden) else "") +
            if (!dry && !SessionPrefs.heatCap(this)) "\n\n" + getString(R.string.confirm_heat_cap_off) else ""
        MaterialAlertDialogBuilder(this)
            .setTitle(if (dry) getString(R.string.switch_dry_dialog_title) else getString(R.string.switch_go_dialog_title, system))
            .setMessage(message)
            .setPositiveButton(if (dry) getString(R.string.switch_dry_title) else getString(R.string.switch_go_title, system)) { _, _ ->
                LaunchJob.start(this, dry, validTicked, hide, SessionPrefs.secIms(this), SessionPrefs.edmStub(this))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private companion object {
        const val KEY_TICKED = "ticked"
        const val KEY_HIDE_GMS = "hide_gms"
    }
}
