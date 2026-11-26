package com.fahadali.oneos.ui

import android.content.Intent
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.WindowManager
import android.widget.Toast
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import com.fahadali.oneos.core.model.GateReport
import com.fahadali.oneos.core.model.ImageIdentifier
import com.fahadali.oneos.core.model.ImageRef
import com.fahadali.oneos.core.model.Images
import com.fahadali.oneos.core.model.Level
import com.fahadali.oneos.core.model.Plain
import com.fahadali.oneos.core.model.SpaceRule
import com.fahadali.oneos.core.model.SwitchGates
import com.fahadali.oneos.core.model.Units
import com.fahadali.oneos.core.root.GuestData
import com.fahadali.oneos.core.root.RootScript
import com.fahadali.oneos.core.root.RootScript.BeginMode
import com.fahadali.oneos.core.root.StageKind
import com.fahadali.oneos.core.root.StageStatus
import com.fahadali.oneos.session.PrepareJob
import com.fahadali.oneos.session.SessionPrefs
import com.fahadali.oneos.session.StageCheck
import com.fahadali.oneos.shared.DualOsIcons
import com.fahadali.oneos.shared.DualOsTheme
import com.fahadali.oneos.shared.ExpressiveGroupedCard
import com.fahadali.oneos.shared.ExpressiveListDivider
import com.fahadali.oneos.shared.ExpressiveListItem
import com.fahadali.oneos.shared.ExpressiveSectionHeader
import java.util.concurrent.Executors

