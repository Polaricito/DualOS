package com.fahadali.oneos.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.fahadali.oneos.BuildConfig
import com.fahadali.oneos.R
import com.fahadali.oneos.core.model.FindingsBuilder
import com.fahadali.oneos.core.model.Units
import com.fahadali.oneos.probe.ProbeSession
import com.fahadali.oneos.report.ReportSender
import com.fahadali.oneos.shared.DualOsIcons
import com.fahadali.oneos.shared.DualOsTheme
import com.fahadali.oneos.shared.ExpressiveGroupedCard
import com.fahadali.oneos.shared.ExpressiveListDivider
import com.fahadali.oneos.shared.ExpressiveListItem
import com.fahadali.oneos.shared.ExpressiveSectionHeader
import java.io.File
import java.util.concurrent.Executors

/**
 * Findings preview: what will be shared (summary.txt, every file, what was
 * removed), then Share (share sheet through the FileProvider) or Save to
 * Downloads (the system file picker; no storage permission). With
 * [EXTRA_ZIP] it shows a session log bundle the same way.
 */
class FindingsActivity : AppCompatActivity() {
    private val logsZip: File? by lazy {
        intent.getStringExtra(EXTRA_ZIP)?.let(::File)?.takeIf { f ->
            f.isFile && f.name.endsWith(".zip") && f.parentFile?.canonicalPath == logsDir(this).canonicalPath
        }
    }

    val isLogs: Boolean get() = logsZip != null

    fun bundleZip(): File? = logsZip ?: if (intent.hasExtra(EXTRA_ZIP)) null else ProbeSession.latestZip(this)

    private lateinit var saveLauncher: ActivityResultLauncher<String>
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private var viewerTitle: String? by mutableStateOf(null)
    private var viewerBody: String? by mutableStateOf(null)
    private var showDeleteConfirm: Boolean by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        saveLauncher = registerForActivityResult(CreateZipDocument()) { uri -> if (uri != null) saveTo(uri) }
        super.onCreate(savedInstanceState)

        val zip = bundleZip()
        val folder = zip?.let { ProbeSession.folderOf(it) }
        val summaryText = folder?.let { File(it, FindingsBuilder.SUMMARY).takeIf(File::isFile)?.readText() }
        val redactionFile = folder?.let { File(it, FindingsBuilder.REDACTION).takeIf(File::isFile) }
        val redactionHeadline: String?
        val redactionCounts: String?
        if (redactionFile != null) {
            val text = redactionFile.readText()
            redactionHeadline = text.lineSequence().firstOrNull { it.startsWith("Removed values") }.orEmpty()
            redactionCounts = text.lineSequence().dropWhile { !it.startsWith("Removed values") }.drop(1)
                .takeWhile { it.startsWith("  ") }.map { it.trim().replace(Regex("\\s+"), " ") }
                .joinToString("\n").ifEmpty { null }
        } else {
            redactionHeadline = null
            redactionCounts = null
        }
        val files = folder?.walkTopDown()?.filter { it.isFile }?.map { it.relativeTo(folder).invariantSeparatorsPath }?.sorted()?.toList().orEmpty()

