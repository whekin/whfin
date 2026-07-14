package dev.whekin.whfin.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.whekin.whfin.R
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.StatementImportEntity
import dev.whekin.whfin.data.db.StatementImportOrigin
import dev.whekin.whfin.data.importer.StatementCoverage
import dev.whekin.whfin.data.importer.StatementImporter
import dev.whekin.whfin.ui.formatMinor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import dev.whekin.whfin.core.ui.WhfinActionStyle
import dev.whekin.whfin.core.ui.WhfinButton
import dev.whekin.whfin.core.ui.WhfinIconButton
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinLedgerRow
import dev.whekin.whfin.core.ui.WhfinNotice
import dev.whekin.whfin.core.ui.WhfinNoticeKind
import dev.whekin.whfin.core.ui.WhfinPaneState
import dev.whekin.whfin.core.ui.WhfinStatePane
import androidx.compose.ui.tooling.preview.Preview
import android.content.res.Configuration
import dev.whekin.whfin.ui.theme.WhfinTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankStatementsScreen(viewModel: BankStatementsViewModel = viewModel()) {
    val context = LocalContext.current
    val histories by viewModel.histories.collectAsState()
    val cardHistories by viewModel.cardHistories.collectAsState()
    val state by viewModel.importState.collectAsState()
    var reviewing by remember { mutableStateOf<AccountStatementHistory?>(null) }
    var removing by remember { mutableStateOf<StatementImportEntity?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.importStatements(uris.map { uri ->
            statementFileName(context, uri) to { context.contentResolver.openInputStream(uri) }
        })
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(stringResource(R.string.statements_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            WhfinButton(
                label = stringResource(R.string.statements_upload),
                onClick = { picker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) },
                enabled = state !is StatementImportUiState.Running,
                leadingIcon = Icons.Default.FileUpload,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        when (val current = state) {
            is StatementImportUiState.Running -> item { ImportProgressCard(current) }
            is StatementImportUiState.Success -> item {
                ImportResultCard(current.files, onDone = viewModel::dismissResult)
            }
            is StatementImportUiState.Error -> item {
                ImportErrorCard(current.message, onDismiss = viewModel::dismissResult)
            }
            StatementImportUiState.Idle -> Unit
        }
        items(histories, key = { it.account.id }) { history ->
            AccountHistoryCard(
                history = history,
                onReview = { reviewing = history },
                onRemoveImport = { removing = it },
            )
        }
        items(cardHistories, key = { "card-${it.source.id}" }) { history ->
            CardSourceHistoryCard(history)
        }
        if (histories.isEmpty() && cardHistories.isEmpty() && state is StatementImportUiState.Idle) item {
            WhfinStatePane(
                state = WhfinPaneState.Empty,
                title = stringResource(R.string.statements_never),
                body = stringResource(R.string.statements_empty_body),
            )
        }
    }
    reviewing?.let { history ->
        ReviewSheet(
            history = history,
            onDismiss = { reviewing = null },
            onKeep = viewModel::keepIssue,
            onDelete = viewModel::deleteDraft,
        )
    }
    removing?.let { item ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.statements_remove_title)) },
            text = { Text(stringResource(R.string.statements_remove_body)) },
            confirmButton = {
                WhfinButton(
                    label = stringResource(R.string.statements_remove_action),
                    onClick = {
                        viewModel.removeNoEffectImport(item)
                        removing = null
                    },
                    style = WhfinActionStyle.Destructive,
                )
            },
            dismissButton = {
                WhfinButton(
                    label = stringResource(R.string.action_cancel),
                    onClick = { removing = null },
                    style = WhfinActionStyle.Quiet,
                )
            },
        )
    }
}

