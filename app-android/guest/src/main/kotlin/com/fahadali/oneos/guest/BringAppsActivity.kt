package com.fahadali.oneos.guest

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import com.fahadali.oneos.shared.DualOsIcons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fahadali.oneos.guest.BringCatalogFile.Progress
import com.fahadali.oneos.shared.DualOsTheme
import com.fahadali.oneos.shared.ExpressiveGroupedCard
import com.fahadali.oneos.shared.ExpressiveListDivider
import com.fahadali.oneos.shared.ExpressiveListItem
import com.fahadali.oneos.shared.ExpressiveLoadingBox
import com.fahadali.oneos.shared.ExpressiveSectionHeader
import java.io.File

/** Bring apps from One UI inside the guest (Compose + Material 3 Expressive). */
class BringAppsActivity : ComponentActivity() {
    private data class AppEntry(val item: BringCatalogFile.Item, val icon: Bitmap?)

    private val handler = Handler(Looper.getMainLooper())
    private var apps by mutableStateOf<List<AppEntry>>(emptyList())
    private var loaded by mutableStateOf(false)
    private var missing by mutableStateOf(false)
    private var tick by mutableIntStateOf(0)
    private val asked = LinkedHashMap<String, Long>()
    private var shown = ""

    private val poll = object : Runnable {
        override fun run() {
            if (asked.isNotEmpty() && snapshot() != shown) {
                shown = snapshot()
                tick++
            }
            handler.postDelayed(this, 2000)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val currentTick = tick
            DualOsTheme {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.surface,
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.bring_title), fontWeight = FontWeight.SemiBold) },
                            navigationIcon = {
                                IconButton(onClick = { onBackPressedDispatcher.onBackPressed() }) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                        )
                    },
                ) { padding ->
                    val writable = requestsWritable()
                    val visibleApps = apps.filter { !installed(it.item.pkg) || it.item.pkg in asked }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        when {
                            !loaded -> item { ExpressiveLoadingBox(label = stringResource(R.string.bring_reading)) }
                            missing -> item {
                                ExpressiveGroupedCard {
                                    ExpressiveListItem(
                                        title = stringResource(R.string.bring_no_catalog),
                                        icon = DualOsIcons.Apps,
                                    )
                                }
                            }
                            visibleApps.isEmpty() -> item {
                                ExpressiveGroupedCard {
                                    ExpressiveListItem(
                                        title = stringResource(R.string.bring_none),
                                        icon = DualOsIcons.Apps,
                                    )
                                }
                            }
                            else -> {
                                item {
                                    ExpressiveSectionHeader(title = stringResource(R.string.bring_intro))
                                }
                                if (!writable) {
                                    item {
                                        ExpressiveGroupedCard {
                                            ExpressiveListItem(title = stringResource(R.string.bring_not_writable))
                                        }
                                    }
                                }
                                item {
                                    // Reference currentTick so polling updates status text smoothly.
                                    @Suppress("UNUSED_EXPRESSION")
                                    currentTick
                                    ExpressiveGroupedCard {
                                        visibleApps.forEachIndexed { idx, app ->
                                            if (idx > 0) ExpressiveListDivider()
                                            val pkg = app.item.pkg
                                            val p = progress(pkg)
                                            val ready = p is Progress.Done || (p != null && installed(pkg) && p !is Progress.Failed)
                                            val summary = when {
                                                p == null -> pkg
                                                ready -> stringResource(R.string.bring_ready)
                                                p is Progress.Failed -> stringResource(R.string.bring_failed_tap, p.why)
                                                p is Progress.Installing -> stringResource(R.string.bring_installing)
                                                p is Progress.Copying -> stringResource(R.string.bring_copying)
                                                else -> stringResource(R.string.bring_sent)
                                            }
                                            val idle = p == null || p is Progress.Failed
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .then(
                                                        if (idle && writable) Modifier.clickable { request(pkg) }
                                                        else Modifier,
                                                    )
                                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                if (app.icon != null) {
                                                    Image(
                                                        bitmap = app.icon.asImageBitmap(),
                                                        contentDescription = null,
                                                        modifier = Modifier
                                                            .size(40.dp)
                                                            .clip(RoundedCornerShape(12.dp)),
                                                    )
                                                    Spacer(modifier = Modifier.width(14.dp))
                                                }
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(app.item.label, style = MaterialTheme.typography.titleMedium)
                                                    Text(
                                                        summary,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                                if (ready) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    FilledTonalButton(
                                                        onClick = {
                                                            packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
                                                        },
                                                        shape = RoundedCornerShape(20.dp),
                                                    ) {
                                                        Text(stringResource(R.string.bring_open))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        load()
        handler.postDelayed(poll, 2000)
    }

    override fun onPause() {
        handler.removeCallbacks(poll)
        super.onPause()
    }

    private fun load() {
        Thread {
            val file = File(BringCatalogFile.CATALOG)
            val text = runCatching { if (file.isFile) file.readText() else null }.getOrNull()
            val list = text?.let { BringCatalogFile.parse(it) }.orEmpty().map { item ->
                AppEntry(item, item.png.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() })
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                apps = list
                missing = text == null
                loaded = true
                shown = snapshot()
                tick++
            }
        }.start()
    }

    private fun installed(pkg: String): Boolean =
        try {
            packageManager.getApplicationInfo(pkg, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    private fun requestsWritable(): Boolean = File(BringCatalogFile.REQUESTS).let { it.isDirectory && it.canWrite() }

    private fun progress(pkg: String): Progress? {
        val at = asked[pkg] ?: return null
        val f = File(BringCatalogFile.REQUESTS, "$pkg.status")
        val line = runCatching { if (f.isFile && f.lastModified() >= at - 2000) f.useLines { it.firstOrNull() } else null }.getOrNull()
        return BringCatalogFile.progress(line)
    }

    private fun snapshot(): String = asked.keys.joinToString(",") { pkg ->
        pkg + "=" + when (val p = progress(pkg)) {
            null -> ""
            is Progress.Failed -> "failed:" + p.why
            else -> p.javaClass.simpleName
        } + (if (installed(pkg)) "+i" else "")
    }

    private fun request(pkg: String) {
        val dir = File(BringCatalogFile.REQUESTS)
        val ok = runCatching {
            File(dir, "$pkg.status").delete()
            File(dir, pkg).delete()
            File(dir, pkg).createNewFile()
        }.getOrDefault(false)
        if (ok) {
            asked[pkg] = System.currentTimeMillis()
        } else {
            Toast.makeText(this, R.string.bring_not_writable, Toast.LENGTH_LONG).show()
        }
        shown = snapshot()
        tick++
    }
}
