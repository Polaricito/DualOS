package com.fahadali.oneos.guest

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import com.fahadali.oneos.shared.DualOsIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fahadali.oneos.core.model.GuestModuleBundle
import com.fahadali.oneos.shared.DualOsTheme
import com.fahadali.oneos.shared.ExpressiveGroupedCard
import com.fahadali.oneos.shared.ExpressiveListItem
import com.fahadali.oneos.shared.ExpressiveLoadingBox
import com.fahadali.oneos.shared.ExpressiveModuleCard
import com.fahadali.oneos.shared.ModuleUpdates
import com.fahadali.oneos.shared.StatusTone
import java.io.File

/** Guest module manager inside the guest (Compose + Material 3 Expressive). */
class GuestModulesActivity : ComponentActivity() {
    private var state by mutableStateOf(GuestRuntime.State())
    private var busy by mutableStateOf(false)
    private var showImportConfirm by mutableStateOf(false)
    private var showRestartConfirm by mutableStateOf(false)
    private var uninstallModule by mutableStateOf<GuestRuntime.Module?>(null)
    private var infoDialog by mutableStateOf<Pair<String, String>?>(null)

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !busy) importModule { ModuleUpdates.copyDocument(this, uri) }
    }

    private fun importModule(source: () -> File) {
        busy = true
        GuestRuntime.work({
            val zip = source()
            val bundle = File.createTempFile("guest-module-", ".bundle", cacheDir)
            try {
                val info = GuestModuleBundle.write(zip, bundle)
                GuestRuntime.control("import", info.id, bundle.absolutePath)
                GuestRuntime.read(applicationContext)
            } finally {
                zip.delete()
                bundle.delete()
            }
        }) { next -> finishChange(next, "Module staged for the next guest boot") }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DualOsTheme {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.surface,
                    topBar = {
                        TopAppBar(
                            title = { Text("Manage guest modules", fontWeight = FontWeight.SemiBold) },
                            navigationIcon = {
                                IconButton(onClick = { onBackPressedDispatcher.onBackPressed() }) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                        )
                    },
                    bottomBar = {
                        Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
                            Button(
                                onClick = { showImportConfirm = true },
                                enabled = state.root && !busy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(horizontal = 20.dp, vertical = 14.dp)
                                    .height(54.dp),
                                shape = RoundedCornerShape(27.dp),
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Import module ZIP", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    },
                ) { padding ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (state.modules.isEmpty()) {
                            item {
                                if (busy) {
                                    ExpressiveLoadingBox(label = "Reading guest modules…")
                                } else {
                                    ExpressiveGroupedCard {
                                        ExpressiveListItem(
                                            title = if (state.error != null) "Guest runtime unavailable" else "No guest modules yet",
                                            subtitle = state.error ?: "Import a module ZIP to stage it for the next guest boot.",
                                            icon = DualOsIcons.Extension,
                                        )
                                    }
                                }
                            }
                        } else {
                            items(state.modules, key = { it.id }) { module ->
                                ExpressiveModuleCard(
                                    name = module.name,
                                    description = module.description,
                                    checked = module.enabled,
                                    enabled = !busy && state.root,
                                    version = module.version.ifBlank { module.versionCode.takeIf { it > 0 }?.toString().orEmpty() },
                                    author = module.author,
                                    status = when {
                                        module.removing -> "Uninstall pending · Next guest restart"
                                        module.pending -> "Staged for next guest restart"
                                        else -> null
                                    },
                                    toggleEnabled = !module.removing && (!module.framework || module.id == state.selected),
                                    options = moduleOptions(module),
                                    onToggle = { enabled ->
                                        busy = true
                                        GuestRuntime.work({
                                            GuestRuntime.control("toggle", module.id, if (enabled) "on" else "off")
                                            GuestRuntime.read(applicationContext)
                                        }) { finishChange(it, "Change saved for the next guest boot") }
                                    },
                                )
                            }
                        }
                        if (state.root) {
                            item {
                                ExpressiveGroupedCard {
                                    ExpressiveListItem(
                                        title = "Restart guest",
                                        subtitle = "Apply pending module changes without returning to One UI",
                                        icon = DualOsIcons.RestartAlt,
                                        iconTone = StatusTone.WARN,
                                        enabled = !busy,
                                        showChevron = true,
                                        onClick = { showRestartConfirm = true },
                                    )
                                }
                            }
                        }
                    }
                }

                if (showImportConfirm) {
                    AlertDialog(
                        onDismissRequest = { showImportConfirm = false },
                        title = { Text("Import guest module") },
                        text = { Text("The module will be staged for the next guest boot. It will not run during this session.") },
                        confirmButton = {
                            Button(onClick = {
                                showImportConfirm = false
                                picker.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed"))
                            }) { Text("Choose ZIP") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showImportConfirm = false }) { Text(stringResource(android.R.string.cancel)) }
                        },
                    )
                }

                if (showRestartConfirm) {
                    AlertDialog(
                        onDismissRequest = { showRestartConfirm = false },
                        title = { Text("Restart this guest?") },
                        text = { Text("Guest apps will close and pending module changes will apply. One UI stays stopped while the guest restarts. If recovery fails, DualOS will return to One UI.") },
                        confirmButton = {
                            Button(onClick = {
                                showRestartConfirm = false
                                GuestRestart.launch(this)
                            }) { Text("Restart guest") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showRestartConfirm = false }) { Text(stringResource(android.R.string.cancel)) }
                        },
                    )
                }

                uninstallModule?.let { module ->
                    AlertDialog(
                        onDismissRequest = { uninstallModule = null },
                        title = { Text(if (module.removing) "Cancel uninstall?" else "Uninstall ${module.name}?") },
                        text = { Text("This change applies at the next guest restart. The active session keeps its current module files.") },
                        confirmButton = {
                            Button(onClick = {
                                uninstallModule = null
                                busy = true
                                GuestRuntime.work({
                                    GuestRuntime.control(if (module.removing) "restore" else "remove", module.id)
                                    GuestRuntime.read(applicationContext)
                                }) { finishChange(it, "Change saved for the next guest restart") }
                            }) { Text(stringResource(android.R.string.ok)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { uninstallModule = null }) { Text(stringResource(android.R.string.cancel)) }
                        },
                    )
                }

                infoDialog?.let { (title, msg) ->
                    AlertDialog(
                        onDismissRequest = { infoDialog = null },
                        title = { Text(title) },
                        text = { Text(msg) },
                        confirmButton = {
                            Button(onClick = { infoDialog = null }) { Text(stringResource(android.R.string.ok)) }
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!busy) {
            busy = true
            GuestRuntime.refresh(this) { next ->
                if (!isDestroyed) {
                    state = next
                    busy = false
                }
            }
        }
    }

    private fun finishChange(next: GuestRuntime.State, message: String) {
        if (isDestroyed) return
        busy = false
        if (next.error == null) {
            state = next
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        } else {
            infoDialog = "Guest modules" to next.error
        }
    }

    private fun moduleOptions(module: GuestRuntime.Module): List<Pair<String, () -> Unit>> = buildList {
        if ("webui" in module.capabilities && !module.pending && !module.removing) {
            add("Open WebUI" to {
                startActivity(Intent(this@GuestModulesActivity, ModuleWebActivity::class.java).putExtra("module", module.id))
            })
        }
        if ("action" in module.capabilities && !module.pending && !module.removing) {
            add("Run action" to {
                busy = true
                GuestRuntime.work({
                    val result = GuestRuntime.control("action", module.id)
                    runOnUiThread {
                        if (!isDestroyed) infoDialog = module.name to result.takeLast(32768).ifBlank { "Action completed" }
                    }
                    GuestRuntime.read(applicationContext)
                }) { finishChange(it, "Action finished") }
            })
        }
        if (module.updateUrl.isNotBlank() && !module.pending && !module.removing) {
            add("Check for updates" to {
                busy = true
                ModuleUpdates.check(this@GuestModulesActivity, module.name, module.id, module.versionCode, module.updateUrl) { zip ->
                    busy = false
                    if (zip != null) importModule { zip }
                }
            })
        }
        add((if (module.removing) "Cancel uninstall" else "Uninstall ${module.name}") to {
            uninstallModule = module
        })
    }
}
