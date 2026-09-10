package dev.whekin.whfin.ui.settings

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.whekin.whfin.R
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.core.ui.*
import dev.whekin.whfin.data.push.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PushJournalRoute(demoMode: Boolean, onOpenMessages: () -> Unit) {
    if (demoMode) { Text(stringResource(R.string.demo_mode_live_import_unavailable)); return }
    val context = LocalContext.current
    val app = context.applicationContext as WhfinApp
    val settings = remember { PushSettings(app) }
    val journal = remember { PushJournal(app) }
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(settings.enabled) }
    var access by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf<List<PushJournal.Entry>?>(null) }
    var selected by remember { mutableStateOf<PushJournal.Entry?>(null) }
    var problem by remember { mutableStateOf(false) }
    var readProblem by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var exportingId by rememberSaveable { mutableStateOf<String?>(null) }
    val revision by PushRuntime.revision.collectAsState()
    val connected by PushRuntime.connected.collectAsState()
    val receiverError by PushRuntime.error.collectAsState()
    val component = remember { ComponentName(context, TbcPushListener::class.java) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        fun refresh() { access = context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component) }
        refresh()
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
            refresh(); PushRuntime.revision.value++
            if (settings.enabled && access && !PushRuntime.connected.value)
                android.service.notification.NotificationListenerService.requestRebind(component)
        } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // The main activity's app lock remains the owner of access to this bank-data screen.
    val activity = context as? dev.whekin.whfin.MainActivity
    DisposableEffect(activity) { activity?.protectBankScreen(true); onDispose { activity?.protectBankScreen(false) } }
    LaunchedEffect(revision) {
        try { entries = withContext(Dispatchers.IO) { journal.entries() }; readProblem = false }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { readProblem = true; entries = emptyList() }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val token = exportingId
        exportingId = null
        if (uri != null && token != null) app.appScope.launch(Dispatchers.IO) {
            try {
                val payload = requireNotNull(journal.entries().firstOrNull { it.id == token }).json().toString(2)
                requireNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(payload.toByteArray()) }
            }
            catch (_: Exception) { withContext(Dispatchers.Main) { problem = true } }
        }
    }
    if (selected != null) {
        val entry = requireNotNull(selected)
        ModalBottomSheet(onDismissRequest = { selected = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxHeight(0.85f).navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            WhfinButton(stringResource(R.string.action_back), { selected = null }, style = WhfinActionStyle.Quiet)
            LazyColumn(Modifier.weight(1f)) { item { Text(entry.json().toString(2), style = MaterialTheme.typography.bodySmall) } }
            WhfinButton(stringResource(R.string.push_export), {
                exportingId = entry.id; export.launch("tbc-push-example.json")
            }, Modifier.fillMaxWidth(), enabled = !busy)
            WhfinButton(stringResource(R.string.push_retry), {
                busy = true
                scope.launch {
                    try { selected = withContext(Dispatchers.IO) { PushRuntime.mutex.withLock { processPush(app, entry.push) } } }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { problem = true }
                    finally { busy = false; PushRuntime.revision.value++ }
                }
            }, Modifier.fillMaxWidth(), enabled = !busy, style = WhfinActionStyle.Secondary)
            if (problem) Text(stringResource(R.string.push_error), color = MaterialTheme.colorScheme.error)
        }
        }
    }
    PushJournalContent(enabled, access, connected, entries, problem || readProblem || receiverError,
        onEnabledChange = { value ->
            settings.enabled = value; enabled = value
            if (value && !access) context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            else if (value) android.service.notification.NotificationListenerService.requestRebind(component)
        },
        onPermissionRequest = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
        onOpenMessages = onOpenMessages, onEntry = { selected = it }, onClear = { confirmClear = true })
    if (confirmClear) dev.whekin.whfin.core.ui.WhfinConfirmDialog(
        title = stringResource(R.string.push_clear), body = stringResource(R.string.push_clear_body),
        dismissLabel = stringResource(R.string.action_cancel), confirmLabel = stringResource(R.string.push_clear), onConfirm = {
            confirmClear = false
            scope.launch {
                try { withContext(Dispatchers.IO) { journal.clear() }; problem = false; readProblem = false; PushRuntime.error.value = false; PushRuntime.revision.value++ }
                catch (_: Exception) { problem = true }
            }
        }, onDismiss = { confirmClear = false })
}

@Composable
internal fun PushJournalContent(enabled: Boolean, access: Boolean, connected: Boolean,
    entries: List<PushJournal.Entry>?, problem: Boolean,
    onEnabledChange: (Boolean) -> Unit, onPermissionRequest: () -> Unit,
    onOpenMessages: () -> Unit, onEntry: (PushJournal.Entry) -> Unit, onClear: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text(stringResource(R.string.push_explanation))
            WhfinLedgerRow(stringResource(R.string.push_enable),
                modifier = Modifier.toggleable(enabled, role = Role.Switch) { value ->
                    haptics.performHapticFeedback(WhfinHaptics.toggle(value))
                    onEnabledChange(value)
                },
                trailing = { WhfinSwitch(enabled, null, contentDescription = stringResource(R.string.push_enable)) })
            Text(stringResource(R.string.push_retention), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(when {
                !enabled -> R.string.push_off
                !access -> R.string.push_permission_missing
                !connected -> R.string.push_connecting
                else -> R.string.push_active
            }))
            WhfinButton(stringResource(R.string.push_permission), onPermissionRequest, Modifier.fillMaxWidth(), style = WhfinActionStyle.Secondary)
            WhfinButton(stringResource(R.string.push_mappings), onOpenMessages, Modifier.fillMaxWidth(), style = WhfinActionStyle.Quiet)
            if (problem) Text(stringResource(R.string.push_error), color = MaterialTheme.colorScheme.error)
        }
        if (entries == null) item { WhfinLoadingIndicator() }
        else if (entries!!.isEmpty()) item { Text(stringResource(R.string.push_empty)) }
        items(entries.orEmpty(), key = { it.id }) { entry ->
            WhfinLedgerRow(title = java.time.Instant.ofEpochMilli(entry.capturedAt).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy · HH:mm")),
                supportingText = stringResource(pushOutcomeText(entry.outcome)), onClick = { onEntry(entry) }, divider = true)
        }
        item { WhfinButton(stringResource(R.string.push_clear), onClear, style = WhfinActionStyle.Quiet) }
        item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Push journal", showBackground = true)
@androidx.compose.ui.tooling.preview.Preview(name = "Push journal dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@androidx.compose.ui.tooling.preview.Preview(name = "Push journal compact large", heightDp = 480, fontScale = 1.5f)
@Composable
private fun PushJournalPreview() = dev.whekin.whfin.ui.theme.WhfinTheme { Surface {
    PushJournalContent(true, true, true, listOf(PushJournal.Entry("synthetic", 0,
        BankPush(TbcPush.PACKAGE, "synthetic", 0, "TBC", "Synthetic future format"), "UNRECOGNIZED")),
        false, {}, {}, {}, {}, {})
} }

internal fun pushOutcomeText(value: String): Int = when (value) {
    "IMPORTED" -> R.string.push_imported
    "ATTACHED", "DUPLICATE" -> R.string.push_duplicate
    "NEEDS_CARD_MAPPING", "CHOOSE_ACCOUNT" -> R.string.push_mapping
    "ERROR", "RECEIVED" -> R.string.push_error
    "GROUP_SUMMARY", "IGNORED", "OLD" -> R.string.push_ignored
    else -> R.string.push_unknown
}
