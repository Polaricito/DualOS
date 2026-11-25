package com.fahadali.oneos.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fahadali.oneos.BuildConfig
import com.fahadali.oneos.R
import com.fahadali.oneos.core.model.CheckItem
import com.fahadali.oneos.core.model.Checklist
import com.fahadali.oneos.core.model.ImageRef
import com.fahadali.oneos.core.model.Images
import com.fahadali.oneos.core.model.PhoneProfile
import com.fahadali.oneos.core.model.Profiles
import com.fahadali.oneos.core.model.ReportLogs
import com.fahadali.oneos.core.model.ReportRecord
import com.fahadali.oneos.core.model.ReportReply
import com.fahadali.oneos.core.model.Reports
import com.fahadali.oneos.core.model.StartAnswer
import com.fahadali.oneos.core.model.Units
import com.fahadali.oneos.core.root.LogTrim
import com.fahadali.oneos.probe.ProbeSession
import com.fahadali.oneos.report.ReportSender
import com.fahadali.oneos.shared.DualOsIcons
import com.fahadali.oneos.shared.DualOsTheme
import com.fahadali.oneos.shared.ExpressiveGroupedCard
import com.fahadali.oneos.shared.ExpressiveListDivider
import com.fahadali.oneos.shared.ExpressiveListItem
import com.fahadali.oneos.shared.ExpressiveSectionHeader
import com.fahadali.oneos.shared.ExpressiveSwitchItem
import com.fahadali.oneos.shared.LocalDualOsStatusColors
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

/**
 * Send to the DualOS team (both editions): a session's log bundle with the
 * user's checklist, or DualOS Probe's findings, straight to [ReportSender].
 */
class SendReportActivity : AppCompatActivity() {
    val zip: File? by lazy {
        intent.getStringExtra(EXTRA_ZIP)?.let(::File)?.takeIf { f ->
            val dir = f.parentFile?.canonicalPath
            f.isFile && f.name.endsWith(".zip") &&
                (dir == FindingsActivity.logsDir(this).canonicalPath || dir == ProbeSession.findingsDir(this).canonicalPath)
        }
    }

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private data class Facts(
        val session: Boolean,
        val sessionId: String?,
        val profile: PhoneProfile?,
        val image: ImageRef?,
        val imageId: String?,
        val outcomeKind: String?,
        val afterSeconds: Int?,
        val sessionAppVersion: String? = null,
        val sessionAppCode: Int? = null,
        val sessionDate: String? = null,
        val fullLogsOff: Boolean = false,
    )

    private var facts: Facts? = null
    private var checklist by mutableStateOf(Checklist())
    private var fullChoice: Boolean? by mutableStateOf(null)
    private var attach by mutableStateOf(true)
    private var sending by mutableStateOf(false)
    private var sentCodeState: String? by mutableStateOf(null)
    private var failure: String? by mutableStateOf(null)
    private var progress: String? by mutableStateOf(null)
    private var fromGuest by mutableStateOf(false)

    private var signingIn by mutableStateOf(false)
    private var claimCode: String? by mutableStateOf(null)
    private var signinUrl: String? by mutableStateOf(null)
    private var claimTries = 0
    private var claimErrors = 0
    private var claimBusy = false
    private var signinNote: String? by mutableStateOf(null)
    private var notMember by mutableStateOf(false)
    private var passState: String? by mutableStateOf(null)
    private var usernameState: String? by mutableStateOf(null)
    private var isResumedNow = false
    private val poll = Runnable { pollClaim() }

    private var showStartDialog by mutableStateOf(false)
    private var showWorksDialog by mutableStateOf(false)
    private var showProblemsDialog by mutableStateOf(false)
    private var showNoteDialog by mutableStateOf(false)
    private var showContactDialog by mutableStateOf(false)