/** Compact import feedback used when statements are picked from the Accounts add flow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatementImportStatusSheet(
    state: StatementImportUiState,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { if (state !is StatementImportUiState.Running) onDismiss() },
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).padding(bottom = 20.dp)) {
            when (state) {
                is StatementImportUiState.Running -> ImportProgressCard(state)
                is StatementImportUiState.Success -> ImportResultCard(state.files, onDismiss)
                is StatementImportUiState.Error -> ImportErrorCard(state.message, onDismiss)
                StatementImportUiState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun CardSourceHistoryCard(history: CardStatementHistory) {
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val from = history.imports.mapNotNull { it.periodFrom }.minOrNull()?.let(LocalDate::ofEpochDay)
    val to = history.imports.mapNotNull { it.periodTo }.maxOrNull()?.let(LocalDate::ofEpochDay)
    WhfinLedgerGroup(tonal = true) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(history.source.label, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(
                    if (history.instrument.type == dev.whekin.whfin.data.db.PaymentInstrumentType.VIRTUAL_CARD)
                        R.string.instrument_virtual_card else R.string.instrument_physical_card,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (from != null && to != null) stringResource(R.string.statements_coverage, from.format(formatter), to.format(formatter))
                else stringResource(R.string.statements_never),
            )
        }
    }
}

@Composable
private fun ImportProgressCard(state: StatementImportUiState.Running) {
    WhfinLedgerGroup(tonal = true) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator()
            Column(Modifier.padding(start = 18.dp)) {
                Text(stringResource(R.string.statements_working), style = MaterialTheme.typography.titleMedium)
                if (state.totalFiles > 1) Text(
                    stringResource(R.string.statements_file_progress, state.fileNumber, state.totalFiles),
                    style = MaterialTheme.typography.labelLarge,
                )
                state.fileName?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
                Text(
                    stringResource(
                        when (state.phase) {
                            StatementImporter.Phase.READING -> R.string.statements_phase_reading
                            StatementImporter.Phase.IMPORTING -> R.string.statements_phase_importing
                            StatementImporter.Phase.RECONCILING -> R.string.statements_phase_reconciling
                            StatementImporter.Phase.VERIFYING -> R.string.statements_phase_verifying
                        },
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ImportResultCard(files: List<StatementImportUiState.FileResult>, onDone: () -> Unit) {
    WhfinLedgerGroup(tonal = true) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.statements_done), style = MaterialTheme.typography.titleLarge)
            files.forEachIndexed { index, file ->
                val result = file.result
                WhfinLedgerRow(
                    title = file.fileName?.let(::statementDisplayName)
                        ?: stringResource(R.string.statements_file_unnamed),
                    supportingText = result?.let {
                        stringResource(R.string.statements_file_result, it.inserted, it.duplicates, it.reconciled)
                    } ?: file.error,
                    markerColor = if (file.error == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    divider = index != files.lastIndex,
                )
            }
            WhfinButton(
                stringResource(R.string.action_done), onDone,
                Modifier.align(Alignment.End), style = WhfinActionStyle.Quiet,
            )
        }
    }
}

private val credoStatementName = Regex(
    """MYCREDO_(GE[0-9A-Z]+)_([A-Z]{3})_STATEMENT_(\d{4})_(\d{2})_(\d{2}).*""",
    RegexOption.IGNORE_CASE,
)

private fun statementDisplayName(fileName: String): String {
    val match = credoStatementName.matchEntire(fileName) ?: return fileName
    val (iban, currency, year, month, day) = match.destructured
    return "Credo ${currency.uppercase()} •${iban.takeLast(4)} · $year-$month-$day"
}

fun statementFileName(context: android.content.Context, uri: Uri): String? =
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }

@Composable
private fun ImportErrorCard(message: String, onDismiss: () -> Unit) {
    WhfinNotice(
        title = stringResource(R.string.statements_error),
        body = message,
        kind = WhfinNoticeKind.Error,
        actionLabel = stringResource(R.string.action_done),
        onAction = onDismiss,
    )
}

@Composable
private fun AccountHistoryCard(
    history: AccountStatementHistory,
    onReview: () -> Unit,
    onRemoveImport: (StatementImportEntity) -> Unit,
) {
    val from = history.imports.mapNotNull { it.periodFrom }.minOrNull()?.let(LocalDate::ofEpochDay)
    val to = history.imports.mapNotNull { it.periodTo }.maxOrNull()?.let(LocalDate::ofEpochDay)
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val gaps = StatementCoverage.gaps(history.imports)
    WhfinLedgerGroup(tonal = true) {
        Column(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(history.account.name, style = MaterialTheme.typography.titleMedium)
                history.account.iban?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(
                    if (from != null && to != null) stringResource(
                        R.string.statements_coverage,
                        from.format(formatter), to.format(formatter),
                    ) else stringResource(R.string.statements_never),
                )
                gaps.firstOrNull()?.let { gap ->
                    Text(
                        stringResource(
                            R.string.statements_gap,
                            gap.from.format(formatter), gap.to.format(formatter),
                        ),
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (history.reviewItems.isNotEmpty()) {
                    WhfinButton(
                        stringResource(R.string.statements_review_count, history.reviewItems.size), onReview,
                        Modifier.align(Alignment.End), style = WhfinActionStyle.Secondary,
                    )
                }
            }
            history.imports.forEachIndexed { index, item ->
                val itemFrom = item.periodFrom?.let(LocalDate::ofEpochDay)?.format(formatter) ?: "—"
                val itemTo = item.periodTo?.let(LocalDate::ofEpochDay)?.format(formatter) ?: "—"
                val origin = stringResource(
                    if (item.origin == StatementImportOrigin.CREDO_SYNC) R.string.statements_origin_credo
                    else R.string.statements_origin_file,
                )
                val result = stringResource(
                    R.string.statements_history_result,
                    item.totalRows,
                    item.inserted,
                    item.duplicates,
                )
                WhfinLedgerRow(
                    title = "$origin · $itemFrom — $itemTo",
                    supportingText = item.fileName?.let(::statementDisplayName)?.let { "$it\n$result" } ?: result,
                    supportingMaxLines = 3,
                    divider = index != history.imports.lastIndex,
                    trailing = if (item.canRemoveFromHistory) {
                        {
                            WhfinIconButton(
                                icon = Icons.Outlined.DeleteOutline,
                                contentDescription = stringResource(R.string.statements_remove_action),
                                onClick = { onRemoveImport(item) },
                                outlined = false,
                            )
                        }
                    } else null,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewSheet(
    history: AccountStatementHistory,
    onDismiss: () -> Unit,
    onKeep: (dev.whekin.whfin.data.db.ReconciliationIssueWithTransaction) -> Unit,
    onDelete: (dev.whekin.whfin.data.db.ReconciliationIssueWithTransaction) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.statements_review_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.statements_review_explanation),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
            )
            history.reviewItems.forEach { item ->
                WhfinLedgerGroup(Modifier.fillMaxWidth().padding(bottom = 10.dp), tonal = true) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            item.transaction.rawCounterparty ?: item.transaction.note
                                ?: stringResource(R.string.feed_no_description),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(formatMinor(item.transaction.amountMinor, item.transaction.currency, withSign = true))
                        Text(stringResource(R.string.statements_not_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            WhfinButton(
                                stringResource(R.string.action_delete_draft), { onDelete(item) },
                                style = WhfinActionStyle.Destructive,
                            )
                            WhfinButton(
                                stringResource(R.string.action_keep_separate), { onKeep(item) },
                                Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Preview(name = "Statements importing", widthDp = 400, heightDp = 420, showBackground = true)
@Composable
private fun StatementImportRunningPreview() {
    WhfinTheme {
        Surface(Modifier.padding(20.dp), color = MaterialTheme.colorScheme.background) {
            ImportProgressCard(
                StatementImportUiState.Running(
                    StatementImporter.Phase.RECONCILING,
                    "MYCREDO_GE00CD0000000000000001_GEL_STATEMENT.xlsx",
                    2,
                    6,
                ),
            )
        }
    }
}

@Preview(name = "Statements result", widthDp = 400, heightDp = 800, showBackground = true)
@Preview(name = "Statements result dark", widthDp = 400, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Statements result font 1.5", widthDp = 400, heightDp = 1000, fontScale = 1.5f, showBackground = true)
@Composable
private fun StatementImportResultPreview() {
    val success = StatementImportUiState.FileResult(
        "MYCREDO_GE00CD0000000000000001_GEL_STATEMENT_2026_04_05.xlsx",
        StatementImporter.Result(1, true, 120, 120, 0, 0, 1, 0),
    )
    val error = StatementImportUiState.FileResult("broken-statement.xlsx", error = "Unsupported workbook")
    WhfinTheme {
        Surface(Modifier.padding(20.dp), color = MaterialTheme.colorScheme.background) {
            ImportResultCard(listOf(success, error), {})
        }
    }
}

@Preview(name = "Statement history", widthDp = 400, heightDp = 760, showBackground = true)
@Preview(name = "Statement history dark", widthDp = 400, heightDp = 760, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Statement history font 1.5", widthDp = 400, heightDp = 980, fontScale = 1.5f, showBackground = true)
@Composable
private fun StatementHistoryPreview() {
    val from = LocalDate.of(2026, 1, 1).toEpochDay()
    val to = LocalDate.of(2026, 7, 14).toEpochDay()
    val account = AccountEntity(
        id = 1,
        name = "Everyday",
        type = AccountType.BANK,
        groupId = 1,
        currency = "GEL",
        iban = "GE00CD0000000000000001",
    )
    val imports = listOf(
        StatementImportEntity(
            id = 2,
            accountId = 1,
            fileName = "MYCREDO_GE00CD0000000000000001_GEL_STATEMENT_2026_07_14.xlsx",
            origin = StatementImportOrigin.CREDO_SYNC,
            periodFrom = LocalDate.of(2026, 4, 15).toEpochDay(),
            periodTo = to,
            openingBalanceMinor = 16_318,
            closingBalanceMinor = 18_738,
            totalRows = 186,
            inserted = 42,
            duplicates = 144,
            reconciled = 0,
            importedAt = 2,
        ),
        StatementImportEntity(
            id = 1,
            accountId = 1,
            fileName = "Credo Statement Q1.xlsx",
            origin = StatementImportOrigin.FILE,
            periodFrom = from,
            periodTo = LocalDate.of(2026, 4, 14).toEpochDay(),
            openingBalanceMinor = 8_100,
            closingBalanceMinor = 16_318,
            totalRows = 218,
            inserted = 0,
            duplicates = 218,
            reconciled = 0,
            importedAt = 1,
        ),
    )
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(20.dp)) {
                AccountHistoryCard(AccountStatementHistory(account, imports, emptyList()), {}, {})
            }
        }
    }
}
