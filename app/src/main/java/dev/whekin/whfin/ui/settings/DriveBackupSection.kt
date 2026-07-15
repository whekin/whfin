package dev.whekin.whfin.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.whekin.whfin.R
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.core.ui.WhfinActionStyle
import dev.whekin.whfin.core.ui.WhfinButton
import dev.whekin.whfin.core.ui.WhfinFormSheet
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinLedgerRow
import dev.whekin.whfin.core.ui.WhfinNotice
import dev.whekin.whfin.core.ui.WhfinNoticeKind
import dev.whekin.whfin.core.ui.WhfinSectionLabel
import dev.whekin.whfin.core.ui.WhfinSwitch
import dev.whekin.whfin.data.backup.WhfinBackupPassphraseException
import dev.whekin.whfin.data.drive.DriveAuthResult
import dev.whekin.whfin.data.drive.DriveBackupAuth
import dev.whekin.whfin.data.drive.DriveBackupFile
import dev.whekin.whfin.data.drive.DriveBackupManager
import dev.whekin.whfin.data.drive.DriveBackupStore
import dev.whekin.whfin.data.drive.DriveBackupWorker
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

internal sealed interface DriveUiStatus {
    data object Idle : DriveUiStatus
    data object Working : DriveUiStatus
    data class Uploaded(val rowCount: Int) : DriveUiStatus
    data class Restored(val rowCount: Int) : DriveUiStatus
    data class Error(val code: String) : DriveUiStatus
}

/** Что сделать сразу после успешной авторизации Google. */
private enum class DriveAuthReason { Enable, BackupNow, ListForRestore, Reauth }

