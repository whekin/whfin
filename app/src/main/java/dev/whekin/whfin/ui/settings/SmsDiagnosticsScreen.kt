package dev.whekin.whfin.ui.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.SmsFailed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.WhfinActionStyle
import dev.whekin.whfin.core.ui.WhfinButton
import dev.whekin.whfin.core.ui.WhfinFormSheet
import dev.whekin.whfin.core.ui.WhfinField
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinLedgerRow
import dev.whekin.whfin.core.ui.WhfinNotice
import dev.whekin.whfin.core.ui.WhfinNoticeKind
import dev.whekin.whfin.core.ui.WhfinPaneState
import dev.whekin.whfin.core.ui.WhfinSectionLabel
import dev.whekin.whfin.core.ui.WhfinStatePane
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.PaymentInstrumentType
import dev.whekin.whfin.data.db.PaymentInstrumentEntity
import dev.whekin.whfin.data.db.SmsDiagnosticEntity
import dev.whekin.whfin.data.db.SmsDiagnosticKind
import dev.whekin.whfin.data.db.SmsDiagnosticOutcome
import dev.whekin.whfin.data.db.SmsDiagnosticReason
import dev.whekin.whfin.ui.formatMinor
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.text.DateFormat
import java.util.Date

@Composable
fun SmsDiagnosticsRoute(
    smsImportEnabled: Boolean,
    hasReceivePermission: Boolean,
    hasHistoryPermission: Boolean,
    canRequestHistoryPermission: Boolean,
    onRequestHistoryPermission: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    viewModel: SmsDiagnosticsViewModel = viewModel(),
) {
    val loadState by viewModel.loadState.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    var scanAfterPermission by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(hasHistoryPermission, scanAfterPermission) {
        if (hasHistoryPermission && scanAfterPermission) {
            scanAfterPermission = false
            viewModel.scanHistory()
        }
    }

    SmsDiagnosticsScreen(
        loadState = loadState,
        scanState = scanState,
        smsImportEnabled = smsImportEnabled,
        hasReceivePermission = hasReceivePermission,
        hasHistoryPermission = hasHistoryPermission,
        canRequestHistoryPermission = canRequestHistoryPermission,
        onScanHistory = {
            if (hasHistoryPermission) viewModel.scanHistory() else {
                scanAfterPermission = true
                if (canRequestHistoryPermission) onRequestHistoryPermission() else onOpenSystemSettings()
            }
        },
        onConfirmHistoryImport = viewModel::confirmHistoryImport,
        onCancelHistoryImport = viewModel::cancelHistoryImport,
        onResolve = viewModel::resolve,
        onAddCardMapping = viewModel::addCardMapping,
    )
}

