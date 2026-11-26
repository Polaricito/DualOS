package com.fahadali.oneos.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
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
import androidx.compose.material.icons.rounded.Delete
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fahadali.oneos.R
import com.fahadali.oneos.core.model.BringApps
import com.fahadali.oneos.core.model.BringApps.Change
import com.fahadali.oneos.core.model.BringApps.CopyState
import com.fahadali.oneos.core.model.ImageRef
import com.fahadali.oneos.core.model.Images
import com.fahadali.oneos.core.model.Level
import com.fahadali.oneos.session.BringAppsJob
import com.fahadali.oneos.session.BringAppsJob.Phase
import com.fahadali.oneos.session.StageCheck
import com.fahadali.oneos.shared.DualOsIcons
import com.fahadali.oneos.shared.DualOsTheme
import com.fahadali.oneos.shared.ExpressiveGroupedCard
import com.fahadali.oneos.shared.ExpressiveListDivider
import com.fahadali.oneos.shared.ExpressiveListItem
import com.fahadali.oneos.shared.ExpressiveSectionHeader
import com.fahadali.oneos.shared.ExpressiveStatusPill

/** Bring apps: copy selected user apps + data one-way from One UI into the guest system. */
class BringAppsActivity : AppCompatActivity() {
    private val subs = mutableListOf<() -> Unit>()
    private var loadOnStart = false
    private var asked = false
    private var forgetting: String? by mutableStateOf(null)
    private var detailsOpen by mutableStateOf(false)
    private var tick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        loadOnStart = savedInstanceState == null
        setContent {
            DualOsTheme {
                Screen()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        asked = false
        syncStage()
        subs += BringAppsJob.observe { tick++ }
        subs += StageCheck.observe { syncStage(); tick++ }
        if (!asked) BringAppsJob.refresh(this)
        val st = BringAppsJob.state
        if (!st.busy && (loadOnStart || st.candidates == null)) BringAppsJob.load(this)
        loadOnStart = false
        if (StageCheck.state.result == null && !StageCheck.state.busy) {
            StageCheck.start(this, StageCheck.Mode.STAGE_ONLY)
        }
    }

    override fun onStop() {
        subs.forEach { it() }
        subs.clear()
        super.onStop()
    }

    @Composable
    private fun Screen() {
        @Suppress("UNUSED_VARIABLE") val t = tick
        val st = BringAppsJob.state
        val stage = StageCheck.state
        val status = stage.result?.status?.takeIf { it.ours }
        val other = status != null && !onThisPage(status.marker?.imageId)
        val prepared = status != null && !other
        val system = systemName()
        val candidates = st.candidates
        val ticked = st.selected.size
        val synced = prepared && st.stageKey != null && st.stageKey == BringAppsJob.stageKey(status?.marker)
        val copies = if (synced) st.copies.orEmpty().associateBy { it.pkg } else emptyMap()
        val stale = synced && st.stale
        val split = BringApps.split(st.selected, if (synced) st.copies else null, if (synced) st.brought else emptySet())
        if (st.phase != Phase.CLEARING) forgetting = null

        DualOsScaffold(
            title = stringResource(R.string.bring_title),
            subtitle = system,
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
                item { ExpressiveSectionHeader(stringResource(R.string.bring_card_about)) }
                item {
                    ExpressiveGroupedCard {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.bring_about_body, system), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.bring_about_one_way, system), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.bring_card_apps),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (ticked > 0) {
                            ExpressiveStatusPill(
                                text = pluralStringResource(R.plurals.bring_ticked_n, ticked, ticked),
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
                item {
                    ExpressiveGroupedCard {
                        when {
                            candidates == null && st.phase == Phase.LOADING ->
                                ExpressiveListItem(title = stringResource(R.string.bring_loading), icon = Icons.Rounded.Refresh)
                            candidates == null -> {
                                ExpressiveListItem(title = stringResource(R.string.bring_not_read), subtitle = st.message, icon = levelIcon(Level.WARN), iconTint = levelColor(Level.WARN))
                                if (!st.busy) {
                                    ExpressiveListDivider()
                                    ExpressiveListItem(title = stringResource(R.string.bring_look_again), icon = Icons.Rounded.Refresh, showChevron = true, onClick = { BringAppsJob.load(this@BringAppsActivity) })
                                }
                            }
                            candidates.isEmpty() ->
                                ExpressiveListItem(title = stringResource(R.string.bring_none), subtitle = stringResource(R.string.bring_none_summary, system), icon = DualOsIcons.Apps)
                            else -> {
                                val newer = candidates.filter { c -> c.pkg !in st.selected && copies[c.pkg]?.let { it.state == CopyState.INSTALLED && it.newer } == true }
                                if (newer.isNotEmpty()) {
                                    ExpressiveListItem(
                                        title = pluralStringResource(R.plurals.bring_newer_n, newer.size, newer.size),
                                        subtitle = stringResource(R.string.bring_newer_summary, system),
                                        icon = levelIcon(Level.INFO),
                                        iconTint = levelColor(Level.INFO),
                                        enabled = !st.busy,
                                        showChevron = !st.busy,
                                        onClick = { BringAppsJob.tick(this@BringAppsActivity, newer.map { it.pkg }) },
                                    )
                                    ExpressiveListDivider()
                                }
                                CheckboxRow(
                                    title = stringResource(R.string.bring_select_all),
                                    subtitle = pluralStringResource(R.plurals.bring_select_all_n, candidates.size, candidates.size),
                                    checked = ticked == candidates.size,
                                    enabled = !st.busy,
                                    onCheckedChange = { BringAppsJob.setAll(this@BringAppsActivity, it) },
                                )
                                candidates.forEach { c ->
                                    ExpressiveListDivider()
                                    CheckboxRow(
                                        title = c.label,
                                        subtitle = rowSummary(c, copies[c.pkg], c.pkg in st.selected, system),
                                        checked = c.pkg in st.selected,
                                        enabled = !st.busy,
                                        onCheckedChange = { BringAppsJob.toggle(this@BringAppsActivity, c.pkg, it) },
                                    )
                                }
                            }
                        }
                    }
                }

                val broughtApps = if (synced) st.brought.sortedBy { label(it).lowercase() } else emptyList()
                if (broughtApps.isNotEmpty() && st.phase != Phase.COLLECTING) {
                    item { ExpressiveSectionHeader(stringResource(R.string.bring_card_brought)) }
                    item {
                        ExpressiveGroupedCard {
                            broughtApps.forEachIndexed { idx, pkg ->
                                if (idx > 0) ExpressiveListDivider()
                                ExpressiveListItem(
                                    title = stringResource(R.string.bring_forget_row, label(pkg)),
                                    subtitle = if (st.phase == Phase.CLEARING && forgetting == pkg) stringResource(R.string.bring_removing)
                                    else stringResource(R.string.bring_forget_row_summary, system),
                                    icon = Icons.Rounded.Delete,
                                    iconTint = MaterialTheme.colorScheme.error,
                                    enabled = !stale && !st.busy,
                                    onClick = { confirmForget(pkg, system) },
                                )
                            }
                        }
                    }
                }

                item {
                    val list = copies.values.sortedBy { label(it.pkg).lowercase() }
                    val message = st.message
                    ExpressiveGroupedCard {
                        var hasTop = false
                        @Composable
                        fun sep() { if (hasTop) ExpressiveListDivider(); hasTop = true }
                        when {
                            prepared -> Unit
                            stage.busy -> {
                                sep(); ExpressiveListItem(title = stringResource(R.string.bring_stage_checking), icon = Icons.Rounded.Refresh)
                            }
                            stage.result == null -> {
                                sep(); ExpressiveListItem(title = stringResource(R.string.bring_stage_not_read), subtitle = stringResource(R.string.bring_stage_not_read_summary), icon = levelIcon(Level.WARN), iconTint = levelColor(Level.WARN))
                                sep(); ExpressiveListItem(title = stringResource(R.string.bring_stage_check_again), icon = Icons.Rounded.Refresh, showChevron = true, onClick = { StageCheck.start(this@BringAppsActivity, StageCheck.Mode.STAGE_ONLY) })
                            }
                            other -> {
                                sep(); ExpressiveListItem(
                                    title = stringResource(R.string.bring_other_system, system),
                                    subtitle = stringResource(R.string.bring_other_system_summary, Images.systemName(status?.marker?.imageId?.let(Images::byId)), system),
                                    icon = levelIcon(Level.WARN), iconTint = levelColor(Level.WARN),
                                )
                            }
                            else -> {
                                sep(); ExpressiveListItem(title = stringResource(R.string.bring_no_stage), subtitle = stringResource(R.string.bring_no_stage_summary), icon = levelIcon(Level.WARN), iconTint = levelColor(Level.WARN))
                            }
                        }
                        if (stale) {
                            sep(); ExpressiveListItem(title = stringResource(R.string.bring_update_first, system), subtitle = stringResource(R.string.bring_update_first_summary, system), icon = levelIcon(Level.WARN), iconTint = levelColor(Level.WARN))
                        }
                        when {
                            st.phase == Phase.COLLECTING -> {
                                val n = if (st.working > 0) st.working else ticked
                                sep(); ExpressiveListItem(
                                    title = pluralStringResource(R.plurals.bring_working_n, n, n),
                                    subtitle = st.current?.let { stringResource(R.string.bring_working_at, label(it)) } ?: stringResource(R.string.bring_working_summary, system),
                                    icon = DualOsIcons.Download,
                                )
                            }
                            ticked == 0 -> {
                                sep(); ExpressiveListItem(title = stringResource(R.string.bring_do_none), subtitle = stringResource(R.string.bring_do_none_summary, system), icon = DualOsIcons.Download, enabled = false)
                            }
                            else -> {
                                val fresh = split.fresh.size
                                val there = split.there.size
                                sep(); ExpressiveListItem(
                                    title = if (fresh > 0) pluralStringResource(R.plurals.bring_do_n, fresh, fresh)
                                    else pluralStringResource(R.plurals.bring_again_n, there, there),
                                    subtitle = when {
                                        there == 0 -> stringResource(R.string.bring_do_summary, system)
                                        fresh == 0 -> stringResource(R.string.bring_again_summary, system)
                                        else -> pluralStringResource(R.plurals.bring_do_there_summary, there, there, system)
                                    },
                                    icon = DualOsIcons.Download,
                                    enabled = prepared && !stale && !st.busy && candidates != null,
                                    showChevron = prepared && !stale && !st.busy && candidates != null,
                                    onClick = { confirmBring(split, system) },
                                )
                            }
                        }
                        if (st.phase == Phase.CLEARING && forgetting == null) {
                            sep(); ExpressiveListItem(title = stringResource(R.string.bring_removing), icon = Icons.Rounded.Delete)
                        } else if (synced && st.brought.isNotEmpty()) {
                            sep(); ExpressiveListItem(
                                title = stringResource(R.string.bring_remove),
                                subtitle = stringResource(R.string.bring_remove_summary, system),
                                icon = Icons.Rounded.Delete,
                                iconTint = MaterialTheme.colorScheme.error,
                                enabled = !stale && !st.busy,
                                onClick = { confirmClear(system) },
                            )
                        }
                        when {
                            st.busy -> Unit
                            message != null && st.failed -> {
                                sep(); ExpressiveListItem(title = message, icon = levelIcon(Level.WARN), iconTint = levelColor(Level.WARN))
                            }
                            message != null && st.phase == Phase.DONE -> {
                                sep(); ExpressiveListItem(
                                    title = message,
                                    subtitle = when {
                                        st.brought.isEmpty() -> null
                                        st.skipped.isNotEmpty() -> stringResource(R.string.bring_done_skipped)
                                        else -> stringResource(R.string.bring_done_summary, system)
                                    },
                                    icon = levelIcon(Level.OK), iconTint = levelColor(Level.OK),
                                )
                            }
                            list.isNotEmpty() -> {
                                val installed = list.count { it.state == CopyState.INSTALLED }
                                val waiting = list.count { it.state == CopyState.WAITING }
                                val failed = list.count { it.state == CopyState.FAILED }
                                val lv = when { failed > 0 -> Level.WARN; waiting > 0 -> Level.INFO; else -> Level.OK }
                                sep(); ExpressiveListItem(
                                    title = listOfNotNull(
                                        if (installed > 0) stringResource(R.string.bring_count_installed, installed, system) else null,
                                        if (waiting > 0) stringResource(R.string.bring_count_waiting, waiting) else null,
                                        if (failed > 0) stringResource(R.string.bring_count_failed, failed) else null,
                                    ).joinToString(" · "),
                                    subtitle = when {
                                        failed > 0 -> stringResource(R.string.bring_count_failed_summary, system)
                                        waiting > 0 -> stringResource(R.string.bring_count_waiting_summary, system)
                                        else -> null
                                    },
                                    icon = levelIcon(lv), iconTint = levelColor(lv),
                                )
                            }
                        }
                        sep(); ExpressiveListItem(
                            title = stringResource(R.string.details_title),
                            subtitle = stringResource(R.string.bring_details_summary, system),
                            icon = if (detailsOpen) DualOsIcons.ArrowDropUp else DualOsIcons.ArrowDropDown,
                            onClick = { detailsOpen = !detailsOpen },
                        )
                        if (detailsOpen) {
                            ExpressiveListDivider()
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.bring_details_how, system), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (list.isNotEmpty()) {
                                    list.forEach { c ->
                                        Text("${label(c.pkg)}\n${copyLines(c, system)}", style = MaterialTheme.typography.bodySmall)
                                    }
                                } else if (synced && st.brought.isNotEmpty()) {
                                    st.brought.sorted().forEach { pkg -> Text("${label(pkg)} ($pkg)", style = MaterialTheme.typography.bodySmall) }
                                } else {
                                    Text(stringResource(R.string.bring_details_none), style = MaterialTheme.typography.bodySmall)
                                }
                                if (st.notes.isNotEmpty()) Text(st.notes.joinToString("\n"), style = MaterialTheme.typography.bodySmall)
                                if (synced && st.log.isNotEmpty()) Text(st.log.joinToString("\n"), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun CheckboxRow(title: String, subtitle: String?, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (enabled) Modifier.clickable { onCheckedChange(!checked) } else Modifier)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Checkbox(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    private fun rowSummary(c: BringAppsJob.Candidate, copy: BringApps.Copy?, ticked: Boolean, system: String): String = when {
        copy == null -> if (c.version.isBlank()) c.pkg else getString(R.string.bring_app_summary, c.version, c.pkg)
        ticked && copy.state == CopyState.INSTALLED -> getString(R.string.bring_row_again, system)
        ticked -> getString(R.string.bring_row_ticked, system)
        copy.state == CopyState.WAITING -> getString(R.string.bring_row_waiting, system)
        copy.state == CopyState.FAILED -> getString(R.string.bring_row_failed, system)
        copy.change == Change.APP -> getString(R.string.bring_row_newer_app, system)
        copy.change == Change.DATA -> getString(R.string.bring_row_newer_data, system)
        copy.stamp > 0 -> getString(R.string.bring_row_installed_on, system, date(copy.stamp))
        else -> getString(R.string.bring_row_installed, system)
    }

    private fun copyLines(c: BringApps.Copy, system: String): String = listOfNotNull(
        c.pkg,
        when (c.state) {
            CopyState.WAITING -> if (c.stamp > 0) getString(R.string.bring_state_waiting_on, date(c.stamp)) else getString(R.string.bring_state_waiting)
            CopyState.INSTALLED -> if (c.stamp > 0) getString(R.string.bring_state_installed_on, system, date(c.stamp)) else getString(R.string.bring_state_installed, system)
            CopyState.FAILED -> getString(R.string.bring_state_failed, system, c.why ?: getString(R.string.bring_state_no_reason))
        },
        when (c.change) {
            Change.SAME -> null
            Change.DATA -> getString(R.string.bring_change_data)
            Change.APP -> getString(R.string.bring_change_app)
            Change.GONE -> getString(R.string.bring_change_gone)
        },
    ).joinToString("\n")

    private fun date(epoch: Long): String = DateUtils.formatDateTime(
        this, epoch * 1000,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_NO_YEAR,
    )

    private fun pageImage(): ImageRef? = intent.getStringExtra(EXTRA_IMAGE)?.let(Images::byId)
    private fun onThisPage(imageId: String?): Boolean = pageImage()?.let { it.id == imageId } ?: true

    private fun syncStage() {
        val status = StageCheck.state.result?.status
        val page = pageImage()?.id
        if (status == null) {
            if (page != null && BringAppsJob.state.imageId != page) BringAppsJob.page(this, page, null)
            return
        }
        val marker = status.marker?.takeIf { status.ours && onThisPage(it.imageId) }
        if (BringAppsJob.page(this, page ?: status.marker?.takeIf { status.ours }?.imageId, marker)) asked = true
    }

    private fun systemName(): String {
        pageImage()?.let { return Images.systemName(it) }
        val status = StageCheck.state.result?.status?.takeIf { it.ours }
        return Images.systemName(status?.marker?.imageId?.let(Images::byId))
    }

    private fun label(pkg: String): String = BringAppsJob.state.candidates?.firstOrNull { it.pkg == pkg }?.label ?: pkg

    private fun confirmBring(split: BringApps.Ticked, system: String) {
        val fresh = split.fresh.size
        val there = split.there.size
        val dialog = MaterialAlertDialogBuilder(this).setNegativeButton(android.R.string.cancel, null)
        when {
            there == 0 -> dialog
                .setTitle(getString(R.string.bring_confirm_title, system))
                .setMessage(resources.getQuantityString(R.plurals.bring_confirm_body, fresh, fresh, system))
                .setPositiveButton(R.string.bring_confirm_do) { _, _ -> BringAppsJob.collect(this) }
            fresh == 0 -> dialog
                .setTitle(resources.getQuantityString(R.plurals.bring_again_title, there, there))
                .setMessage(resources.getQuantityString(R.plurals.bring_again_body, there, there, system))
                .setPositiveButton(R.string.bring_again_do) { _, _ -> BringAppsJob.collect(this) }
            else -> dialog
                .setTitle(getString(R.string.bring_confirm_title, system))
                .setMessage(
                    resources.getQuantityString(R.plurals.bring_confirm_body, fresh, fresh, system) + "\n\n" +
                        resources.getQuantityString(R.plurals.bring_confirm_there, there, there, system),
                )
                .setPositiveButton(R.string.bring_confirm_new) { _, _ -> BringAppsJob.collect(this, split.fresh) }
                .setNeutralButton(getString(R.string.bring_confirm_all, fresh + there)) { _, _ -> BringAppsJob.collect(this) }
        }
        dialog.show()
    }

    private fun confirmForget(pkg: String, system: String) {
        val name = label(pkg)
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.bring_forget_title, name))
            .setMessage(getString(R.string.bring_forget_confirm, name, system))
            .setPositiveButton(R.string.bring_remove_do) { _, _ -> forgetting = pkg; BringAppsJob.forget(this, pkg) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmClear(system: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.bring_remove_title)
            .setMessage(getString(R.string.bring_remove_confirm, system))
            .setPositiveButton(R.string.bring_remove_do) { _, _ -> BringAppsJob.clear(this) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        const val EXTRA_IMAGE = "image"

        fun intent(ctx: Context, image: ImageRef): Intent =
            Intent(ctx, BringAppsActivity::class.java).putExtra(EXTRA_IMAGE, image.id)
    }
}
