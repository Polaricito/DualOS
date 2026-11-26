package com.fahadali.oneos.ui

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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fahadali.oneos.R
import com.fahadali.oneos.core.model.Images
import com.fahadali.oneos.core.model.Level
import com.fahadali.oneos.core.model.Units
import com.fahadali.oneos.core.root.OldStage
import com.fahadali.oneos.session.OldStagesJob
import com.fahadali.oneos.session.StageCheck
import com.fahadali.oneos.shared.DualOsIcons
import com.fahadali.oneos.shared.DualOsTheme
import com.fahadali.oneos.shared.ExpressiveGroupedCard
import com.fahadali.oneos.shared.ExpressiveListDivider
import com.fahadali.oneos.shared.ExpressiveListItem
import com.fahadali.oneos.shared.ExpressiveSectionHeader

/** Free up space: phone storage usage, current stage size, and old stage copies to restore or delete. */
class OldStagesActivity : AppCompatActivity() {
    private val subs = mutableListOf<() -> Unit>()
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
        subs += OldStagesJob.observe { tick++ }
        subs += StageCheck.observe { tick++ }
        if (!OldStagesJob.state.busy) OldStagesJob.refresh(this)
    }

    override fun onStop() {
        subs.forEach { it() }
        subs.clear()
        super.onStop()
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun Screen() {
        @Suppress("UNUSED_VARIABLE") val t = tick
        val st = OldStagesJob.state
        val stages = st.stages
        val oldBytes = stages?.sumOf { it.kib }?.times(1024)
        val storage = readDataStorage()
        val now = StageCheck.state.result?.status?.takeIf { it.ours }
        val nowRaw = now?.rawSize

        DualOsScaffold(
            title = stringResource(R.string.space_title),
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
                item { ExpressiveSectionHeader(stringResource(R.string.space_card_storage)) }
                item {
                    ExpressiveGroupedCard {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (storage != null) {
                                val (free, total) = storage
                                val usedFraction = ((total - free).toFloat() / total.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                                LinearWavyProgressIndicator(progress = { usedFraction }, modifier = Modifier.fillMaxWidth())
                                Text(
                                    text = stringResource(R.string.space_free_line, Units.bytes(free), Units.bytes(total)),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (now != null && nowRaw != null) {
                                    val system = Images.systemName(now.marker?.imageId?.let(Images::byId))
                                    StatBox(Units.bytes(nowRaw), stringResource(R.string.space_system_label, system), Modifier.weight(1f))
                                }
                                val oldVal = when {
                                    stages == null -> stringResource(R.string.space_value_unknown)
                                    stages.isEmpty() -> stringResource(R.string.space_value_none)
                                    else -> Units.bytes(oldBytes ?: 0)
                                }
                                val oldLabel = when {
                                    stages.isNullOrEmpty() -> stringResource(R.string.space_old_label)
                                    else -> pluralStringResource(R.plurals.advanced_space_copies, stages.size, stages.size)
                                }
                                StatBox(oldVal, oldLabel, Modifier.weight(1f))
                            }
                            if (!stages.isNullOrEmpty() && oldBytes != null) {
                                Text(
                                    text = stringResource(R.string.space_frees, Units.bytes(oldBytes)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                item { ExpressiveSectionHeader(stringResource(R.string.space_card_old)) }
                item {
                    val deletable = stages.orEmpty().filter { !it.busy }
                    val batch = st.batch
                    ExpressiveGroupedCard {
                        Text(
                            text = stringResource(R.string.space_old_line),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                        if (batch != null) {
                            ExpressiveListDivider()
                            ExpressiveListItem(title = stringResource(R.string.space_deleting_n, batch.first, batch.second), icon = Icons.Rounded.Delete)
                        } else if (deletable.size > 1 && !st.busy) {
                            ExpressiveListDivider()
                            ExpressiveListItem(
                                title = stringResource(R.string.space_delete_all),
                                subtitle = stringResource(R.string.space_delete_all_summary, Units.bytes(deletable.sumOf { it.kib } * 1024)),
                                icon = Icons.Rounded.Delete,
                                iconTint = MaterialTheme.colorScheme.error,
                                showChevron = true,
                                onClick = { confirmDeleteAll(deletable) },
                            )
                        }
                        when {
                            st.phase == OldStagesJob.Phase.LISTING -> {
                                ExpressiveListDivider()
                                ExpressiveListItem(title = stringResource(R.string.space_listing), icon = Icons.Rounded.Refresh)
                            }
                            stages == null -> {
                                ExpressiveListDivider()
                                ExpressiveListItem(title = stringResource(R.string.space_not_read), subtitle = stringResource(R.string.space_not_read_line), icon = levelIcon(Level.WARN), iconTint = levelColor(Level.WARN))
                            }
                            stages.isEmpty() -> {
                                ExpressiveListDivider()
                                ExpressiveListItem(title = stringResource(R.string.space_none), subtitle = stringResource(R.string.space_none_summary), icon = levelIcon(Level.OK), iconTint = levelColor(Level.OK))
                            }
                            else -> stages.forEach { s ->
                                val deleting = st.deleting == s.name
                                val restoring = st.restoring == s.name
                                val usable = !s.busy || restorable(s)
                                ExpressiveListDivider()
                                ExpressiveListItem(
                                    title = when {
                                        deleting -> stringResource(R.string.space_deleting)
                                        restoring -> stringResource(R.string.space_restoring)
                                        else -> copyTitle(s)
                                    },
                                    subtitle = describe(s),
                                    icon = if (s.busy) Icons.Rounded.Lock else Icons.Rounded.Delete,
                                    iconTint = when {
                                        deleting || restoring -> MaterialTheme.colorScheme.primary
                                        s.busy -> levelColor(Level.WARN)
                                        else -> MaterialTheme.colorScheme.error
                                    },
                                    enabled = usable && (!st.busy || deleting || restoring),
                                    showChevron = usable && !st.busy,
                                    onClick = { if (!st.busy && usable) choose(s) },
                                )
                            }
                        }
                        if (!st.busy && st.stages != null && st.message != null) {
                            val freed = st.freedKib
                            val statusPair: Pair<Level, Pair<String, String?>>? = when {
                                st.phase == OldStagesJob.Phase.DONE && st.restored -> Level.OK to (stringResource(R.string.space_restored) to null)
                                st.phase == OldStagesJob.Phase.FAILED && st.restoreFailed -> Level.WARN to (stringResource(R.string.space_not_restored) to stringResource(R.string.space_not_restored_line))
                                st.phase == OldStagesJob.Phase.DONE && freed != null && st.deletedNames != null ->
                                    Level.OK to (pluralStringResource(R.plurals.space_deleted_n, st.deletedNames.size, st.deletedNames.size, Units.bytes(freed * 1024)) to null)
                                st.phase == OldStagesJob.Phase.DONE && freed != null -> Level.OK to (stringResource(R.string.space_deleted, Units.bytes(freed * 1024)) to null)
                                st.phase == OldStagesJob.Phase.FAILED && st.deleteFailed -> Level.WARN to (stringResource(R.string.space_not_deleted) to stringResource(R.string.space_not_deleted_line))
                                st.phase == OldStagesJob.Phase.FAILED -> Level.WARN to (stringResource(R.string.space_not_read) to stringResource(R.string.space_not_read_line))
                                else -> null
                            }
                            statusPair?.let { (lv, pair) ->
                                ExpressiveListDivider()
                                ExpressiveListItem(title = pair.first, subtitle = pair.second, icon = levelIcon(lv), iconTint = levelColor(lv))
                            }
                        }
                        if (!st.busy) {
                            ExpressiveListDivider()
                            ExpressiveListItem(
                                title = stringResource(R.string.space_check_again),
                                icon = Icons.Rounded.Refresh,
                                showChevron = true,
                                onClick = { OldStagesJob.refresh(this@OldStagesActivity) },
                            )
                        }
                    }
                }

                item {
                    ExpressiveGroupedCard {
                        ExpressiveListItem(
                            title = stringResource(R.string.details_title),
                            subtitle = stringResource(R.string.space_details_summary),
                            icon = if (detailsOpen) DualOsIcons.ArrowDropUp else DualOsIcons.ArrowDropDown,
                            onClick = { detailsOpen = !detailsOpen },
                        )
                        if (detailsOpen) {
                            ExpressiveListDivider()
                            Text(
                                text = stringResource(R.string.space_details_rules),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                            stages?.forEach { s ->
                                ExpressiveListDivider()
                                ExpressiveListItem(title = s.name, subtitle = stringResource(R.string.space_details_folder, OldStagesJob.gib(s.kib), s.imageId ?: "-"))
                            }
                            st.message?.let {
                                ExpressiveListDivider()
                                ExpressiveListItem(title = stringResource(R.string.setup_details_result), subtitle = it)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun StatBox(value: String, label: String, modifier: Modifier = Modifier) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    private fun copyTitle(s: OldStage): String {
        val name = s.imageId?.let(Images::byId)?.name
        return when {
            s.isTrash -> getString(R.string.space_old_trash)
            name != null -> getString(R.string.space_old_of, name)
            else -> getString(R.string.space_old_unknown)
        }
    }

    private fun describe(s: OldStage): String {
        val parts = mutableListOf(Units.bytes(s.kib * 1024))
        since(s)?.let { parts += it }
        if (s.hasData) parts += getString(R.string.space_old_has_data)
        if (s.busy) parts += getString(R.string.space_old_in_use)
        return parts.joinToString(" · ")
    }

    private fun since(s: OldStage): String? = s.setAsideEpoch?.let { e ->
        getString(if (s.isTrash) R.string.space_old_stopped_since else R.string.space_old_kept_since, formatMediumDate(this, e * 1000))
    }

    private fun restorable(s: OldStage): Boolean = !s.isTrash && s.name.startsWith("oneos.old-") && s.imageId?.let(Images::byId) != null

    private fun choose(s: OldStage) {
        if (!restorable(s)) {
            if (!s.busy) confirmDelete(s)
            return
        }
        val items = mutableListOf(getString(R.string.space_choose_use) to { confirmRestore(s) })
        if (!s.busy) items += getString(R.string.space_choose_delete) to { confirmDelete(s) }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.space_choose_title, copyTitle(s)))
            .setItems(items.map { it.first }.toTypedArray()) { _, i -> items[i].second() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmRestore(s: OldStage) {
        val name = s.imageId?.let(Images::byId)?.name ?: return
        val sizeAndDate = listOfNotNull(Units.bytes(s.kib * 1024), since(s)).joinToString(", ")
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.space_restore_title)
            .setMessage(getString(R.string.space_restore_message, name, sizeAndDate))
            .setPositiveButton(R.string.space_restore_run) { _, _ -> OldStagesJob.restore(this, s) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteAll(copies: List<OldStage>) {
        val what = resources.getQuantityString(R.plurals.space_delete_all_count, copies.size, copies.size)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.space_delete_all_title)
            .setMessage(getString(R.string.space_delete_all_message, what, Units.bytes(copies.sumOf { it.kib } * 1024)))
            .setPositiveButton(R.string.space_delete_run) { _, _ -> SystemManage.deleteAllCopies(this) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(s: OldStage) {
        val name = s.imageId?.let(Images::byId)?.name
        val what = if (name != null && !s.isTrash) getString(R.string.space_delete_what_of, name) else getString(R.string.space_delete_what)
        val sizeAndDate = listOfNotNull(Units.bytes(s.kib * 1024), since(s)).joinToString(", ")
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.space_delete_title)
            .setMessage(getString(R.string.space_delete_message, what, sizeAndDate))
            .setPositiveButton(R.string.space_delete_run) { _, _ -> SystemManage.deleteCopy(this, s, "$what ($sizeAndDate)") }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
