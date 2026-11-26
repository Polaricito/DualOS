package com.fahadali.oneos.ui

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.StatFs
import android.text.format.DateFormat
import android.widget.Toast
import androidx.annotation.DrawableRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.fahadali.oneos.R
import com.fahadali.oneos.core.model.ImageRef
import com.fahadali.oneos.core.model.Images
import com.fahadali.oneos.core.model.Level
import com.fahadali.oneos.core.model.SystemFamily
import com.fahadali.oneos.core.model.Units
import com.fahadali.oneos.core.root.OutcomeKind
import com.fahadali.oneos.session.LogJob
import com.fahadali.oneos.session.OldStagesJob
import com.fahadali.oneos.session.RemoveJob
import com.fahadali.oneos.session.SessionPrefs
import com.fahadali.oneos.session.StageCheck
import com.fahadali.oneos.shared.DualOsIcons
import com.fahadali.oneos.shared.ExpressiveGroupedCard
import com.fahadali.oneos.shared.ExpressiveListDivider
import com.fahadali.oneos.shared.ExpressiveListItem
import com.fahadali.oneos.shared.ExpressiveSectionHeader
import com.fahadali.oneos.shared.ExpressiveSegmentedControl
import com.fahadali.oneos.shared.ExpressiveStatusPill
import com.fahadali.oneos.shared.ExpressiveSwitchItem
import com.fahadali.oneos.shared.LocalDualOsStatusColors
import java.util.Date

internal object EditionBeforeYouStart {
    val ackRes: Int = R.string.before_ack

    fun items(): List<BeforeItem> = listOf(
        BeforeItem(R.string.before_experimental_title, R.string.before_experimental_summary, Icons.Rounded.Warning),
        BeforeItem(R.string.before_backup_title, R.string.before_backup_summary, DualOsIcons.Save),
        BeforeItem(R.string.before_lock_title, R.string.before_lock_summary, Icons.Rounded.Lock),
        BeforeItem(R.string.before_apps_title, R.string.before_apps_summary, DualOsIcons.Security),
        BeforeItem(R.string.before_tested_title, R.string.tested_scope, DualOsIcons.PhoneAndroid),
    )
}

@DrawableRes
internal fun systemArtworkRes(family: SystemFamily?, header: Boolean = false): Int = when (family) {
    SystemFamily.INFINITYX -> if (header) R.drawable.os_infinity_header else R.drawable.os_infinity
    else -> if (header) R.drawable.os_lineage_header else R.drawable.os_lineage
}

internal fun readDataStorage(): Pair<Long, Long>? = runCatching {
    val stat = StatFs(Environment.getDataDirectory().path)
    stat.availableBytes to stat.totalBytes
}.getOrNull()

internal fun formatMediumDate(ctx: Context, wallMs: Long): String =
    DateFormat.getMediumDateFormat(ctx).format(Date(wallMs))

internal fun formatDateTime(ctx: Context, wallMs: Long): String =
    DateFormat.getMediumDateFormat(ctx).format(Date(wallMs)) + " · " + DateFormat.getTimeFormat(ctx).format(Date(wallMs))

internal data class CheckTileUi(
    val id: String,
    val title: String,
    val value: String,
    val level: Level,
    val icon: ImageVector,
    val onClick: (() -> Unit)? = null,
)

