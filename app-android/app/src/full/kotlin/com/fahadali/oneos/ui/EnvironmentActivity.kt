package com.fahadali.oneos.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fahadali.oneos.R
import com.fahadali.oneos.core.device.FactProbe
import com.fahadali.oneos.core.model.DeviceFacts
import com.fahadali.oneos.core.model.FixAction
import com.fahadali.oneos.core.model.GatePhase
import com.fahadali.oneos.core.model.ImageRef
import com.fahadali.oneos.core.model.Images
import com.fahadali.oneos.core.model.Level
import com.fahadali.oneos.core.model.PatchSource
import com.fahadali.oneos.core.model.Plain
import com.fahadali.oneos.core.model.PlainMessage
import com.fahadali.oneos.core.model.Profiles
import com.fahadali.oneos.core.model.RootKind
import com.fahadali.oneos.core.model.SwitchGates
import com.fahadali.oneos.core.model.Units
import com.fahadali.oneos.core.root.RootProbe
import com.fahadali.oneos.session.GuestRuntimeStore
import com.fahadali.oneos.session.LaunchJob
import com.fahadali.oneos.session.LogJob
import com.fahadali.oneos.session.OldStagesJob
import com.fahadali.oneos.session.PatchChannels
import com.fahadali.oneos.session.PrepareJob
import com.fahadali.oneos.session.Readiness
import com.fahadali.oneos.session.RemoveJob
import com.fahadali.oneos.session.StageCheck
import com.fahadali.oneos.session.ToolApps
import com.fahadali.oneos.shared.DualOsIcons
import com.fahadali.oneos.shared.DualOsTheme
import com.fahadali.oneos.shared.ExpressiveGroupedCard
import com.fahadali.oneos.shared.ExpressiveListDivider
import com.fahadali.oneos.shared.ExpressiveListItem
import com.fahadali.oneos.shared.ExpressiveSectionHeader
import com.fahadali.oneos.shared.ExpressiveSwitchItem
import java.util.concurrent.Executors

/** Read-only pre-boot environment checks. Boot always goes through SwitchConfirm and LaunchJob's fresh gates. */
class EnvironmentActivity : AppCompatActivity() {
    private lateinit var image: ImageRef
    private var lastToolMessage: String? = null
    private var preview = false
    private var facts: DeviceFacts? by mutableStateOf(null)
    private val subscriptions = mutableListOf<() -> Unit>()
    private var patchBusy by mutableStateOf(false)
    private var rootBusy by mutableStateOf(false)
    private var rootState: GuestRuntimeStore.State? by mutableStateOf(null)
    private var tick by mutableIntStateOf(0)
    private val io = Executors.newSingleThreadExecutor()
    private val recheck = Runnable { if (!isDestroyed && !preview) { Readiness.tick(this); tick++ } }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val selected = intent.getStringExtra("image")?.let(Images::byId) ?: EnvironmentNavigation.image(this)
        if (selected == null) { catalogue(); return }
        image = selected
        preview = DebugPreview.enabled(this) && intent.getBooleanExtra("preview", false)

