package com.fahadali.oneos.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fahadali.oneos.R
import com.fahadali.oneos.core.model.FixAction
import com.fahadali.oneos.core.model.ImageRef
import com.fahadali.oneos.core.model.Images
import com.fahadali.oneos.session.LaunchJob
import com.fahadali.oneos.shared.DualOsTheme
import com.fahadali.oneos.shared.ExpressiveGroupedCard

/** Visible boot preparation in Material 3 Expressive Compose. Debug preview never starts or cancels a real launch. */
class BootProgressActivity : AppCompatActivity() {
    companion object {
        fun intent(ctx: Context, image: ImageRef, preview: Boolean = false) = Intent(ctx, BootProgressActivity::class.java)
            .putExtra("image", image.id).putExtra("preview", preview)
    }

    private lateinit var image: ImageRef
    private var preview = false
    private var cancelled by mutableStateOf(false)
    private var launchState by mutableStateOf(LaunchJob.state)
    private var unsubscribe: (() -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private var shownFailure = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val img = intent.getStringExtra("image")?.let(Images::byId) ?: LaunchJob.state.image
        if (img == null) { finish(); return }
        image = img
        preview = DebugPreview.enabled(this) && intent.getBooleanExtra("preview", false)
        setContent {
            DualOsTheme {
                Screen()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (preview) {
            handler.postDelayed({
                if (!isFinishing && !isDestroyed) AlertDialog.Builder(this)
                    .setTitle(R.string.setup_preview_complete)
                    .setMessage(R.string.setup_preview_simulated)
                    .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
                    .show()
            }, 8_000L)
        } else {
            unsubscribe = LaunchJob.observe { state ->
                launchState = state
                if (state.phase == LaunchJob.Phase.IDLE) finish()
                if (state.phase == LaunchJob.Phase.FAILED && !shownFailure) {
                    shownFailure = true
                    val plain = state.plain
                    val refusal = state.message
                    val img = state.image
                    val fix = plain?.takeIf { it.actionLabel != null && it.action != FixAction.NONE && it.action != FixAction.ACKNOWLEDGE }
                    val done = { LaunchJob.clear(); finish() }
                    val b = AlertDialog.Builder(this)
                        .setTitle(plain?.title ?: getString(R.string.setup_failed_title))
                        .setMessage(plain?.line ?: refusal)
                        .setCancelable(false)
                    if (fix != null) {
                        b.setPositiveButton(fix.actionLabel) { _, _ ->
                            LaunchJob.clear()
                            FixActions.run(this, fix.action, img, refusal, then = { finish() })
                        }
                        b.setNegativeButton(android.R.string.cancel) { _, _ -> done() }
                    } else {
                        b.setPositiveButton(android.R.string.ok) { _, _ -> done() }
                    }
                    b.show()
                }
            }
        }
    }

    override fun onStop() {
        handler.removeCallbacksAndMessages(null)
        unsubscribe?.invoke()
        unsubscribe = null
        super.onStop()
    }

    private fun cancelBoot() {
        if (preview) {
            handler.removeCallbacksAndMessages(null)
            finish()
        } else if (LaunchJob.cancel()) {
            cancelled = true
        } else if (!cancelled) {
            Toast.makeText(this, R.string.boot_cannot_cancel_line, Toast.LENGTH_LONG).show()
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun Screen() {
        val state = launchState
        BackHandler(enabled = !preview) { cancelBoot() }

        DualOsScaffold(
            title = image.name,
            subtitle = if (preview) stringResource(R.string.environment_preview) else null,
            onBack = { cancelBoot() },
            largeTopBar = false,
            bottomBar = {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        val canCancel = preview || (state.cancellable && !cancelled)
                        val label = when {
                            cancelled -> stringResource(R.string.environment_boot_cancelling)
                            !preview && !state.cancellable -> stringResource(R.string.boot_cannot_cancel)
                            else -> stringResource(android.R.string.cancel)
                        }
                        OutlinedButton(
                            onClick = { cancelBoot() },
                            enabled = canCancel,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Text(label)
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
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    RomArtworkBanner(
                        image = image,
                        title = image.name,
                        height = 180.dp,
                    )
                }
                item {
                    ExpressiveGroupedCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            LoadingIndicator()
                            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(
                                text = state.detail ?: stringResource(R.string.splash_shutdown),
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                            )
                            if (!state.apps.isNullOrEmpty()) {
                                Text(
                                    text = state.apps!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center,
                                )
                            }
                            Text(
                                text = stringResource(R.string.switch_go_dialog_back_app, image.name),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}
