package com.fahadali.oneos.guest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import com.fahadali.oneos.shared.DualOsIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.fahadali.oneos.core.model.GuestRootPermissions as Permissions
import com.fahadali.oneos.shared.DualOsTheme
import com.fahadali.oneos.shared.ExpressiveGroupedCard
import com.fahadali.oneos.shared.ExpressiveListDivider
import com.fahadali.oneos.shared.ExpressiveListItem
import com.fahadali.oneos.shared.ExpressiveLoadingBox
import com.fahadali.oneos.shared.ExpressiveSegmentedControl
import com.fahadali.oneos.shared.ExpressiveStatusPill
import com.fahadali.oneos.shared.StatusTone

/** Guest-only root access manager in Jetpack Compose + Material 3 Expressive. */
class GuestRootActivity : ComponentActivity() {
    private lateinit var model: RootPermissionsModel
    private var uiState by mutableStateOf(RootPermissionsModel.State())
    private var query by mutableStateOf("")
    private var access by mutableStateOf(Permissions.Access.ALL)
    private var kind by mutableStateOf(Permissions.Kind.USER)

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this)[RootPermissionsModel::class.java]
        query = savedInstanceState?.getString("query").orEmpty()
        access = Permissions.Access.entries.getOrElse(savedInstanceState?.getInt("access") ?: 0) { Permissions.Access.ALL }
        kind = Permissions.Kind.entries.getOrElse(savedInstanceState?.getInt("kind") ?: 0) { Permissions.Kind.USER }
        uiState = model.state

        setContent {
            DualOsTheme {
                val visible = Permissions.filter(uiState.groups, query, access, kind)
                val accessLabels = resources.getStringArray(R.array.root_access_options)
                val kindLabels = resources.getStringArray(R.array.root_kind_options)

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.surface,
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.root_permissions_title), fontWeight = FontWeight.SemiBold) },
                            navigationIcon = {
                                IconButton(onClick = { onBackPressedDispatcher.onBackPressed() }) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                        )
                    },
                ) { padding ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = { Text(stringResource(R.string.root_search_hint)) },
                                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (query.isNotEmpty()) {
                                        IconButton(onClick = { query = "" }) {
                                            Icon(Icons.Rounded.Close, contentDescription = "Clear")
                                        }
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                            )
                        }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ExpressiveSegmentedControl(
                                    options = Permissions.Access.entries.mapIndexed { i, v -> v to accessLabels.getOrElse(i) { v.name } },
                                    selected = access,
                                    onSelect = { access = it },
                                )
                                ExpressiveSegmentedControl(
                                    options = Permissions.Kind.entries.mapIndexed { i, v -> v to kindLabels.getOrElse(i) { v.name } },
                                    selected = kind,
                                    onSelect = { kind = it },
                                )
                            }
                        }
                        when {
                            uiState.loading && uiState.groups.isEmpty() -> item {
                                ExpressiveLoadingBox(label = stringResource(R.string.root_loading))
                            }
                            visible.isEmpty() -> item {
                                ExpressiveGroupedCard {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Text(
                                            text = when {
                                                uiState.error != null && uiState.groups.isEmpty() -> uiState.error!!
                                                uiState.groups.isEmpty() -> stringResource(R.string.root_none)
                                                else -> stringResource(R.string.root_no_matches)
                                            },
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        if (uiState.error != null) {
                                            Button(onClick = { model.refresh() }) {
                                                Text(stringResource(R.string.root_retry))
                                            }
                                        }
                                    }
                                }
                            }
                            else -> item {
                                ExpressiveGroupedCard {
                                    visible.forEachIndexed { idx, group ->
                                        if (idx > 0) ExpressiveListDivider()
                                        val pendingVal = uiState.pending[group.uid]
                                        val checked = pendingVal ?: group.allowed
                                        val sharedText = if (group.apps.size > 1) {
                                            stringResource(R.string.root_shared, group.apps.size)
                                        } else {
                                            null
                                        }
                                        ExpressiveListItem(
                                            title = group.primary.label,
                                            subtitle = buildString {
                                                append(group.primary.packageName)
                                                append(" · ")
                                                append(
                                                    when {
                                                        pendingVal != null -> stringResource(R.string.root_saving)
                                                        checked -> stringResource(R.string.root_allowed)
                                                        else -> stringResource(R.string.root_denied)
                                                    },
                                                )
                                            },
                                            icon = DualOsIcons.Shield,
                                            iconTone = if (checked) StatusTone.OK else StatusTone.NEUTRAL,
                                            badge = sharedText,
                                            badgeTone = StatusTone.INFO,
                                            enabled = !uiState.loading && pendingVal == null,
                                            trailing = {
                                                Switch(
                                                    checked = checked,
                                                    onCheckedChange = { model.change(group, it) },
                                                    enabled = !uiState.loading && pendingVal == null,
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }

                if (uiState.error != null && uiState.groups.isNotEmpty()) {
                    AlertDialog(
                        onDismissRequest = { model.dismissError() },
                        title = { Text(stringResource(R.string.root_permissions_title)) },
                        text = { Text(uiState.error!!) },
                        confirmButton = {
                            Button(onClick = { model.dismissError() }) { Text(stringResource(android.R.string.ok)) }
                        },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        model.observer = { state -> uiState = state }
        uiState = model.state
        model.refresh()
    }

    override fun onStop() {
        model.observer = null
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("query", query)
        outState.putInt("access", access.ordinal)
        outState.putInt("kind", kind.ordinal)
        super.onSaveInstanceState(outState)
    }
}