/** Repair or start fresh: read-only checks, stage mode selection, and confirmed setup start. */
class PrepareActivity : AppCompatActivity() {
    private val team: Boolean get() = intent.getBooleanExtra(EXTRA_TEAM, false)
    private val subs = mutableListOf<() -> Unit>()
    private val io = Executors.newSingleThreadExecutor()
    private var mode: BeginMode? by mutableStateOf(null)
    private var detailsOpen by mutableStateOf(false)
    private var deletingGz by mutableStateOf(false)
    private var tick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        mode = savedInstanceState?.getString(KEY_MODE)?.let { m -> BeginMode.entries.firstOrNull { it.name == m } }
        detailsOpen = team
        setContent {
            DualOsTheme {
                Screen()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mode?.let { outState.putString(KEY_MODE, it.name) }
    }

    override fun onStart() {
        super.onStart()
        subs += StageCheck.observe { tick++ }
        subs += PrepareJob.observe { s ->
            if (s.busy) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            tick++
        }
    }

    override fun onStop() {
        subs.forEach { it() }
        subs.clear()
        super.onStop()
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }

    @Composable
    private fun Screen() {
        @Suppress("UNUSED_VARIABLE") val t = tick
        val job = PrepareJob.state
        val check = StageCheck.state
        val r = check.result
        val source = SessionPrefs.source(this)
        val st = r?.status
        val stale = r != null && r.ageMs > StageCheck.FRESH_MS

        var blocked = true
        var report: GateReport? = null
        var modes: List<BeginMode> = emptyList()
        var onPhoneNeedsKeep = false

        if (r != null && st != null) {
            modes = st.beginModes(null)
            if (mode !in modes) mode = modes.firstOrNull()
            if (r.installd != null) {
                fun gates() = StageCheck.gates(
                    this, r, GatePhase.PREPARE, SessionPrefs.picked(this)?.check, source == SessionPrefs.Source.ON_PHONE,
                    if (mode == BeginMode.KEEP) st.rawSize ?: 0 else 0,
                )
                var rep = gates()
                modes = st.beginModes(rep.image?.id)
                if (mode !in modes) {
                    mode = modes.firstOrNull()
                    rep = gates()
                }
                report = rep
                blocked = rep.gates.any {
                    it.level == Level.BLOCK && !(it.id == SwitchGates.ID_IMAGE && it.finding.reason == ImageIdentifier.R_NOT_CHECKED)
                }
                onPhoneNeedsKeep = source == SessionPrefs.Source.ON_PHONE && mode != BeginMode.KEEP
                if (onPhoneNeedsKeep || modes.isEmpty()) blocked = true
            }
        }

        DualOsScaffold(
            title = stringResource(if (team) R.string.prepare_title_team else R.string.prepare_title),
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
                    ExpressiveGroupedCard {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.prepare_intro), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.prepare_intro_safe), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                item { ExpressiveSectionHeader(stringResource(R.string.prepare_cat_checks)) }
                item {
                    ExpressiveGroupedCard {
                        if (check.busy) {
                            ExpressiveListItem(
                                title = (if (team) check.step else null) ?: stringResource(R.string.prepare_checking),
                                icon = Icons.Rounded.Refresh,
                            )
                        } else {
                            ExpressiveListItem(
                                title = stringResource(if (r == null) R.string.prepare_check_title else R.string.prepare_check_again),
                                subtitle = stringResource(if (check.message != null) R.string.prepare_check_failed else R.string.prepare_check_summary),
                                icon = Icons.Rounded.Refresh,
                                enabled = !job.busy,
                                showChevron = !job.busy,
                                onClick = { if (!job.busy) confirmCheck() },
                            )
                        }
                        if (r != null && st != null) {
                            if (r.installd == null) {
                                ExpressiveListDivider()
                                ExpressiveListItem(title = stringResource(R.string.prepare_need_full), icon = levelIcon(Level.BLOCK), iconTint = levelColor(Level.BLOCK))
                            } else if (report != null) {
                                val problems = report.report.sorted.filter { it.level == Level.BLOCK || it.level == Level.WARN }
                                if (problems.isEmpty() && !blocked && !stale) {
                                    ExpressiveListDivider()
                                    ExpressiveListItem(title = stringResource(R.string.prepare_checks_ok), icon = levelIcon(Level.OK), iconTint = levelColor(Level.OK))
                                }
                                problems.forEach { f ->
                                    val m = Plain.of(f, report, r.facts)
                                    val lv = if (f.id == SwitchGates.ID_IMAGE && f.reason == ImageIdentifier.R_NOT_CHECKED) Level.INFO else f.level
                                    ExpressiveListDivider()
                                    ExpressiveListItem(title = m.title, subtitle = m.line, icon = levelIcon(lv), iconTint = levelColor(lv))
                                }
                                if (onPhoneNeedsKeep) {
                                    ExpressiveListDivider()
                                    ExpressiveListItem(title = stringResource(R.string.prepare_on_phone_needs_keep), icon = levelIcon(Level.BLOCK), iconTint = levelColor(Level.BLOCK))
                                }
                            }
                        }
                    }
                }

                if (r != null && st != null) {
                    item { ExpressiveSectionHeader(stringResource(R.string.prepare_cat_stage)) }
                    item {
                        ExpressiveGroupedCard {
                            ExpressiveListItem(title = stageTitle(st), icon = DualOsIcons.FolderOpen)
                            if (modes.isEmpty()) {
                                ExpressiveListDivider()
                                ExpressiveListItem(title = stringResource(R.string.prepare_stage_not_folder), icon = levelIcon(Level.BLOCK), iconTint = levelColor(Level.BLOCK))
                            } else if (modes.size > 1) {
                                modes.forEach { m ->
                                    ExpressiveListDivider()
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(if (!job.busy) Modifier.clickable { mode = m } else Modifier)
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        RadioButton(selected = mode == m, enabled = !job.busy, onClick = { mode = m })
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(modeTitle(m), style = MaterialTheme.typography.titleSmall)
                                            Text(modeSummary(m, st, report?.image), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            } else {
                                modes.firstOrNull()?.let { m ->
                                    ExpressiveListDivider()
                                    ExpressiveListItem(title = modeTitle(m), subtitle = modeSummary(m, st, report?.image))
                                }
                            }
                        }
                    }
                }

                item { ExpressiveSectionHeader(stringResource(R.string.prepare_cat_run)) }
                item {
                    val image = report?.image
                    val set = report?.set
                    val canRun = !job.busy && !check.busy && r != null && !stale && !blocked && mode != null
                    val replaced = st?.takeIf { it.ours }?.marker?.imageId?.let(Images::byId)
                        ?.takeIf { image != null && it.id != image.id && source != SessionPrefs.Source.ON_PHONE }

                    ExpressiveGroupedCard {
                        var hasTop = false
                        @Composable
                        fun sep() { if (hasTop) ExpressiveListDivider(); hasTop = true }
                        if (replaced != null && image != null && !job.busy) {
                            sep(); ExpressiveListItem(
                                title = stringResource(R.string.prepare_other_system_title, image.name, replaced.name),
                                subtitle = stringResource(R.string.prepare_other_system_summary, image.name, replaced.name),
                                icon = levelIcon(Level.WARN), iconTint = levelColor(Level.WARN),
                            )
                        }
                        if (job.busy && !team) {
                            sep(); ExpressiveListItem(
                                title = stringResource(R.string.prepare_running_title, job.percent ?: 0),
                                subtitle = stringResource(R.string.prepare_running),
                                icon = Icons.Rounded.Refresh,
                                showChevron = true,
                                onClick = { startActivity(Intent(this@PrepareActivity, SetupProgressActivity::class.java)) },
                            )
                        } else {
                            val needs = if (image != null) Units.bytes(SpaceRule.toStage(image, set)) else stringResource(R.string.prepare_needs_unknown)
                            sep(); ExpressiveListItem(
                                title = if (image != null) stringResource(R.string.prepare_run_title_system, image.name) else stringResource(R.string.prepare_run_title),
                                subtitle = when {
                                    job.busy -> stringResource(R.string.prepare_running)
                                    r == null -> stringResource(R.string.prepare_run_check_first)
                                    stale -> stringResource(R.string.prepare_run_stale)
                                    blocked -> stringResource(R.string.prepare_run_blocked)
                                    else -> stringResource(R.string.prepare_run_summary, needs)
                                },
                                icon = DualOsIcons.Download,
                                enabled = canRun || job.busy,
                                showChevron = canRun,
                                onClick = { if (canRun) confirmPrepare(mode!!, source, image, replaced) },
                            )
                        }
                        if (!job.busy && job.phase != PrepareJob.Phase.IDLE) {
                            val done = job.phase == PrepareJob.Phase.DONE
                            val system = Images.systemName(job.image ?: image, report?.profile)
                            val resTitle = if (done) stringResource(R.string.setup_done_title, system) else job.plain?.title ?: stringResource(R.string.setup_failed_title)
                            val resLine = if (done) stringResource(R.string.setup_done_subtitle) else job.plain?.line
                            val lv = if (done) Level.OK else (job.plain?.level ?: Level.WARN)
                            sep(); ExpressiveListItem(
                                title = resTitle, subtitle = resLine, icon = levelIcon(lv), iconTint = levelColor(lv), showChevron = true,
                                onClick = { startActivity(Intent(this@PrepareActivity, SetupProgressActivity::class.java)) },
                            )
                            if (done && team) {
                                sep(); ExpressiveListItem(
                                    title = stringResource(R.string.switch_title),
                                    subtitle = stringResource(R.string.prepare_next_summary),
                                    icon = DualOsIcons.Science,
                                    showChevron = true,
                                    onClick = { startActivity(Intent(this@PrepareActivity, SwitchActivity::class.java)) },
                                )
                            }
                            val picked = SessionPrefs.picked(this@PrepareActivity)
                            if (done && source == SessionPrefs.Source.GZ && picked != null) {
                                sep(); ExpressiveListItem(
                                    title = stringResource(R.string.prepare_delete_gz_title),
                                    subtitle = if (deletingGz) stringResource(R.string.manage_card_deleting) else stringResource(R.string.prepare_delete_gz_summary, picked.name ?: ""),
                                    icon = Icons.Rounded.Delete,
                                    iconTint = MaterialTheme.colorScheme.error,
                                    enabled = !deletingGz,
                                    onClick = { if (!deletingGz) confirmDeleteGz() },
                                )
                            }
                        }
                    }
                }

                item {
                    ExpressiveGroupedCard {
                        ExpressiveListItem(
                            title = stringResource(R.string.details_title),
                            subtitle = stringResource(R.string.prepare_details_summary),
                            icon = if (detailsOpen) DualOsIcons.ArrowDropUp else DualOsIcons.ArrowDropDown,
                            onClick = { detailsOpen = !detailsOpen },
                        )
                        if (detailsOpen) {
                            ExpressiveListDivider()
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(stringResource(R.string.prepare_what_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(R.string.prepare_safe_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            check.step?.takeIf { check.busy }?.let { ExpressiveListDivider(); ExpressiveListItem(title = it) }
                            check.message?.let { ExpressiveListDivider(); ExpressiveListItem(title = stringResource(R.string.setup_details_result), subtitle = it) }
                            report?.report?.sorted?.forEach { f ->
                                ExpressiveListDivider()
                                ExpressiveListItem(title = f.title, subtitle = f.detail.ifEmpty { null }, icon = levelIcon(f.level), iconTint = levelColor(f.level))
                            }
                            if (st != null) {
                                ExpressiveListDivider()
                                ExpressiveListItem(title = stageTitle(st), subtitle = stageSummary(st))
                            }
                            if (job.phase != PrepareJob.Phase.IDLE) {
                                PrepareJob.Step.entries.forEach { s ->
                                    val lv = when {
                                        s in job.done -> Level.OK
                                        s == job.step && job.busy -> Level.INFO
                                        s == job.step -> Level.BLOCK
                                        else -> null
                                    }
                                    ExpressiveListDivider()
                                    ExpressiveListItem(
                                        title = s.label,
                                        subtitle = if (s == job.step) job.detail else null,
                                        icon = lv?.let(::levelIcon),
                                        iconTint = lv?.let { levelColor(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (job.busy) {
                                ExpressiveListDivider()
                                ExpressiveListItem(title = stringResource(R.string.tests_stop_title), subtitle = stringResource(R.string.prepare_stop_summary), icon = Icons.Rounded.Close, onClick = { PrepareJob.cancel() })
                            }
                            job.message?.let {
                                ExpressiveListDivider()
                                ExpressiveListItem(title = stringResource(if (job.phase == PrepareJob.Phase.DONE) R.string.prepare_done else R.string.prepare_not_done), subtitle = it)
                            }
                            job.infos.takeIf { it.isNotEmpty() }?.let {
                                ExpressiveListDivider()
                                ExpressiveListItem(title = stringResource(R.string.prepare_moved_title), subtitle = it.joinToString("\n"))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun stageTitle(st: StageStatus): String = when {
        !st.exists -> getString(R.string.prepare_stage_none)
        st.kind != StageKind.FOLDER -> getString(R.string.prepare_stage_odd)
        st.ours -> getString(R.string.prepare_stage_ours, Images.systemName(st.marker?.imageId?.let(Images::byId)))
        st.empty -> getString(R.string.prepare_stage_empty)
        st.partial -> getString(R.string.prepare_stage_partial)
        else -> getString(R.string.prepare_stage_foreign)
    }

    private fun stageSummary(st: StageStatus): String = buildList {
        if (st.exists && st.kind == StageKind.FOLDER) {
            add("Owner uid ${st.ownerUid}, mode ${st.mode}" + if (!st.safe) " (not root's alone: DualOS makes it root's)" else "")
            add(
                when (st.guest) {
                    GuestData.NONE -> "No guest data"
                    GuestData.EMPTY -> "An empty guest data folder"
                    GuestData.OTHER -> "Holds guest data DualOS does not recognise"
                    GuestData.LINEAGEOS -> "Holds an older system's guest data"
                    else -> "Holds guest data of " + (st.guest.family?.let { f -> Images.ALL.firstOrNull { it.family == f }?.name } ?: Images.OTHER_SYSTEM)
                },
            )
            st.rawSize?.let { add("system.raw: ${Units.bytes(it)}") }
            st.marker?.let { add("Marker: ${it.imageId}, set ${it.setId}, app ${it.versionCode}") }
        }
        if (st.oldStages.isNotEmpty()) add("Moved aside before: ${st.oldStages.joinToString { it.substringAfterLast('/') }}")
    }.joinToString("\n").ifEmpty { "Nothing yet in ${RootScript.STAGE}" }

    private fun modeTitle(m: BeginMode): String = getString(
        when (m) {
            BeginMode.NEW -> R.string.prepare_mode_new
            BeginMode.KEEP -> R.string.prepare_mode_keep
            BeginMode.ASIDE -> R.string.prepare_mode_aside
        },
    )

    private fun modeSummary(m: BeginMode, st: StageStatus, image: ImageRef?): String {
        val other = st.otherImageData(image?.id)
        val system = Images.systemName(image)
        return when {
            m == BeginMode.NEW -> getString(R.string.prepare_mode_new_summary, system)
            m == BeginMode.KEEP -> getString(R.string.prepare_mode_keep_summary)
            st.guest == GuestData.LINEAGEOS -> getString(R.string.prepare_mode_aside_older, system)
            other != null && image != null -> getString(R.string.prepare_mode_aside_other_image, Images.systemName(Images.byId(other)), image.name)
            image != null && st.guest.otherSystemThan(image) -> getString(R.string.prepare_mode_aside_other_system, system)
            else -> getString(R.string.prepare_mode_aside_summary, system)
        }
    }

    private fun confirmCheck() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.prepare_check_dialog_title)
            .setMessage(R.string.prepare_check_dialog_message)
            .setPositiveButton(R.string.prepare_check_run) { _, _ -> StageCheck.start(this, StageCheck.Mode.FULL) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmPrepare(m: BeginMode, source: SessionPrefs.Source, image: ImageRef?, replaced: ImageRef?) {
        val system = Images.systemName(image)
        val what = buildString {
            if (replaced != null && image != null) append(getString(R.string.prepare_dialog_other_system, image.name, replaced.name)).append("\n\n")
            append(getString(R.string.prepare_dialog_message)).append("\n\n")
            append(modeTitle(m)).append(". ")
            append(if (source == SessionPrefs.Source.ON_PHONE) getString(R.string.prepare_dialog_on_phone, system) else getString(R.string.prepare_dialog_gz, system))
            if (team) append("\n\n").append(getString(R.string.prepare_dialog_team))
        }
        val stepsHere = team
        MaterialAlertDialogBuilder(this)
            .setTitle(if (image != null) getString(R.string.confirm_setup_title, image.name) else getString(R.string.prepare_dialog_title))
            .setMessage(what)
            .setPositiveButton(R.string.confirm_setup_go) { _, _ ->
                PrepareJob.start(this, PrepareJob.Plan(m, source))
                if (!stepsHere && PrepareJob.state.busy) startActivity(Intent(this, SetupProgressActivity::class.java))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteGz() {
        val ctx = applicationContext
        val picked = SessionPrefs.picked(ctx) ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.prepare_delete_gz_title)
            .setMessage(getString(R.string.prepare_delete_gz_message, picked.name ?: ""))
            .setPositiveButton(R.string.delete) { _, _ ->
                deletingGz = true
                io.execute {
                    val ok = runCatching { DocumentsContract.deleteDocument(ctx.contentResolver, picked.uri) }.getOrDefault(false)
                    if (ok) SessionPrefs.clearPicked(ctx)
                    runOnUiThread {
                        deletingGz = false
                        Toast.makeText(ctx, if (ok) R.string.prepare_deleted else R.string.prepare_delete_failed, Toast.LENGTH_SHORT).show()
                        tick++
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        const val EXTRA_TEAM = "com.fahadali.oneos.extra.TEAM"
        private const val KEY_MODE = "mode"
    }
}
