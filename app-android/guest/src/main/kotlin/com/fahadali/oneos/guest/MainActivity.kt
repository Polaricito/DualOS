package com.fahadali.oneos.guest

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import com.fahadali.oneos.shared.DualOsIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fahadali.oneos.shared.DualOsTheme
import com.fahadali.oneos.shared.ExpressiveGroupedCard
import com.fahadali.oneos.shared.ExpressiveListDivider
import com.fahadali.oneos.shared.ExpressiveListItem
import com.fahadali.oneos.shared.ExpressiveSectionHeader
import com.fahadali.oneos.shared.ExpressiveStatusPill
import com.fahadali.oneos.shared.StatusTone

/** DualOS guest companion home in Jetpack Compose + Material 3 Expressive. */
class MainActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var runtimeState by mutableStateOf(GuestRuntime.State(error = "Reading guest runtime…"))
    private var returnPending by mutableStateOf(false)
    private var noAnswerShown by mutableStateOf(false)
    private var tileAdded by mutableStateOf(false)
    private var fullLogsStatus by mutableStateOf(FullLogs.Status.UNSUPPORTED)
    private var confirmReturnReason by mutableStateOf<String?>(null)
    private var confirmRestartOpen by mutableStateOf(false)
    private var confirmFullLogsOpen by mutableStateOf(false)
    private var frameworkError by mutableStateOf<String?>(null)

    private val noAnswer = Runnable { noAnswerShown = true }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ensureReturnShortcut()
        val systemName = GuestName.read()
        if (savedInstanceState != null) {
            confirmReturnReason = savedInstanceState.getString(STATE_CONFIRM)
        } else {
            fromTile(intent)
        }
        refreshUi()
        setContent {
            DualOsTheme {
                GuestHomeScreen(
                    systemName = systemName,
                    runtime = runtimeState,
                    returnPending = returnPending,
                    noAnswerShown = noAnswerShown,
                    tileAdded = tileAdded,
                    supportsAddTile = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                    fullLogsStatus = fullLogsStatus,
                    confirmReturnReason = confirmReturnReason,
                    confirmRestartOpen = confirmRestartOpen,
                    confirmFullLogsOpen = confirmFullLogsOpen,
                    frameworkError = frameworkError,
                    onRequestReturn = { ask(ReturnRequest.REASON_APP) },
                    onConfirmReturn = { reason ->
                        confirmReturnReason = null
                        send(reason)
                    },
                    onDismissReturn = { reason ->
                        confirmReturnReason = null
                        if (reason in setOf(ReturnRequest.REASON_TILE, REASON_SHORTCUT)) finish()
                    },
                    onRequestTile = { requestTile() },
                    onOpenChecklist = { startActivity(Intent(this, ChecklistActivity::class.java)) },
                    onOpenBringApps = { startActivity(Intent(this, BringAppsActivity::class.java)) },
                    onOpenFullLogsConfirm = { confirmFullLogsOpen = true },
                    onConfirmFullLogs = {
                        confirmFullLogsOpen = false
                        runCatching { FullLogs.request(this) }.onFailure { e ->
                            Toast.makeText(
                                this,
                                getString(R.string.full_logs_failed, e.message ?: e.javaClass.simpleName),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                        fullLogsStatus = FullLogs.status(this)
                        handler.postDelayed({ if (!isDestroyed) refreshUi() }, 2500)
                    },
                    onDismissFullLogs = { confirmFullLogsOpen = false },
                    onOpenRootPermissions = { startActivity(Intent(this, GuestRootActivity::class.java)) },
                    onOpenModules = { startActivity(Intent(this, GuestModulesActivity::class.java)) },
                    onRequestRestart = { confirmRestartOpen = true },
                    onConfirmRestart = {
                        confirmRestartOpen = false
                        GuestRestart.launch(this)
                    },
                    onDismissRestart = { confirmRestartOpen = false },
                    onOpenFramework = {
                        val current = runtimeState
                        GuestRuntime.work({ GuestRuntime.openFramework(this, current); current }) { next ->
                            if (!isDestroyed && next.error != null) frameworkError = next.error
                        }
                    },
                    onDismissFrameworkError = { frameworkError = null },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        fromTile(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        confirmReturnReason?.let { outState.putString(STATE_CONFIRM, it) }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun refreshUi() {
        returnPending = ReturnRequest.pending(this)
        fullLogsStatus = FullLogs.status(this)
        handler.removeCallbacks(noAnswer)
        if (returnPending) {
            handler.postDelayed(noAnswer, NO_ANSWER_MS)
        } else {
            noAnswerShown = false
        }
        GuestRuntime.refresh(this) { if (!isDestroyed) runtimeState = it }
    }

    private fun fromTile(i: Intent?) {
        val reason = i?.getStringExtra(EXTRA_REASON) ?: return
        if (reason == ReturnRequest.REASON_TILE || reason == REASON_SHORTCUT) ask(reason)
    }

    private fun ask(reason: String) {
        if (ReturnRequest.pending(this)) {
            refreshUi()
            return
        }
        confirmReturnReason = reason
    }

    private fun send(reason: String) {
        startActivity(Intent(this, ReturnSplashActivity::class.java).putExtra(EXTRA_REASON, reason))
    }

    private fun ensureReturnShortcut() {
        val shortcuts = getSystemService(ShortcutManager::class.java) ?: return
        runCatching {
            if (shortcuts.manifestShortcuts.any { it.id == "return_to_oneui" }) return
            shortcuts.addDynamicShortcuts(
                listOf(
                    ShortcutInfo.Builder(this, "return_to_oneui")
                        .setShortLabel(getString(R.string.return_shortcut))
                        .setLongLabel(getString(R.string.return_shortcut))
                        .setIcon(Icon.createWithResource(this, R.drawable.ic_tile))
                        .setActivity(ComponentName(this, MainActivity::class.java))
                        .setIntent(
                            Intent(this, MainActivity::class.java)
                                .setAction(Intent.ACTION_VIEW)
                                .putExtra(EXTRA_REASON, REASON_SHORTCUT),
                        )
                        .build(),
                ),
            )
        }.onFailure { android.util.Log.w("DualOSGuest", "Could not refresh return shortcut", it) }
    }

    private fun requestTile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(this, ReturnTileService::class.java),
            getString(R.string.tile_label),
            Icon.createWithResource(this, R.drawable.ic_tile),
            mainExecutor,
        ) { result ->
            if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
            ) {
                tileAdded = true
            }
        }
    }

    companion object {
        private const val REASON_SHORTCUT = "shortcut"
        const val EXTRA_REASON = "com.fahadali.oneos.guest.REASON"
        private const val STATE_CONFIRM = "confirm"
        private const val NO_ANSWER_MS = 30_000L
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GuestHomeScreen(
    systemName: String?,
    runtime: GuestRuntime.State,
    returnPending: Boolean,
    noAnswerShown: Boolean,
    tileAdded: Boolean,
    supportsAddTile: Boolean,
    fullLogsStatus: FullLogs.Status,
    confirmReturnReason: String?,
    confirmRestartOpen: Boolean,
    confirmFullLogsOpen: Boolean,
    frameworkError: String?,
    onRequestReturn: () -> Unit,
    onConfirmReturn: (String) -> Unit,
    onDismissReturn: (String) -> Unit,
    onRequestTile: () -> Unit,
    onOpenChecklist: () -> Unit,
    onOpenBringApps: () -> Unit,
    onOpenFullLogsConfirm: () -> Unit,
    onConfirmFullLogs: () -> Unit,
    onDismissFullLogs: () -> Unit,
    onOpenRootPermissions: () -> Unit,
    onOpenModules: () -> Unit,
    onRequestRestart: () -> Unit,
    onConfirmRestart: () -> Unit,
    onDismissRestart: () -> Unit,
    onOpenFramework: () -> Unit,
    onDismissFrameworkError: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        fontWeight = FontWeight.Bold,
                    )
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (returnPending) {
                        Text(
                            text = if (noAnswerShown) stringResource(R.string.status_no_answer) else stringResource(R.string.status_returning),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Button(
                        onClick = onRequestReturn,
                        enabled = !returnPending,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ExitToApp,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.back),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(188.dp)
                                .clip(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)),
                        ) {
                            Image(
                                painter = painterResource(GuestArtworkView.headerDrawable(systemName)),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.68f)),
                                        ),
                                    ),
                            )
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                ExpressiveStatusPill(
                                    text = "DualOS · Galaxy A53 5G",
                                    tone = StatusTone.OK,
                                )
                                Text(
                                    text = systemName?.let { stringResource(R.string.title_in, it) }
                                        ?: stringResource(R.string.title_in_other),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color.White,
                                )
                                Text(
                                    text = stringResource(R.string.body),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.88f),
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.back_caption),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        )
                    }
                }
            }

            item {
                ExpressiveSectionHeader(title = stringResource(R.string.ways_title))
                ExpressiveGroupedCard {
                    if (supportsAddTile) {
                        ExpressiveListItem(
                            title = if (tileAdded) stringResource(R.string.tile_added) else stringResource(R.string.add_tile_title),
                            subtitle = stringResource(R.string.way_tile_summary),
                            icon = DualOsIcons.QuickTile,
                            iconTone = if (tileAdded) StatusTone.OK else StatusTone.INFO,
                            enabled = !tileAdded,
                            trailing = {
                                FilledTonalButton(
                                    onClick = onRequestTile,
                                    enabled = !tileAdded,
                                    shape = RoundedCornerShape(20.dp),
                                ) {
                                    Text(if (tileAdded) stringResource(R.string.tile_added) else stringResource(R.string.add_tile))
                                }
                            },
                        )
                        ExpressiveListDivider()
                    }
                    ExpressiveListItem(
                        title = stringResource(R.string.way_keys_title),
                        subtitle = stringResource(R.string.way_keys_summary),
                        icon = DualOsIcons.VolumeUp,
                        iconTone = StatusTone.NEUTRAL,
                    )
                }
            }

            item {
                ExpressiveSectionHeader(title = stringResource(R.string.bring_title))
                ExpressiveGroupedCard {
                    ExpressiveListItem(
                        title = stringResource(R.string.bring_entry_title),
                        subtitle = stringResource(R.string.bring_entry_summary),
                        icon = DualOsIcons.Apps,
                        iconTone = StatusTone.INFO,
                        showChevron = true,
                        onClick = onOpenBringApps,
                    )
                    ExpressiveListDivider()
                    ExpressiveListItem(
                        title = stringResource(R.string.guest_report),
                        subtitle = stringResource(R.string.checklist_card_summary),
                        icon = DualOsIcons.Checklist,
                        iconTone = StatusTone.OK,
                        showChevron = true,
                        onClick = onOpenChecklist,
                    )
                }
            }

            if (fullLogsStatus != FullLogs.Status.UNSUPPORTED) {
                item {
                    ExpressiveSectionHeader(title = stringResource(R.string.logs_title))
                    ExpressiveGroupedCard {
                        val summary = when (fullLogsStatus) {
                            FullLogs.Status.ON -> stringResource(R.string.full_logs_on)
                            FullLogs.Status.STARTING -> stringResource(R.string.full_logs_starting)
                            else -> stringResource(R.string.full_logs_off)
                        }
                        ExpressiveListItem(
                            title = stringResource(R.string.full_logs_title),
                            subtitle = summary,
                            icon = DualOsIcons.BugReport,
                            iconTone = if (fullLogsStatus == FullLogs.Status.ON) StatusTone.OK else StatusTone.WARN,
                            badge = if (fullLogsStatus == FullLogs.Status.ON) "Active" else null,
                            badgeTone = StatusTone.OK,
                            enabled = fullLogsStatus == FullLogs.Status.OFF,
                            onClick = if (fullLogsStatus == FullLogs.Status.OFF) onOpenFullLogsConfirm else null,
                        )
                    }
                }
            }

            item {
                ExpressiveSectionHeader(
                    title = "Root & Modules",
                    trailing = if (runtime.root) "Active" else "Inactive",
                    trailingTone = if (runtime.root) StatusTone.OK else StatusTone.NEUTRAL,
                )
                ExpressiveGroupedCard {
                    ExpressiveListItem(
                        title = "Root status",
                        subtitle = if (runtime.root) "Active · DualOS guest runtime" else runtime.error ?: "Inactive",
                        icon = DualOsIcons.Security,
                        iconTone = if (runtime.root) StatusTone.OK else StatusTone.NEUTRAL,
                    )
                    ExpressiveListDivider()
                    ExpressiveListItem(
                        title = stringResource(R.string.root_permissions_title),
                        subtitle = "Manage access for apps in this guest",
                        icon = DualOsIcons.Shield,
                        iconTone = StatusTone.INFO,
                        enabled = runtime.root,
                        showChevron = true,
                        onClick = onOpenRootPermissions,
                    )
                    ExpressiveListDivider()
                    ExpressiveListItem(
                        title = "Manage guest modules",
                        subtitle = "Imports and switches apply on the next guest boot",
                        icon = DualOsIcons.Extension,
                        iconTone = StatusTone.INFO,
                        enabled = runtime.root,
                        showChevron = true,
                        onClick = onOpenModules,
                    )
                    ExpressiveListDivider()
                    ExpressiveListItem(
                        title = "Restart guest",
                        subtitle = "Restart this guest and apply pending module changes",
                        icon = DualOsIcons.RestartAlt,
                        iconTone = StatusTone.WARN,
                        showChevron = true,
                        onClick = onRequestRestart,
                    )
                }
            }

            item {
                ExpressiveSectionHeader(title = "Xposed")
                ExpressiveGroupedCard {
                    ExpressiveListItem(
                        title = "Framework status",
                        subtitle = when {
                            runtime.active -> "${runtime.providerName} · Active"
                            runtime.provider != "none" -> "${runtime.providerName} · Not active"
                            else -> "Inactive"
                        },
                        icon = DualOsIcons.Layers,
                        iconTone = if (runtime.active) StatusTone.OK else StatusTone.NEUTRAL,
                    )
                    ExpressiveListDivider()
                    ExpressiveListItem(
                        title = "Open ${if (runtime.provider == "none") "framework manager" else runtime.providerName}",
                        subtitle = "Reopen the manager without its notification",
                        icon = DualOsIcons.Extension,
                        iconTone = StatusTone.INFO,
                        enabled = runtime.active,
                        showChevron = true,
                        onClick = onOpenFramework,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (confirmReturnReason != null) {
        AlertDialog(
            onDismissRequest = { onDismissReturn(confirmReturnReason) },
            title = { Text(stringResource(R.string.confirm_title)) },
            text = { Text(stringResource(R.string.confirm_message)) },
            confirmButton = {
                Button(onClick = { onConfirmReturn(confirmReturnReason) }) {
                    Text(stringResource(R.string.confirm_return))
                }
            },
            dismissButton = {
                TextButton(onClick = { onDismissReturn(confirmReturnReason) }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    if (confirmRestartOpen) {
        AlertDialog(
            onDismissRequest = onDismissRestart,
            title = { Text("Restart this guest?") },
            text = {
                Text("Guest apps will close and pending module changes will apply. One UI stays stopped while the guest restarts. If recovery fails, DualOS will return to One UI.")
            },
            confirmButton = {
                Button(onClick = onConfirmRestart) { Text("Restart guest") }
            },
            dismissButton = {
                TextButton(onClick = onDismissRestart) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }

    if (confirmFullLogsOpen) {
        AlertDialog(
            onDismissRequest = onDismissFullLogs,
            title = { Text(stringResource(R.string.full_logs_title)) },
            text = { Text(stringResource(R.string.full_logs_confirm)) },
            confirmButton = {
                Button(onClick = onConfirmFullLogs) { Text(stringResource(R.string.full_logs_start)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissFullLogs) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }

    if (frameworkError != null) {
        AlertDialog(
            onDismissRequest = onDismissFrameworkError,
            title = { Text("Framework manager") },
            text = { Text(frameworkError) },
            confirmButton = {
                Button(onClick = onDismissFrameworkError) { Text(stringResource(android.R.string.ok)) }
            },
        )
    }
}
