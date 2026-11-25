package com.fahadali.oneos.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import com.fahadali.oneos.core.model.FixAction
import com.fahadali.oneos.core.model.ImageRef
import com.fahadali.oneos.core.model.Images
import com.fahadali.oneos.core.model.Profiles
import com.fahadali.oneos.core.model.SetupPreviewTimeline
import com.fahadali.oneos.core.model.Units
import com.fahadali.oneos.core.root.SetupPlan
import com.fahadali.oneos.session.OldStagesJob
import com.fahadali.oneos.session.PrepareJob
import com.fahadali.oneos.session.SessionPrefs
import com.fahadali.oneos.session.StageCheck
import com.fahadali.oneos.shared.DualOsTheme
import com.fahadali.oneos.shared.ExpressiveGroupedCard
import com.fahadali.oneos.shared.ExpressiveListDivider
import com.fahadali.oneos.shared.ExpressiveListItem
import com.fahadali.oneos.shared.LocalDualOsStatusColors

private val MEASURED_STEPS = setOf(PrepareJob.Step.HASH, PrepareJob.Step.DOWNLOAD, PrepareJob.Step.IMAGE, PrepareJob.Step.FILES)

/** Live setup progress screen in Material 3 Expressive Compose with wavy indicators. */
class SetupProgressActivity : AppCompatActivity() {
    var previewImage: ImageRef? = null
        private set

    private val previewTimeline = SetupPreviewTimeline(PrepareJob.Step.entries.size, stepDurationMs = 3000)
    private val previewHandler = Handler(Looper.getMainLooper())
    private var previewElapsedMs = 0L
    private var previewResumedAt = 0L
    private var previewCancelledAt: Long? = null

    private var unsubscribe: (() -> Unit)? = null
    private var stopping by mutableStateOf(false)
    private var routed = false
    private var listedFor: String? = null
    private var tick by mutableIntStateOf(0)

