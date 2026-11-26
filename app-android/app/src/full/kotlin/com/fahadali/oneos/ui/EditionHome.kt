package com.fahadali.oneos.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.fahadali.oneos.BuildConfig
import com.fahadali.oneos.R
import com.fahadali.oneos.core.device.FactProbe
import com.fahadali.oneos.core.model.AndroidRelease
import com.fahadali.oneos.core.model.DeviceFacts
import com.fahadali.oneos.core.model.FixAction
import com.fahadali.oneos.core.model.GatePhase
import com.fahadali.oneos.core.model.GateReport
import com.fahadali.oneos.core.model.ImageIdentifier
import com.fahadali.oneos.core.model.ImageRef
import com.fahadali.oneos.core.model.Images
import com.fahadali.oneos.core.model.Level
import com.fahadali.oneos.core.model.PackageSets
import com.fahadali.oneos.core.model.PhoneProfile
import com.fahadali.oneos.core.model.Plain
import com.fahadali.oneos.core.model.Profiles
import com.fahadali.oneos.core.model.RootKind
import com.fahadali.oneos.core.model.ScreenLockRule
import com.fahadali.oneos.core.model.SpaceRule
import com.fahadali.oneos.core.model.SwitchGates
import com.fahadali.oneos.core.model.Units
import com.fahadali.oneos.core.root.HomeState
import com.fahadali.oneos.core.root.OldStage
import com.fahadali.oneos.core.root.OutcomeKind
import com.fahadali.oneos.core.root.RootProbe
import com.fahadali.oneos.report.ReportSender
import com.fahadali.oneos.report.UsageCheckIn
import com.fahadali.oneos.session.AppUpdater
import com.fahadali.oneos.session.LaunchJob
import com.fahadali.oneos.session.LogJob
import com.fahadali.oneos.session.OldStagesJob
import com.fahadali.oneos.session.PrepareJob
import com.fahadali.oneos.session.QuickSwitch
import com.fahadali.oneos.session.Readiness
import com.fahadali.oneos.session.RemoveJob
import com.fahadali.oneos.session.SessionPrefs
import com.fahadali.oneos.session.StageCheck
import com.fahadali.oneos.shared.DualOsIcons
import com.fahadali.oneos.shared.ExpressiveGroupedCard
import com.fahadali.oneos.shared.ExpressiveListDivider
import com.fahadali.oneos.shared.ExpressiveListItem
import com.fahadali.oneos.shared.ExpressiveSectionHeader
import com.fahadali.oneos.shared.ExpressiveStatusPill
import com.fahadali.oneos.shared.LocalDualOsStatusColors
import java.util.concurrent.Executors

/**
 * Full edition Material 3 Expressive 3-tab hub:
 * - Tab 0: Systems (ROM cards, readiness checks, A53 guest capabilities, restore/update banners)
 * - Tab 1: Settings (A53 features, session options, calls & camera, risky lock override, tools)
 * - Tab 2: Diagnostics (Last session & logs, device details, phone tests, all checks, about)
 */
internal object EditionHome {
    fun onboarding(a: MainActivity) = Intent(a, OnboardingActivity::class.java)

    fun redirect(a: MainActivity): Intent? = null

