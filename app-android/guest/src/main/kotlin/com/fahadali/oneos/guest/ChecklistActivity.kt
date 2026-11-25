package com.fahadali.oneos.guest

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fahadali.oneos.shared.DualOsTheme
import com.fahadali.oneos.shared.ExpressiveGroupedCard
import com.fahadali.oneos.shared.ExpressiveSegmentedControl
import java.io.IOException

/** How's it going? checklist inside the guest (Compose + Material 3 Expressive). */
class ChecklistActivity : ComponentActivity() {
    private var answers by mutableStateOf<Map<String, String>>(emptyMap())
    private var note by mutableStateOf("")

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Checklist.read(this).let {
            answers = it.items
            note = it.note
        }
        val itemsList = Checklist.ITEMS.filter { it.first != "fold" || Build.MODEL.startsWith("SM-F") }

        setContent {
            DualOsTheme {
                val options = listOf(
                    Checklist.WORKS to stringResource(R.string.answer_works),
                    Checklist.PROBLEM to stringResource(R.string.answer_problem),
                    Checklist.NOT_TRIED to stringResource(R.string.answer_not_tried),
                )
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.surface,
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.checklist_title), fontWeight = FontWeight.SemiBold) },
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
                            .padding(padding)
                            .imePadding(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            Text(
                                text = stringResource(R.string.checklist_intro),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                        items(itemsList, key = { it.first }) { (id, labelRes) ->
                            ExpressiveGroupedCard {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(
                                        text = stringResource(labelRes),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    ExpressiveSegmentedControl(
                                        options = options,
                                        selected = answers[id] ?: Checklist.NOT_TRIED,
                                        onSelect = { value ->
                                            answers = if (value == Checklist.NOT_TRIED) answers - id else answers + (id to value)
                                            save()
                                        },
                                    )
                                }
                            }
                        }
                        item {
                            ExpressiveGroupedCard {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.checklist_note),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    OutlinedTextField(
                                        value = note,
                                        onValueChange = { note = it.take(Checklist.NOTE_MAX) },
                                        placeholder = { Text(stringResource(R.string.checklist_note_hint)) },
                                        minLines = 3,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = MaterialTheme.shapes.medium,
                                    )
                                    Text(
                                        text = stringResource(R.string.checklist_saved),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        save()
        super.onPause()
    }

    private fun save() {
        try {
            Checklist.save(this, Checklist.Answers(answers, note))
        } catch (e: IOException) {
            Toast.makeText(
                this,
                getString(R.string.checklist_not_saved, e.message ?: e.javaClass.simpleName),
                Toast.LENGTH_LONG,
            ).show()
        }
    }
}
