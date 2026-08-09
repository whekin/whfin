package dev.whekin.whfin.ui.settings

import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.whekin.whfin.R
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.core.ui.WhfinActionStyle
import dev.whekin.whfin.core.ui.WhfinButton
import dev.whekin.whfin.core.ui.WhfinConfirmDialog
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinLedgerRow
import dev.whekin.whfin.core.ui.WhfinNotice
import dev.whekin.whfin.core.ui.WhfinNoticeKind
import dev.whekin.whfin.core.ui.WhfinSectionLabel
import dev.whekin.whfin.core.ui.WhfinField
import dev.whekin.whfin.core.ui.WhfinFormSheet
import dev.whekin.whfin.data.backup.RestoreSafetyBackup
import dev.whekin.whfin.data.backup.WhfinBackupManager
import dev.whekin.whfin.data.backup.WhfinBackupMetadata
import dev.whekin.whfin.data.backup.WhfinBackupPassphraseException
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal sealed interface BackupUiState {
    data object Idle : BackupUiState
    data object Exporting : BackupUiState
    data object Restoring : BackupUiState
    data class Exported(val rowCount: Int) : BackupUiState
    data class Restored(val rowCount: Int) : BackupUiState
    data object Error : BackupUiState
}

internal data class PendingRestore(val uri: Uri, val encrypted: Boolean)

@Composable
fun BackupRoute(appVersion: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val resolver = context.contentResolver
    val safetyBackup = remember(context.applicationContext, appVersion) {
        RestoreSafetyBackup(context.applicationContext, appVersion)
    }
    val manager = remember(context.applicationContext, safetyBackup) {
        WhfinBackupManager((context.applicationContext as WhfinApp).db, safetyBackup)
    }
    val scope = rememberCoroutineScope()
    var uiState by remember { mutableStateOf<BackupUiState>(BackupUiState.Idle) }
    var pendingRestore by remember { mutableStateOf<PendingRestore?>(null) }
    var exportPassphraseSheet by remember { mutableStateOf(false) }
    // Живёт только между вводом passphrase и завершением записи файла.
    var exportPassphrase by remember { mutableStateOf<CharArray?>(null) }
    var restorePassphraseFor by remember { mutableStateOf<Uri?>(null) }
    var safetyCopy by remember { mutableStateOf(safetyBackup.latest()) }
    var confirmUndo by remember { mutableStateOf(false) }
    var restorePassphraseError by remember { mutableStateOf(false) }

    fun metadata() = WhfinBackupMetadata(
        exportedAt = Instant.now(),
        appVersion = appVersion,
        primaryCurrency = "GEL",
    )

    val createPlainBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) scope.launch {
            uiState = BackupUiState.Exporting
            uiState = runCatching {
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri, "wt")?.use { output ->
                        manager.export(output, metadata())
                    } ?: error("Could not open the selected backup destination.")
                }
            }.fold(
                onSuccess = { BackupUiState.Exported(it.rowCount) },
                onFailure = { BackupUiState.Error },
            )
        }
    }

    val createEncryptedBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val passphrase = exportPassphrase
        if (uri != null && passphrase != null) scope.launch {
            uiState = BackupUiState.Exporting
            uiState = runCatching {
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri, "wt")?.use { output ->
                        manager.exportEncrypted(output, metadata(), passphrase)
                    } ?: error("Could not open the selected backup destination.")
                }
            }.fold(
                onSuccess = { BackupUiState.Exported(it.rowCount) },
                onFailure = { BackupUiState.Error },
            )
            passphrase.fill('\u0000')
            exportPassphrase = null
        } else {
            passphrase?.fill('\u0000')
            exportPassphrase = null
        }
    }

    val chooseBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) scope.launch {
            val encrypted = runCatching {
                withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)?.use { manager.isEncrypted(it) } ?: false
                }
            }.getOrDefault(false)
            pendingRestore = PendingRestore(uri, encrypted)
        }
    }

    fun restore(uri: Uri, passphrase: CharArray?) {
        scope.launch {
            uiState = BackupUiState.Restoring
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)?.use { input -> manager.restore(input, passphrase) }
                        ?: error("Could not open the selected backup file.")
                }
            }
            passphrase?.fill('\u0000')
            result.fold(
                onSuccess = {
                    restorePassphraseFor = null
                    restorePassphraseError = false
                    safetyCopy = safetyBackup.latest()
                    uiState = BackupUiState.Restored(it.rowCount)
                },
                onFailure = { error ->
                    if (error is WhfinBackupPassphraseException && restorePassphraseFor != null) {
                        // Пароль не подошёл: остаёмся в диалоге с ошибкой, состояние не трогаем.
                        restorePassphraseError = true
                        uiState = BackupUiState.Idle
                    } else {
                        uiState = BackupUiState.Error
                    }
                },
            )
        }
    }

    BackupScreen(
        uiState = uiState,
        pendingRestore = pendingRestore,
        driveSection = { DriveBackupSection(appVersion = appVersion) },
        onExportEncrypted = { exportPassphraseSheet = true },
        onExportPlain = { createPlainBackup.launch("whfin-backup-${LocalDate.now()}.json") },
        onRestore = { chooseBackup.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
        onConfirmRestore = {
            pendingRestore?.let { pending ->
                pendingRestore = null
                if (pending.encrypted) {
                    restorePassphraseError = false
                    restorePassphraseFor = pending.uri
                } else {
                    restore(pending.uri, passphrase = null)
                }
            }
        },
        onDismissRestore = { pendingRestore = null },
        safetyCopyTakenAt = safetyCopy?.takenAt,
        onUndoRestore = { confirmUndo = true },
    )

    if (confirmUndo) {
        val snapshot = safetyCopy
        WhfinConfirmDialog(
            title = stringResource(R.string.backup_safety_confirm_title),
            body = stringResource(R.string.backup_safety_confirm_body),
            confirmLabel = stringResource(R.string.backup_safety_action),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = {
                confirmUndo = false
                if (snapshot != null) {
                    scope.launch {
                        uiState = BackupUiState.Restoring
                        // Going back is itself a restore, so it takes its own snapshot first: a
                        // mistaken undo must not become the one step without a way back.
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                snapshot.file.inputStream().use { manager.restore(it) }
                            }
                        }
                        safetyCopy = safetyBackup.latest()
                        uiState = result.fold(
                            onSuccess = { BackupUiState.Restored(it.rowCount) },
                            onFailure = { BackupUiState.Error },
                        )
                    }
                }
            },
            onDismiss = { confirmUndo = false },
        )
    }

    if (exportPassphraseSheet) {
        BackupPassphraseSheet(
            title = stringResource(R.string.backup_passphrase_title),
            body = stringResource(R.string.backup_passphrase_body),
            primaryLabel = stringResource(R.string.action_continue),
            requireConfirmation = true,
            errorText = null,
            onDismiss = { exportPassphraseSheet = false },
            onSubmit = { passphrase ->
                exportPassphraseSheet = false
                exportPassphrase = passphrase
                createEncryptedBackup.launch(
                    "whfin-backup-${LocalDate.now()}.${dev.whekin.whfin.data.backup.WhfinEncryptedBackupEnvelope.FILE_EXTENSION}",
                )
            },
        )
    }

    restorePassphraseFor?.let { uri ->
        BackupPassphraseSheet(
            title = stringResource(R.string.backup_restore_passphrase_title),
            body = stringResource(R.string.backup_restore_passphrase_body),
            primaryLabel = stringResource(R.string.backup_restore_start),
            requireConfirmation = false,
            errorText = if (restorePassphraseError) stringResource(R.string.backup_wrong_passphrase) else null,
            onDismiss = {
                restorePassphraseFor = null
                restorePassphraseError = false
            },
            onSubmit = { passphrase -> restore(uri, passphrase) },
        )
    }
}

