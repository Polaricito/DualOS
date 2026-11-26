package com.fahadali.oneos.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.fahadali.oneos.R
import com.fahadali.oneos.core.device.FactProbe
import com.fahadali.oneos.core.model.AndroidRelease
import com.fahadali.oneos.core.model.FixAction
import com.fahadali.oneos.core.model.ImageIdentifier
import com.fahadali.oneos.core.model.ImageIdentity
import com.fahadali.oneos.core.model.ImageRef
import com.fahadali.oneos.core.model.Images
import com.fahadali.oneos.core.model.PhoneProfile
import com.fahadali.oneos.core.model.Plain
import com.fahadali.oneos.core.model.Profiles
import com.fahadali.oneos.core.model.Units
import com.fahadali.oneos.core.root.OldStage
import com.fahadali.oneos.core.root.RootProbe
import com.fahadali.oneos.session.AppUpdater
import com.fahadali.oneos.session.LaunchJob
import com.fahadali.oneos.session.LogJob
import com.fahadali.oneos.session.OldStagesJob
import com.fahadali.oneos.session.PrepareJob
import com.fahadali.oneos.session.Readiness
import com.fahadali.oneos.session.RemoveJob
import com.fahadali.oneos.session.SessionPrefs
import com.fahadali.oneos.session.StageCheck
import com.fahadali.oneos.shared.DualOsIcons
import com.fahadali.oneos.shared.DualOsTheme
import com.fahadali.oneos.shared.ExpressiveGroupedCard
import com.fahadali.oneos.shared.ExpressiveListItem
import java.util.concurrent.Executors

/** Standalone Choose-a-system screen in Material 3 Expressive Compose. */
open class SystemsActivity : AppCompatActivity() {
    open val isRootLauncher: Boolean get() = false

    private lateinit var pick: ActivityResultLauncher<Unit>
    private var pickFor: String? = null
    private val io = Executors.newSingleThreadExecutor()
    private val subs = mutableListOf<() -> Unit>()
    private var profile: PhoneProfile? by mutableStateOf(null)
    private var profileRead: Boolean by mutableStateOf(false)
    private var tick: Int by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        pick = registerForActivityResult(SystemsNav.OpenGz()) { uri -> if (uri != null) picked(uri) }
        pickFor = savedInstanceState?.getString(KEY_PICK_FOR)
        super.onCreate(savedInstanceState)

