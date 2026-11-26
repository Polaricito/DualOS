package com.fahadali.oneos.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fahadali.oneos.R
import com.fahadali.oneos.core.device.FactProbe
import com.fahadali.oneos.core.model.ImageIdentifier
import com.fahadali.oneos.core.model.ImageIdentity
import com.fahadali.oneos.core.model.ImageState
import com.fahadali.oneos.core.model.Images
import com.fahadali.oneos.core.model.Level
import com.fahadali.oneos.core.model.PhoneProfile
import com.fahadali.oneos.core.model.Plain
import com.fahadali.oneos.core.model.Profiles
import com.fahadali.oneos.core.model.Units
import com.fahadali.oneos.core.root.RootProbe
import com.fahadali.oneos.core.root.RootScript
import com.fahadali.oneos.session.ImageCheckJob
import com.fahadali.oneos.session.SessionPrefs
import com.fahadali.oneos.session.StageCheck
import com.fahadali.oneos.shared.DualOsIcons
import com.fahadali.oneos.shared.DualOsTheme
import com.fahadali.oneos.shared.ExpressiveGroupedCard
import com.fahadali.oneos.shared.ExpressiveListDivider
import com.fahadali.oneos.shared.ExpressiveListItem
import com.fahadali.oneos.shared.ExpressiveLoadingBox
import com.fahadali.oneos.shared.ExpressiveSectionHeader
import java.util.concurrent.Executors