@Composable
internal fun BackupPassphraseSheet(
    title: String,
    body: String,
    primaryLabel: String,
    requireConfirmation: Boolean,
    errorText: String?,
    onDismiss: () -> Unit,
    onSubmit: (CharArray) -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val tooShort = passphrase.isNotEmpty() && passphrase.length < 6
    val mismatch = requireConfirmation && repeat.isNotEmpty() && passphrase != repeat
    val valid = passphrase.length >= 6 && (!requireConfirmation || passphrase == repeat)
    WhfinFormSheet(
        title = title,
        onDismiss = onDismiss,
        primaryLabel = primaryLabel,
        primaryEnabled = valid,
        onPrimary = { onSubmit(passphrase.toCharArray()) },
    ) {
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WhfinField(
            value = passphrase,
            onValueChange = { passphrase = it.take(128) },
            label = stringResource(R.string.backup_passphrase),
            supportingText = when {
                errorText != null -> errorText
                tooShort -> stringResource(R.string.backup_passphrase_short)
                else -> null
            },
            isError = errorText != null || tooShort,
            keyboardType = KeyboardType.Password,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        if (requireConfirmation) {
            WhfinField(
                value = repeat,
                onValueChange = { repeat = it.take(128) },
                label = stringResource(R.string.backup_passphrase_repeat),
                supportingText = if (mismatch) stringResource(R.string.backup_passphrase_mismatch) else null,
                isError = mismatch,
                keyboardType = KeyboardType.Password,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun BackupScreen(
    uiState: BackupUiState,
    pendingRestore: PendingRestore?,
    driveSection: @Composable () -> Unit = {},
    onExportEncrypted: () -> Unit,
    onExportPlain: () -> Unit,
    onRestore: () -> Unit,
    onConfirmRestore: () -> Unit,
    onDismissRestore: () -> Unit,
    safetyCopyTakenAt: Instant? = null,
    onUndoRestore: () -> Unit = {},
) {
    val working = uiState == BackupUiState.Exporting || uiState == BackupUiState.Restoring
    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        WhfinNotice(
            title = stringResource(R.string.backup_notice_title),
            body = stringResource(R.string.backup_notice_body),
            icon = Icons.Default.Security,
            kind = WhfinNoticeKind.Attention,
            modifier = Modifier.fillMaxWidth(),
        )

        WhfinSectionLabel(stringResource(R.string.backup_copy_section))
        WhfinLedgerGroup(Modifier.fillMaxWidth()) {
            WhfinLedgerRow(
                title = stringResource(R.string.backup_copy_title),
                supportingText = stringResource(R.string.backup_copy_body),
                supportingMaxLines = 6,
                icon = Icons.Default.SaveAlt,
            )
        }
        WhfinButton(
            label = stringResource(R.string.backup_export_encrypted_action),
            onClick = onExportEncrypted,
            enabled = !working,
            leadingIcon = Icons.Default.Lock,
            modifier = Modifier.fillMaxWidth(),
        )
        WhfinButton(
            label = stringResource(R.string.backup_export_plain_action),
            onClick = onExportPlain,
            enabled = !working,
            style = WhfinActionStyle.Secondary,
            leadingIcon = Icons.Default.SaveAlt,
            modifier = Modifier.fillMaxWidth(),
        )
        WhfinButton(
            label = stringResource(R.string.backup_restore_action),
            onClick = onRestore,
            enabled = !working,
            style = WhfinActionStyle.DestructiveSecondary,
            leadingIcon = Icons.Default.Restore,
            modifier = Modifier.fillMaxWidth(),
        )

        // Only after a restore has happened is there something to go back to, so the row appears
        // exactly when it is useful instead of advertising a feature with nothing behind it.
        safetyCopyTakenAt?.let { takenAt ->
            WhfinSectionLabel(stringResource(R.string.backup_safety_section))
            WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                WhfinLedgerRow(
                    title = stringResource(R.string.backup_safety_title),
                    supportingText = stringResource(
                        R.string.backup_safety_body,
                        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                            .withLocale(Locale.getDefault())
                            .format(takenAt.atZone(ZoneId.systemDefault())),
                    ),
                    supportingMaxLines = 6,
                    icon = Icons.Default.History,
                )
            }
            WhfinButton(
                label = stringResource(R.string.backup_safety_action),
                onClick = onUndoRestore,
                enabled = !working,
                style = WhfinActionStyle.DestructiveSecondary,
                leadingIcon = Icons.Default.History,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        driveSection()

        when (uiState) {
            BackupUiState.Idle -> Unit
            BackupUiState.Exporting -> WhfinNotice(
                title = stringResource(R.string.backup_exporting_title),
                body = stringResource(R.string.backup_exporting_body),
                kind = WhfinNoticeKind.Unavailable,
                modifier = Modifier.fillMaxWidth(),
            )
            BackupUiState.Restoring -> WhfinNotice(
                title = stringResource(R.string.backup_restoring_title),
                body = stringResource(R.string.backup_restoring_body),
                kind = WhfinNoticeKind.Unavailable,
                modifier = Modifier.fillMaxWidth(),
            )
            is BackupUiState.Exported -> WhfinNotice(
                title = stringResource(R.string.backup_exported_title),
                body = stringResource(R.string.backup_exported_body, uiState.rowCount),
                kind = WhfinNoticeKind.Info,
                modifier = Modifier.fillMaxWidth(),
            )
            is BackupUiState.Restored -> WhfinNotice(
                title = stringResource(R.string.backup_restored_title),
                body = stringResource(R.string.backup_restored_body, uiState.rowCount),
                kind = WhfinNoticeKind.Info,
                modifier = Modifier.fillMaxWidth(),
            )
            BackupUiState.Error -> WhfinNotice(
                title = stringResource(R.string.backup_error_title),
                body = stringResource(R.string.backup_error_body),
                kind = WhfinNoticeKind.Error,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (pendingRestore != null) AlertDialog(
        onDismissRequest = onDismissRestore,
        title = { Text(stringResource(R.string.backup_restore_confirm_title)) },
        text = {
            Text(
                stringResource(R.string.backup_restore_confirm_body) +
                    if (pendingRestore.encrypted) "\n\n" + stringResource(R.string.backup_restore_encrypted_hint) else "",
            )
        },
        confirmButton = {
            WhfinButton(
                label = stringResource(R.string.backup_restore_confirm_action),
                onClick = onConfirmRestore,
                style = WhfinActionStyle.Destructive,
            )
        },
        dismissButton = {
            WhfinButton(
                label = stringResource(R.string.action_cancel),
                onClick = onDismissRestore,
                style = WhfinActionStyle.Quiet,
            )
        },
    )
}

@Preview(name = "Backup light", widthDp = 400, heightDp = 800, showBackground = true)
@Preview(name = "Backup dark", widthDp = 400, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Backup font 1.5", widthDp = 400, heightDp = 900, fontScale = 1.5f, showBackground = true)
@Preview(name = "Backup compact", widthDp = 400, heightDp = 500, showBackground = true)
@Composable
private fun BackupScreenPreview() {
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            BackupScreen(
                uiState = BackupUiState.Idle,
                pendingRestore = null,
                onExportEncrypted = {},
                onExportPlain = {},
                onRestore = {},
                onConfirmRestore = {},
                onDismissRestore = {},
            )
        }
    }
}