        StageCheck.state.result?.facts?.let { f -> Profiles.resolve(f).profile }?.let {
            profile = it
            profileRead = true
        }
        if (!profileRead) {
            val app = applicationContext
            io.execute {
                val f = runCatching { FactProbe(app).collect(RootProbe(app).signals()) }.getOrNull()
                val p = f?.let { Profiles.resolve(it).profile }
                runOnUiThread {
                    if (!isDestroyed) {
                        profile = p
                        profileRead = true
                        tick++
                    }
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
        subs += StageCheck.observe { if (!it.busy) Readiness.tick(this); tick++ }
        subs += AppUpdater.observe { tick++ }
        subs += PrepareJob.observe { tick++ }
        subs += OldStagesJob.observe { SystemManage.showResult(this); tick++ }
        subs += RemoveJob.observe { SystemManage.showResult(this); tick++ }
    }

    override fun onResume() {
        super.onResume()
        Readiness.onResume(this)
        AppUpdater.check(this)
        tick++
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pickFor?.let { outState.putString(KEY_PICK_FOR, it) }
    }

    fun choose(imageId: String?) {
        pickFor = imageId
        try {
            pick.launch(Unit)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.fix_no_app, Toast.LENGTH_LONG).show()
        }
    }

    private fun picked(uri: Uri) {
        SystemsNav.persistPick(this, uri)
        val p = profile
        val file = SessionPrefs.picked(this)
        if (p == null || file == null) {
            tick++
            return
        }
        val good = FixActions.pickedImage(this, p)
        if (good == null) {
            tick++
            SystemsNav.showBadPick(this, p, ImageIdentifier.identify(file.check, p), pickFor) { choose(it) }
            return
        }
        if (pickFor != null && good.id != pickFor) {
            Toast.makeText(this, getString(R.string.systems_pick_other, Images.systemName(good, p)), Toast.LENGTH_LONG).show()
        }
        val st = StageCheck.state.result?.status
        when {
            PrepareJob.state.busy -> tick++
            st?.ours == true -> {
                tick++
                FixActions.run(this, FixAction.SET_UP, good)
            }
            else -> SystemsNav.home(this, good)
        }
    }

    @Composable
    private fun Screen() {
        @Suppress("UNUSED_VARIABLE") val t = tick
        val p = profile
        val check = StageCheck.state
        val prep = PrepareJob.state
        val jobsBusy = prep.busy || LaunchJob.state.busy || LogJob.state.busy || OldStagesJob.state.busy || RemoveJob.state.busy
        val st = check.result?.status
        val available = p?.images().orEmpty()
        val rememberedImage = EnvironmentNavigation.preparedImage(this)
        val setUpId = st?.marker?.takeIf { st.ours && it.profileId == p?.id }?.imageId
            ?: check.result?.takeIf { it.status.partial }?.let(ImageMaintenance::identifiedImage)?.id
            ?: rememberedImage?.id?.takeIf { st?.complete != true }
        val pickedId = p?.let { FixActions.pickedImage(this, it)?.id }

        DualOsScaffold(
            title = stringResource(R.string.systems_title),
            onBack = if (isRootLauncher) null else ({ finish() }),
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
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
                        item(key = image.id) {
                            val setUp = image.id == setUpId && setUpId != null
                            val settingUp = prep.busy && prep.image?.id == image.id
                            val maintenance = if (setUp) ImageMaintenance.message(this@SystemsActivity, image, check.result) else null
                            val savedCopy = st?.oldImages?.entries?.firstOrNull { it.value == image.id }?.key
                            val ready = pickedId == image.id

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(28.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    RomArtworkBanner(
                                        image = image,
                                        title = image.nameWithVariant,
                                        subtitle = "Android ${AndroidRelease.name(image.sdk)} · ${Units.bytes(image.gzBytes)}",
                                        badge = stringResource(
                                            when {
                                                setUp -> R.string.systems_badge_ready
                                                p?.choice(image.id)?.tested == true -> R.string.catalog_optimised
                                                else -> R.string.catalog_experimental
                                            },
                                        ),
                                        height = 160.dp,
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        when {
                                            settingUp -> Button(
                                                onClick = { SystemsNav.progress(this@SystemsActivity) },
                                                modifier = Modifier.weight(1f).height(48.dp),
                                                shape = RoundedCornerShape(16.dp),
                                            ) { Text(stringResource(R.string.systems_show_progress)) }
                                            setUp && maintenance != null -> Button(
                                                onClick = {
                                                    if (maintenance.action == FixAction.SET_UP_AGAIN) {
                                                        SystemsNav.askDownloaded(this@SystemsActivity, image) { choose(image.id) }
                                                    } else {
                                                        FixActions.run(this@SystemsActivity, maintenance.action, image)
                                                    }
                                                },
                                                enabled = !jobsBusy && StageCheck.state.phase == StageCheck.Phase.DONE,
                                                modifier = Modifier.weight(1f).height(48.dp),
                                                shape = RoundedCornerShape(16.dp),
                                            ) { Text(maintenance.actionLabel ?: stringResource(R.string.catalog_update)) }
                                            setUp -> Button(
                                                onClick = {
                                                    CarouselSelection.remember(this@SystemsActivity, image)
                                                    startActivity(EnvironmentNavigation.intent(this@SystemsActivity, image))
                                                },
                                                modifier = Modifier.weight(1f).height(48.dp),
                                                shape = RoundedCornerShape(16.dp),
                                            ) { Text(stringResource(R.string.hub_open_checks)) }
                                            savedCopy != null -> Button(
                                                onClick = {
                                                    val current = StageCheck.state.result?.status?.marker?.imageId?.let(Images::byId)?.name
                                                        ?: getString(R.string.home_other_current_unknown)
                                                    MaterialAlertDialogBuilder(this@SystemsActivity)
                                                        .setTitle(getString(R.string.home_other_confirm_title, image.name))
                                                        .setMessage(getString(R.string.home_other_confirm_message, image.name, current))
                                                        .setPositiveButton(R.string.home_other_confirm_go) { _, _ ->
                                                            OldStagesJob.restore(this@SystemsActivity, OldStage(savedCopy, 0, image.id, hasData = true, busy = false))
                                                            if (OldStagesJob.state.restoring == savedCopy && OldStagesJob.state.busy) {
                                                                startActivity(EnvironmentNavigation.intent(this@SystemsActivity, image))
                                                            }
                                                        }
                                                        .setNegativeButton(android.R.string.cancel, null)
                                                        .show()
                                                },
                                                enabled = !jobsBusy && !StageCheck.state.busy,
                                                modifier = Modifier.weight(1f).height(48.dp),
                                                shape = RoundedCornerShape(16.dp),
                                            ) { Text(stringResource(R.string.catalog_switch)) }
                                            else -> {
                                                Button(
                                                    onClick = {
                                                        CarouselSelection.remember(this@SystemsActivity, image)
                                                        if (ready) FixActions.run(this@SystemsActivity, FixAction.SET_UP, image)
                                                        else SystemsNav.askDownloaded(this@SystemsActivity, image) { choose(image.id) }
                                                    },
                                                    enabled = !jobsBusy,
                                                    modifier = Modifier.weight(1f).height(48.dp),
                                                    shape = RoundedCornerShape(16.dp),
                                                ) { Text(stringResource(R.string.catalog_set_up)) }
                                                FilledTonalButton(
                                                    onClick = { choose(image.id) },
                                                    modifier = Modifier.height(48.dp),
                                                    shape = RoundedCornerShape(16.dp),
                                                ) { Text(stringResource(R.string.systems_have_file)) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    OutlinedButton(
                        onClick = { choose(null) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(stringResource(R.string.hub_pick_file))
                    }
                }
            }
        }
    }

    private companion object {
        const val KEY_PICK_FOR = "pick_for"
    }
}

/** Navigation and picker helpers shared by Systems and Home. */
internal object SystemsNav {
    fun home(a: Activity, image: ImageRef? = null) {
        a.startActivity(
            Intent(a, MainActivity::class.java)
                .putExtra("system_detail", true)
                .putExtra("system_name", image?.name)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        a.finish()
    }

    fun progress(a: Activity) {
        a.startActivity(Intent(a, SetupProgressActivity::class.java))
    }

    fun open(a: Activity, url: String) {
        try {
            a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(a, R.string.fix_no_app, Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(a, R.string.fix_no_app, Toast.LENGTH_LONG).show()
        }
    }

    fun persistPick(a: Activity, uri: Uri) {
        runCatching { a.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        var name: String? = null
        var size: Long? = null
        runCatching {
            a.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    name = c.getString(0)
                    size = if (c.isNull(1)) null else c.getLong(1)
                }
            }
        }
        SessionPrefs.setPicked(a, uri, name, size)
        SessionPrefs.setSource(a, SessionPrefs.Source.GZ)
    }

    fun showBadPick(a: Activity, p: PhoneProfile, id: ImageIdentity, cardId: String?, onRepick: (String?) -> Unit) {
        val card = cardId?.let { cid -> p.images().firstOrNull { it.id == cid } }
        val b = MaterialAlertDialogBuilder(a)
        if (id.finding.reason == ImageIdentifier.R_SIZE_UNKNOWN) {
            b.setTitle(R.string.systems_size_unknown_title)
                .setMessage(R.string.systems_size_unknown_line)
                .setPositiveButton(R.string.systems_open_file_page) { _, _ -> a.startActivity(Intent(a, ImageActivity::class.java)) }
        } else {
            val plain = Plain.of(id.finding, id.image ?: card, p)
            b.setTitle(plain.title).setMessage(plain.line)
            val label = plain.actionLabel
            if (label != null) {
                when (plain.action) {
                    FixAction.CHOOSE_FILE -> b.setPositiveButton(label) { _, _ -> onRepick(cardId) }
                    FixAction.DOWNLOAD_AGAIN -> (id.image ?: card)?.mirrorUrl?.let { url ->
                        b.setPositiveButton(label) { _, _ -> open(a, url) }
                    }
                    else -> Unit
                }
            }
        }
        b.setNegativeButton(R.string.systems_close, null).show()
    }

    fun askDownloaded(a: FragmentActivity, image: ImageRef, onHaveFile: () -> Unit) {
        MaterialAlertDialogBuilder(a)
            .setTitle(a.getString(R.string.setup_mock_title, image.name))
            .setMessage(R.string.setup_download_question)
            .setPositiveButton(R.string.setup_download_yes) { _, _ ->
                if (DebugPreview.enabled(a)) {
                    if (PrepareJob.state.busy || LaunchJob.state.busy || StageCheck.state.busy || LogJob.state.busy || OldStagesJob.state.busy || RemoveJob.state.busy) {
                        Toast.makeText(a, R.string.setup_preview_busy, Toast.LENGTH_LONG).show()
                    } else {
                        a.startActivity(Intent(a, SetupProgressActivity::class.java).putExtra(SetupProgressActivity.EXTRA_PREVIEW_IMAGE, image.id))
                    }
                } else {
                    onHaveFile()
                }
            }
            .setNegativeButton(R.string.setup_download_no) { _, _ -> open(a, image.mirrorUrl) }
            .setNeutralButton(android.R.string.cancel, null)
            .show()
    }

    class OpenGz : ActivityResultContract<Unit, Uri?>() {
        override fun createIntent(context: Context, input: Unit): Intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/gzip", "application/x-gzip", "application/octet-stream", "*/*"))

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? = if (resultCode == Activity.RESULT_OK) intent?.data else null
    }
}