@Composable
internal fun SmsDiagnosticsScreen(
    loadState: SmsDiagnosticsLoadState,
    scanState: SmsScanState,
    smsImportEnabled: Boolean,
    hasReceivePermission: Boolean,
    hasHistoryPermission: Boolean,
    canRequestHistoryPermission: Boolean,
    onScanHistory: () -> Unit,
    onConfirmHistoryImport: () -> Unit,
    onCancelHistoryImport: () -> Unit,
    onResolve: (Long, Long, PaymentInstrumentType) -> Unit,
    onAddCardMapping: (Long, String, PaymentInstrumentType) -> Unit,
) {
    var selectedDiagnosticId by rememberSaveable { mutableLongStateOf(0L) }
    var showAddCard by rememberSaveable { mutableStateOf(false) }
    val content = (loadState as? SmsDiagnosticsLoadState.Content)?.data
    val selectedDiagnostic = content?.diagnostics?.firstOrNull { it.id == selectedDiagnosticId }

    LazyColumn(
        Modifier.fillMaxSize().testTag("sms-diagnostics-list"),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = 12.dp,
            end = 20.dp,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item("monitoring") {
            MonitoringNotice(smsImportEnabled, hasReceivePermission)
        }
        if (content != null) {
            item("card-mappings-label") {
                WhfinSectionLabel(stringResource(R.string.sms_card_mappings_title))
            }
            item("card-mappings") {
                CardMappings(
                    mappings = content.cardMappings,
                    onAdd = { showAddCard = true },
                )
            }
        }
        item("history") {
            HistoryControl(
                state = scanState,
                hasHistoryPermission = hasHistoryPermission,
                canRequestHistoryPermission = canRequestHistoryPermission,
                onScan = onScanHistory,
                onConfirm = onConfirmHistoryImport,
                onCancel = onCancelHistoryImport,
            )
        }
        when (loadState) {
            SmsDiagnosticsLoadState.Loading -> item("loading") {
                WhfinStatePane(
                    WhfinPaneState.Loading,
                    stringResource(R.string.sms_diagnostics_title),
                    stringResource(R.string.sms_history_scanning),
                    Modifier.fillMaxWidth(),
                )
            }
            SmsDiagnosticsLoadState.Error -> item("error") {
                WhfinStatePane(
                    WhfinPaneState.Error,
                    stringResource(R.string.sms_diagnostics_error_title),
                    stringResource(R.string.sms_diagnostics_error_body),
                    Modifier.fillMaxWidth(),
                )
            }
            is SmsDiagnosticsLoadState.Content -> {
                val attention = loadState.data.diagnostics.filter(SmsDiagnosticEntity::needsAttention)
                val recent = loadState.data.diagnostics.filterNot(SmsDiagnosticEntity::needsAttention)
                if (attention.isEmpty() && recent.isEmpty()) {
                    item("empty") {
                        WhfinStatePane(
                            WhfinPaneState.Empty,
                            stringResource(R.string.sms_diagnostics_empty_title),
                            stringResource(R.string.sms_diagnostics_empty_body),
                            Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (attention.isNotEmpty()) {
                    item("attention-label") { WhfinSectionLabel(stringResource(R.string.sms_diagnostics_attention)) }
                    item("attention-group") {
                        DiagnosticGroup(attention) { selectedDiagnosticId = it.id }
                    }
                }
                if (recent.isNotEmpty()) {
                    item("recent-label") { WhfinSectionLabel(stringResource(R.string.sms_diagnostics_recent)) }
                    items(recent, key = { "diagnostic-${it.id}" }) { item ->
                        DiagnosticRow(item, onClick = null)
                    }
                }
            }
        }
    }

    if (selectedDiagnostic != null) {
        AccountMappingSheet(
            diagnostic = selectedDiagnostic,
            accounts = content.accounts,
            onDismiss = { selectedDiagnosticId = 0L },
            onSave = { accountId, cardType ->
                onResolve(selectedDiagnostic.id, accountId, cardType)
                selectedDiagnosticId = 0L
            },
        )
    }
    if (showAddCard && content != null) {
        AddCardMappingSheet(
            accounts = content.accounts,
            onDismiss = { showAddCard = false },
            onSave = { accountId, last4, cardType ->
                onAddCardMapping(accountId, last4, cardType)
                showAddCard = false
            },
        )
    }
}

@Composable
private fun CardMappings(
    mappings: List<SmsCardMapping>,
    onAdd: () -> Unit,
) {
    if (mappings.isEmpty()) {
        WhfinNotice(
            title = stringResource(R.string.sms_card_required_title),
            body = stringResource(R.string.sms_card_required_body),
            icon = Icons.Default.CreditCard,
            kind = WhfinNoticeKind.Attention,
            actionLabel = stringResource(R.string.sms_add_card_action),
            onAction = onAdd,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                mappings.forEachIndexed { index, mapping ->
                    WhfinLedgerRow(
                        title = stringResource(R.string.sms_card_suffix, mapping.instrument.last4),
                        supportingText = listOf(
                            mapping.account.label,
                            mapping.account.account.currency,
                            stringResource(
                                if (mapping.instrument.type == PaymentInstrumentType.VIRTUAL_CARD) {
                                    R.string.sms_card_virtual
                                } else {
                                    R.string.sms_card_physical
                                },
                            ),
                        ).joinToString(" · "),
                        icon = Icons.Default.CreditCard,
                        divider = index != mappings.lastIndex,
                    )
                }
            }
            WhfinButton(
                label = stringResource(R.string.sms_add_another_card_action),
                onClick = onAdd,
                style = WhfinActionStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AddCardMappingSheet(
    accounts: List<SmsAccountOption>,
    onDismiss: () -> Unit,
    onSave: (Long, String, PaymentInstrumentType) -> Unit,
) {
    var last4 by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableLongStateOf(accounts.singleOrNull()?.account?.id ?: 0L) }
    var cardType by rememberSaveable { mutableStateOf(PaymentInstrumentType.PHYSICAL_CARD) }
    val valid = last4.length == 4 && selectedId != 0L
    WhfinFormSheet(
        title = stringResource(R.string.sms_add_card_title),
        onDismiss = onDismiss,
        primaryLabel = stringResource(R.string.sms_save_card_action),
        primaryEnabled = valid,
        onPrimary = { if (valid) onSave(selectedId, last4, cardType) },
    ) {
        Text(
            stringResource(R.string.sms_add_card_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WhfinField(
            value = last4,
            onValueChange = { value -> last4 = value.filter(Char::isDigit).take(4) },
            label = stringResource(R.string.sms_last_four_label),
            placeholder = "2533",
            supportingText = stringResource(
                if (last4.isEmpty() || last4.length == 4) R.string.sms_last_four_support
                else R.string.sms_last_four_error,
            ),
            isError = last4.isNotEmpty() && last4.length != 4,
            keyboardType = KeyboardType.Number,
            leadingIcon = Icons.Default.AddCard,
            modifier = Modifier.fillMaxWidth(),
        )
        WhfinSectionLabel(stringResource(R.string.sms_account_label))
        if (accounts.isEmpty()) {
            WhfinNotice(
                title = stringResource(R.string.sms_no_bank_accounts_title),
                body = stringResource(R.string.sms_no_bank_accounts_body),
                icon = Icons.Default.AccountBalance,
                kind = WhfinNoticeKind.Unavailable,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                accounts.forEachIndexed { index, option ->
                    WhfinLedgerRow(
                        title = option.label,
                        supportingText = listOfNotNull(
                            option.account.iban?.takeLast(4)?.let { "••$it" },
                            option.account.currency,
                        ).joinToString(" · "),
                        icon = Icons.Default.AccountBalance,
                        trailing = if (selectedId == option.account.id) {{ Icon(Icons.Default.Check, null) }} else null,
                        onClick = { selectedId = option.account.id },
                        divider = index != accounts.lastIndex,
                    )
                }
            }
        }
        WhfinSectionLabel(stringResource(R.string.sms_card_type))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = cardType == PaymentInstrumentType.PHYSICAL_CARD,
                onClick = { cardType = PaymentInstrumentType.PHYSICAL_CARD },
                label = { Text(stringResource(R.string.sms_card_physical)) },
            )
            FilterChip(
                selected = cardType == PaymentInstrumentType.VIRTUAL_CARD,
                onClick = { cardType = PaymentInstrumentType.VIRTUAL_CARD },
                label = { Text(stringResource(R.string.sms_card_virtual)) },
            )
        }
    }
}

@Composable
private fun MonitoringNotice(enabled: Boolean, hasPermission: Boolean) {
    val (title, body, kind) = when {
        !enabled -> Triple(
            stringResource(R.string.sms_monitoring_off),
            stringResource(R.string.sms_monitoring_off_body),
            WhfinNoticeKind.Unavailable,
        )
        !hasPermission -> Triple(
            stringResource(R.string.sms_monitoring_permission),
            stringResource(R.string.sms_monitoring_permission_body),
            WhfinNoticeKind.Attention,
        )
        else -> Triple(
            stringResource(R.string.sms_monitoring_on),
            stringResource(R.string.sms_monitoring_on_body),
            WhfinNoticeKind.Info,
        )
    }
    WhfinNotice(
        title = title,
        body = body,
        icon = Icons.Default.MarkEmailRead,
        kind = kind,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun HistoryControl(
    state: SmsScanState,
    hasHistoryPermission: Boolean,
    canRequestHistoryPermission: Boolean,
    onScan: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    when (state) {
        SmsScanState.Idle -> WhfinNotice(
            title = stringResource(R.string.sms_history_title),
            body = stringResource(R.string.sms_history_body),
            icon = Icons.Default.History,
            kind = if (hasHistoryPermission) WhfinNoticeKind.Info else WhfinNoticeKind.Attention,
            actionLabel = if (!hasHistoryPermission && !canRequestHistoryPermission) {
                stringResource(R.string.permission_open_settings)
            } else if (!hasHistoryPermission) {
                stringResource(R.string.sms_read_permission)
            } else {
                stringResource(R.string.sms_history_action)
            },
            onAction = onScan,
            modifier = Modifier.fillMaxWidth(),
        )
        SmsScanState.Scanning, SmsScanState.Importing -> WhfinNotice(
            title = stringResource(R.string.sms_history_title),
            body = stringResource(
                if (state == SmsScanState.Scanning) R.string.sms_history_scanning
                else R.string.sms_history_importing,
            ),
            icon = Icons.Default.History,
            kind = WhfinNoticeKind.Unavailable,
            modifier = Modifier.fillMaxWidth(),
        )
        is SmsScanState.Preview -> WhfinNotice(
            title = stringResource(
                if (state.summary.total == 0) R.string.sms_history_empty_title
                else R.string.sms_history_preview_title,
            ),
            body = if (state.summary.total == 0) stringResource(R.string.sms_history_empty_preview) else stringResource(
                R.string.sms_history_preview_body,
                state.summary.total,
                state.summary.importable,
                state.summary.duplicates,
                state.summary.needsAttention,
                state.summary.ignored,
            ),
            icon = Icons.Default.CheckCircle,
            kind = WhfinNoticeKind.Info,
            modifier = Modifier.fillMaxWidth(),
            trailing = {
                WhfinButton(stringResource(R.string.sms_history_cancel), onCancel, style = WhfinActionStyle.Quiet)
            },
            actionLabel = if (state.summary.total > 0) stringResource(R.string.sms_history_import) else null,
            onAction = if (state.summary.total > 0) onConfirm else null,
        )
        is SmsScanState.Complete -> WhfinNotice(
            title = stringResource(R.string.sms_history_complete_title),
            body = stringResource(R.string.sms_history_complete_body, state.imported, state.needsAttention),
            icon = Icons.Default.CheckCircle,
            kind = if (state.needsAttention > 0) WhfinNoticeKind.Attention else WhfinNoticeKind.Info,
            actionLabel = stringResource(R.string.sms_history_action),
            onAction = onScan,
            modifier = Modifier.fillMaxWidth(),
        )
        SmsScanState.Error -> WhfinNotice(
            title = stringResource(R.string.sms_history_error_title),
            body = stringResource(R.string.sms_history_error_body),
            icon = Icons.Default.ErrorOutline,
            kind = WhfinNoticeKind.Error,
            actionLabel = stringResource(R.string.action_retry),
            onAction = onScan,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DiagnosticGroup(items: List<SmsDiagnosticEntity>, onClick: (SmsDiagnosticEntity) -> Unit) {
    WhfinLedgerGroup(Modifier.fillMaxWidth(), tonal = true) {
        items.forEachIndexed { index, item ->
            val canResolve = item.outcome == SmsDiagnosticOutcome.NEEDS_CARD_MAPPING ||
                item.outcome == SmsDiagnosticOutcome.CHOOSE_ACCOUNT
            DiagnosticRow(
                item,
                onClick = if (canResolve) {{ onClick(item) }} else null,
                divider = index != items.lastIndex,
            )
        }
    }
}

@Composable
private fun DiagnosticRow(
    item: SmsDiagnosticEntity,
    onClick: (() -> Unit)?,
    divider: Boolean = false,
) {
    val presentation = diagnosticPresentation(item)
    val amount = item.amountMinor?.let { amount ->
        item.currency?.let { formatMinor(amount, it) }
    }
    val kind = stringResource(item.kind.labelResource())
    val details = listOfNotNull(
        amount,
        item.cardLast4?.let { stringResource(R.string.sms_card_suffix, it) },
        item.counterparty,
    ).joinToString(" · ").ifBlank { kind }
    val time = remember(item.receivedAt) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.receivedAt))
    }
    WhfinLedgerRow(
        title = stringResource(presentation.title),
        supportingText = stringResource(R.string.sms_diagnostic_detail, details, time),
        supportingMaxLines = 3,
        icon = presentation.icon,
        iconTint = presentation.color(),
        markerColor = if (item.needsAttention()) presentation.color() else null,
        trailing = if (onClick != null) {{ Icon(Icons.Default.Link, null, tint = presentation.color()) }} else null,
        onClick = onClick,
        divider = divider,
        modifier = Modifier.fillMaxWidth(),
    )
}

private data class DiagnosticPresentation(
    val title: Int,
    val icon: ImageVector,
    val color: @Composable () -> Color,
)

@Composable
private fun diagnosticPresentation(item: SmsDiagnosticEntity): DiagnosticPresentation = when (item.outcome) {
    SmsDiagnosticOutcome.IMPORTED -> DiagnosticPresentation(
        R.string.sms_outcome_imported, Icons.Default.CheckCircle,
    ) { MaterialTheme.colorScheme.primary }
    SmsDiagnosticOutcome.DUPLICATE -> DiagnosticPresentation(
        R.string.sms_outcome_duplicate, Icons.Default.Check,
    ) { MaterialTheme.colorScheme.onSurfaceVariant }
    SmsDiagnosticOutcome.IGNORED -> DiagnosticPresentation(
        R.string.sms_outcome_ignored, Icons.Default.Block,
    ) { MaterialTheme.colorScheme.onSurfaceVariant }
    SmsDiagnosticOutcome.UNRECOGNIZED -> DiagnosticPresentation(
        R.string.sms_outcome_unrecognized, Icons.Default.SmsFailed,
    ) { MaterialTheme.colorScheme.tertiary }
    SmsDiagnosticOutcome.NEEDS_CARD_MAPPING, SmsDiagnosticOutcome.CHOOSE_ACCOUNT -> DiagnosticPresentation(
        R.string.sms_outcome_mapping,
        if (item.cardLast4 != null) Icons.Default.CreditCard else Icons.Default.AccountBalance,
    ) { MaterialTheme.colorScheme.tertiary }
    SmsDiagnosticOutcome.ERROR -> DiagnosticPresentation(
        R.string.sms_outcome_error, Icons.Default.ErrorOutline,
    ) { MaterialTheme.colorScheme.error }
}

private fun SmsDiagnosticEntity.needsAttention(): Boolean = when (outcome) {
    SmsDiagnosticOutcome.NEEDS_CARD_MAPPING,
    SmsDiagnosticOutcome.CHOOSE_ACCOUNT,
    SmsDiagnosticOutcome.UNRECOGNIZED,
    SmsDiagnosticOutcome.ERROR -> true
    else -> false
}

private fun SmsDiagnosticKind.labelResource(): Int = when (this) {
    SmsDiagnosticKind.CARD_PAYMENT -> R.string.sms_kind_card
    SmsDiagnosticKind.OUTGOING_TRANSFER -> R.string.sms_kind_outgoing
    SmsDiagnosticKind.INCOMING_TRANSFER -> R.string.sms_kind_incoming
    SmsDiagnosticKind.OWN_TRANSFER -> R.string.sms_kind_own_transfer
    SmsDiagnosticKind.CURRENCY_EXCHANGE -> R.string.sms_kind_exchange
    SmsDiagnosticKind.IGNORED -> R.string.sms_kind_ignored
    SmsDiagnosticKind.UNRECOGNIZED -> R.string.sms_kind_unknown
}

@Composable
private fun AccountMappingSheet(
    diagnostic: SmsDiagnosticEntity,
    accounts: List<SmsAccountOption>,
    onDismiss: () -> Unit,
    onSave: (Long, PaymentInstrumentType) -> Unit,
) {
    val currency = diagnostic.balanceCurrency ?: diagnostic.currency ?: "—"
    val matching = remember(accounts, currency) { accounts.filter { it.account.currency == currency } }
    var selectedId by remember(diagnostic.id, matching) { mutableLongStateOf(matching.singleOrNull()?.account?.id ?: 0L) }
    var cardType by remember(diagnostic.id) { mutableStateOf(PaymentInstrumentType.PHYSICAL_CARD) }
    WhfinFormSheet(
        title = stringResource(R.string.sms_choose_account_title),
        onDismiss = onDismiss,
        primaryLabel = stringResource(R.string.sms_link_action),
        primaryEnabled = selectedId != 0L,
        onPrimary = { if (selectedId != 0L) onSave(selectedId, cardType) },
    ) {
        Text(
            stringResource(R.string.sms_choose_account_body, currency),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (matching.isEmpty()) {
            WhfinNotice(
                title = stringResource(R.string.sms_outcome_mapping),
                body = stringResource(R.string.sms_no_matching_accounts, currency),
                kind = WhfinNoticeKind.Unavailable,
                icon = Icons.Default.AccountBalance,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                matching.forEachIndexed { index, option ->
                    WhfinLedgerRow(
                        title = option.label,
                        supportingText = listOfNotNull(option.account.iban?.takeLast(4)?.let { "••$it" }, currency)
                            .joinToString(" · "),
                        icon = Icons.Default.AccountBalance,
                        trailing = if (selectedId == option.account.id) {{ Icon(Icons.Default.Check, null) }} else null,
                        onClick = { selectedId = option.account.id },
                        divider = index != matching.lastIndex,
                    )
                }
            }
        }
        if (diagnostic.cardLast4 != null) {
            WhfinSectionLabel(stringResource(R.string.sms_card_type))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = cardType == PaymentInstrumentType.PHYSICAL_CARD,
                    onClick = { cardType = PaymentInstrumentType.PHYSICAL_CARD },
                    label = { Text(stringResource(R.string.sms_card_physical)) },
                )
                FilterChip(
                    selected = cardType == PaymentInstrumentType.VIRTUAL_CARD,
                    onClick = { cardType = PaymentInstrumentType.VIRTUAL_CARD },
                    label = { Text(stringResource(R.string.sms_card_virtual)) },
                )
            }
        }
    }
}

private val previewDiagnostics = listOf(
    SmsDiagnosticEntity(
        id = 1,
        externalKey = "preview-1",
        kind = SmsDiagnosticKind.CARD_PAYMENT,
        outcome = SmsDiagnosticOutcome.NEEDS_CARD_MAPPING,
        reason = SmsDiagnosticReason.NO_CARD_MAPPING,
        receivedAt = 1_753_000_000_000,
        amountMinor = 2380,
        currency = "GEL",
        balanceCurrency = "GEL",
        cardLast4 = "2533",
        counterparty = "Goodwill",
        updatedAt = 1_753_000_000_000,
    ),
    SmsDiagnosticEntity(
        id = 2,
        externalKey = "preview-2",
        kind = SmsDiagnosticKind.CARD_PAYMENT,
        outcome = SmsDiagnosticOutcome.IMPORTED,
        receivedAt = 1_752_900_000_000,
        amountMinor = 1240,
        currency = "USD",
        cardLast4 = "7586",
        counterparty = "Spotify",
        updatedAt = 1_752_900_000_000,
    ),
)

@Preview(name = "SMS diagnostics light", widthDp = 400, heightDp = 850, showBackground = true)
@Preview(name = "SMS diagnostics dark", widthDp = 400, heightDp = 850, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "SMS diagnostics font 1.5", widthDp = 400, heightDp = 950, fontScale = 1.5f, showBackground = true)
@Preview(name = "SMS diagnostics compact", widthDp = 400, heightDp = 500, showBackground = true)
@Composable
private fun SmsDiagnosticsPreview() {
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SmsDiagnosticsScreen(
                loadState = SmsDiagnosticsLoadState.Content(
                    SmsDiagnosticsData(
                        diagnostics = previewDiagnostics,
                        accounts = listOf(
                            SmsAccountOption(AccountEntity(1, "Main", AccountType.BANK, 1, "GEL"), "Credo"),
                        ),
                        cardMappings = listOf(
                            SmsCardMapping(
                                PaymentInstrumentEntity(
                                    id = 1,
                                    groupId = 1,
                                    type = PaymentInstrumentType.PHYSICAL_CARD,
                                    last4 = "2533",
                                ),
                                SmsAccountOption(AccountEntity(1, "Main", AccountType.BANK, 1, "GEL"), "Credo"),
                            ),
                        ),
                    ),
                ),
                scanState = SmsScanState.Preview(SmsScanSummary(12, 5, 4, 1, 2)),
                smsImportEnabled = true,
                hasReceivePermission = true,
                hasHistoryPermission = true,
                canRequestHistoryPermission = true,
                onScanHistory = {},
                onConfirmHistoryImport = {},
                onCancelHistoryImport = {},
                onResolve = { _, _, _ -> },
                onAddCardMapping = { _, _, _ -> },
            )
        }
    }
}

@Preview(name = "SMS diagnostics empty", widthDp = 400, heightDp = 700, showBackground = true)
@Preview(name = "SMS diagnostics unavailable", widthDp = 400, heightDp = 700, showBackground = true)
@Composable
private fun SmsDiagnosticsEmptyPreview() {
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            SmsDiagnosticsScreen(
                loadState = SmsDiagnosticsLoadState.Content(SmsDiagnosticsData()),
                scanState = SmsScanState.Idle,
                smsImportEnabled = false,
                hasReceivePermission = false,
                hasHistoryPermission = false,
                canRequestHistoryPermission = true,
                onScanHistory = {},
                onConfirmHistoryImport = {},
                onCancelHistoryImport = {},
                onResolve = { _, _, _ -> },
                onAddCardMapping = { _, _, _ -> },
            )
        }
    }
}
