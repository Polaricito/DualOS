package com.fahadali.oneos.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fahadali.oneos.core.model.GuestModuleBundle
import com.fahadali.oneos.core.root.RootScript
import com.fahadali.oneos.core.root.RootShell
import com.fahadali.oneos.session.GuestRuntimeStore
import com.fahadali.oneos.session.RootVerbs
import com.fahadali.oneos.shared.DualOsIcons
import com.fahadali.oneos.shared.DualOsTheme
import com.fahadali.oneos.shared.ExpressiveGroupedCard
import com.fahadali.oneos.shared.ExpressiveListItem
import com.fahadali.oneos.shared.ExpressiveLoadingBox
import com.fahadali.oneos.shared.ExpressiveModuleCard
import com.fahadali.oneos.shared.ModuleUpdates
import com.fahadali.oneos.shared.StatusTone
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors

/** Guest module manager on the host using the shared Material 3 Expressive module cards. */
class GuestRuntimeActivity : AppCompatActivity() {
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var image: String
    private var state by mutableStateOf(GuestRuntimeStore.State(""))
    private var busy by mutableStateOf(false)
    private var loaded by mutableStateOf(false)
    private var importFramework = false

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !busy) importModule { ModuleUpdates.copyDocument(this, uri) }
    }

    private fun importModule(source: () -> File) {
        busy = true
        io.execute {
            val result = runCatching {
                val zip = source()
                val bundle = File.createTempFile("guest-module-", ".bundle", cacheDir)
                try {
                    val info = GuestModuleBundle.write(zip, bundle)
                    val md = MessageDigest.getInstance("SHA-256")
                    bundle.inputStream().use { input ->
                        val buffer = ByteArray(65536)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            md.update(buffer, 0, n)
                        }
                    }
                    val sum = md.digest().joinToString("") { "%02x".format(it) }
                    val reply = RootVerbs.run(
                        applicationContext,
                        RootScript.Verb.GUEST_MODULE,
                        listOf(image, info.id, bundle.length().toString(), sum),
                        RootShell.Feed { out -> bundle.inputStream().use { it.copyTo(out) } },
                    )
                    check(reply.ok) { reply.problem ?: "Module import failed" }
                    GuestRuntimeStore.read(applicationContext, image) to info.name
                } finally {
                    zip.delete()
                    bundle.delete()
                }
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                busy = false
                result.onSuccess { (next, name) ->
                    state = next
                    Toast.makeText(this, "$name staged for the next boot", Toast.LENGTH_LONG).show()
                }.onFailure { showError(it.message ?: "Could not import module") }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        image = intent.getStringExtra("image") ?: run { finish(); return }
        state = GuestRuntimeStore.State(image)
        importFramework = intent.getBooleanExtra("import_framework", false)
        setContent {
            DualOsTheme {
                Screen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!::image.isInitialized || busy) return
        busy = true
        GuestRuntimeStore.refresh(this, image) { next ->
            if (isDestroyed) return@refresh
            busy = false
            loaded = true
            state = if (next.error == null) next else state.copy(error = next.error)
            if (importFramework && state.ready) {
                importFramework = false
                picker.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed"))
            }
        }
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    @Composable
    private fun Screen() {
        val st = state
        DualOsScaffold(
            title = "Manage guest modules",
            onBack = { finish() },
            bottomBar = {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
                    Button(
                        onClick = { picker.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) },
                        enabled = loaded && st.ready && !busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .height(50.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Import module ZIP")
                    }
                }
            },
        ) { padding ->
            if (!loaded) {
                ExpressiveLoadingBox(
                    label = "Reading guest modules…",
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp,
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = padding.calculateBottomPadding() + 28.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when {
                        st.error != null -> item {
                            ExpressiveGroupedCard {
                                ExpressiveListItem(title = "Couldn’t read guest modules", subtitle = st.error, icon = DualOsIcons.Extension)
                            }
                        }
                        st.modules.isEmpty() -> item {
                            ExpressiveGroupedCard {
                                ExpressiveListItem(
                                    title = "No guest modules yet",
                                    subtitle = if (st.ready) "Import a module ZIP to add it to this system."
                                    else "Update this system on the pre-boot page to add guest module support.",
                                    icon = DualOsIcons.Extension,
                                )
                            }
                        }
                        else -> items(st.modules, key = { it.id }) { module ->
                            ExpressiveModuleCard(
                                name = module.name,
                                description = module.description,
                                checked = module.enabled,
                                enabled = !busy && st.ready,
                                version = module.version.ifBlank { module.versionCode.takeIf { it > 0 }?.toString().orEmpty() },
                                author = module.author,
                                status = when {
                                    module.removing -> "Uninstall pending · Next guest restart"
                                    module.pending -> "Staged for next guest restart"
                                    else -> null
                                },
                                toggleEnabled = !module.removing && (module.framework == null || module.id == st.selected),
                                options = moduleOptions(module),
                                onToggle = { value ->
                                    busy = true
                                    GuestRuntimeStore.setModule(this@GuestRuntimeActivity, image, module.id, value) { next ->
                                        if (isDestroyed) return@setModule
                                        busy = false
                                        if (next.error != null) showError(next.error) else state = next
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun moduleOptions(module: GuestRuntimeStore.Module): List<Pair<String, () -> Unit>> = buildList {
        if ("webui" in module.capabilities) add("WebUI · Open inside guest" to { showError("Open this module's WebUI from DualOS inside the guest.") })
        if ("action" in module.capabilities) add("Action · Run inside guest" to { showError("Run this module's action from DualOS inside the guest.") })
        if (module.updateUrl.isNotBlank() && !module.pending && !module.removing) add("Check for updates" to {
            busy = true
            ModuleUpdates.check(this@GuestRuntimeActivity, module.name, module.id, module.versionCode, module.updateUrl) { zip ->
                busy = false
                if (zip != null) importModule { zip }
            }
        })
        add((if (module.removing) "Cancel uninstall" else "Uninstall ${module.name}") to {
            MaterialAlertDialogBuilder(this@GuestRuntimeActivity)
                .setTitle(if (module.removing) "Cancel uninstall?" else "Uninstall ${module.name}?")
                .setMessage("This change applies at the next guest restart. Module cleanup scripts run only inside the guest.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    busy = true
                    GuestRuntimeStore.changeModule(this@GuestRuntimeActivity, image, module.id, if (module.removing) "restore" else "remove") { next ->
                        busy = false
                        if (next.error != null) showError(next.error) else state = next
                    }
                }
                .show()
        })
    }

    private fun showError(message: String) {
        MaterialAlertDialogBuilder(this).setTitle("Guest modules").setMessage(message).setPositiveButton(android.R.string.ok, null).show()
    }
}