        setContent {
            DualOsTheme {
                Screen(zip, folder, summaryText, redactionFile, redactionHeadline, redactionCounts, files)
            }
        }
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    @Composable
    private fun Screen(
        zip: File?,
        folder: File?,
        summaryText: String?,
        redactionFile: File?,
        redactionHeadline: String?,
        redactionCounts: String?,
        files: List<String>,
    ) {
        val logs = isLogs
        DualOsScaffold(
            title = stringResource(if (logs) R.string.logs_title else R.string.findings_title),
            subtitle = zip?.name,
            onBack = { finish() },
            bottomBar = {
                if (zip != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 3.dp,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (ReportSender.offered) {
                                Button(
                                    onClick = { startActivity(SendReportActivity.intent(this@FindingsActivity, zip)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                ) {
                                    Text(stringResource(R.string.findings_send_team))
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                if (ReportSender.offered) {
                                    FilledTonalButton(
                                        onClick = { share(zip) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(16.dp),
                                    ) {
                                        Text(stringResource(R.string.findings_share))
                                    }
                                } else {
                                    Button(
                                        onClick = { share(zip) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(16.dp),
                                    ) {
                                        Text(stringResource(R.string.findings_share))
                                    }
                                }
                                OutlinedButton(
                                    onClick = { saveLauncher.launch(zip.name) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp),
                                ) {
                                    Text(stringResource(R.string.findings_save))
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
                if (zip == null || folder == null) {
                    item {
                        ExpressiveGroupedCard {
                            ExpressiveListItem(
                                title = stringResource(R.string.findings_none),
                                icon = DualOsIcons.Description,
                            )
                        }
                    }
                    return@LazyColumn
                }

                item { ExpressiveSectionHeader(stringResource(if (logs) R.string.logs_cat_bundle else R.string.findings_cat_bundle)) }
                item {
                    ExpressiveGroupedCard {
                        ExpressiveListItem(
                            title = stringResource(if (logs) R.string.logs_send_title else R.string.findings_send_title),
                            subtitle = stringResource(if (logs) R.string.logs_send_summary else R.string.findings_send_summary),
                            icon = Icons.Rounded.Share,
                        )
                        ExpressiveListDivider()
                        ExpressiveListItem(
                            title = zip.name,
                            subtitle = Units.bytes(zip.length()),
                            icon = DualOsIcons.Save,
                        )
                    }
                }

                if (!summaryText.isNullOrBlank()) {
                    item { ExpressiveSectionHeader(stringResource(R.string.findings_cat_summary)) }
                    item {
                        ExpressiveGroupedCard {
                            Text(
                                text = summaryText.trimEnd(),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }

                if (redactionFile != null && redactionHeadline != null) {
                    item { ExpressiveSectionHeader(stringResource(R.string.findings_cat_privacy)) }
                    item {
                        ExpressiveGroupedCard {
                            ExpressiveListItem(
                                title = redactionHeadline,
                                subtitle = redactionCounts,
                                icon = DualOsIcons.Shield,
                                showChevron = true,
                                onClick = { openFilePreview(FindingsBuilder.REDACTION, redactionFile) },
                            )
                        }
                    }
                }

                item { ExpressiveSectionHeader("${stringResource(R.string.findings_cat_files)} (${files.size})") }
                item {
                    ExpressiveGroupedCard {
                        files.forEachIndexed { idx, rel ->
                            if (idx > 0) ExpressiveListDivider()
                            val f = File(folder, rel)
                            val binary = FindingsBuilder.isBinary(rel)
                            ExpressiveListItem(
                                title = rel,
                                subtitle = Units.bytes(f.length()) + if (binary) " · ${getString(R.string.findings_binary)}" else "",
                                icon = DualOsIcons.Description,
                                showChevron = !binary,
                                onClick = if (binary) null else ({ openFilePreview(rel, f) }),
                            )
                        }
                    }
                }

                item {
                    ExpressiveGroupedCard {
                        ExpressiveListItem(
                            title = stringResource(if (logs) R.string.logs_delete_title else R.string.findings_delete_title),
                            subtitle = stringResource(if (logs) R.string.logs_delete_summary else R.string.findings_delete_summary),
                            icon = Icons.Rounded.Delete,
                            iconTint = MaterialTheme.colorScheme.error,
                            onClick = { showDeleteConfirm = true },
                        )
                    }
                }
            }

            if (viewerTitle != null && viewerBody != null) {
                MonospaceViewerDialog(
                    title = viewerTitle!!,
                    body = viewerBody!!,
                    onDismiss = { viewerTitle = null; viewerBody = null },
                )
            }

            if (showDeleteConfirm && zip != null) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = { Text(stringResource(if (logs) R.string.logs_delete_confirm else R.string.findings_delete_confirm)) },
                    text = { Text(stringResource(if (logs) R.string.logs_delete_summary else R.string.findings_delete_summary)) },
                    confirmButton = {
                        TextButton(onClick = {
                            showDeleteConfirm = false
                            if (logs) {
                                ProbeSession.folderOf(zip).deleteRecursively()
                                zip.delete()
                            } else {
                                ProbeSession.deleteFindings(this@FindingsActivity)
                            }
                            finish()
                        }) {
                            Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirm = false }) {
                            Text(stringResource(android.R.string.cancel))
                        }
                    },
                )
            }
        }
    }

    private fun openFilePreview(rel: String, f: File) {
        val bytes = f.inputStream().use { input ->
            val buf = ByteArray(minOf(f.length(), MAX_VIEW.toLong()).toInt())
            var n = 0
            while (n < buf.size) {
                val r = input.read(buf, n, buf.size - n)
                if (r < 0) break
                n += r
            }
            buf.copyOf(n)
        }
        var text = bytes.toString(Charsets.ISO_8859_1)
        if (f.length() > MAX_VIEW) text += "\n… (${Units.bytes(f.length())} in all)"
        viewerTitle = rel
        viewerBody = text
    }

    private fun share(zip: File) {
        val uri = FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.findings", zip)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/zip")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "${getString(if (isLogs) R.string.logs_share_subject else R.string.findings_share_subject)}: ${zip.name}")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(zip.name, uri)
        try {
            startActivity(Intent.createChooser(send, getString(if (isLogs) R.string.logs_share_chooser else R.string.findings_share_chooser)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.findings_no_share_app, Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveTo(uri: Uri) {
        val zip = bundleZip() ?: return
        val app = applicationContext
        io.execute {
            val ok = runCatching {
                app.contentResolver.openOutputStream(uri, "w")!!.use { out -> zip.inputStream().use { it.copyTo(out) } }
            }.isSuccess
            main.post {
                Toast.makeText(app, if (ok) R.string.findings_saved else R.string.findings_save_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private class CreateZipDocument : ActivityResultContract<String, Uri?>() {
        override fun createIntent(context: Context, input: String): Intent =
            Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/zip")
                .putExtra(Intent.EXTRA_TITLE, input)

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
            if (resultCode == Activity.RESULT_OK) intent?.data else null
    }

    companion object {
        const val EXTRA_ZIP = "com.fahadali.oneos.extra.ZIP"
        private const val MAX_VIEW = 512 * 1024

        fun logsDir(ctx: Context) = File(ctx.applicationContext.filesDir, "logs")
    }
}