/** Use a downloaded file: pick .img.gz, verify SHA-256, or reuse staged system.raw. */
class ImageActivity : AppCompatActivity() {
    private val team: Boolean get() = intent.getBooleanExtra(EXTRA_TEAM, false)
    private lateinit var pick: ActivityResultLauncher<Unit>
    private val io = Executors.newSingleThreadExecutor()
    private var unsubscribe: (() -> Unit)? = null
    private var unsubscribeCheck: (() -> Unit)? = null
    private var profile: PhoneProfile? by mutableStateOf(null)
    private var profileRead by mutableStateOf(false)
    private var detailsOpen by mutableStateOf(false)
    private var tick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        pick = registerForActivityResult(SystemsNav.OpenGz()) { uri -> if (uri != null) picked(uri) }
        super.onCreate(savedInstanceState)
        detailsOpen = team
        val app = applicationContext
        io.execute {
            val f = runCatching { FactProbe(app).collect(RootProbe(app).signals()) }.getOrNull()
            runOnUiThread {
                if (!isDestroyed) {
                    profile = f?.let { Profiles.resolve(it).profile }
                    profileRead = true
                    tick++
                }
            }
        }
        setContent {
            DualOsTheme {
                Screen()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        unsubscribe = ImageCheckJob.observe { tick++ }
        unsubscribeCheck = StageCheck.observe { tick++ }
    }

    override fun onStop() {
        unsubscribe?.invoke()
        unsubscribeCheck?.invoke()
        unsubscribe = null
        unsubscribeCheck = null
        super.onStop()
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }

    private fun picked(uri: Uri) {
        SystemsNav.persistPick(this, uri)
        tick++
    }

    @Composable
    private fun Screen() {
        @Suppress("UNUSED_VARIABLE") val t = tick
        DualOsScaffold(
            title = stringResource(if (team) R.string.image_title_team else R.string.image_title),
            onBack = { finish() },
        ) { padding ->
            if (!profileRead) {
                ExpressiveLoadingBox(
                    label = stringResource(R.string.report_reading),
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
                return@DualOsScaffold
            }
            val p = profile
            val images = p?.images().orEmpty()
            if (p == null || images.isEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 8.dp, 16.dp, 24.dp),
                ) {
                    item {
                        ExpressiveGroupedCard {
                            ExpressiveListItem(
                                title = stringResource(R.string.image_no_profile_title),
                                subtitle = stringResource(R.string.image_no_profile_summary),
                                icon = levelIcon(Level.BLOCK),
                                iconTint = levelColor(Level.BLOCK),
                            )
                        }
                    }
                }
                return@DualOsScaffold
            }

            val source = SessionPrefs.source(this)
            val picked = SessionPrefs.picked(this)
            val job = ImageCheckJob.state
            val check = StageCheck.state
            val st = check.result?.status
            val pickedId = picked?.let { ImageIdentifier.identify(it.check, p) }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { ExpressiveSectionHeader(stringResource(R.string.image_cat_file)) }
                item {
                    ExpressiveGroupedCard {
                        ExpressiveListItem(
                            title = stringResource(R.string.image_pick_title),
                            subtitle = stringResource(R.string.image_pick_summary),
                            icon = DualOsIcons.FolderOpen,
                            showChevron = true,
                            onClick = { pick.launch(Unit) },
                        )
                        if (picked != null && pickedId != null) {
                            val size = picked.bytes?.let { Units.bytes(it) } ?: stringResource(R.string.image_size_unknown)
                            ExpressiveListDivider()
                            ExpressiveListItem(
                                title = picked.name ?: stringResource(R.string.image_picked_unnamed),
                                subtitle = if (source == SessionPrefs.Source.GZ) size else stringResource(R.string.image_not_used, size),
                                icon = DualOsIcons.Save,
                            )
                            ExpressiveListDivider()
                            IdentityRow(pickedId, p, onPhone = false)
                            val busy = job.busy && job.uri == picked.uri
                            if (busy) {
                                val total = (job.total ?: pickedId.image?.gzBytes ?: picked.bytes ?: 0L).coerceAtLeast(1)
                                ExpressiveListDivider()
                                ExpressiveListItem(
                                    title = stringResource(R.string.image_checking, (job.read * 100 / total).toInt()),
                                    subtitle = stringResource(R.string.image_checking_bytes, Units.bytes(job.read), Units.bytes(total)),
                                    icon = Icons.Rounded.Refresh,
                                )
                                ExpressiveListDivider()
                                ExpressiveListItem(
                                    title = stringResource(R.string.tests_stop_title),
                                    subtitle = stringResource(R.string.image_stop_summary),
                                    icon = Icons.Rounded.Close,
                                    onClick = { ImageCheckJob.cancel() },
                                )
                            } else if (pickedId.state == ImageState.NOT_CHECKED || pickedId.state == ImageState.DAMAGED) {
                                ExpressiveListDivider()
                                ExpressiveListItem(
                                    title = stringResource(R.string.image_check_title),
                                    subtitle = stringResource(R.string.image_check_summary),
                                    icon = DualOsIcons.Security,
                                    showChevron = true,
                                    onClick = { ImageCheckJob.start(this@ImageActivity, picked.uri, picked.bytes) },
                                )
                            } else if (pickedId.state == ImageState.VERIFIED && source != SessionPrefs.Source.GZ) {
                                ExpressiveListDivider()
                                ExpressiveListItem(
                                    title = stringResource(R.string.image_use_gz_title),
                                    subtitle = stringResource(R.string.image_use_gz_summary),
                                    icon = Icons.Rounded.CheckCircle,
                                    showChevron = true,
                                    onClick = { SessionPrefs.setSource(this@ImageActivity, SessionPrefs.Source.GZ); tick++ },
                                )
                            }
                            ExpressiveListDivider()
                            ExpressiveListItem(
                                title = stringResource(R.string.image_forget_title),
                                subtitle = stringResource(R.string.image_forget_summary),
                                icon = Icons.Rounded.Delete,
                                iconTint = MaterialTheme.colorScheme.error,
                                onClick = { SessionPrefs.clearPicked(this@ImageActivity); tick++ },
                            )
                        }
                    }
                }

                item { ExpressiveSectionHeader(stringResource(R.string.image_cat_on_phone)) }
                item {
                    ExpressiveGroupedCard {
                        when {
                            check.busy -> ExpressiveListItem(title = stringResource(R.string.image_looking), icon = Icons.Rounded.Refresh)
                            st == null -> ExpressiveListItem(
                                title = stringResource(R.string.image_look_title),
                                subtitle = stringResource(if (check.message != null) R.string.image_look_failed else R.string.image_look_summary),
                                icon = Icons.Rounded.Search,
                                showChevron = true,
                                onClick = { confirmLook() },
                            )
                            st.rawSize == null -> ExpressiveListItem(title = stringResource(R.string.image_on_phone_none), icon = levelIcon(Level.INFO), iconTint = levelColor(Level.INFO))
                            else -> {
                                val sid = ImageIdentifier.identifyStaged(st.stagedRaw, p)
                                IdentityRow(sid, p, onPhone = true)
                                val keepable = RootScript.BeginMode.KEEP in st.beginModes(sid.image?.id)
                                ExpressiveListDivider()
                                when {
                                    sid.finding.level == Level.OK && !keepable ->
                                        ExpressiveListItem(title = stringResource(R.string.image_on_phone_aside), subtitle = stringResource(R.string.image_on_phone_aside_summary))
                                    sid.finding.level == Level.OK && source == SessionPrefs.Source.ON_PHONE ->
                                        ExpressiveListItem(title = stringResource(R.string.image_on_phone_chosen), subtitle = stringResource(R.string.image_on_phone_chosen_summary), icon = levelIcon(Level.OK), iconTint = levelColor(Level.OK))
                                    sid.finding.level == Level.OK ->
                                        ExpressiveListItem(
                                            title = stringResource(R.string.image_use_on_phone_title),
                                            subtitle = stringResource(R.string.image_use_on_phone_summary),
                                            icon = Icons.Rounded.CheckCircle,
                                            showChevron = true,
                                            onClick = { SessionPrefs.setSource(this@ImageActivity, SessionPrefs.Source.ON_PHONE); tick++ },
                                        )
                                    else -> ExpressiveListItem(title = stringResource(R.string.image_on_phone_replaced))
                                }
                            }
                        }
                        if (!check.busy && st != null) {
                            ExpressiveListDivider()
                            ExpressiveListItem(title = stringResource(R.string.image_look_again), icon = Icons.Rounded.Refresh, showChevron = true, onClick = { confirmLook() })
                        }
                    }
                }

                item { ExpressiveSectionHeader(stringResource(R.string.image_takes_title, p.label)) }
                item {
                    ExpressiveGroupedCard {
                        Text(
                            text = stringResource(R.string.image_takes_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                        images.forEach { image ->
                            ExpressiveListDivider()
                            val notes = buildList {
                                add(getString(R.string.image_choice_sizes, Units.bytes(image.gzBytes), Units.bytes(image.rawBytes)))
                                add(getString(if (p.choice(image.id)?.tested == true) R.string.systems_chip_tested else R.string.systems_chip_untested))
                                if (image.googleServices) add(getString(R.string.systems_chip_google))
                            }
                            ExpressiveListItem(title = Images.systemName(image, p), subtitle = notes.joinToString(" · "), icon = DualOsIcons.Layers)
                        }
                    }
                }

                item {
                    ExpressiveGroupedCard {
                        ExpressiveListItem(
                            title = stringResource(R.string.details_title),
                            subtitle = stringResource(R.string.image_details_summary),
                            icon = if (detailsOpen) DualOsIcons.ArrowDropUp else DualOsIcons.ArrowDropDown,
                            onClick = { detailsOpen = !detailsOpen },
                        )
                        if (detailsOpen) {
                            images.forEach { image ->
                                ExpressiveListDivider()
                                ExpressiveListItem(
                                    title = stringResource(R.string.image_choice_title, image.shortName),
                                    subtitle = stringResource(R.string.image_details_file, image.gzName, Units.bytes(image.gzBytes), Units.bytes(image.rawBytes), image.label),
                                )
                            }
                            pickedId?.let {
                                ExpressiveListDivider()
                                ExpressiveListItem(title = it.finding.title, subtitle = it.finding.detail, icon = levelIcon(it.finding.level), iconTint = levelColor(it.finding.level))
                            }
                            picked?.sha256?.let {
                                ExpressiveListDivider()
                                ExpressiveListItem(title = stringResource(R.string.image_details_sha), subtitle = it)
                            }
                            job.message?.takeIf { job.uri == picked?.uri }?.let {
                                ExpressiveListDivider()
                                ExpressiveListItem(title = stringResource(R.string.setup_details_result), subtitle = it)
                            }
                            if (st?.rawSize != null) {
                                val sf = ImageIdentifier.identifyStaged(st.stagedRaw, p).finding
                                ExpressiveListDivider()
                                ExpressiveListItem(title = sf.title, subtitle = sf.detail, icon = levelIcon(sf.level), iconTint = levelColor(sf.level))
                            }
                            (check.step ?: check.message)?.let {
                                ExpressiveListDivider()
                                ExpressiveListItem(title = stringResource(R.string.image_details_look), subtitle = it)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun IdentityRow(id: ImageIdentity, p: PhoneProfile, onPhone: Boolean) {
        val f = id.finding
        if (f.level == Level.OK) {
            val system = Images.systemName(id.image, p)
            ExpressiveListItem(
                title = stringResource(if (onPhone) R.string.image_on_phone_ok else R.string.image_file_ok, system),
                icon = levelIcon(Level.OK),
                iconTint = levelColor(Level.OK),
            )
            return
        }
        val m = Plain.of(f, id.image, p)
        val lv = if (f.reason == ImageIdentifier.R_NOT_CHECKED) Level.INFO else f.level
        ExpressiveListItem(title = m.title, subtitle = m.line, icon = levelIcon(lv), iconTint = levelColor(lv))
    }

    private fun confirmLook() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.image_look_dialog_title)
            .setMessage(R.string.image_look_dialog_message)
            .setPositiveButton(R.string.image_look_run) { _, _ -> StageCheck.start(this, StageCheck.Mode.STAGE_ONLY) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        const val EXTRA_TEAM = "com.fahadali.oneos.extra.TEAM"
    }
}