    private val previewTick = object : Runnable {
        override fun run() {
            if (isDestroyed || previewImage == null) return
            tick++
            if (displayedState().busy) previewHandler.postDelayed(this, 160)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        previewImage = if (DebugPreview.enabled(this)) intent.getStringExtra(EXTRA_PREVIEW_IMAGE)?.let(Images::byId) else null
        super.onCreate(savedInstanceState)
        previewElapsedMs = savedInstanceState?.getLong("preview_elapsed") ?: 0L
        previewCancelledAt = savedInstanceState?.getLong("preview_cancelled", -1)?.takeIf { it >= 0 }

        if (savedInstanceState == null && Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 41)
        }
        setContent {
            DualOsTheme {
                Screen()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (previewImage != null) {
            previewResumedAt = SystemClock.uptimeMillis()
            previewHandler.post(previewTick)
            return
        }
        val p = PrepareJob.observe { s ->
            if (s.busy) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            tick++
        }
        val o = OldStagesJob.observe { tick++ }
        unsubscribe = { p(); o() }
    }

    override fun onStop() {
        if (previewImage != null) {
            previewElapsedMs = elapsedPreview()
            previewResumedAt = 0L
            previewHandler.removeCallbacks(previewTick)
        }
        unsubscribe?.invoke()
        unsubscribe = null
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (previewImage != null) {
            outState.putLong("preview_elapsed", elapsedPreview())
            outState.putLong("preview_cancelled", previewCancelledAt ?: -1)
        }
    }

    private fun elapsedPreview(): Long =
        previewElapsedMs + if (previewResumedAt == 0L) 0 else SystemClock.uptimeMillis() - previewResumedAt

    private fun displayedState(): PrepareJob.State {
        val img = previewImage ?: return PrepareJob.state
        val snap = previewTimeline.at(elapsedPreview(), previewCancelledAt)
        return PrepareJob.State(
            phase = when (snap.phase) {
                SetupPreviewTimeline.Phase.RUNNING -> PrepareJob.Phase.RUNNING
                SetupPreviewTimeline.Phase.DONE -> PrepareJob.Phase.DONE
                SetupPreviewTimeline.Phase.CANCELLED -> PrepareJob.Phase.STOPPED
            },
            step = snap.currentStep?.let { PrepareJob.Step.entries[it] },
            done = PrepareJob.Step.entries.take(snap.completedSteps).toSet(),
            image = img,
            bytesDone = snap.percent.toLong(),
            bytesTotal = 100,
            detail = getString(R.string.setup_preview_simulated),
            etaSeconds = ((previewTimeline.durationMs - elapsedPreview()).coerceAtLeast(0) / 1000).toInt(),
        )
    }

    private fun checklist(): List<Pair<String, Set<PrepareJob.Step>>> = listOf(
        getString(R.string.setup_check_phone) to setOf(PrepareJob.Step.CHECK),
        getString(R.string.setup_check_files) to setOf(PrepareJob.Step.HASH),
        getString(R.string.setup_download_files) to setOf(PrepareJob.Step.DOWNLOAD),
        getString(R.string.setup_image) to setOf(PrepareJob.Step.BEGIN, PrepareJob.Step.IMAGE),
        getString(R.string.setup_files_patches) to setOf(PrepareJob.Step.FILES),
        getString(R.string.setup_table) to setOf(PrepareJob.Step.TABLE),
        getString(R.string.setup_session) to setOf(PrepareJob.Step.ENV),
        getString(R.string.setup_cleanup) to setOf(PrepareJob.Step.FINISH),
        getString(R.string.setup_step_finish) to setOf(PrepareJob.Step.VERIFY),
    )

    private fun rowCompleted(s: PrepareJob.State, stages: Set<PrepareJob.Step>, reuseImage: Boolean): Boolean {
        if (s.phase == PrepareJob.Phase.DONE) return true
        val now = s.step?.ordinal ?: -1
        return stages.all {
            it in s.done || when (it) {
                PrepareJob.Step.HASH -> reuseImage || now > PrepareJob.Step.HASH.ordinal
                PrepareJob.Step.DOWNLOAD -> now > PrepareJob.Step.DOWNLOAD.ordinal
                else -> false
            }
        }
    }

    private fun reuseImageFor(s: PrepareJob.State): Boolean =
        if (s.source != null) s.source == SessionPrefs.Source.ON_PHONE
        else intent.getBooleanExtra(EXTRA_REUSE_IMAGE, false)

    private fun stepWisePercent(s: PrepareJob.State, reuseImage: Boolean): Int {
        val rows = checklist()
        if (rows.isEmpty()) return 0
        var done = 0f
        for ((_, stages) in rows) {
            done += when {
                rowCompleted(s, stages, reuseImage) -> 1f
                s.step in stages && s.step in MEASURED_STEPS -> s.stepFraction ?: 0f
                else -> 0f
            }
        }
        return (done * 100 / rows.size).toInt().coerceIn(0, 100)
    }

    private fun asideCopy(s: PrepareJob.State): String? =
        s.infos.lastOrNull { it.startsWith("aside ") }?.substringAfter("aside ")?.trim()?.substringAfterLast('/')
            ?.takeIf { it.startsWith("oneos.old-") }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun Screen() {
        @Suppress("UNUSED_VARIABLE") val t = tick
        val s = displayedState()
        val phase = s.phase
        if (phase == PrepareJob.Phase.DONE && !routed && s.image != null) {
            routed = true
            val simulated = previewImage != null
            EnvironmentNavigation.remember(this, s.image, simulated)
            if (!simulated && intent.getBooleanExtra(EXTRA_RETURN_TO_CHECKS, false)) {
                finish()
                return
            }
            startActivity(EnvironmentNavigation.intent(this, s.image, simulated))
            finish()
            return
        }
        if (phase != PrepareJob.Phase.RUNNING) stopping = false

        val profile = StageCheck.state.result?.facts?.let { Profiles.resolve(it).profile }
        val image = s.image ?: intent.getStringExtra(EXTRA_IMAGE)?.let(Images::byId) ?: FixActions.pickedImage(this, profile)
        val updating = intent.getBooleanExtra(EXTRA_UPDATE, false) ||
            s.plan?.kind in setOf(SetupPlan.Kind.UPDATE, SetupPlan.Kind.REPAIR)
        val reuseImage = reuseImageFor(s)
        val system = image?.let { Images.systemName(it, profile) }
        val pct = if (phase == PrepareJob.Phase.DONE) 100 else stepWisePercent(s, reuseImage)

        val title = when {
            previewImage != null && phase == PrepareJob.Phase.STOPPED -> stringResource(R.string.setup_preview_cancelled_title)
            phase == PrepareJob.Phase.IDLE -> stringResource(R.string.setup_idle_title)
            phase == PrepareJob.Phase.RUNNING -> system?.let { stringResource(if (updating) R.string.setup_update_title else R.string.setup_mock_title, it) } ?: stringResource(R.string.setup_label)
            phase == PrepareJob.Phase.DONE -> system?.let { stringResource(R.string.setup_done_title, it) } ?: stringResource(R.string.setup_ring_done)
            else -> s.plain?.title ?: stringResource(R.string.setup_failed_title)
        }
        val stateLine = when (phase) {
            PrepareJob.Phase.RUNNING -> listOf(
                stringResource(if (updating) R.string.setup_updating else R.string.setup_installing),
                stringResource(R.string.setup_percent, pct),
                s.etaSeconds?.let { if (it < 60) stringResource(R.string.setup_eta_soon) else stringResource(R.string.setup_eta_minutes, (it + 59) / 60) }
                    ?: stringResource(R.string.setup_eta_estimating),
            ).joinToString(" · ")
            PrepareJob.Phase.DONE -> stringResource(R.string.setup_ring_done)
            PrepareJob.Phase.STOPPED -> stringResource(R.string.setup_ring_stopped)
            PrepareJob.Phase.FAILED -> stringResource(R.string.setup_ring_failed)
            PrepareJob.Phase.IDLE -> stringResource(R.string.setup_idle_subtitle)
        }

        val statusColors = LocalDualOsStatusColors.current

        DualOsScaffold(
            title = title,
            subtitle = if (previewImage != null) stringResource(R.string.setup_preview_badge) else null,
            onBack = { finish() },
            largeTopBar = false,
            bottomBar = {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (previewImage != null) {
                            if (phase == PrepareJob.Phase.DONE) {
                                Button(
                                    onClick = {
                                        AlertDialog.Builder(this@SetupProgressActivity)
                                            .setTitle(getString(R.string.setup_preview_boot_title, s.image?.name.orEmpty()))
                                            .setMessage(R.string.setup_preview_boot_message)
                                            .setPositiveButton(R.string.setup_preview_finish) { _, _ ->
                                                startActivity(Intent(this@SetupProgressActivity, GapSplashActivity::class.java).putExtra(EXTRA_PREVIEW_IMAGE, s.image?.id))
                                            }
                                            .setNegativeButton(android.R.string.cancel, null)
                                            .show()
                                    },
                                    modifier = Modifier.fillMaxWidth().height(50.dp),
                                    shape = RoundedCornerShape(16.dp),
                                ) { Text(stringResource(R.string.setup_preview_boot)) }
                            }
                            OutlinedButton(
                                onClick = {
                                    if (s.busy) {
                                        previewCancelledAt = elapsedPreview()
                                        previewHandler.removeCallbacks(previewTick)
                                        tick++
                                    } else finish()
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(16.dp),
                            ) { Text(stringResource(if (s.busy) android.R.string.cancel else R.string.setup_close)) }
                        } else {
                            when (phase) {
                                PrepareJob.Phase.RUNNING -> {
                                    OutlinedButton(
                                        onClick = {
                                            val name = s.image?.name ?: getString(R.string.setup_label)
                                            AlertDialog.Builder(this@SetupProgressActivity)
                                                .setTitle(getString(R.string.setup_stop_title, name))
                                                .setMessage(R.string.prepare_stop_summary)
                                                .setPositiveButton(R.string.setup_stop) { _, _ ->
                                                    stopping = true
                                                    PrepareJob.cancel()
                                                }
                                                .setNegativeButton(R.string.setup_keep_going, null)
                                                .show()
                                        },
                                        enabled = !stopping,
                                        modifier = Modifier.fillMaxWidth().height(50.dp),
                                        shape = RoundedCornerShape(16.dp),
                                    ) { Text(stringResource(if (stopping) R.string.setup_stopping else R.string.setup_stop_setup)) }
                                }
                                PrepareJob.Phase.DONE -> {
                                    Button(
                                        onClick = { SystemsNav.home(this@SetupProgressActivity) },
                                        modifier = Modifier.fillMaxWidth().height(50.dp),
                                        shape = RoundedCornerShape(16.dp),
                                    ) { Text(stringResource(R.string.setup_done_button)) }
                                    val aside = asideCopy(s)
                                    val old = OldStagesJob.state
                                    if (aside != null && listedFor != aside && !old.busy && old.stages?.none { it.name == aside } != false) {
                                        listedFor = aside
                                        OldStagesJob.refresh(this@SetupProgressActivity)
                                    }
                                    if (aside != null) {
                                        val label = when {
                                            old.deletedNames?.contains(aside) == true -> stringResource(R.string.setup_old_deleted, Units.bytes((old.freedKib ?: 0) * 1024))
                                            old.phase == OldStagesJob.Phase.DELETING -> stringResource(R.string.setup_old_deleting)
                                            old.deletedNames != null && old.deleteFailed -> stringResource(R.string.setup_old_not_deleted)
                                            else -> stringResource(R.string.setup_delete_old)
                                        }
                                        val enabled = old.deletedNames?.contains(aside) != true && !old.busy && !(old.deletedNames != null && old.deleteFailed)
                                        OutlinedButton(
                                            onClick = {
                                                val size = old.stages?.firstOrNull { it.name == aside }?.let { Units.bytes(it.kib * 1024) }
                                                    ?: getString(R.string.setup_old_size_unknown)
                                                AlertDialog.Builder(this@SetupProgressActivity)
                                                    .setTitle(R.string.setup_delete_old_title)
                                                    .setMessage(getString(R.string.setup_delete_old_message, size))
                                                    .setPositiveButton(R.string.space_delete_run) { _, _ -> OldStagesJob.deleteAll(this@SetupProgressActivity, setOf(aside)) }
                                                    .setNegativeButton(android.R.string.cancel, null)
                                                    .show()
                                            },
                                            enabled = enabled,
                                            modifier = Modifier.fillMaxWidth().height(48.dp),
                                            shape = RoundedCornerShape(16.dp),
                                        ) { Text(label) }
                                    }
                                }
                                PrepareJob.Phase.STOPPED, PrepareJob.Phase.FAILED -> {
                                    val fix = s.plain?.takeIf { it.action != FixAction.NONE && it.action != FixAction.ACKNOWLEDGE && it.actionLabel != null }
                                    if (s.plan?.keepsData == true && fix?.action != FixAction.RESTORE) {
                                        Button(
                                            onClick = { FixActions.run(this@SetupProgressActivity, FixAction.UPDATE, s.image) },
                                            modifier = Modifier.fillMaxWidth().height(50.dp),
                                            shape = RoundedCornerShape(16.dp),
                                        ) { Text(stringResource(R.string.retry_update)) }
                                    } else if (fix != null) {
                                        Button(
                                            onClick = { FixActions.run(this@SetupProgressActivity, fix.action, s.image) },
                                            modifier = Modifier.fillMaxWidth().height(50.dp),
                                            shape = RoundedCornerShape(16.dp),
                                        ) { Text(fix.actionLabel!!) }
                                    }
                                    OutlinedButton(
                                        onClick = { SystemsNav.home(this@SetupProgressActivity) },
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        shape = RoundedCornerShape(16.dp),
                                    ) { Text(stringResource(R.string.setup_close)) }
                                }
                                PrepareJob.Phase.IDLE -> {
                                    OutlinedButton(
                                        onClick = { SystemsNav.home(this@SetupProgressActivity) },
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        shape = RoundedCornerShape(16.dp),
                                    ) { Text(stringResource(R.string.setup_close)) }
                                }
                            }
                        }
                    }
                }
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    RomArtworkBanner(
                        image = image,
                        title = title,
                        subtitle = stateLine,
                        height = 165.dp,
                    )
                }

                item {
                    ExpressiveGroupedCard {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            LinearWavyProgressIndicator(
                                progress = { (pct / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            val sub = when (phase) {
                                PrepareJob.Phase.STOPPED, PrepareJob.Phase.FAILED -> s.plain?.line.orEmpty()
                                else -> stateLine
                            }
                            if (sub.isNotBlank()) {
                                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                item {
                    val rows = checklist()
                    ExpressiveGroupedCard {
                        rows.forEachIndexed { idx, (defaultLabel, stages) ->
                            if (idx > 0) ExpressiveListDivider()
                            val label = when {
                                reuseImage && PrepareJob.Step.IMAGE in stages -> stringResource(R.string.setup_verify_existing_image)
                                updating && PrepareJob.Step.CHECK in stages -> stringResource(R.string.setup_check_update)
                                else -> defaultLabel
                            }
                            val completed = rowCompleted(s, stages, reuseImage)
                            val current = s.step in stages
                            val failed = current && phase in setOf(PrepareJob.Phase.FAILED, PrepareJob.Phase.STOPPED)
                            val measured = s.step in MEASURED_STEPS
                            val sub = when {
                                current && s.busy && measured && previewImage == null -> s.detail
                                failed -> s.plain?.line ?: s.message
                                else -> null
                            }
                            if (current && s.busy && !completed && !failed) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(label, style = MaterialTheme.typography.titleSmall)
                                        if (!sub.isNullOrBlank()) {
                                            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            } else {
                                ExpressiveListItem(
                                    title = label,
                                    subtitle = sub,
                                    icon = when {
                                        completed -> Icons.Rounded.CheckCircle
                                        failed -> Icons.Rounded.Warning
                                        else -> Icons.Rounded.Info
                                    },
                                    iconTint = when {
                                        completed -> statusColors.ok
                                        failed -> statusColors.warn
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                    },
                                )
                            }
                        }
                    }
                }

                if (phase == PrepareJob.Phase.FAILED && (s.finding != null || s.message != null)) {
                    item {
                        ExpressiveGroupedCard {
                            s.finding?.let { f ->
                                ExpressiveListItem(
                                    title = f.title,
                                    subtitle = f.detail.ifEmpty { null },
                                    icon = levelIcon(f.level),
                                    iconTint = levelColor(f.level),
                                )
                                if (s.message != null) ExpressiveListDivider()
                            }
                            s.message?.let { msg ->
                                ExpressiveListItem(
                                    title = stringResource(R.string.setup_details_result),
                                    subtitle = msg,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_PREVIEW_IMAGE = "oneos.debug.preview_image"
        const val EXTRA_UPDATE = "oneos.setup.update"
        const val EXTRA_REUSE_IMAGE = "oneos.setup.reuse_image"
        const val EXTRA_IMAGE = "oneos.setup.image"
        const val EXTRA_RETURN_TO_CHECKS = "oneos.setup.return_to_checks"
    }
}