        setContent {
            DualOsTheme {
                Screen()
            }
        }
        if (!preview) io.execute {
            val collected = runCatching { FactProbe(applicationContext).collect(RootProbe(applicationContext).signals()) }.getOrNull()
            runOnUiThread { if (!isDestroyed && !isFinishing) { facts = collected; tick++ } }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!::image.isInitialized || preview) return
        subscriptions += StageCheck.observe { if (!it.busy) Readiness.tick(this); tick++ }
        subscriptions += PrepareJob.observe { tick++ }
        subscriptions += LaunchJob.observe { tick++ }
        subscriptions += LogJob.observeBy({ Triple(it.busy, it.recovering, it.stopping) }) { tick++ }
        subscriptions += OldStagesJob.observe { if (!it.busy) Readiness.tick(this); tick++ }
        subscriptions += RemoveJob.observe { removed(); tick++ }
        ToolApps.refresh(this)
        subscriptions += ToolApps.observe { st ->
            if (!st.busy && st.message != null && st.message != lastToolMessage) {
                lastToolMessage = st.message
                Toast.makeText(applicationContext, st.message, Toast.LENGTH_LONG).show()
            }
            tick++
        }
    }

    override fun onResume() {
        super.onResume()
        if (::image.isInitialized && !preview) {
            Readiness.onResume(this)
            tick++
            if (!rootBusy) {
                rootBusy = true
                GuestRuntimeStore.refresh(this, image.id) { next ->
                    if (!isDestroyed) { rootBusy = false; rootState = next; tick++ }
                }
            }
        }
    }

    override fun onStop() {
        window.decorView.removeCallbacks(recheck)
        subscriptions.forEach { it() }
        subscriptions.clear()
        super.onStop()
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }

    private fun removed() {
        val r = SystemManage.takeResult() ?: return
        when {
            r.systemRemoved -> {
                if (r.message.isNotEmpty()) Toast.makeText(applicationContext, r.message, Toast.LENGTH_LONG).show()
                catalogue()
            }
            r.failed -> MaterialAlertDialogBuilder(this).setTitle(R.string.environment_manage_failed).setMessage(r.message)
                .setPositiveButton(android.R.string.ok, null).show()
            else -> if (r.message.isNotEmpty()) Toast.makeText(applicationContext, r.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun catalogue() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }

    @Composable
    private fun Screen() {
        @Suppress("UNUSED_VARIABLE") val t = tick
        val result = StageCheck.state.result
        val currentFacts = if (!preview) result?.facts?.let { FactProbe(this).refresh(it) } ?: facts else null
        val report = if (!preview && result?.installd != null && currentFacts != null) {
            StageCheck.gates(this, result, GatePhase.SWITCH, screenLock = currentFacts.screenLockSet, facts = currentFacts)
        } else null
        if (!preview && result?.status?.ours == true) {
            result.status.marker?.imageId?.let(Images::byId)?.let {
                if (it.id == image.id) EnvironmentNavigation.remember(this, it, false)
            }
        }
        val maintenance = if (!preview) ImageMaintenance.message(this, image, result, report) else null

        val tileReport = report ?: if (!preview && result != null && currentFacts != null) {
            StageCheck.gates(this, result, GatePhase.SWITCH, screenLock = currentFacts.screenLockSet, facts = currentFacts)
        } else null
        fun tileLevel(id: String): Level = when {
            preview -> Level.OK
            StageCheck.state.busy -> Level.INFO
            StageCheck.state.failure?.id == id -> StageCheck.state.failure!!.level
            StageCheck.state.phase == StageCheck.Phase.DONE -> tileReport?.find(id)?.level ?: Level.INFO
            else -> Level.INFO
        }

        val rootValue = if (preview) "KernelSU active" else currentFacts?.rootKind?.takeUnless { it == RootKind.NONE }?.label?.let {
            stringResource(R.string.environment_active, it)
        } ?: stringResource(R.string.environment_no_root)
        val storageValue = if (preview) "51.2 GB free" else currentFacts?.storage?.let {
            stringResource(R.string.environment_free, Units.bytes(it.freeBytes))
        } ?: stringResource(R.string.environment_unknown)
        val batteryValue = if (preview) "98% Charging · 28°C" else currentFacts?.battery?.let {
            "${it.levelPercent}%" + (if (it.charging) " · Charging" else "") + (it.temperatureC?.let { temp -> " · ${temp.toInt()}°C" } ?: "")
        } ?: stringResource(R.string.environment_unknown)
        val lockValue = if (preview) stringResource(R.string.environment_lock_off) else stringResource(
            when (currentFacts?.screenLockSet) {
                false -> R.string.environment_lock_off
                true -> R.string.environment_lock_on
                null -> R.string.environment_unknown
            },
        )

        val blocked = report?.gates?.firstOrNull { it.level == Level.BLOCK }
        val blockPlain: PlainMessage? = if (!preview && blocked != null && currentFacts != null &&
            (maintenance == null || blocked.finding.reason != report.find(SwitchGates.ID_IMAGE)?.finding?.reason)
        ) {
            Plain.of(blocked.finding, report, currentFacts).takeIf { maintenance == null || it.action == FixAction.RESTORE }
        } else null

        val checking = !preview && (patchBusy || StageCheck.state.busy || StageCheck.state.phase == StageCheck.Phase.IDLE ||
            (StageCheck.state.phase != StageCheck.Phase.FAILED && report == null) ||
            Readiness.anyBusy() || RemoveJob.state.busy)

        fun readyToBoot(): Boolean = preview || (!patchBusy && StageCheck.state.phase == StageCheck.Phase.DONE &&
            StageCheck.state.result === result && result != null && result.ageMs <= Readiness.CHECK_AGE_MS &&
            report != null && (report.image?.id == image.id || (maintenance != null && ImageMaintenance.identifiedImage(result)?.id == image.id)) &&
            (maintenance != null || !report.blocked) && !Readiness.anyBusy() && !RemoveJob.state.busy)

        window.decorView.removeCallbacks(recheck)
        if (!preview && !checking && result != null && StageCheck.state.phase == StageCheck.Phase.DONE) {
            window.decorView.postDelayed(recheck, (Readiness.CHECK_AGE_MS - result.ageMs).coerceAtLeast(0L) + 500L)
        }

        val fix = blockPlain?.takeIf { !checking }
        val restoreFirst = fix?.action == FixAction.RESTORE
        val ctaLabel = when {
            result?.status?.pendingUpdate != null && maintenance != null && !restoreFirst -> stringResource(R.string.retry_update)
            fix != null -> fix.actionLabel ?: stringResource(R.string.environment_what_stops)
            else -> stringResource(if (maintenance != null) R.string.environment_update else R.string.environment_boot, image.name)
        }

        DualOsScaffold(
            title = image.name,
            subtitle = if (preview) stringResource(R.string.environment_preview) else null,
            onBack = { finish() },
            largeTopBar = false,
            bottomBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 3.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Button(
                            onClick = {
                                if (fix != null) {
                                    if (fix.actionLabel == null || fix.action == FixAction.NONE || fix.action == FixAction.ACKNOWLEDGE) {
                                        MaterialAlertDialogBuilder(this@EnvironmentActivity).setTitle(fix.title).setMessage(fix.line)
                                            .setPositiveButton(android.R.string.ok, null).show()
                                    } else {
                                        FixActions.run(this@EnvironmentActivity, fix.action, image)
                                    }
                                    return@Button
                                }
                                if (!readyToBoot()) {
                                    Readiness.tick(this@EnvironmentActivity)
                                    tick++
                                    return@Button
                                }
                                if (maintenance != null) {
                                    FixActions.run(this@EnvironmentActivity, maintenance.action, image)
                                    return@Button
                                }
                                if (preview) startActivity(BootProgressActivity.intent(this@EnvironmentActivity, image, true))
                                else SwitchConfirm.show(this@EnvironmentActivity)
                            },
                            enabled = !checking,
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            if (checking) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Text(
                                        stringResource(R.string.home_checking),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            } else {
                                Text(ctaLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
                        title = image.nameWithVariant,
                        subtitle = image.blurb,
                        height = 170.dp,
                    )
                }

                if (!preview && !StageCheck.state.busy && (Readiness.anyBusy() || RemoveJob.state.busy)) {
                    val logs = LogJob.state
                    val why = when {
                        logs.busy && logs.stopping -> R.string.environment_busy_logs_stopping
                        logs.busy -> R.string.environment_busy_logs
                        else -> R.string.environment_busy_other
                    }
                    item {
                        ExpressiveGroupedCard {
                            ExpressiveListItem(title = stringResource(why), icon = Icons.Rounded.Refresh)
                            if (logs.busy && !logs.recovering && !logs.stopping) {
                                ExpressiveListDivider()
                                ExpressiveListItem(
                                    title = stringResource(R.string.logs_skip),
                                    subtitle = stringResource(R.string.logs_skip_summary),
                                    icon = DualOsIcons.History,
                                    showChevron = true,
                                    onClick = { LogJob.skip() },
                                )
                            }
                        }
                    }
                }

                item {
                    CheckTilesGrid(
                        tiles = listOf(
                            CheckTileUi(SwitchGates.ID_ROOT, stringResource(R.string.environment_root), rootValue, tileLevel(SwitchGates.ID_ROOT), DualOsIcons.Security),
                            CheckTileUi(SwitchGates.ID_SPACE, stringResource(R.string.environment_storage), storageValue, tileLevel(SwitchGates.ID_SPACE), DualOsIcons.Storage),
                            CheckTileUi(SwitchGates.ID_BATTERY, stringResource(R.string.environment_battery), batteryValue, tileLevel(SwitchGates.ID_BATTERY), DualOsIcons.Battery),
                            CheckTileUi(SwitchGates.ID_SCREEN_LOCK, stringResource(R.string.environment_lock), lockValue, tileLevel(SwitchGates.ID_SCREEN_LOCK), Icons.Rounded.Lock),
                        ),
                    )
                }

                if (blockPlain != null) {
                    item {
                        ExpressiveGroupedCard {
                            ExpressiveListItem(
                                title = blockPlain.title,
                                subtitle = blockPlain.line,
                                icon = levelIcon(Level.BLOCK),
                                iconTint = levelColor(Level.BLOCK),
                                showChevron = true,
                                onClick = { FixActions.run(this@EnvironmentActivity, blockPlain.action, image) },
                            )
                        }
                    }
                } else if (!preview && !StageCheck.state.busy && StageCheck.state.failure != null) {
                    val message = Plain.of(StageCheck.state.failure!!, image, null).line
                    item {
                        ExpressiveGroupedCard {
                            ExpressiveListItem(title = message, icon = Icons.Rounded.Warning, iconTint = levelColor(Level.WARN))
                        }
                    }
                }

                if (maintenance != null) {
                    item {
                        ExpressiveGroupedCard {
                            ExpressiveListItem(
                                title = maintenance.title,
                                subtitle = maintenance.line,
                                icon = Icons.Rounded.Warning,
                                iconTint = levelColor(Level.WARN),
                            )
                        }
                    }
                }

                if (image.family == com.fahadali.oneos.core.model.SystemFamily.INFINITYX) {
                    item { ExpressiveSectionHeader(stringResource(R.string.a53_system_options_section, image.shortName)) }
                    item { SystemSpecialFeaturesCard(ctx = this@EnvironmentActivity, image = image) }
                }

                val patchProfile = currentFacts?.let { Profiles.resolve(it).profile }
                val patchSources = patchProfile?.let { PatchChannels.sources(this@EnvironmentActivity, it.id, image.id) }.orEmpty()
                if (!preview && patchProfile != null && patchSources.isNotEmpty()) {
                    val heading = getString(R.string.patch_channel_title, patchProfile.label.removePrefix("Galaxy "))
                    val source = PatchChannels.selected(this@EnvironmentActivity, patchProfile.id, image.id)
                    val idle = !patchBusy && !Readiness.anyBusy() && !StageCheck.state.busy
                    fun label(choice: PatchSource) = if (choice.stable) getString(R.string.patch_channel_stable) else choice.label
                    item { ExpressiveSectionHeader("Patches") }
                    item {
                        ExpressiveGroupedCard {
                            ExpressiveListItem(
                                title = "${label(source)} · ${patchProfile.label.removePrefix("Galaxy ")}",
                                subtitle = stringResource(if (patchBusy) R.string.patch_channel_checking else R.string.patch_channel_summary),
                                icon = DualOsIcons.Sync,
                                enabled = idle,
                                showChevron = idle,
                                onClick = {
                                    MaterialAlertDialogBuilder(this@EnvironmentActivity).setTitle(heading)
                                        .setSingleChoiceItems(patchSources.map { label(it) }.toTypedArray(), patchSources.indexOfFirst { it.id == source.id }) { dialog, which ->
                                            dialog.dismiss(); choosePatches(patchSources[which].id)
                                        }.setNegativeButton(android.R.string.cancel, null).show()
                                },
                            )
                            ExpressiveListDivider()
                            ExpressiveListItem(
                                title = stringResource(R.string.patch_channel_refresh),
                                subtitle = PatchChannels.setFor(patchProfile.id, image.id)?.let { set ->
                                    if (!source.stable) runCatching { PatchChannels.snapshot(this@EnvironmentActivity, set, patchProfile.id)?.revision }.getOrNull()
                                    else getString(R.string.patch_channel_stable_version, PatchChannels.stableVersion(this@EnvironmentActivity))
                                },
                                icon = Icons.Rounded.Refresh,
                                enabled = idle,
                                showChevron = idle,
                                onClick = { checkPatches() },
                            )
                        }
                    }
                }

                val guest = rootState ?: GuestRuntimeStore.State(image.id)
                val rootIdle = !preview && !rootBusy && !Readiness.anyBusy() && !StageCheck.state.busy
                item { ExpressiveSectionHeader("Root") }
                item {
                    ExpressiveGroupedCard {
                        ExpressiveSwitchItem(
                            title = "Enable guest runtime",
                            subtitle = when {
                                rootBusy -> "Reading guest root…"
                                guest.error != null -> guest.error
                                !guest.ready -> "Update this system to add guest root support"
                                else -> null
                            },
                            icon = DualOsIcons.Security,
                            checked = guest.enabled,
                            enabled = rootIdle && guest.ready,
                            onCheckedChange = { enabled ->
                                changeRoot { done -> GuestRuntimeStore.setEnabled(this@EnvironmentActivity, image.id, enabled, done) }
                            },
                        )
                        ExpressiveListDivider()
                        ExpressiveListItem(
                            title = stringResource(R.string.guest_root_shared_kernel_title),
                            subtitle = stringResource(R.string.guest_root_shared_kernel_summary),
                        )
                        ExpressiveListDivider()
                        ExpressiveListItem(
                            title = "Xposed framework",
                            subtitle = guest.providerName(),
                            icon = DualOsIcons.Layers,
                            enabled = rootIdle && guest.ready,
                            showChevron = rootIdle && guest.ready,
                            onClick = {
                                val providers = listOf("zygisk_vector") + guest.modules.filter { it.framework != null }.map { it.id }.filter { it != "zygisk_vector" }
                                val names = providers.map { guest.providerName(it) } + "Import LSPosed or Vector ZIP…"
                                MaterialAlertDialogBuilder(this@EnvironmentActivity).setTitle("Xposed framework")
                                    .setSingleChoiceItems(names.toTypedArray(), providers.indexOf(guest.selected)) { dialog, which ->
                                        dialog.dismiss()
                                        if (which == providers.size) startActivity(
                                            Intent(this@EnvironmentActivity, GuestRuntimeActivity::class.java)
                                                .putExtra("image", image.id).putExtra("import_framework", true),
                                        )
                                        else changeRoot { done -> GuestRuntimeStore.select(this@EnvironmentActivity, image.id, providers[which], done) }
                                    }.setNegativeButton(android.R.string.cancel, null).show()
                            },
                        )
                        ExpressiveListDivider()
                        ExpressiveListItem(
                            title = "Manage guest modules",
                            icon = DualOsIcons.Extension,
                            enabled = !rootBusy,
                            showChevron = !rootBusy,
                            onClick = { startActivity(Intent(this@EnvironmentActivity, GuestRuntimeActivity::class.java).putExtra("image", image.id)) },
                        )
                    }
                }

                item { ExpressiveSectionHeader(stringResource(R.string.environment_apps)) }
                item {
                    ExpressiveGroupedCard {
                        ExpressiveListItem(
                            title = stringResource(R.string.bring_title),
                            subtitle = stringResource(R.string.environment_bring_summary),
                            icon = DualOsIcons.Apps,
                            enabled = !preview,
                            showChevron = !preview,
                            onClick = { startActivity(BringAppsActivity.intent(this@EnvironmentActivity, image)) },
                        )
                        ExpressiveListDivider()
                        ExpressiveListItem(
                            title = stringResource(R.string.environment_report),
                            icon = DualOsIcons.BugReport,
                            showChevron = true,
                            onClick = {
                                if (preview) MaterialAlertDialogBuilder(this@EnvironmentActivity).setTitle(R.string.environment_report)
                                    .setMessage(R.string.environment_preview).setPositiveButton(android.R.string.ok, null).show()
                                else FixActions.run(this@EnvironmentActivity, FixAction.SEND_REPORT, image)
                            },
                        )
                    }
                }

                val toolApps = if (preview) emptyList() else ToolApps.turnedOff(this@EnvironmentActivity)
                if (toolApps.isNotEmpty()) {
                    item { ExpressiveSectionHeader(stringResource(R.string.tool_app_section)) }
                    item {
                        ExpressiveGroupedCard {
                            toolApps.forEachIndexed { idx, pkg ->
                                if (idx > 0) ExpressiveListDivider()
                                ExpressiveListItem(
                                    title = stringResource(R.string.tool_app_on_title, pkg),
                                    subtitle = stringResource(R.string.tool_app_on_summary),
                                    enabled = !ToolApps.state.busy,
                                    showChevron = !ToolApps.state.busy,
                                    onClick = {
                                        MaterialAlertDialogBuilder(this@EnvironmentActivity).setMessage(getString(R.string.tool_app_on_confirm, pkg))
                                            .setPositiveButton(R.string.tool_app_on_run) { _, _ -> ToolApps.turnOn(this@EnvironmentActivity, pkg) }
                                            .setNegativeButton(android.R.string.cancel, null).show()
                                    },
                                )
                            }
                        }
                    }
                }

                val ours = result?.status?.takeIf { it.ours }?.marker?.imageId == image.id
                if (!preview && (ours || maintenance != null)) {
                    val canManage = SystemManage.canRun() && !patchBusy && !rootBusy
                    item { ExpressiveSectionHeader(stringResource(R.string.manage_section, image.name)) }
                    item {
                        ExpressiveGroupedCard {
                            ExpressiveListItem(
                                title = stringResource(R.string.manage_start_fresh),
                                subtitle = stringResource(R.string.manage_start_fresh_summary),
                                icon = DualOsIcons.RestartAlt,
                                enabled = canManage,
                                showChevron = canManage,
                                onClick = { SystemManage.confirmStartFresh(this@EnvironmentActivity, image) },
                            )
                            ExpressiveListDivider()
                            ExpressiveListItem(
                                title = stringResource(R.string.manage_remove, image.name),
                                subtitle = stringResource(R.string.manage_remove_summary),
                                icon = Icons.Rounded.Delete,
                                iconTint = MaterialTheme.colorScheme.error,
                                enabled = canManage,
                                showChevron = canManage,
                                onClick = { SystemManage.confirmRemove(this@EnvironmentActivity, image) { if (it.systemRemoved) catalogue() } },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun changeRoot(action: ((GuestRuntimeStore.State) -> Unit) -> Unit) {
        if (rootBusy) return
        rootBusy = true
        action { next ->
            if (!isDestroyed) {
                rootBusy = false
                if (next.error == null) rootState = next
                else MaterialAlertDialogBuilder(this).setTitle("Guest root").setMessage(next.error).setPositiveButton(android.R.string.ok, null).show()
                tick++
            }
        }
    }

    private fun channelProfile() = (StageCheck.state.result?.facts ?: facts)?.let { Profiles.resolve(it).profile }

    private fun checkPatches() {
        val profile = channelProfile() ?: return
        val source = PatchChannels.selected(this, profile.id, image.id)
        if (!source.stable) { choosePatches(source.id, showResult = true); return }
        if (patchBusy || Readiness.anyBusy()) return
        patchBusy = true
        io.execute {
            val result = runCatching { PatchChannels.stableRelease() }
            runOnUiThread {
                patchBusy = false
                if (!isDestroyed) {
                    val release = result.getOrNull()
                    val message = release?.let {
                        getString(
                            if (it.versionCode > PatchChannels.stableCode(this)) R.string.patch_channel_stable_newer
                            else R.string.patch_channel_stable_current,
                            it.versionName,
                        )
                    } ?: result.exceptionOrNull()?.message
                    MaterialAlertDialogBuilder(this).setTitle(if (result.isSuccess) R.string.patch_channel_refresh else R.string.patch_channel_failed)
                        .setMessage(message).setPositiveButton(android.R.string.ok, null).show()
                    tick++
                }
            }
        }
    }

    private fun choosePatches(sourceId: String, showResult: Boolean = false) {
        if (patchBusy || Readiness.anyBusy()) return
        patchBusy = true
        val selectedImage = image.id
        val profile = channelProfile() ?: run { patchBusy = false; return }
        io.execute {
            val result = runCatching {
                val set = PatchChannels.setFor(profile.id, selectedImage)!!
                val source = PatchChannels.sources(this, profile.id, selectedImage).first { it.id == sourceId }
                if (!source.stable) PatchChannels.refresh(this, set, profile.id, source.id)
                PatchChannels.select(this, profile.id, selectedImage, source.id)
            }
            runOnUiThread {
                patchBusy = false
                if (!isDestroyed) {
                    result.exceptionOrNull()?.let { error ->
                        MaterialAlertDialogBuilder(this).setTitle(R.string.patch_channel_failed).setMessage(error.message)
                            .setPositiveButton(android.R.string.ok, null).show()
                    }
                    if (showResult && result.isSuccess) Toast.makeText(this, R.string.patch_channel_checked, Toast.LENGTH_LONG).show()
                    StageCheck.start(this, StageCheck.Mode.FULL)
                    tick++
                }
            }
        }
    }
}
