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
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Security
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
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinLedgerRow
import dev.whekin.whfin.core.ui.WhfinNotice
import dev.whekin.whfin.core.ui.WhfinNoticeKind
import dev.whekin.whfin.core.ui.WhfinSectionLabel
import dev.whekin.whfin.data.backup.WhfinBackupManager
import dev.whekin.whfin.data.backup.WhfinBackupMetadata
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.time.Instant
import java.time.LocalDate
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

@Composable
fun BackupRoute(appVersion: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val resolver = context.contentResolver
    val manager = remember(context.applicationContext) {
        WhfinBackupManager((context.applicationContext as WhfinApp).db)
    }
    val scope = rememberCoroutineScope()
    var uiState by remember { mutableStateOf<BackupUiState>(BackupUiState.Idle) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) scope.launch {
            uiState = BackupUiState.Exporting
            uiState = runCatching {
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri, "wt")?.use { output ->
                        manager.export(
                            output,
                            WhfinBackupMetadata(
                                exportedAt = Instant.now(),
                                appVersion = appVersion,
                                primaryCurrency = "GEL",
                            ),
                        )
                    } ?: error("Could not open the selected backup destination.")
                }
            }.fold(
                onSuccess = { BackupUiState.Exported(it.rowCount) },
                onFailure = { BackupUiState.Error },
            )
        }
    }
    val chooseBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> pendingRestoreUri = uri }

    fun restore(uri: Uri) {
        pendingRestoreUri = null
        scope.launch {
            uiState = BackupUiState.Restoring
            uiState = runCatching {
                withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)?.use { input -> manager.restore(input) }
                        ?: error("Could not open the selected backup file.")
                }
            }.fold(
                onSuccess = { BackupUiState.Restored(it.rowCount) },
                onFailure = { BackupUiState.Error },
            )
        }
    }

    BackupScreen(
        uiState = uiState,
        showRestoreConfirmation = pendingRestoreUri != null,
        onExport = {
            createBackup.launch("whfin-backup-${LocalDate.now()}.json")
        },
        onRestore = { chooseBackup.launch(arrayOf("application/json", "text/plain")) },
        onConfirmRestore = { pendingRestoreUri?.let(::restore) },
        onDismissRestore = { pendingRestoreUri = null },
    )
}

@Composable
internal fun BackupScreen(
    uiState: BackupUiState,
    showRestoreConfirmation: Boolean,
    onExport: () -> Unit,
    onRestore: () -> Unit,
    onConfirmRestore: () -> Unit,
    onDismissRestore: () -> Unit,
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
            label = stringResource(R.string.backup_export_action),
            onClick = onExport,
            enabled = !working,
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

    if (showRestoreConfirmation) AlertDialog(
        onDismissRequest = onDismissRestore,
        title = { Text(stringResource(R.string.backup_restore_confirm_title)) },
        text = { Text(stringResource(R.string.backup_restore_confirm_body)) },
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
                showRestoreConfirmation = false,
                onExport = {},
                onRestore = {},
                onConfirmRestore = {},
                onDismissRestore = {},
            )
        }
    }
}