@Composable
fun DriveBackupSection(appVersion: String) {
    val context = LocalContext.current
    val app = context.applicationContext as WhfinApp
    val store = remember(app) { DriveBackupStore(app) }
    val manager = remember(app) { DriveBackupManager(app, app.userDb) }
    val scope = rememberCoroutineScope()

    var enabled by remember { mutableStateOf(store.enabled) }
    var lastSuccessAt by remember { mutableLongStateOf(store.lastSuccessAt) }
    var needsReauth by remember { mutableStateOf(store.needsReauth) }
    var status by remember {
        mutableStateOf<DriveUiStatus>(
            store.lastError?.let { DriveUiStatus.Error(it) } ?: DriveUiStatus.Idle,
        )
    }
    var authReason by remember { mutableStateOf<DriveAuthReason?>(null) }
    var setupPassphrase by remember { mutableStateOf(false) }
    var copies by remember { mutableStateOf<List<DriveBackupFile>?>(null) }
    var restoreFrom by remember { mutableStateOf<DriveBackupFile?>(null) }
    var restorePassphraseError by remember { mutableStateOf(false) }

    fun refreshFromStore() {
        enabled = store.enabled
        lastSuccessAt = store.lastSuccessAt
        needsReauth = store.needsReauth
    }

    fun runBackup(token: String) {
        scope.launch {
            status = DriveUiStatus.Working
            status = try {
                val result = manager.backupNow(token, appVersion)
                store.lastSuccessAt = System.currentTimeMillis()
                store.lastError = null
                store.needsReauth = false
                refreshFromStore()
                DriveUiStatus.Uploaded(result.rowCount)
            } catch (error: Exception) {
                val code = if (error is dev.whekin.whfin.data.drive.DriveBackupMissingPassphraseException) {
                    DriveBackupWorker.ERROR_PASSPHRASE
                } else {
                    DriveBackupWorker.ERROR_NETWORK
                }
                store.lastError = code
                refreshFromStore()
                DriveUiStatus.Error(code)
            }
        }
    }

    fun loadCopies(token: String) {
        scope.launch {
            status = DriveUiStatus.Working
            try {
                copies = manager.listBackups(token)
                status = DriveUiStatus.Idle
            } catch (error: Exception) {
                status = DriveUiStatus.Error(DriveBackupWorker.ERROR_NETWORK)
            }
        }
    }

    var pendingToken by remember { mutableStateOf<String?>(null) }

    fun onAuthorized(token: String, reason: DriveAuthReason) {
        pendingToken = token
        when (reason) {
            DriveAuthReason.Enable -> {
                store.enabled = true
                store.needsReauth = false
                DriveBackupWorker.schedule(app)
                refreshFromStore()
                runBackup(token)
            }
            DriveAuthReason.BackupNow -> runBackup(token)
            DriveAuthReason.ListForRestore -> loadCopies(token)
            DriveAuthReason.Reauth -> {
                store.needsReauth = false
                store.lastError = null
                refreshFromStore()
                status = DriveUiStatus.Idle
            }
        }
    }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { activityResult ->
        val reason = authReason ?: return@rememberLauncherForActivityResult
        authReason = null
        when (val auth = DriveBackupAuth.fromConsentIntent(context, activityResult.data)) {
            is DriveAuthResult.Authorized -> onAuthorized(auth.accessToken, reason)
            else -> status = DriveUiStatus.Error(DriveBackupWorker.ERROR_AUTH)
        }
    }

    fun authorize(reason: DriveAuthReason) {
        scope.launch {
            status = DriveUiStatus.Working
            when (val auth = DriveBackupAuth.authorize(context)) {
                is DriveAuthResult.Authorized -> onAuthorized(auth.accessToken, reason)
                is DriveAuthResult.ConsentRequired -> {
                    authReason = reason
                    status = DriveUiStatus.Idle
                    consentLauncher.launch(IntentSenderRequest.Builder(auth.intentSender).build())
                }
                is DriveAuthResult.Failed -> {
                    status = DriveUiStatus.Error(
                        if (auth.missingOAuthClient) ERROR_MISSING_CLIENT else DriveBackupWorker.ERROR_AUTH,
                    )
                }
            }
        }
    }

    fun disable() {
        store.enabled = false
        DriveBackupWorker.cancel(app)
        refreshFromStore()
        status = DriveUiStatus.Idle
    }

    val working = status == DriveUiStatus.Working

    WhfinSectionLabel(stringResource(R.string.drive_section))
    WhfinLedgerGroup(Modifier.fillMaxWidth()) {
        WhfinLedgerRow(
            title = stringResource(R.string.drive_title),
            supportingText = if (lastSuccessAt > 0) {
                stringResource(R.string.drive_last_backup, formatBackupTime(lastSuccessAt))
            } else {
                stringResource(R.string.drive_last_backup_never)
            },
            icon = Icons.Default.CloudUpload,
            trailing = {
                WhfinSwitch(
                    checked = enabled,
                    onCheckedChange = { checked ->
                        if (checked) {
                            if (store.hasPassphrase()) authorize(DriveAuthReason.Enable) else setupPassphrase = true
                        } else {
                            disable()
                        }
                    },
                    contentDescription = stringResource(R.string.drive_toggle),
                    enabled = !working,
                )
            },
        )
    }
    Text(
        stringResource(R.string.drive_body),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (enabled) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            WhfinButton(
                label = stringResource(R.string.drive_backup_now),
                onClick = { authorize(DriveAuthReason.BackupNow) },
                enabled = !working,
                style = WhfinActionStyle.Secondary,
                leadingIcon = Icons.Default.CloudUpload,
                modifier = Modifier.fillMaxWidth(),
            )
            WhfinButton(
                label = stringResource(R.string.drive_restore_action),
                onClick = { authorize(DriveAuthReason.ListForRestore) },
                enabled = !working,
                style = WhfinActionStyle.DestructiveSecondary,
                leadingIcon = Icons.Default.Restore,
                modifier = Modifier.fillMaxWidth(),
            )
            WhfinButton(
                label = stringResource(R.string.drive_passphrase_change),
                onClick = { setupPassphrase = true },
                enabled = !working,
                style = WhfinActionStyle.Quiet,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (needsReauth) {
        WhfinNotice(
            title = stringResource(R.string.drive_error_title),
            body = stringResource(R.string.drive_error_reauth),
            kind = WhfinNoticeKind.Attention,
            actionLabel = stringResource(R.string.drive_reauth_action),
            onAction = { authorize(DriveAuthReason.Reauth) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    when (val current = status) {
        DriveUiStatus.Idle -> Unit
        DriveUiStatus.Working -> WhfinNotice(
            title = stringResource(R.string.drive_working),
            body = "",
            kind = WhfinNoticeKind.Unavailable,
            modifier = Modifier.fillMaxWidth(),
        )
        is DriveUiStatus.Uploaded -> WhfinNotice(
            title = stringResource(R.string.drive_backup_done_title),
            body = stringResource(R.string.drive_backup_done_body, current.rowCount),
            kind = WhfinNoticeKind.Info,
            modifier = Modifier.fillMaxWidth(),
        )
        is DriveUiStatus.Restored -> WhfinNotice(
            title = stringResource(R.string.backup_restored_title),
            body = stringResource(R.string.backup_restored_body, current.rowCount),
            kind = WhfinNoticeKind.Info,
            modifier = Modifier.fillMaxWidth(),
        )
        is DriveUiStatus.Error -> WhfinNotice(
            title = stringResource(R.string.drive_error_title),
            body = stringResource(
                when (current.code) {
                    DriveBackupWorker.ERROR_REAUTH -> R.string.drive_error_reauth
                    DriveBackupWorker.ERROR_AUTH -> R.string.drive_error_auth
                    DriveBackupWorker.ERROR_PASSPHRASE -> R.string.drive_error_passphrase
                    DriveBackupWorker.ERROR_NETWORK -> R.string.drive_error_network
                    ERROR_MISSING_CLIENT -> R.string.drive_error_missing_client
                    else -> R.string.drive_error_unknown
                },
            ),
            kind = WhfinNoticeKind.Error,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (setupPassphrase) {
        BackupPassphraseSheet(
            title = stringResource(R.string.backup_passphrase_title),
            body = stringResource(R.string.backup_passphrase_body),
            primaryLabel = stringResource(R.string.action_continue),
            requireConfirmation = true,
            errorText = null,
            onDismiss = { setupPassphrase = false },
            onSubmit = { passphrase ->
                setupPassphrase = false
                store.savePassphrase(passphrase)
                passphrase.fill(' ')
                if (!enabled) authorize(DriveAuthReason.Enable)
            },
        )
    }

    copies?.let { files ->
        DriveCopiesSheet(
            files = files,
            onDismiss = { copies = null },
            onSelect = { file ->
                copies = null
                restorePassphraseError = false
                restoreFrom = file
            },
        )
    }

    restoreFrom?.let { file ->
        BackupPassphraseSheet(
            title = stringResource(R.string.backup_restore_passphrase_title),
            body = stringResource(R.string.backup_restore_confirm_body),
            primaryLabel = stringResource(R.string.backup_restore_confirm_action),
            requireConfirmation = false,
            errorText = if (restorePassphraseError) stringResource(R.string.backup_wrong_passphrase) else null,
            onDismiss = {
                restoreFrom = null
                restorePassphraseError = false
            },
            onSubmit = { passphrase ->
                val token = pendingToken
                if (token == null) {
                    restoreFrom = null
                    status = DriveUiStatus.Error(DriveBackupWorker.ERROR_AUTH)
                } else {
                    scope.launch {
                        status = DriveUiStatus.Working
                        try {
                            val summary = manager.restore(token, file.id, passphrase)
                            restoreFrom = null
                            restorePassphraseError = false
                            status = DriveUiStatus.Restored(summary.rowCount)
                        } catch (error: WhfinBackupPassphraseException) {
                            restorePassphraseError = true
                            status = DriveUiStatus.Idle
                        } catch (error: Exception) {
                            restoreFrom = null
                            status = DriveUiStatus.Error(DriveBackupWorker.ERROR_NETWORK)
                        } finally {
                            passphrase.fill(' ')
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun DriveCopiesSheet(
    files: List<DriveBackupFile>,
    onDismiss: () -> Unit,
    onSelect: (DriveBackupFile) -> Unit,
) {
    WhfinFormSheet(
        title = stringResource(R.string.drive_copies_title),
        onDismiss = onDismiss,
        primaryLabel = stringResource(R.string.action_cancel),
        primaryEnabled = true,
        onPrimary = onDismiss,
    ) {
        if (files.isEmpty()) {
            Text(
                stringResource(R.string.drive_copies_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                files.forEachIndexed { index, file ->
                    WhfinLedgerRow(
                        title = file.createdAt?.let(::formatBackupInstant) ?: file.name,
                        supportingText = stringResource(
                            R.string.drive_copy_size,
                            file.name,
                            (file.sizeBytes ?: 0L) / 1024,
                        ),
                        onClick = { onSelect(file) },
                        divider = index < files.lastIndex,
                    )
                }
            }
        }
    }
}

private fun formatBackupTime(epochMillis: Long): String =
    formatBackupInstant(Instant.ofEpochMilli(epochMillis))

private fun formatBackupInstant(instant: Instant): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())
        .format(instant)

private const val ERROR_MISSING_CLIENT = "missing_client"