@Composable
internal fun RomArtworkBanner(
    image: ImageRef?,
    title: String,
    subtitle: String? = null,
    badge: String? = null,
    badgeColor: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 180.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        if (image != null) {
            Image(
                painter = painterResource(systemArtworkRes(image.family, header = true)),
                contentDescription = image.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.14f),
                                Color.Black.copy(alpha = 0.68f),
                            ),
                        ),
                    ),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        ),
                    ),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (!badge.isNullOrBlank()) {
                    ExpressiveStatusPill(
                        text = badge,
                        containerColor = badgeColor,
                        contentColor = Color.White,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (image != null) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (image != null) Color.White.copy(alpha = 0.88f) else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun CheckTilesGrid(
    tiles: List<CheckTileUi>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        tiles.chunked(2).forEach { rowTiles ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowTiles.forEach { tile ->
                    val status = LocalDualOsStatusColors.current
                    val bg = when (tile.level) {
                        Level.BLOCK -> status.blockContainer
                        Level.WARN -> status.warnContainer
                        else -> MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                    val accent = when (tile.level) {
                        Level.BLOCK -> status.block
                        Level.WARN -> status.warn
                        Level.OK -> status.ok
                        Level.INFO -> MaterialTheme.colorScheme.primary
                    }
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .then(if (tile.onClick != null) Modifier.clickable(onClick = tile.onClick) else Modifier),
                        color = bg,
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = tile.icon,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(22.dp),
                            )
                            Text(
                                text = tile.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = tile.value,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (rowTiles.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun A53GeneralFeaturesCard(
    ctx: Context,
    showStatusBadges: Boolean = true,
) {
    var nfc by remember { mutableStateOf(SessionPrefs.guestNfc(ctx)) }
    var sdMode by remember { mutableStateOf(SessionPrefs.guestSdCard(ctx)) }
    val statusColors = LocalDualOsStatusColors.current

    ExpressiveGroupedCard {
        ExpressiveSwitchItem(
            title = stringResource(R.string.a53_nfc_title),
            subtitle = stringResource(R.string.a53_nfc_summary),
            icon = DualOsIcons.Nfc,
            checked = nfc,
            onCheckedChange = {
                nfc = it
                SessionPrefs.setGuestNfc(ctx, it)
            },
        )
        ExpressiveListDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = DualOsIcons.SdCard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.a53_sdcard_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.a53_sdcard_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val modes = listOf(
                SessionPrefs.SdCardMode.RO to stringResource(R.string.a53_sdcard_ro),
                SessionPrefs.SdCardMode.RW to stringResource(R.string.a53_sdcard_rw),
                SessionPrefs.SdCardMode.OFF to stringResource(R.string.a53_sdcard_off),
            )
            ExpressiveSegmentedControl(
                options = modes,
                selected = sdMode,
                onSelect = { chosen ->
                    sdMode = chosen
                    SessionPrefs.setGuestSdCard(ctx, chosen)
                },
            )
        }
        if (showStatusBadges) {
            ExpressiveListDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ExpressiveStatusPill(
                        text = stringResource(R.string.a53_pin_badge),
                        containerColor = statusColors.okContainer,
                        contentColor = statusColors.ok,
                    )
                    Text(
                        text = stringResource(R.string.a53_pin_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ExpressiveStatusPill(
                        text = stringResource(R.string.a53_selinux_badge),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        text = stringResource(R.string.a53_selinux_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SystemSpecialFeaturesCard(
    ctx: Context,
    image: ImageRef,
) {
    if (image.family != com.fahadali.oneos.core.model.SystemFamily.INFINITYX) return
    var blur by remember { mutableStateOf(SessionPrefs.guestBlur(ctx)) }
    ExpressiveGroupedCard {
        ExpressiveSwitchItem(
            title = stringResource(R.string.a53_blur_title),
            subtitle = stringResource(R.string.a53_blur_summary),
            icon = DualOsIcons.BlurOn,
            checked = blur,
            onCheckedChange = {
                blur = it
                SessionPrefs.setGuestBlur(ctx, it)
            },
        )
    }
}

@Composable
internal fun SettingsTabContent(
    activity: FragmentActivity,
    contentPadding: PaddingValues,
) {
    var tick by remember { mutableIntStateOf(0) }
    var heatCap by remember { mutableStateOf(SessionPrefs.heatCap(activity)) }
    var fullLogs by remember { mutableStateOf(SessionPrefs.fullLogs(activity)) }
    var usageCount by remember { mutableStateOf(SessionPrefs.usageCount(activity)) }
    var secIms by remember { mutableStateOf(SessionPrefs.secIms(activity)) }
    var edmStub by remember { mutableStateOf(SessionPrefs.edmStub(activity)) }
    var verboseBoot by remember { mutableStateOf(SessionPrefs.verboseBoot(activity)) }
    var allowLock by remember { mutableStateOf(SessionPrefs.allowScreenLock(activity)) }

    DisposableEffect(Unit) {
        val subs = listOf(
            LogJob.observeBy({ Triple(it.phase, it.recovering, it.stopping) }) { tick++ },
            OldStagesJob.observe { tick++ },
            StageCheck.observe { tick++ },
            RemoveJob.observe { tick++ },
        )
        onDispose { subs.forEach { it() } }
    }
    @Suppress("UNUSED_VARIABLE") val refreshTick = tick

    val removing = RemoveJob.state
    val logs = LogJob.state
    val latestZip = LogJob.latestZip(activity)
    val preparedImage = EnvironmentNavigation.preparedImage(activity)
    val statusColors = LocalDualOsStatusColors.current

    val spaceSummary = run {
        val free = readDataStorage()?.first
        val old = OldStagesJob.state.stages
        val oldNames = StageCheck.state.result?.status?.oldStages
        when {
            free == null -> stringResource(R.string.advanced_space_summary)
            old != null && old.isEmpty() -> stringResource(R.string.advanced_space_none, Units.bytes(free))
            old != null -> stringResource(
                R.string.advanced_space_old,
                Units.bytes(free),
                pluralStringResource(R.plurals.advanced_space_copies, old.size, old.size),
                Units.bytes(old.sumOf { it.kib } * 1024),
            )
            !oldNames.isNullOrEmpty() -> stringResource(
                R.string.advanced_space_count,
                Units.bytes(free),
                pluralStringResource(R.plurals.advanced_space_copies, oldNames.size, oldNames.size),
            )
            else -> stringResource(R.string.advanced_space_free, Units.bytes(free))
        }
    }

    val lastSummary = run {
        val launch = SessionPrefs.lastLaunch(activity)
        when {
            launch == null -> stringResource(R.string.advanced_last_none)
            logs.busy && logs.stopping -> stringResource(R.string.logs_stopping)
            logs.busy && !logs.recovering -> stringResource(R.string.home_row_last_reading)
            else -> {
                val system = Images.systemName(Images.byId(launch.imageId))
                val outcome = launch.outcome?.let { o -> OutcomeKind.entries.firstOrNull { it.name == o } }
                val date = formatMediumDate(activity, launch.wallMs)
                when {
                    outcome != null -> stringResource(R.string.advanced_last_outcome, date, outcome.title(system).trimEnd('.'))
                    launch.dryRun -> stringResource(R.string.advanced_last_test, date)
                    else -> stringResource(R.string.home_row_last_unread, date)
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ExpressiveSectionHeader(stringResource(R.string.a53_caps_section)) }
        item { A53GeneralFeaturesCard(ctx = activity, showStatusBadges = true) }

        item { ExpressiveSectionHeader(stringResource(R.string.advanced_card_system)) }
        item {
            ExpressiveGroupedCard {
                if (preparedImage != null) {
                    ExpressiveListItem(
                        title = stringResource(R.string.bring_title),
                        subtitle = stringResource(R.string.environment_bring_summary),
                        icon = DualOsIcons.Apps,
                        showChevron = true,
                        onClick = { activity.startActivity(BringAppsActivity.intent(activity, preparedImage)) },
                    )
                    ExpressiveListDivider()
                    ExpressiveListItem(
                        title = "Manage guest modules",
                        subtitle = preparedImage.name,
                        icon = DualOsIcons.Extension,
                        showChevron = true,
                        onClick = { activity.startActivity(Intent(activity, GuestRuntimeActivity::class.java).putExtra("image", preparedImage.id)) },
                    )
                    ExpressiveListDivider()
                }
                ExpressiveListItem(
                    title = stringResource(R.string.prepare_title),
                    subtitle = stringResource(R.string.advanced_setup_summary),
                    icon = Icons.Rounded.Refresh,
                    showChevron = true,
                    onClick = { activity.startActivity(Intent(activity, PrepareActivity::class.java)) },
                )
                ExpressiveListDivider()
                ExpressiveListItem(
                    title = stringResource(R.string.image_title),
                    subtitle = stringResource(R.string.advanced_file_summary),
                    icon = DualOsIcons.FolderOpen,
                    showChevron = true,
                    onClick = { activity.startActivity(Intent(activity, ImageActivity::class.java)) },
                )
                ExpressiveListDivider()
                ExpressiveListItem(
                    title = stringResource(R.string.space_title),
                    subtitle = spaceSummary,
                    icon = DualOsIcons.Storage,
                    showChevron = true,
                    onClick = { activity.startActivity(Intent(activity, OldStagesActivity::class.java)) },
                )
                ExpressiveListDivider()
                ExpressiveListItem(
                    title = stringResource(if (removing.busy) R.string.remove_running else R.string.remove_title),
                    subtitle = if (removing.busy) null else (removing.message ?: stringResource(R.string.remove_summary)),
                    icon = Icons.Rounded.Delete,
                    iconTint = MaterialTheme.colorScheme.error,
                    enabled = !removing.busy,
                    showChevron = !removing.busy,
                    onClick = {
                        if (!RemoveJob.canStart()) {
                            Toast.makeText(activity, R.string.remove_busy, Toast.LENGTH_LONG).show()
                        } else {
                            MaterialAlertDialogBuilder(activity)
                                .setTitle(R.string.remove_confirm_title)
                                .setMessage(R.string.remove_confirm_message)
                                .setPositiveButton(R.string.remove_confirm_run) { _, _ -> SystemManage.removeAll(activity) }
                                .setNegativeButton(android.R.string.cancel, null)
                                .show()
                        }
                    },
                )
            }
        }

        item { ExpressiveSectionHeader(stringResource(R.string.advanced_card_sessions)) }
        item {
            ExpressiveGroupedCard {
                ExpressiveListItem(
                    title = stringResource(R.string.last_title),
                    subtitle = lastSummary,
                    icon = DualOsIcons.History,
                    showChevron = true,
                    onClick = { activity.startActivity(Intent(activity, LastSessionActivity::class.java)) },
                )
                ExpressiveListDivider()
                ExpressiveSwitchItem(
                    title = stringResource(R.string.advanced_heat_cap_title),
                    subtitle = stringResource(R.string.advanced_heat_cap_summary),
                    icon = DualOsIcons.Thermostat,
                    checked = heatCap,
                    onCheckedChange = { heatCap = it; SessionPrefs.setHeatCap(activity, it) },
                )
                ExpressiveListDivider()
                ExpressiveSwitchItem(
                    title = stringResource(R.string.advanced_full_logs_title),
                    subtitle = stringResource(R.string.advanced_full_logs_summary),
                    icon = DualOsIcons.Description,
                    checked = fullLogs,
                    onCheckedChange = { fullLogs = it; SessionPrefs.setFullLogs(activity, it) },
                )
                ExpressiveListDivider()
                ExpressiveSwitchItem(
                    title = stringResource(R.string.usage_count_title),
                    subtitle = stringResource(R.string.usage_count_summary),
                    icon = Icons.Rounded.Info,
                    checked = usageCount,
                    onCheckedChange = { usageCount = it; SessionPrefs.setUsageCount(activity, it) },
                )
                if (latestZip != null && !logs.busy) {
                    ExpressiveListDivider()
                    ExpressiveListItem(
                        title = stringResource(R.string.logs_title),
                        subtitle = stringResource(R.string.advanced_logs_summary),
                        icon = DualOsIcons.Description,
                        showChevron = true,
                        onClick = { activity.startActivity(Intent(activity, FindingsActivity::class.java).putExtra(FindingsActivity.EXTRA_ZIP, latestZip.path)) },
                    )
                }
                if (logs.busy && logs.recovering) {
                    ExpressiveListDivider()
                    ExpressiveListItem(
                        title = stringResource(R.string.home_restoring),
                        icon = DualOsIcons.RestartAlt,
                        iconTint = MaterialTheme.colorScheme.primary,
                    )
                } else if (!logs.busy && SessionRestore.offered(activity)) {
                    ExpressiveListDivider()
                    ExpressiveListItem(
                        title = stringResource(R.string.last_restore_title),
                        subtitle = stringResource(R.string.advanced_restore_summary),
                        icon = DualOsIcons.RestartAlt,
                        iconTint = statusColors.warn,
                        showChevron = true,
                        onClick = { SessionRestore.confirm(activity) },
                    )
                }
            }
        }

        item { ExpressiveSectionHeader(stringResource(R.string.advanced_card_calls)) }
        item {
            ExpressiveGroupedCard {
                ExpressiveSwitchItem(
                    title = stringResource(R.string.advanced_sec_ims_title),
                    subtitle = stringResource(R.string.advanced_sec_ims_summary),
                    icon = Icons.Rounded.Phone,
                    checked = secIms,
                    onCheckedChange = { secIms = it; SessionPrefs.setSecIms(activity, it) },
                )
                ExpressiveListDivider()
                ExpressiveSwitchItem(
                    title = stringResource(R.string.advanced_edm_stub_title),
                    subtitle = stringResource(R.string.advanced_edm_stub_summary),
                    icon = DualOsIcons.Camera,
                    checked = edmStub,
                    onCheckedChange = { edmStub = it; SessionPrefs.setEdmStub(activity, it) },
                )
                ExpressiveListDivider()
                ExpressiveSwitchItem(
                    title = stringResource(R.string.verbose_boot_title),
                    subtitle = stringResource(R.string.verbose_boot_summary),
                    icon = DualOsIcons.Terminal,
                    checked = verboseBoot,
                    onCheckedChange = { verboseBoot = it; SessionPrefs.setVerboseBoot(activity, it) },
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.advanced_card_risky),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = statusColors.block,
                )
                ExpressiveStatusPill(
                    text = stringResource(R.string.advanced_card_risky_tag),
                    containerColor = statusColors.blockContainer,
                    contentColor = statusColors.block,
                )
            }
        }
        item {
            ExpressiveGroupedCard {
                ExpressiveSwitchItem(
                    title = stringResource(R.string.advanced_allow_lock_title),
                    subtitle = stringResource(R.string.advanced_allow_lock_summary),
                    icon = Icons.Rounded.Lock,
                    checked = allowLock,
                    onCheckedChange = { on ->
                        if (!on) {
                            allowLock = false
                            SessionPrefs.setAllowScreenLock(activity, false)
                        } else {
                            LockRiskDialog.show(activity) { yes ->
                                allowLock = yes
                                SessionPrefs.setAllowScreenLock(activity, yes)
                            }
                        }
                    },
                )
            }
        }

        item { ExpressiveSectionHeader(stringResource(R.string.advanced_card_help)) }
        item {
            ExpressiveGroupedCard {
                ExpressiveListItem(
                    title = stringResource(R.string.about_title),
                    subtitle = stringResource(R.string.advanced_about_summary),
                    icon = Icons.Rounded.Info,
                    showChevron = true,
                    onClick = { activity.startActivity(Intent(activity, AboutActivity::class.java)) },
                )
                ExpressiveListDivider()
                ExpressiveListItem(
                    title = stringResource(R.string.team_title),
                    subtitle = stringResource(R.string.advanced_team_summary),
                    icon = Icons.Rounded.Build,
                    showChevron = true,
                    onClick = { activity.startActivity(Intent(activity, DebugSettingsActivity::class.java)) },
                )
            }
        }
    }
}