    fun started(a: MainActivity) {
        UsageCheckIn.maybeSend(
            a,
            profile = { Profiles.resolve(Build.MODEL, Build.DEVICE, Build.SOC_MODEL).profile?.id },
            systems = { listOf(SessionPrefs.homeSnapshot(a)?.imageId, SessionPrefs.lastLaunch(a)?.imageId?.takeIf { it.isNotEmpty() }) },
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Content(a: MainActivity) {
        var selectedTab by rememberSaveable { mutableIntStateOf(0) }
        var tick by remember { mutableIntStateOf(0) }
        var profile by remember { mutableStateOf<PhoneProfile?>(StageCheck.state.result?.facts?.let { Profiles.resolve(it).profile }) }
        var profileRead by remember { mutableStateOf(profile != null) }
        var pickTargetId by remember { mutableStateOf<String?>(null) }

        val filePicker = rememberLauncherForActivityResult(SystemsNav.OpenGz()) { uri ->
            if (uri != null) {
                SystemsNav.persistPick(a, uri)
                val p = profile
                val picked = SessionPrefs.picked(a)
                val good = if (p != null) FixActions.pickedImage(a, p) else null
                when {
                    p == null || picked == null -> tick++
                    good == null -> SystemsNav.showBadPick(a, p, ImageIdentifier.identify(picked.check, p), pickTargetId) { id ->
                        pickTargetId = id
                        a.startActivity(Intent(a, ImageActivity::class.java))
                    }
                    else -> {
                        if (pickTargetId != null && good.id != pickTargetId) {
                            Toast.makeText(a, a.getString(R.string.systems_pick_other, Images.systemName(good, p)), Toast.LENGTH_LONG).show()
                        }
                        if (!PrepareJob.state.busy) FixActions.run(a, FixAction.SET_UP, good)
                    }
                }
                tick++
            }
        }

        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    Readiness.onResume(a)
                    AppUpdater.check(a)
                    tick++
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            val subs = listOf(
                StageCheck.observe { if (!it.busy) Readiness.tick(a); tick++ },
                PrepareJob.observe { tick++ },
                LaunchJob.observe { tick++ },
                LogJob.observeBy({ Triple(it.phase, it.recovering, it.stopping) }) { tick++ },
                OldStagesJob.observe { SystemManage.showResult(a); tick++ },
                RemoveJob.observe { SystemManage.showResult(a); tick++ },
                AppUpdater.observe { tick++ },
            )
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                subs.forEach { it() }
            }
        }

        LaunchedEffect(Unit) {
            if (!profileRead) {
                val exec = Executors.newSingleThreadExecutor()
                exec.execute {
                    val f = runCatching { FactProbe(a.applicationContext).collect(RootProbe(a.applicationContext).signals()) }.getOrNull()
                    val p = f?.let { Profiles.resolve(it).profile }
                    a.runOnUiThread {
                        profile = p
                        profileRead = true
                        tick++
                    }
                    exec.shutdown()
                }
            }
        }

        val tabTitles = listOf(
            stringResource(R.string.app_name),
            stringResource(R.string.nav_settings),
            stringResource(R.string.nav_diagnostics),
        )

        Scaffold(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = tabTitles[selectedTab],
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            if (selectedTab == 0) {
                                Text(
                                    text = profile?.label ?: stringResource(R.string.home_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { Readiness.tick(a, force = true); tick++ }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                        }
                        IconButton(onClick = { a.startActivity(Intent(a, AboutActivity::class.java)) }) {
                            Icon(Icons.Rounded.Info, contentDescription = stringResource(R.string.about_title))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    ),
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(DualOsIcons.Layers, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_systems)) },
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_settings)) },
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(DualOsIcons.Science, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_diagnostics)) },
                    )
                }
            },
        ) { padding ->
            val listPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            )
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "hub_tab",
            ) { tab ->
                when (tab) {
                    0 -> SystemsHubTab(
                        a = a,
                        profile = profile,
                        profileRead = profileRead,
                        refreshTick = tick,
                        contentPadding = listPadding,
                        onPickFile = { id ->
                            pickTargetId = id
                            runCatching { filePicker.launch(Unit) }.onFailure {
                                Toast.makeText(a, R.string.fix_no_app, Toast.LENGTH_LONG).show()
                            }
                        },
                    )
                    1 -> SettingsTabContent(activity = a, contentPadding = listPadding)
                    else -> DiagnosticsHubTab(a = a, refreshTick = tick, contentPadding = listPadding)
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun SystemsHubTab(
        a: MainActivity,
        profile: PhoneProfile?,
        profileRead: Boolean,
        refreshTick: Int,
        contentPadding: PaddingValues,
        onPickFile: (String?) -> Unit,
    ) {
        @Suppress("UNUSED_VARIABLE") val t = refreshTick
        val check = StageCheck.state
        val prep = PrepareJob.state
        val launch = LaunchJob.state
        val logs = LogJob.state
        val old = OldStagesJob.state
        val removing = RemoveJob.state
        val result = check.result
        val facts = result?.facts?.let { FactProbe(a).refresh(it) }
        val report = if (result?.installd != null && facts != null) {
            StageCheck.gates(a, result, GatePhase.SWITCH, screenLock = facts.screenLockSet, facts = facts)
        } else null

        val jobsBusy = prep.busy || launch.busy || logs.busy || old.busy || removing.busy
        val st = result?.status
        val p = profile ?: result?.facts?.let { Profiles.resolve(it).profile }
        val available = p?.images().orEmpty()
        val rememberedImage = EnvironmentNavigation.preparedImage(a)
        val setUpId = st?.marker?.takeIf { st.ours && it.profileId == p?.id }?.imageId
            ?: result?.takeIf { it.status.partial }?.let(ImageMaintenance::identifiedImage)?.id
            ?: rememberedImage?.id?.takeIf { st?.complete != true }
        val pickedImage = p?.let { FixActions.pickedImage(a, it) }
        val statusColors = LocalDualOsStatusColors.current

        val appUpdate = AppUpdater.state.update?.takeIf { it.newerThan(BuildConfig.VERSION_CODE) }
        val needsRestore = if (st != null) st.unfinishedSession else SessionPrefs.homeSnapshot(a)?.kind == HomeState.Kind.RESTORE
        val last = SessionPrefs.lastLaunch(a)
        val showWelcome = last != null && !SessionPrefs.welcomeSeenFor(a, last.sessionId) && last.endedSince()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (appUpdate != null) {
                item {
                    val u = AppUpdater.state
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.systems_update_banner_title, appUpdate.versionName),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                text = when (u.phase) {
                                    AppUpdater.Phase.DOWNLOADING -> stringResource(R.string.home_update_downloading, u.percent)
                                    AppUpdater.Phase.INSTALLING -> stringResource(R.string.home_app_update_installing)
                                    AppUpdater.Phase.FAILED -> stringResource(R.string.systems_update_banner_failed)
                                    AppUpdater.Phase.CONFIRM -> stringResource(R.string.systems_update_banner_confirm)
                                    else -> appUpdate.notes ?: stringResource(R.string.systems_update_banner_default)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            if (!u.busy) {
                                Button(
                                    onClick = { FixActions.confirmAppUpdate(a) },
                                    shape = RoundedCornerShape(14.dp),
                                ) {
                                    Text(stringResource(R.string.systems_update_action))
                                }
                            }
                        }
                    }
                }
            }

            if (needsRestore && !logs.recovering) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = statusColors.warnContainer),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.last_restore_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = statusColors.warn,
                            )
                            Text(
                                text = stringResource(R.string.advanced_restore_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Button(
                                onClick = { SessionRestore.confirm(a) },
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Text(stringResource(R.string.last_restore_run))
                            }
                        }
                    }
                }
            }

            if (prep.busy) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = prep.image?.name?.let { stringResource(R.string.setup_mock_title, it) }
                                    ?: stringResource(R.string.setup_label),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            LinearWavyProgressIndicator(
                                progress = { ((prep.percent ?: 0) / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = prep.detail ?: "${prep.percent ?: 0}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                FilledTonalButton(
                                    onClick = { a.startActivity(Intent(a, SetupProgressActivity::class.java)) },
                                    shape = RoundedCornerShape(14.dp),
                                ) {
                                    Text(stringResource(R.string.systems_show_progress))
                                }
                            }
                        }
                    }
                }
            }

            if (showWelcome && last != null) {
                item {
                    val outcome = last.outcome?.let { o -> OutcomeKind.entries.firstOrNull { it.name == o } }
                    val systemName = Images.systemName(Images.byId(last.imageId))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.home_welcome),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = outcome?.title(systemName) ?: stringResource(R.string.home_welcome_good),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    onClick = {
                                        SessionPrefs.setWelcomeSeen(a, last.sessionId)
                                        a.startActivity(Intent(a, LastSessionActivity::class.java))
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                ) {
                                    Text(stringResource(R.string.home_row_session))
                                }
                                OutlinedButton(
                                    onClick = { SessionPrefs.setWelcomeSeen(a, last.sessionId) },
                                    shape = RoundedCornerShape(14.dp),
                                ) {
                                    Text(stringResource(android.R.string.ok))
                                }
                            }
                        }
                    }
                }
            }

            item { ExpressiveSectionHeader(stringResource(R.string.nav_systems)) }

            if (available.isEmpty()) {
                item {
                    ExpressiveGroupedCard {
                        ExpressiveListItem(
                            title = if (!profileRead) stringResource(R.string.report_reading) else stringResource(R.string.image_no_profile_title),
                            subtitle = if (!profileRead) null else stringResource(R.string.systems_none_line),
                            icon = DualOsIcons.PhoneAndroid,
                        )
                    }
                }
            } else {
                available.forEach { image ->
                    item(key = "rom_${image.id}") {
                        val isSetUp = image.id == setUpId && setUpId != null
                        val isSettingUp = prep.busy && prep.image?.id == image.id
                        val maintenance = if (isSetUp) ImageMaintenance.message(a, image, result, report) else null
                        val savedCopy = st?.oldImages?.entries?.firstOrNull { it.value == image.id }?.key
                        val isPicked = pickedImage?.id == image.id

                        RomSystemCard(
                            a = a,
                            profile = p,
                            image = image,
                            isSetUp = isSetUp,
                            isSettingUp = isSettingUp,
                            isPicked = isPicked,
                            jobsBusy = jobsBusy,
                            maintenance = maintenance,
                            savedCopy = savedCopy,
                            onPickFile = { onPickFile(image.id) },
                        )
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = { onPickFile(null) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(stringResource(R.string.hub_pick_file))
                }
            }

            if (report != null || facts != null) {
                item {
                    val tiles = buildCheckTiles(a, report, facts, check.busy)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ExpressiveSectionHeader(
                            title = stringResource(if (tiles.none { it.level == Level.BLOCK || it.level == Level.WARN }) R.string.home_checks_ready else R.string.home_checks_fix),
                        )
                        CheckTilesGrid(tiles = tiles)
                    }
                }
            }
        }
    }

    @Composable
    private fun RomSystemCard(
        a: MainActivity,
        profile: PhoneProfile?,
        image: ImageRef,
        isSetUp: Boolean,
        isSettingUp: Boolean,
        isPicked: Boolean,
        jobsBusy: Boolean,
        maintenance: com.fahadali.oneos.core.model.PlainMessage?,
        savedCopy: String?,
        onPickFile: () -> Unit,
    ) {
        var menuOpen by remember { mutableStateOf(false) }
        val statusColors = LocalDualOsStatusColors.current
        val badgeText: String
        val badgeColor: Color
        when {
            isSettingUp -> {
                badgeText = stringResource(R.string.setup_installing)
                badgeColor = MaterialTheme.colorScheme.primary
            }
            isSetUp && maintenance != null -> {
                badgeText = stringResource(R.string.catalog_update)
                badgeColor = statusColors.warn
            }
            isSetUp -> {
                badgeText = stringResource(R.string.systems_badge_ready)
                badgeColor = statusColors.ok
            }
            savedCopy != null -> {
                badgeText = stringResource(R.string.catalog_switch)
                badgeColor = MaterialTheme.colorScheme.secondary
            }
            profile?.choice(image.id)?.tested == true -> {
                badgeText = stringResource(R.string.catalog_optimised)
                badgeColor = MaterialTheme.colorScheme.primary
            }
            else -> {
                badgeText = stringResource(R.string.catalog_experimental)
                badgeColor = MaterialTheme.colorScheme.tertiary
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RomArtworkBanner(
                    image = image,
                    title = image.nameWithVariant,
                    subtitle = "Android ${AndroidRelease.name(image.sdk)} · ${Units.bytes(image.gzBytes)} · ${image.blurb}",
                    badge = badgeText,
                    badgeColor = badgeColor,
                    height = 172.dp,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val primaryText: String
                    val primaryEnabled: Boolean
                    val primaryAction: () -> Unit
                    when {
                        isSettingUp -> {
                            primaryText = stringResource(R.string.systems_show_progress)
                            primaryEnabled = true
                            primaryAction = { a.startActivity(Intent(a, SetupProgressActivity::class.java)) }
                        }
                        isSetUp && maintenance != null -> {
                            primaryText = maintenance.actionLabel ?: stringResource(R.string.catalog_update)
                            primaryEnabled = !jobsBusy && StageCheck.state.phase == StageCheck.Phase.DONE
                            primaryAction = {
                                if (maintenance.action == FixAction.SET_UP_AGAIN) {
                                    SystemsNav.askDownloaded(a, image, onPickFile)
                                } else {
                                    FixActions.run(a, maintenance.action, image)
                                }
                            }
                        }
                        isSetUp -> {
                            primaryText = stringResource(R.string.hub_open_checks)
                            primaryEnabled = true
                            primaryAction = {
                                CarouselSelection.remember(a, image)
                                a.startActivity(EnvironmentNavigation.intent(a, image))
                            }
                        }
                        savedCopy != null -> {
                            primaryText = stringResource(R.string.catalog_switch)
                            primaryEnabled = !jobsBusy && !StageCheck.state.busy
                            primaryAction = {
                                val current = StageCheck.state.result?.status?.marker?.imageId?.let(Images::byId)?.name
                                    ?: a.getString(R.string.home_other_current_unknown)
                                MaterialAlertDialogBuilder(a)
                                    .setTitle(a.getString(R.string.home_other_confirm_title, image.name))
                                    .setMessage(a.getString(R.string.home_other_confirm_message, image.name, current))
                                    .setPositiveButton(R.string.home_other_confirm_go) { _, _ ->
                                        OldStagesJob.restore(a, OldStage(savedCopy, 0, image.id, hasData = true, busy = false))
                                        if (OldStagesJob.state.restoring == savedCopy && OldStagesJob.state.busy) {
                                            a.startActivity(EnvironmentNavigation.intent(a, image))
                                        }
                                    }
                                    .setNegativeButton(android.R.string.cancel, null)
                                    .show()
                            }
                        }
                        else -> {
                            primaryText = stringResource(R.string.catalog_set_up)
                            primaryEnabled = !jobsBusy
                            primaryAction = {
                                CarouselSelection.remember(a, image)
                                if (isPicked) FixActions.run(a, FixAction.SET_UP, image)
                                else SystemsNav.askDownloaded(a, image, onPickFile)
                            }
                        }
                    }

                    Button(
                        onClick = primaryAction,
                        enabled = primaryEnabled,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(primaryText, style = MaterialTheme.typography.labelLarge)
                    }

                    if (isSetUp && maintenance != null) {
                        FilledTonalButton(
                            onClick = { a.startActivity(EnvironmentNavigation.intent(a, image)) },
                            modifier = Modifier.height(48.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Text("Checks")
                        }
                    }

                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (isSetUp && !isSettingUp) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.manage_start_fresh)) },
                                    onClick = { menuOpen = false; SystemManage.confirmStartFresh(a, image) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.manage_remove, image.name)) },
                                    onClick = { menuOpen = false; SystemManage.confirmRemove(a, image) },
                                )
                            } else if (savedCopy != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.manage_delete_saved, image.name)) },
                                    onClick = { menuOpen = false; SystemManage.confirmDeleteSaved(a, image, savedCopy) },
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.systems_download)) },
                                    onClick = { menuOpen = false; SystemsNav.open(a, image.mirrorUrl) },
                                )
                                image.downloadUrl?.let { drive ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.systems_download_drive)) },
                                        onClick = { menuOpen = false; SystemsNav.open(a, drive) },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.systems_have_file)) },
                                    onClick = { menuOpen = false; onPickFile() },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.catalog_details)) },
                                onClick = {
                                    menuOpen = false
                                    MaterialAlertDialogBuilder(a)
                                        .setTitle(image.displayName)
                                        .setMessage(image.blurb + "\n\n" + image.gzName + "\n" + Units.bytes(image.gzBytes))
                                        .setPositiveButton(android.R.string.ok, null)
                                        .show()
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun DiagnosticsHubTab(
        a: MainActivity,
        refreshTick: Int,
        contentPadding: PaddingValues,
    ) {
        @Suppress("UNUSED_VARIABLE") val t = refreshTick
        val launch = SessionPrefs.lastLaunch(a)
        val logs = LogJob.state
        val latestZip = LogJob.latestZip(a)
        val lastLine = when {
            launch == null -> stringResource(R.string.advanced_last_none)
            logs.busy && logs.stopping -> stringResource(R.string.logs_stopping)
            logs.busy && !logs.recovering -> stringResource(R.string.home_row_last_reading)
            else -> {
                val system = Images.systemName(Images.byId(launch.imageId))
                val outcome = launch.outcome?.let { o -> OutcomeKind.entries.firstOrNull { it.name == o } }
                val date = formatMediumDate(a, launch.wallMs)
                when {
                    outcome != null -> stringResource(R.string.advanced_last_outcome, date, outcome.title(system).trimEnd('.'))
                    launch.dryRun -> stringResource(R.string.advanced_last_test, date)
                    else -> stringResource(R.string.home_row_last_unread, date)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ExpressiveSectionHeader(stringResource(R.string.last_title)) }
            item {
                ExpressiveGroupedCard {
                    ExpressiveListItem(
                        title = stringResource(R.string.last_title),
                        subtitle = lastLine,
                        icon = DualOsIcons.History,
                        showChevron = true,
                        onClick = { a.startActivity(Intent(a, LastSessionActivity::class.java)) },
                    )
                    if (latestZip != null && !logs.busy) {
                        ExpressiveListDivider()
                        ExpressiveListItem(
                            title = stringResource(R.string.last_view_title),
                            subtitle = latestZip.name,
                            icon = Icons.Rounded.Share,
                            showChevron = true,
                            onClick = { a.startActivity(Intent(a, FindingsActivity::class.java).putExtra(FindingsActivity.EXTRA_ZIP, latestZip.path)) },
                        )
                        if (ReportSender.offered) {
                            ExpressiveListDivider()
                            ExpressiveListItem(
                                title = stringResource(R.string.send_title),
                                subtitle = stringResource(R.string.last_send_summary),
                                icon = DualOsIcons.OpenInNew,
                                showChevron = true,
                                onClick = { a.startActivity(SendReportActivity.intent(a, latestZip)) },
                            )
                        }
                    }
                    if (!logs.busy && SessionRestore.offered(a)) {
                        ExpressiveListDivider()
                        ExpressiveListItem(
                            title = stringResource(R.string.last_restore_title),
                            subtitle = stringResource(R.string.advanced_restore_summary),
                            icon = DualOsIcons.RestartAlt,
                            showChevron = true,
                            onClick = { SessionRestore.confirm(a) },
                        )
                    }
                }
            }

            item { ExpressiveSectionHeader(stringResource(R.string.hub_diagnostics_device)) }
            item {
                ExpressiveGroupedCard {
                    ExpressiveListItem(
                        title = stringResource(R.string.report_title),
                        subtitle = stringResource(R.string.team_phone_summary),
                        icon = DualOsIcons.PhoneAndroid,
                        showChevron = true,
                        onClick = { a.startActivity(Intent(a, DeviceReportActivity::class.java)) },
                    )
                    ExpressiveListDivider()
                    ExpressiveListItem(
                        title = stringResource(R.string.tests_title),
                        subtitle = stringResource(R.string.team_tests_summary),
                        icon = DualOsIcons.Science,
                        showChevron = true,
                        onClick = { a.startActivity(Intent(a, RunTestsActivity::class.java)) },
                    )
                    ExpressiveListDivider()
                    ExpressiveListItem(
                        title = stringResource(R.string.switch_title),
                        subtitle = stringResource(R.string.team_switch_summary),
                        icon = DualOsIcons.Power,
                        showChevron = true,
                        onClick = { a.startActivity(Intent(a, SwitchActivity::class.java)) },
                    )
                    ExpressiveListDivider()
                    ExpressiveListItem(
                        title = stringResource(R.string.team_title),
                        subtitle = stringResource(R.string.advanced_team_summary),
                        icon = Icons.Rounded.Build,
                        showChevron = true,
                        onClick = { a.startActivity(Intent(a, DebugSettingsActivity::class.java)) },
                    )
                }
            }

            item { ExpressiveSectionHeader(stringResource(R.string.hub_diagnostics_help)) }
            item {
                ExpressiveGroupedCard {
                    ExpressiveListItem(
                        title = stringResource(R.string.before_title),
                        subtitle = stringResource(R.string.before_link_summary),
                        icon = DualOsIcons.Shield,
                        showChevron = true,
                        onClick = { a.startActivity(Intent(a, BeforeYouStartActivity::class.java)) },
                    )
                    ExpressiveListDivider()
                    ExpressiveListItem(
                        title = stringResource(R.string.about_title),
                        subtitle = stringResource(R.string.advanced_about_summary),
                        icon = Icons.Rounded.Info,
                        showChevron = true,
                        onClick = { a.startActivity(Intent(a, AboutActivity::class.java)) },
                    )
                }
            }
        }
    }

    private fun buildCheckTiles(
        a: MainActivity,
        r: GateReport?,
        facts: DeviceFacts?,
        checking: Boolean,
    ): List<CheckTileUi> {
        val unknown = a.getString(R.string.home_value_unknown)
        val oldCount = StageCheck.state.result?.status?.oldStages?.size ?: 0
        fun gateTile(id: String, titleRes: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, value: String): CheckTileUi {
            val f = r?.find(id)?.finding
            val level = if (checking) Level.INFO else (f?.level ?: Level.INFO)
            val plain = f?.takeIf { it.level == Level.BLOCK || it.level == Level.WARN }?.let { if (r != null) Plain.of(it, r, facts) else null }
            val action = plain?.action?.takeIf { it != FixAction.NONE && it != FixAction.ACKNOWLEDGE }
                ?: FixAction.FREE_UP_SPACE.takeIf { id == SwitchGates.ID_SPACE && oldCount > 0 }
            val click: (() -> Unit)? = when {
                action != null -> ({ FixActions.run(a, action, r?.image) })
                f != null -> ({
                    MaterialAlertDialogBuilder(a)
                        .setTitle(f.title)
                        .setMessage(f.detail.ifEmpty { value })
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                })
                else -> null
            }
            return CheckTileUi(id, a.getString(titleRes), value, level, icon, click)
        }

        val rootVal = when {
            facts == null -> unknown
            facts.rootKind != RootKind.NONE && facts.rootKind != RootKind.OTHER -> a.getString(R.string.home_value_root_app, facts.rootKind.label)
            r?.find(SwitchGates.ID_ROOT)?.level == Level.OK -> a.getString(R.string.home_value_root_ok)
            else -> a.getString(R.string.home_value_root_missing)
        }
        val spaceVal = facts?.storage?.freeBytes?.let { free ->
            if (oldCount > 0) a.getString(R.string.home_value_space_old, Units.bytes(free), a.resources.getQuantityString(R.plurals.home_space_old_copies, oldCount, oldCount))
            else a.getString(R.string.home_value_space_free, Units.bytes(free))
        } ?: unknown
        val batVal = facts?.battery?.let { b ->
            b.temperatureC?.let { t -> a.getString(R.string.home_value_battery, b.levelPercent, t.toInt()) }
                ?: a.getString(R.string.home_value_battery_no_temp, b.levelPercent)
        } ?: unknown
        val lockVal = when (facts?.screenLockSet) {
            false -> a.getString(R.string.home_value_lock_off)
            true -> a.getString(R.string.home_value_lock_on)
            null -> unknown
        }

        return listOf(
            gateTile(SwitchGates.ID_ROOT, R.string.home_tile_root, DualOsIcons.Security, rootVal),
            gateTile(SwitchGates.ID_SPACE, R.string.home_tile_space, DualOsIcons.Storage, spaceVal),
            gateTile(SwitchGates.ID_BATTERY, R.string.home_tile_battery, DualOsIcons.Battery, batVal),
            gateTile(SwitchGates.ID_SCREEN_LOCK, R.string.home_tile_lock, Icons.Rounded.Lock, lockVal),
        )
    }
}