    private val full: Boolean get() = facts?.session == true && (fullChoice ?: checklist.wantsFullLogs)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        refreshAuthState()
        val z = zip
        if (z != null && ReportSender.available && ReportSender.refusal == null) {
            val f = readFacts(z)
            facts = f
            sentCodeState = sentCode(this, z)
            var c = Checklist(
                start = when (f.outcomeKind) {
                    "BLACK_SCREEN" -> StartAnswer.BLACK
                    "BOOT_TIMEOUT" -> StartAnswer.LOGO
                    else -> null
                },
            )
            if (f.session) {
                guestAnswers(z)?.let { g ->
                    c = c.copy(start = StartAnswer.HOME, works = g.works - g.problems, problems = g.problems, note = g.note)
                    fromGuest = true
                }
            }
            savedContact(this)?.let { c = c.copy(contact = it) }
            checklist = c
        }
        setContent {
            DualOsTheme {
                Screen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isResumedNow = true
        refreshAuthState()
        if (claimCode != null) schedulePoll()
    }

    override fun onPause() {
        isResumedNow = false
        main.removeCallbacks(poll)
        super.onPause()
    }

    override fun onDestroy() {
        main.removeCallbacks(poll)
        io.shutdown()
        super.onDestroy()
    }

    private fun refreshAuthState() {
        passState = prefs(this).getString(PASS_KEY, null)?.takeIf { it.isNotEmpty() }
        usernameState = prefs(this).getString(USERNAME_KEY, null)?.takeIf { it.isNotEmpty() }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun Screen() {
        val z = zip
        val f = facts
        val statusColors = LocalDualOsStatusColors.current

        DualOsScaffold(
            title = stringResource(R.string.send_title),
            onBack = { finish() },
        ) { padding ->
            if (z == null || !ReportSender.available) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 8.dp, 16.dp, 24.dp),
                ) {
                    item {
                        ExpressiveGroupedCard {
                            ExpressiveListItem(title = stringResource(R.string.send_unavailable), icon = Icons.Rounded.Info)
                        }
                    }
                }
                return@DualOsScaffold
            }
            val refusal = ReportSender.refusal
            if (refusal != null || f == null) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 8.dp, 16.dp, 24.dp),
                ) {
                    item {
                        ExpressiveGroupedCard {
                            ExpressiveListItem(title = refusal ?: stringResource(R.string.send_unavailable), icon = Icons.Rounded.Warning)
                        }
                    }
                }
                return@DualOsScaffold
            }

            val items = CheckItem.forForm(f.profile?.form)
            val ran = f.sessionAppVersion
            val stale = f.session && ran != null &&
                ran.substringBefore('-') != BuildConfig.VERSION_NAME.substringBefore('-')

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { ExpressiveSectionHeader(stringResource(if (f.session) R.string.send_cat_session else R.string.send_cat_probe)) }
                item {
                    ExpressiveGroupedCard {
                        ExpressiveListItem(
                            title = listOfNotNull(f.profile?.label ?: Build.MODEL, f.image?.let(::imageLabel)).joinToString(" · "),
                            subtitle = when {
                                !f.session -> stringResource(R.string.send_probe_summary)
                                ran == null -> stringResource(R.string.send_session_summary, f.sessionId ?: "?")
                                else -> stringResource(R.string.send_session_summary_full, f.sessionDate ?: "?", ran, f.sessionId ?: "?")
                            },
                            icon = DualOsIcons.PhoneAndroid,
                        )
                        if (ran != null && stale) {
                            ExpressiveListDivider()
                            ExpressiveListItem(
                                title = stringResource(R.string.send_session_old_title),
                                subtitle = stringResource(R.string.send_session_old_summary, ran, BuildConfig.VERSION_NAME),
                                icon = Icons.Rounded.Warning,
                                iconTint = statusColors.warn,
                            )
                        }
                        if (f.session && f.fullLogsOff) {
                            ExpressiveListDivider()
                            ExpressiveListItem(
                                title = stringResource(R.string.send_full_logs_off_title),
                                subtitle = stringResource(R.string.send_full_logs_off_summary),
                                icon = Icons.Rounded.Info,
                            )
                        }
                    }
                }

                if (f.session) {
                    item { ExpressiveSectionHeader(stringResource(R.string.send_cat_how)) }
                    item {
                        ExpressiveGroupedCard {
                            if (fromGuest) {
                                ExpressiveListItem(
                                    title = stringResource(R.string.send_from_guest),
                                    subtitle = stringResource(R.string.send_from_guest_summary),
                                    icon = DualOsIcons.Checklist,
                                )
                                ExpressiveListDivider()
                            }
                            ExpressiveListItem(
                                title = stringResource(R.string.send_start_title),
                                subtitle = checklist.start?.label ?: stringResource(R.string.send_choose),
                                enabled = !sending,
                                showChevron = !sending,
                                onClick = { showStartDialog = true },
                            )
                            ExpressiveListDivider()
                            ExpressiveListItem(
                                title = stringResource(R.string.send_works_title),
                                subtitle = names(checklist.works, items),
                                enabled = !sending,
                                showChevron = !sending,
                                onClick = { showWorksDialog = true },
                            )
                            ExpressiveListDivider()
                            ExpressiveListItem(
                                title = stringResource(R.string.send_problems_title),
                                subtitle = names(checklist.problems, items),
                                enabled = !sending,
                                showChevron = !sending,
                                onClick = { showProblemsDialog = true },
                            )
                        }
                    }
                }

                item { ExpressiveSectionHeader(stringResource(R.string.send_cat_more)) }
                item {
                    val contactSummary = when {
                        checklist.contact.isEmpty() -> stringResource(R.string.send_contact_hint)
                        Reports.telegramUsername(checklist.contact) == null -> stringResource(R.string.send_contact_invalid, checklist.contact)
                        else -> checklist.contact
                    }
                    ExpressiveGroupedCard {
                        ExpressiveListItem(
                            title = stringResource(R.string.send_note_title),
                            subtitle = checklist.note.ifEmpty { stringResource(R.string.send_note_hint) },
                            icon = Icons.Rounded.Edit,
                            enabled = !sending,
                            showChevron = !sending,
                            onClick = { showNoteDialog = true },
                        )
                        ExpressiveListDivider()
                        ExpressiveListItem(
                            title = stringResource(R.string.send_contact_title) + " *",
                            subtitle = contactSummary,
                            icon = Icons.Rounded.Edit,
                            enabled = !sending,
                            showChevron = !sending,
                            onClick = { showContactDialog = true },
                        )
                    }
                }

                item { ExpressiveSectionHeader(stringResource(R.string.send_cat_telegram)) }
                item {
                    ExpressiveGroupedCard {
                        if (passState != null) {
                            val name = usernameState
                            ExpressiveListItem(
                                title = if (name != null) stringResource(R.string.send_tg_signed_in_as, name) else stringResource(R.string.send_tg_signed_in),
                                icon = Icons.Rounded.CheckCircle,
                                iconTint = statusColors.ok,
                            )
                            ExpressiveListDivider()
                            ExpressiveListItem(
                                title = stringResource(R.string.send_tg_sign_out),
                                enabled = !sending,
                                showChevron = !sending,
                                onClick = {
                                    prefs(this@SendReportActivity).edit().remove(PASS_KEY).remove(USERNAME_KEY).apply()
                                    refreshAuthState()
                                    notMember = false
                                    signinNote = null
                                },
                            )
                            if (notMember) {
                                ExpressiveListDivider()
                                ExpressiveListItem(
                                    title = stringResource(R.string.send_tg_join_title),
                                    subtitle = stringResource(R.string.send_tg_join_summary),
                                    icon = DualOsIcons.OpenInNew,
                                    showChevron = true,
                                    onClick = { openLink(ReportSender.GROUP_URL) },
                                )
                            }
                        } else {
                            signinNote?.let { note ->
                                ExpressiveListItem(title = note, icon = Icons.Rounded.Warning, iconTint = statusColors.warn)
                                ExpressiveListDivider()
                            }
                            if (claimCode != null) {
                                ExpressiveListItem(
                                    title = stringResource(R.string.send_tg_waiting_title),
                                    subtitle = stringResource(R.string.send_tg_waiting_summary),
                                    icon = DualOsIcons.OpenInNew,
                                    showChevron = true,
                                    onClick = { signinUrl?.let(::openLink) },
                                )
                            } else {
                                ExpressiveListItem(
                                    title = stringResource(R.string.send_tg_sign_in),
                                    subtitle = stringResource(R.string.send_tg_sign_in_summary),
                                    icon = DualOsIcons.OpenInNew,
                                    enabled = !signingIn && !sending,
                                    showChevron = !signingIn && !sending,
                                    onClick = { startSignIn() },
                                )
                            }
                        }
                    }
                }

                item { ExpressiveSectionHeader(stringResource(R.string.send_cat_what)) }
                item {
                    ExpressiveGroupedCard {
                        ExpressiveSwitchItem(
                            title = stringResource(if (f.session) R.string.send_attach_logs else R.string.send_attach_findings),
                            subtitle = when {
                                !f.session -> stringResource(R.string.send_attach_findings_summary, Units.bytes(z.length()))
                                full -> stringResource(R.string.send_full_summary, Units.bytes(z.length()))
                                else -> stringResource(R.string.send_short_summary)
                            },
                            checked = attach,
                            enabled = !sending,
                            onCheckedChange = { attach = it },
                        )
                        if (f.session && attach) {
                            ExpressiveListDivider()
                            ExpressiveSwitchItem(
                                title = stringResource(R.string.send_full_title),
                                subtitle = stringResource(if (checklist.wantsFullLogs && fullChoice == null) R.string.send_full_auto else R.string.send_full_hint),
                                checked = full,
                                enabled = !sending,
                                onCheckedChange = { fullChoice = it },
                            )
                        }
                        ExpressiveListDivider()
                        ExpressiveListItem(
                            title = stringResource(R.string.send_privacy_title),
                            subtitle = stringResource(R.string.send_privacy_summary),
                            icon = DualOsIcons.Shield,
                        )
                    }
                }

                item {
                    ExpressiveGroupedCard {
                        when {
                            sending -> {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(stringResource(R.string.send_sending), style = MaterialTheme.typography.titleMedium)
                                    progress?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                            }
                            sentCodeState != null && failure == null -> {
                                val code = sentCodeState!!
                                ExpressiveListItem(
                                    title = stringResource(R.string.send_done_title),
                                    subtitle = stringResource(R.string.send_done_summary, code),
                                    icon = Icons.Rounded.CheckCircle,
                                    iconTint = statusColors.ok,
                                    onClick = {
                                        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("DualOS report", code))
                                        Toast.makeText(this@SendReportActivity, R.string.send_copied, Toast.LENGTH_SHORT).show()
                                    },
                                )
                                ExpressiveListDivider()
                                ExpressiveListItem(
                                    title = stringResource(R.string.send_again),
                                    showChevron = true,
                                    onClick = { sentCodeState = null },
                                )
                            }
                            else -> {
                                failure?.let { err ->
                                    ExpressiveListItem(
                                        title = stringResource(R.string.send_failed_title),
                                        subtitle = err,
                                        icon = Icons.Rounded.Warning,
                                        iconTint = statusColors.block,
                                    )
                                    ExpressiveListDivider()
                                }
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Button(
                                        onClick = { send(z) },
                                        enabled = passState != null && !sending,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                    ) {
                                        Text(stringResource(R.string.send_button))
                                    }
                                    if (failure != null) {
                                        FilledTonalButton(
                                            onClick = { shareInstead(z) },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(16.dp),
                                        ) {
                                            Text(stringResource(R.string.send_share_instead))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showStartDialog) {
                AlertDialog(
                    onDismissRequest = { showStartDialog = false },
                    title = { Text(stringResource(R.string.send_start_title)) },
                    text = {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            StartAnswer.entries.forEach { ans ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            checklist = checklist.copy(start = ans)
                                            showStartDialog = false
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(selected = checklist.start == ans, onClick = {
                                        checklist = checklist.copy(start = ans)
                                        showStartDialog = false
                                    })
                                    Text(ans.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showStartDialog = false }) { Text(stringResource(android.R.string.cancel)) }
                    },
                )
            }

            if (showWorksDialog) {
                MultiPickDialog(
                    title = stringResource(R.string.send_works_title),
                    items = items,
                    initial = checklist.works,
                    onConfirm = { picked ->
                        checklist = checklist.copy(works = picked, problems = checklist.problems - picked)
                        showWorksDialog = false
                    },
                    onDismiss = { showWorksDialog = false },
                )
            }

            if (showProblemsDialog) {
                MultiPickDialog(
                    title = stringResource(R.string.send_problems_title),
                    items = items,
                    initial = checklist.problems,
                    onConfirm = { picked ->
                        checklist = checklist.copy(problems = picked, works = checklist.works - picked)
                        showProblemsDialog = false
                    },
                    onDismiss = { showProblemsDialog = false },
                )
            }

            if (showNoteDialog) {
                TextInputDialog(
                    title = stringResource(R.string.send_note_title),
                    initial = checklist.note,
                    maxLen = Reports.NOTE_MAX,
                    singleLine = false,
                    onConfirm = { v ->
                        checklist = checklist.copy(note = Reports.cleanText(v, Reports.NOTE_MAX))
                        showNoteDialog = false
                    },
                    onDismiss = { showNoteDialog = false },
                )
            }

            if (showContactDialog) {
                TextInputDialog(
                    title = stringResource(R.string.send_contact_title),
                    initial = checklist.contact,
                    maxLen = Reports.CONTACT_MAX,
                    singleLine = true,
                    onConfirm = { raw ->
                        val cleaned = Reports.cleanText(raw, Reports.CONTACT_MAX)
                        val user = Reports.telegramUsername(cleaned)
                        checklist = checklist.copy(contact = user ?: cleaned)
                        if (user != null) {
                            prefs(this@SendReportActivity).edit().putString(CONTACT_KEY, user).apply()
                        } else if (cleaned.isNotBlank()) {
                            Toast.makeText(this@SendReportActivity, getString(R.string.send_contact_invalid, cleaned), Toast.LENGTH_LONG).show()
                        }
                        showContactDialog = false
                    },
                    onDismiss = { showContactDialog = false },
                )
            }
        }
    }

    @Composable
    private fun MultiPickDialog(
        title: String,
        items: List<CheckItem>,
        initial: Set<CheckItem>,
        onConfirm: (Set<CheckItem>) -> Unit,
        onDismiss: () -> Unit,
    ) {
        var selected by remember(initial) { mutableStateOf(initial) }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    items.forEach { item ->
                        val checked = item in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = if (checked) selected - item else selected + item }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = { on -> selected = if (on) selected + item else selected - item })
                            Text(item.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text(stringResource(android.R.string.ok)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
        )
    }

    @Composable
    private fun TextInputDialog(
        title: String,
        initial: String,
        maxLen: Int,
        singleLine: Boolean,
        onConfirm: (String) -> Unit,
        onDismiss: () -> Unit,
    ) {
        var text by remember(initial) { mutableStateOf(initial) }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= maxLen) text = it },
                    singleLine = singleLine,
                    minLines = if (singleLine) 1 else 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(stringResource(android.R.string.ok)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
        )
    }

    private fun readFacts(zip: File): Facts {
        val json = runCatching { JSONObject(File(ProbeSession.folderOf(zip), "report.json").readText()) }.getOrNull()
        val session = json?.optString("schema")?.startsWith("oneos.logbundle") == true
        return if (session) {
            val profile = json!!.optString("profile").takeIf { it.isNotEmpty() && it != "null" }?.let { Profiles.byId(it) }
            val imageId = json.optString("image").takeIf { it.isNotEmpty() && it != "null" }
            val outcome = json.optJSONObject("outcome")
            Facts(
                session = true,
                sessionId = json.optString("sessionId").takeIf { it.isNotEmpty() },
                profile = profile,
                image = imageId?.let { Images.byId(it) },
                imageId = imageId,
                outcomeKind = outcome?.optString("kind")?.takeIf { it.isNotEmpty() },
                afterSeconds = outcome?.takeIf { it.has("afterSeconds") && !it.isNull("afterSeconds") }?.optInt("afterSeconds"),
                sessionAppVersion = json.optJSONObject("app")?.optString("version")?.takeIf { it.isNotEmpty() },
                sessionAppCode = json.optJSONObject("app")?.optInt("versionCode")?.takeIf { it > 0 },
                sessionDate = json.optString("generatedAt").takeIf { it.length >= 10 }?.take(10),
                fullLogsOff = runCatching {
                    val stage = File(ProbeSession.folderOf(zip), "stage")
                    File(stage, "session.env").readLines().contains("ONEOS_FULL_LOGS=0") && !File(stage, "full_logs_mid.log").isFile
                }.getOrDefault(false),
            )
        } else {
            val device = json?.optJSONObject("device")
            val profile = Profiles.resolve(
                device?.optString("model") ?: Build.MODEL,
                device?.optString("device") ?: Build.DEVICE,
                device?.optString("soc") ?: Build.SOC_MODEL,
            ).profile
            Facts(false, null, profile, null, null, null, null)
        }
    }

    private fun guestAnswers(zip: File): Checklist? {
        val folder = ProbeSession.folderOf(zip)
        val f = File(folder, "guest").listFiles { x -> x.isFile && x.name.endsWith("oneos_checklist.json") }?.firstOrNull() ?: return null
        val json = runCatching { JSONObject(f.readText()) }.getOrNull() ?: return null
        val started = runCatching { JSONObject(File(folder, "stage/current.json").readText()).optLong("epoch") }.getOrNull() ?: 0L
        if (started > 0 && json.optLong("updatedAt") < started) return null
        val items = json.optJSONObject("items") ?: return null
        val works = mutableSetOf<CheckItem>()
        val problems = mutableSetOf<CheckItem>()
        for (k in items.keys()) {
            val item = CheckItem.byId(k) ?: continue
            when (items.optString(k)) {
                "works" -> works += item
                "problem" -> problems += item
            }
        }
        val note = Reports.cleanText(json.optString("note"), Reports.NOTE_MAX)
        if (works.isEmpty() && problems.isEmpty() && note.isEmpty()) return null
        return Checklist(works = works, problems = problems, note = note)
    }

    private fun openLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.send_tg_no_app, Toast.LENGTH_LONG).show()
        }
    }

    private fun startSignIn() {
        signingIn = true
        signinNote = null
        io.execute {
            val result = runCatching { ReportSender.signin() }
            main.post {
                signingIn = false
                if (isDestroyed) return@post
                result.onSuccess { (code, url) ->
                    claimCode = code
                    signinUrl = url
                    claimTries = 0
                    claimErrors = 0
                    openLink(url)
                    if (isResumedNow) schedulePoll()
                }.onFailure { e -> signinNote = e.message ?: e.javaClass.simpleName }
            }
        }
    }

    private fun schedulePoll() {
        main.removeCallbacks(poll)
        main.postDelayed(poll, 3_000L)
    }

    private fun pollClaim() {
        val code = claimCode ?: return
        if (claimBusy || isDestroyed) return
        if (claimTries >= 60) return endSignIn(getString(R.string.send_tg_expired))
        claimTries++
        claimBusy = true
        io.execute {
            val result = runCatching { ReportSender.claim(code) }
            main.post {
                claimBusy = false
                if (isDestroyed || claimCode != code) return@post
                result.onSuccess { claim ->
                    claimErrors = 0
                    when (claim) {
                        ReportReply.Claim.Waiting -> if (isResumedNow) schedulePoll()
                        ReportReply.Claim.Expired -> endSignIn(getString(R.string.send_tg_expired))
                        is ReportReply.Claim.Done -> {
                            prefs(this).edit()
                                .putString(PASS_KEY, claim.pass)
                                .putString(USERNAME_KEY, claim.username ?: "")
                                .apply()
                            refreshAuthState()
                            notMember = !claim.member
                            endSignIn(null)
                        }
                    }
                }.onFailure { e ->
                    if (e !is ReportSender.Failed && ++claimErrors < 3) {
                        if (isResumedNow) schedulePoll()
                    } else {
                        endSignIn(e.message ?: e.javaClass.simpleName)
                    }
                }
            }
        }
    }

    private fun endSignIn(note: String?) {
        main.removeCallbacks(poll)
        claimCode = null
        signinUrl = null
        signinNote = note
    }

    private fun imageLabel(i: ImageRef): String = if (i.name.contains(i.shortName)) i.name else "${i.name} (${i.shortName})"

    private fun names(set: Set<CheckItem>, items: List<CheckItem>): String =
        items.filter { it in set }.joinToString(", ") { it.label }.ifEmpty { getString(R.string.send_none_ticked) }

    private fun savedContact(ctx: Context): String? = prefs(ctx).getString(CONTACT_KEY, null)?.let(Reports::telegramUsername)

    private fun send(zip: File) {
        val f = facts ?: return
        if (f.session && checklist.start == null) {
            Toast.makeText(this, R.string.send_need_start, Toast.LENGTH_SHORT).show()
            return
        }
        if (Reports.telegramUsername(checklist.contact) == null) {
            Toast.makeText(this, R.string.send_contact_needed, Toast.LENGTH_LONG).show()
            showContactDialog = true
            return
        }
        val tgPass = passState ?: return
        val app = applicationContext
        sending = true
        failure = null
        progress = getString(R.string.send_preparing)
        val c = checklist.cleaned()
        val wantFull = full
        val withLogs = attach
        io.execute {
            val result = runCatching {
                val shortDir = File(app.cacheDir, "report-short").apply { deleteRecursively(); mkdirs() }
                val file = when {
                    !withLogs -> null
                    !f.session || wantFull -> zip
                    else -> File(shortDir, zip.name.removeSuffix(".zip") + "-short.zip").takeIf { LogTrim.shorten(zip, it) } ?: zip
                }
                check(file == null || file.length() <= Reports.MAX_ZIP_BYTES) { app.getString(R.string.send_too_big, Units.bytes(file!!.length())) }
                val rnd = ByteArray(4).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
                val record = ReportRecord(
                    reportId = Reports.reportId(f.sessionId ?: zip.name.removeSuffix(".zip"), rnd),
                    createdAt = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME).take(25),
                    app = ProbeSession.appInfo(app),
                    series = BuildConfig.VERSION_NAME.substringAfterLast('-', "").takeIf { it in setOf("a53", "fold", "s26", "s25") }.orEmpty(),
                    model = Build.MODEL.take(40),
                    device = Build.DEVICE.lowercase().take(48),
                    profileId = f.profile?.id,
                    phoneLabel = f.profile?.label,
                    imageId = f.imageId,
                    imageName = f.image?.let { imageLabel(it) },
                    sessionId = f.sessionId,
                    sessionAppVersion = f.sessionAppVersion,
                    sessionAppCode = f.sessionAppCode,
                    sessionDate = f.sessionDate,
                    outcomeKind = f.outcomeKind,
                    afterSeconds = f.afterSeconds,
                    checklist = if (f.session) c else Checklist(note = c.note, contact = c.contact),
                    form = f.profile?.form,
                    logs = file?.let { ReportLogs(if (f.session) "session" else "probe", it.name, full = f.session && it == zip, bytes = it.length(), sha256 = sha256(it)) },
                )
                try {
                    ReportSender.send(record, file, tgPass) { sent, total ->
                        if (total > 0) main.post { progress = app.getString(R.string.send_progress, (sent * 100 / total).toInt(), Units.bytes(total)) }
                    }
                } finally {
                    shortDir.deleteRecursively()
                }
                record.reportId
            }
            main.post {
                sending = false
                result.onSuccess { code ->
                    sentCodeState = code
                    remember(app, zip, code)
                }.onFailure { e ->
                    failure = e.message ?: e.javaClass.simpleName
                    when (e) {
                        is ReportSender.NeedsSignIn -> {
                            prefs(app).edit().remove(PASS_KEY).remove(USERNAME_KEY).apply()
                            refreshAuthState()
                            notMember = false
                        }
                        is ReportSender.NotMember -> { notMember = true; failure = null }
                    }
                }
            }
        }
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun shareInstead(zip: File) {
        val i = Intent(this, FindingsActivity::class.java)
        if (zip.parentFile?.canonicalPath == FindingsActivity.logsDir(this).canonicalPath) i.putExtra(FindingsActivity.EXTRA_ZIP, zip.path)
        startActivity(i)
    }

    companion object {
        const val EXTRA_ZIP = "com.fahadali.oneos.extra.REPORT_ZIP"

        fun intent(ctx: Context, zip: File): Intent = Intent(ctx, SendReportActivity::class.java).putExtra(EXTRA_ZIP, zip.path)

        fun sentCode(ctx: Context, zip: File): String? = prefs(ctx).getString(zip.name.removeSuffix(".zip"), null)

        internal fun prefs(ctx: Context) = ctx.getSharedPreferences("reports_sent", Context.MODE_PRIVATE)

        internal const val CONTACT_KEY = "contact_telegram"
        internal const val PASS_KEY = "tg_pass"
        internal const val USERNAME_KEY = "tg_username"

        internal fun remember(ctx: Context, zip: File, code: String) {
            prefs(ctx).edit().putString(zip.name.removeSuffix(".zip"), code).apply()
        }
    }
}
