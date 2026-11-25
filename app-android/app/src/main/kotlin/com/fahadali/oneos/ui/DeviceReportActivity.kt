package com.fahadali.oneos.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fahadali.oneos.R
import com.fahadali.oneos.core.device.FactProbe
import com.fahadali.oneos.core.model.CompatEvaluator
import com.fahadali.oneos.core.model.CompatReport
import com.fahadali.oneos.core.model.CpuRules
import com.fahadali.oneos.core.model.DeviceFacts
import com.fahadali.oneos.core.model.Level
import com.fahadali.oneos.core.model.ModelFamily
import com.fahadali.oneos.core.model.PackageSets
import com.fahadali.oneos.core.model.Profiles
import com.fahadali.oneos.core.model.RootKind
import com.fahadali.oneos.core.model.SpaceRule
import com.fahadali.oneos.core.model.SuProbeResult
import com.fahadali.oneos.core.model.SuState
import com.fahadali.oneos.core.model.Units
import com.fahadali.oneos.core.model.Verdict
import com.fahadali.oneos.core.root.RootProbe
import com.fahadali.oneos.core.root.SuProbe
import com.fahadali.oneos.probe.CarrierProps
import com.fahadali.oneos.shared.DualOsIcons
import com.fahadali.oneos.shared.DualOsTheme
import com.fahadali.oneos.shared.ExpressiveGroupedCard
import com.fahadali.oneos.shared.ExpressiveListDivider
import com.fahadali.oneos.shared.ExpressiveListItem
import com.fahadali.oneos.shared.ExpressiveLoadingBox
import com.fahadali.oneos.shared.ExpressiveSectionHeader
import com.fahadali.oneos.shared.ExpressiveStatusPill
import com.fahadali.oneos.shared.LocalDualOsStatusColors
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Device report: the read-only facts plus the Compatible / Warning / Not supported summary. */
class DeviceReportActivity : AppCompatActivity() {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private var probe: SuProbeResult? by mutableStateOf(null)
    private var probing: Boolean by mutableStateOf(false)
    private var facts: DeviceFacts? by mutableStateOf(null)
    private var report: CompatReport? by mutableStateOf(null)
    private var errorText: String? by mutableStateOf(null)
    private var showRootDialog: Boolean by mutableStateOf(false)
    private var shownScreenLock: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        probe = savedInstanceState?.let(::restoreProbe)
        setContent {
            DualOsTheme {
                Screen()
            }
        }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (facts != null && !probing && FactProbe.screenLockSet(this) != shownScreenLock) {
            refresh()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        probe?.let { p ->
            outState.putString(KEY_STATE, p.state.name)
            p.uid?.let { outState.putInt(KEY_UID, it) }
            outState.putString(KEY_CONTEXT, p.context)
            outState.putString(KEY_OUTPUT, p.output)
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun refresh() {
        val ctx = applicationContext
        val probeNow = probe
        val app = getString(R.string.app_name)
        executor.execute {
            val result = runCatching {
                val f = CarrierProps.withCarrier(FactProbe(ctx).collect(RootProbe(ctx).signals(probeNow)))
                f to CompatEvaluator.evaluate(f, app = app)
            }
            main.post {
                if (isDestroyed) return@post
                result.fold(
                    onSuccess = { (f, r) ->
                        facts = f
                        report = r
                        errorText = null
                        shownScreenLock = f.screenLockSet
                    },
                    onFailure = { e ->
                        errorText = "${e.javaClass.simpleName}: ${e.message.orEmpty()}".take(500)
                    },
                )
            }
        }
    }

    private fun runRootCheck() {
        if (probing) return
        probing = true
        executor.execute {
            val result = SuProbe.runId()
            main.post {
                probing = false
                probe = result
                if (!isDestroyed) refresh()
            }
        }
    }

    @Composable
    private fun Screen() {
        val f = facts
        val r = report
        val err = errorText
        val subtitle = if (f != null && r != null) "${f.model} · ${r.verdict.label}" else null

        DualOsScaffold(
            title = stringResource(R.string.report_title),
            subtitle = subtitle,
            onBack = { finish() },
        ) { padding ->
            when {
                err != null -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp,
                            top = padding.calculateTopPadding() + 8.dp,
                            bottom = padding.calculateBottomPadding() + 24.dp,
                        ),
                    ) {
                        item {
                            ExpressiveGroupedCard {
                                ExpressiveListItem(
                                    title = stringResource(R.string.report_failed),
                                    subtitle = err,
                                    icon = levelIcon(Level.BLOCK),
                                    iconTint = levelColor(Level.BLOCK),
                                )
                            }
                        }
                    }
                }
                f == null || r == null -> {
                    ExpressiveLoadingBox(
                        label = stringResource(R.string.report_reading),
                        modifier = Modifier.fillMaxSize().padding(padding),
                    )
                }
                else -> {
                    val statusColors = LocalDualOsStatusColors.current
                    val (vBg, vFg) = when (r.verdict) {
                        Verdict.COMPATIBLE -> statusColors.okContainer to statusColors.ok
                        Verdict.WARNING -> statusColors.warnContainer to statusColors.warn
                        Verdict.NOT_SUPPORTED -> statusColors.blockContainer to statusColors.block
                    }
                    val profile = Profiles.resolve(f).profile
                    val images = profile?.images().orEmpty()

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
                                colors = CardDefaults.cardColors(containerColor = vBg),
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        ExpressiveStatusPill(
                                            text = r.verdict.label,
                                            containerColor = vFg,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                        )
                                    }
                                    Text(
                                        text = verdictSummary(r),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = vFg,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }

                        if (r.sorted.isNotEmpty()) {
                            item { ExpressiveSectionHeader(stringResource(R.string.report_cat_summary)) }
                            item {
                                ExpressiveGroupedCard {
                                    r.sorted.forEachIndexed { idx, finding ->
                                        if (idx > 0) ExpressiveListDivider()
                                        ExpressiveListItem(
                                            title = finding.title,
                                            subtitle = finding.detail.ifEmpty { null },
                                            icon = levelIcon(finding.level),
                                            iconTint = levelColor(finding.level),
                                        )
                                    }
                                }
                            }
                        }

                        item { ExpressiveSectionHeader(stringResource(R.string.report_cat_phone)) }
                        item {
                            val family = f.family
                            ExpressiveGroupedCard {
                                ExpressiveListItem(
                                    title = "Model",
                                    subtitle = if (family == ModelFamily.OTHER) f.model else "${f.model} · ${family.label}",
                                    icon = DualOsIcons.PhoneAndroid,
                                )
                                ExpressiveListDivider()
                                ExpressiveListItem(title = "Codename", subtitle = f.device.ifEmpty { NOT_REPORTED })
                                ExpressiveListDivider()
                                ExpressiveListItem(title = "Maker", subtitle = "${f.brand} / ${f.manufacturer}")
                                ExpressiveListDivider()
                                ExpressiveListItem(
                                    title = "Form factor",
                                    subtitle = buildString {
                                        append(f.form.form.label)
                                        append("\n")
                                        append(f.form.evidence.joinToString("; "))
                                        if (f.form.mismatch) append("\nModel number says ${f.form.prefixForm?.label}")
                                    },
                                )
                                ExpressiveListDivider()
                                ExpressiveListItem(title = "Displays", subtitle = displaysText(f))
                            }
                        }

                        item { ExpressiveSectionHeader(stringResource(R.string.report_cat_chip)) }
                        item {
                            ExpressiveGroupedCard {
                                ExpressiveListItem(title = "SoC", subtitle = f.soc.display)
                                ExpressiveListDivider()
                                ExpressiveListItem(
                                    title = "Chip maker",
                                    subtitle = buildString {
                                        append(f.soc.vendor.label)
                                        if (f.socProps.manufacturer.isNotEmpty()) append(" (ro.soc.manufacturer ${f.socProps.manufacturer})")
                                    },
                                )
                                ExpressiveListDivider()
                                ExpressiveListItem(title = "Graphics", subtitle = f.socProps.egl.ifEmpty { NOT_REPORTED })
                                ExpressiveListDivider()
                                ExpressiveListItem(title = "64-bit ABIs", subtitle = f.abis64.joinToString().ifEmpty { "None" })
                                ExpressiveListDivider()
                                ExpressiveListItem(
                                    title = "CPU",
                                    subtitle = f.cpu?.let { cpu ->
                                        if (images.isNotEmpty()) {
                                            "${cpu.summary}\n${CpuRules.presence(cpu)}\n" +
                                                images.joinToString("\n") { "${it.shortName} image: ${CpuRules.forImage(it, cpu).title}" }
                                        } else {
                                            val missing = CpuRules.missingForColorOs(cpu)
                                            "${cpu.summary}\n${CpuRules.presence(cpu)}\nSome system images need " +
                                                CpuRules.COLOROS_REQUIRED.joinToString(", ") { it.uppercase(Locale.ROOT) } + ": " +
                                                if (missing.isEmpty()) "all present" else "missing ${missing.joinToString(", ") { it.uppercase(Locale.ROOT) }}"
                                        }
                                    } ?: NOT_REPORTED,
                                )
                            }
                        }

                        item { ExpressiveSectionHeader(stringResource(R.string.report_cat_software)) }
                        item {
                            ExpressiveGroupedCard {
                                ExpressiveListItem(title = "One UI", subtitle = f.oneUi?.let { "$it (${f.oneUiRaw})" } ?: "Not found")
                                ExpressiveListDivider()
                                ExpressiveListItem(title = "Android", subtitle = "${f.androidRelease} (SDK ${f.sdk})")
                                ExpressiveListDivider()
                                ExpressiveListItem(title = "Build ID", subtitle = f.buildId.ifEmpty { NOT_REPORTED })
                                ExpressiveListDivider()
                                ExpressiveListItem(title = "Fingerprint", subtitle = f.fingerprint.ifEmpty { NOT_REPORTED })
                                ExpressiveListDivider()
                                ExpressiveListItem(title = "Vendor API level", subtitle = f.vendorApiLevel.ifEmpty { NOT_REPORTED })
                                ExpressiveListDivider()
                                ExpressiveListItem(title = "Kernel", subtitle = f.kernel.ifEmpty { NOT_REPORTED })
                            }
                        }

                        item { ExpressiveSectionHeader(stringResource(R.string.report_cat_root)) }
                        item {
                            ExpressiveGroupedCard {
                                ExpressiveListItem(
                                    title = "Root manager",
                                    subtitle = rootText(f),
                                    icon = DualOsIcons.Security,
                                )
                                ExpressiveListDivider()
                                ExpressiveListItem(
                                    title = stringResource(R.string.check_root_title),
                                    subtitle = if (probing) stringResource(R.string.check_root_running) else probeText(probe),
                                    icon = DualOsIcons.Terminal,
                                    enabled = !probing,
                                    showChevron = !probing,
                                    onClick = { showRootDialog = true },
                                )
                            }
                        }

                        item { ExpressiveSectionHeader(stringResource(R.string.report_cat_storage)) }
                        item {
                            ExpressiveGroupedCard {
                                ExpressiveListItem(
                                    title = "Free space on /data",
                                    subtitle = f.storage?.let {
                                        "${Units.bytes(it.freeBytes)} free of ${Units.bytes(it.totalBytes)}\n" +
                                            if (profile != null && images.isNotEmpty()) {
                                                images.joinToString("\n") { image ->
                                                    "Preparing the ${image.shortName} image needs about " +
                                                        Units.bytes(SpaceRule.toStage(image, profile.choice(image.id)?.let { c -> PackageSets.byId(c.packageSetId) }))
                                                }
                                            } else {
                                                "A session with a typical ${Units.bytes(SpaceRule.TYPICAL_IMAGE_BYTES)} image needs about " + Units.bytes(SpaceRule.required())
                                            }
                                    } ?: NOT_REPORTED,
                                    icon = DualOsIcons.Storage,
                                )
                                ExpressiveListDivider()
                                ExpressiveListItem(
                                    title = "Battery",
                                    subtitle = f.battery?.let { b ->
                                        buildString {
                                            append("${b.levelPercent}%")
                                            append(if (b.charging) ", charging" else ", not charging")
                                            b.temperatureC?.let { append(String.format(Locale.ROOT, ", %.1f °C", it)) }
                                        }
                                    } ?: NOT_REPORTED,
                                    icon = DualOsIcons.Battery,
                                )
                            }
                        }

                        item { ExpressiveSectionHeader(stringResource(R.string.report_cat_boot)) }
                        item {
                            ExpressiveGroupedCard {
                                ExpressiveListItem(
                                    title = "Bootloader",
                                    subtitle = when (f.flashLocked) {
                                        "1" -> "Locked (nothing is flashed)"
                                        "0" -> "Unlocked"
                                        else -> NOT_REPORTED
                                    },
                                    icon = DualOsIcons.Shield,
                                )
                                ExpressiveListDivider()
                                ExpressiveListItem(title = "Verified boot", subtitle = f.verifiedBootState.ifEmpty { NOT_REPORTED })
                                ExpressiveListDivider()
                                ExpressiveListItem(
                                    title = "Screen lock",
                                    subtitle = when (f.screenLockSet) {
                                        true -> "Set (PIN, pattern or password). Remove it before switching."
                                        false -> "None"
                                        null -> NOT_REPORTED
                                    },
                                    icon = Icons.Rounded.Lock,
                                )
                                ExpressiveListDivider()
                                ExpressiveListItem(
                                    title = stringResource(R.string.report_readonly_title),
                                    subtitle = stringResource(R.string.report_readonly_summary),
                                )
                            }
                        }
                    }
                }
            }

            if (showRootDialog) {
                AlertDialog(
                    onDismissRequest = { showRootDialog = false },
                    title = { Text(stringResource(R.string.check_root_dialog_title)) },
                    text = { Text(stringResource(R.string.check_root_dialog_message)) },
                    confirmButton = {
                        TextButton(onClick = { showRootDialog = false; runRootCheck() }) {
                            Text(stringResource(R.string.check_root_run))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRootDialog = false }) {
                            Text(stringResource(android.R.string.cancel))
                        }
                    },
                )
            }
        }
    }

    private fun displaysText(f: DeviceFacts): String {
        if (f.panels.isEmpty()) return "None visible"
        val lines = f.panels.map { p ->
            val size = p.diagonalInches?.let { String.format(Locale.ROOT, ", %.1f in", it) } ?: ""
            "#${p.displayId} ${p.name}: ${p.widthPx} x ${p.heightPx} px$size"
        }.toMutableList()
        if (!f.panelsComplete) {
            lines += "Only active displays are visible on this Android version"
        }
        return lines.joinToString("\n")
    }

    private fun rootText(f: DeviceFacts): String = buildString {
        append(f.rootKind.label)
        if (f.root.managerPackages.isNotEmpty()) append("\nApp: ${f.root.managerPackages.joinToString()}")
        append(
            if (f.root.suPaths.isNotEmpty()) "\nsu visible at ${f.root.suPaths.joinToString()}"
            else "\nsu not visible to ${getString(R.string.app_name)}",
        )
        if (f.rootKind == RootKind.KERNELSU && f.root.suPaths.isEmpty()) {
            append(" (KernelSU shows it only to apps it allows)")
        }
    }

    private fun probeText(p: SuProbeResult?): String = when (p?.state) {
        null -> getString(R.string.check_root_summary)
        SuState.GRANTED -> "Root works: ${p.output}"
        SuState.TIMEOUT -> "No answer in time. Allow ${getString(R.string.app_name)} in your root manager, then tap to try again."
        SuState.NOT_FOUND -> "su not found for ${getString(R.string.app_name)}: ${p.output}"
        SuState.DENIED -> "Root refused: ${p.output.ifEmpty { "no output" }}"
        SuState.ERROR -> "Check failed: ${p.output}"
    }

    private fun verdictSummary(r: CompatReport): String {
        val warns = r.count(Level.WARN)
        val blocks = r.count(Level.BLOCK)
        val app = getString(R.string.app_name)
        return when (r.verdict) {
            Verdict.COMPATIBLE -> "Nothing found that stops $app on this phone. ${CompatEvaluator.TESTED_SCOPE}"
            Verdict.WARNING -> "Read the $warns warning${if (warns == 1) "" else "s"} below. ${getString(R.string.report_warning_next)}"
            Verdict.NOT_SUPPORTED -> "$app can't run on this phone: $blocks blocking finding${if (blocks == 1) "" else "s"} below."
        }
    }

    private fun restoreProbe(b: Bundle): SuProbeResult? {
        val state = b.getString(KEY_STATE)?.let { runCatching { SuState.valueOf(it) }.getOrNull() } ?: return null
        return SuProbeResult(
            state = state,
            uid = if (b.containsKey(KEY_UID)) b.getInt(KEY_UID) else null,
            context = b.getString(KEY_CONTEXT),
            output = b.getString(KEY_OUTPUT).orEmpty(),
        )
    }

    private companion object {
        const val NOT_REPORTED = "Not reported"
        const val KEY_STATE = "probe_state"
        const val KEY_UID = "probe_uid"
        const val KEY_CONTEXT = "probe_context"
        const val KEY_OUTPUT = "probe_output"
    }
}
