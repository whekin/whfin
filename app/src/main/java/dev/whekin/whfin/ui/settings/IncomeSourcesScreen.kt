package dev.whekin.whfin.ui.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.WhfinActionStyle
import dev.whekin.whfin.core.ui.WhfinButton
import dev.whekin.whfin.core.ui.WhfinField
import dev.whekin.whfin.core.ui.WhfinFieldLabel
import dev.whekin.whfin.core.ui.WhfinFormSheet
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinLedgerRow
import dev.whekin.whfin.core.ui.WhfinNotice
import dev.whekin.whfin.core.ui.WhfinNoticeKind
import dev.whekin.whfin.core.ui.WhfinPaneState
import dev.whekin.whfin.core.ui.WhfinSectionLabel
import dev.whekin.whfin.core.ui.WhfinStatePane
import dev.whekin.whfin.data.crypto.CryptoBankTransfer
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.IncomeSourceEntity
import dev.whekin.whfin.data.income.IncomeExpectation
import dev.whekin.whfin.ui.formatMinor
import dev.whekin.whfin.ui.parseToMinor
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@Composable
fun IncomeSourcesRoute(viewModel: IncomeSourcesViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.refreshFromChain() }
    IncomeSourcesScreen(
        state = state,
        onSave = viewModel::save,
        onEnd = viewModel::end,
        onDelete = viewModel::delete,
        onRefresh = viewModel::refreshFromChain,
        onLink = viewModel::link,
        onUnlink = viewModel::unlink,
    )
}

@Composable
fun IncomeSourcesScreen(
    state: IncomeSourcesState?,
    onSave: (IncomeSourceEntity?, String, Long, String, Long?, Int, Int, Long) -> Unit,
    onEnd: (IncomeSourceEntity) -> Unit,
    onDelete: (IncomeSourceEntity) -> Unit,
    onRefresh: () -> Unit = {},
    onLink: (CryptoBankTransfer) -> Unit = {},
    onUnlink: (CryptoBankTransfer) -> Unit = {},
) {
    var editing by remember { mutableStateOf<IncomeSourceEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var selectedTransfer by remember { mutableStateOf<CryptoBankTransfer?>(null) }

    if (state == null) {
        WhfinStatePane(
            state = WhfinPaneState.Loading,
            title = stringResource(R.string.income_sources_title),
            body = stringResource(R.string.income_sources_loading),
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        WhfinNotice(
            title = stringResource(R.string.income_sources_explainer_title),
            body = stringResource(R.string.income_sources_explainer_body),
            icon = Icons.Default.SouthWest,
            kind = WhfinNoticeKind.Info,
            actionLabel = stringResource(
                if (state.isReadingChain) R.string.income_sources_reading else R.string.income_sources_recheck,
            ),
            onAction = onRefresh,
        )

        WhfinLedgerGroup(Modifier.fillMaxWidth()) {
            state.expectations.forEach { expectation ->
                val account = state.accounts.firstOrNull { it.id == expectation.source.accountId }
                WhfinLedgerRow(
                    title = expectation.source.label,
                    supportingText = statusLine(expectation, account),
                    onClick = { editing = expectation.source },
                    divider = true,
                )
            }
            WhfinLedgerRow(
                title = stringResource(R.string.income_sources_add),
                icon = Icons.Default.Add,
                onClick = { creating = true },
            )
        }

        state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (state.transfers.isNotEmpty() || state.linkedTransfers.isNotEmpty()) {
            WhfinSectionLabel(stringResource(R.string.crypto_bridge_title))
            Text(stringResource(R.string.crypto_bridge_body), style = MaterialTheme.typography.bodySmall)
            WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                (state.transfers + state.linkedTransfers).forEach { transfer ->
                    val linked = transfer.withdrawal.transferGroupId != null
                    WhfinLedgerRow(
                        title = "${formatMinor(-transfer.withdrawal.amountMinor, transfer.withdrawal.currency)} → ${formatMinor(transfer.credit.amountMinor, transfer.credit.currency)}",
                        supportingText = listOf(
                            transfer.credit.rawCounterparty ?: state.accounts.firstOrNull { it.id == transfer.credit.accountId }?.name.orEmpty(),
                            Instant.ofEpochMilli(transfer.credit.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate().toString(),
                            stringResource(if (linked) R.string.crypto_bridge_linked else R.string.crypto_bridge_review),
                        ).joinToString(" · "),
                        onClick = { selectedTransfer = transfer }, divider = true,
                    )
                }
            }
        }

        if (state.ended.isNotEmpty()) {
            WhfinSectionLabel(stringResource(R.string.income_sources_past))
            WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                state.ended.forEach { source ->
                    WhfinLedgerRow(
                        title = source.label,
                        supportingText = stringResource(
                            R.string.income_sources_past_range,
                            LocalDate.ofEpochDay(source.startedOn).toString(),
                            source.endedOn?.let { LocalDate.ofEpochDay(it).toString() }.orEmpty(),
                        ),
                        onClick = { editing = source },
                        divider = source != state.ended.last(),
                    )
                }
            }
        }
    }

    selectedTransfer?.let { transfer ->
        val linked = transfer.withdrawal.transferGroupId != null
        WhfinFormSheet(
            title = stringResource(R.string.crypto_bridge_sheet_title),
            onDismiss = { selectedTransfer = null },
            primaryEnabled = true,
            primaryLabel = stringResource(if (linked) R.string.crypto_bridge_unlink else R.string.crypto_bridge_link),
            onPrimary = {
                if (linked) onUnlink(transfer) else onLink(transfer)
                selectedTransfer = null
            },
        ) {
            listOf(transfer.withdrawal, transfer.credit).forEach { transaction ->
                WhfinLedgerRow(
                    title = formatMinor(transaction.amountMinor, transaction.currency),
                    supportingText = listOf(
                        state.accounts.firstOrNull { it.id == transaction.accountId }?.name.orEmpty(),
                        transaction.rawCounterparty.orEmpty(),
                        Instant.ofEpochMilli(transaction.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate().toString(),
                    ).filter(String::isNotBlank).joinToString(" · "),
                )
            }
            Text(stringResource(R.string.crypto_bridge_effect), style = MaterialTheme.typography.bodyMedium)
        }
    }

    if (creating || editing != null) {
        IncomeSourceSheet(
            source = editing,
            accounts = state.accounts,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { label, amount, currency, accountId, from, to, startedOn ->
                onSave(editing, label, amount, currency, accountId, from, to, startedOn)
                creating = false
                editing = null
            },
            onEnd = editing?.takeIf { it.endedOn == null }?.let { source ->
                {
                    onEnd(source)
                    editing = null
                }
            },
            onDelete = editing?.let { source ->
                {
                    onDelete(source)
                    editing = null
                }
            },
        )
    }
}

@Composable
private fun statusLine(expectation: IncomeExpectation, account: AccountEntity?): String {
    val declared = formatMinor(expectation.source.amountMinor, expectation.source.currency)
    val window = stringResource(
        R.string.income_sources_window,
        expectation.source.expectedDayFrom,
        expectation.source.expectedDayTo,
    )
    return when {
        account == null -> stringResource(R.string.income_sources_no_account, declared)
        account.type == AccountType.CRYPTO && account.currency !in setOf("USDT", "USDC") ->
            stringResource(R.string.crypto_history_scope)
        expectation.unreadable -> stringResource(R.string.income_sources_unreadable, declared)
        expectation.arrived -> stringResource(
            R.string.income_sources_arrived,
            declared,
            formatMinor(expectation.receivedMinor, account.currency),
        )
        expectation.overdue -> stringResource(R.string.income_sources_overdue, declared, window)
        else -> stringResource(R.string.income_sources_waiting, declared, window)
    }
}

/** Shared editor used by Settings and the dedicated first-run salary declaration. */
@Composable
fun IncomeSourceSheet(
    source: IncomeSourceEntity?,
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onSave: (String, Long, String, Long?, Int, Int, Long) -> Unit,
    onEnd: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    initialLabel: String = "",
    initialCurrency: String = "USD",
) {
    var label by remember { mutableStateOf(source?.label ?: initialLabel) }
    var amount by remember {
        mutableStateOf(source?.let { (it.amountMinor / 100.0).toString() }.orEmpty())
    }
    var currency by remember { mutableStateOf(source?.currency ?: initialCurrency) }
    var accountId by remember { mutableStateOf(source?.accountId) }
    var dayFrom by remember { mutableStateOf((source?.expectedDayFrom ?: 5).toString()) }
    var dayTo by remember { mutableStateOf((source?.expectedDayTo ?: 10).toString()) }
    var started by remember { mutableStateOf(LocalDate.ofEpochDay(source?.startedOn ?: LocalDate.now().withDayOfMonth(1).toEpochDay()).toString()) }
    val startDate = runCatching { LocalDate.parse(started) }.getOrNull()
    var choosingAccount by remember { mutableStateOf(false) }
    val minor = parseToMinor(amount)

    WhfinFormSheet(
        title = stringResource(
            if (source == null) R.string.income_sources_add else R.string.income_sources_edit,
        ),
        onDismiss = onDismiss,
        primaryLabel = stringResource(R.string.action_save),
        primaryEnabled = label.isNotBlank() && minor != null && minor > 0 && currency.isNotBlank() &&
            startDate != null && (source?.endedOn == null || startDate.toEpochDay() <= source.endedOn) &&
            (source?.accountId == null || accountId == source.accountId || startDate.toEpochDay() > source.startedOn) &&
            dayFrom.toIntOrNull() in 1..28 && dayTo.toIntOrNull() in ((dayFrom.toIntOrNull() ?: 29)..28),
        onPrimary = {
            onSave(
                label,
                minor ?: 0L,
                currency,
                accountId,
                dayFrom.toIntOrNull() ?: 1,
                dayTo.toIntOrNull() ?: 28,
                requireNotNull(startDate).toEpochDay(),
            )
        },
    ) {
        WhfinField(
            value = label,
            onValueChange = { label = it.take(32) },
            label = stringResource(R.string.income_sources_label),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            WhfinField(
                value = amount,
                onValueChange = { amount = it.take(12) },
                label = stringResource(R.string.income_sources_amount),
                modifier = Modifier.weight(2f),
            )
            WhfinField(
                value = currency,
                onValueChange = { currency = it.take(5).uppercase() },
                label = stringResource(R.string.income_sources_currency),
                modifier = Modifier.weight(1f),
            )
        }
        WhfinFieldLabel(stringResource(R.string.income_sources_account))
        val selectedAccount = accounts.firstOrNull { it.id == accountId }
        WhfinLedgerRow(
            title = selectedAccount?.let { "${it.name} · ${it.currency}" }
                ?: stringResource(R.string.income_sources_account_none),
            supportingText = stringResource(R.string.income_sources_choose_account),
            onClick = { choosingAccount = !choosingAccount },
        )
        if (choosingAccount) {
            WhfinLedgerRow(
                title = stringResource(R.string.income_sources_account_none),
                onClick = {
                    if (source?.accountId != null && started == LocalDate.ofEpochDay(source.startedOn).toString()) {
                        started = LocalDate.now().toString()
                    }
                    accountId = null
                    choosingAccount = false
                },
            )
            accounts.forEach { account ->
                WhfinLedgerRow(
                    title = "${account.name} · ${account.currency}",
                    onClick = {
                        if (source?.accountId != null && account.id != source.accountId &&
                            started == LocalDate.ofEpochDay(source.startedOn).toString()
                        ) started = LocalDate.now().toString()
                        accountId = account.id
                        currency = account.currency
                        choosingAccount = false
                    },
                )
            }
        }
        WhfinField(
            value = started, onValueChange = { started = it.take(10) },
            label = stringResource(R.string.income_sources_started), modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.income_sources_started_hint), style = MaterialTheme.typography.bodySmall)
        WhfinFieldLabel(stringResource(R.string.income_sources_days))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            WhfinField(
                value = dayFrom,
                onValueChange = { dayFrom = it.filter(Char::isDigit).take(2) },
                label = stringResource(R.string.income_sources_day_from),
                modifier = Modifier.weight(1f),
            )
            WhfinField(
                value = dayTo,
                onValueChange = { dayTo = it.filter(Char::isDigit).take(2) },
                label = stringResource(R.string.income_sources_day_to),
                modifier = Modifier.weight(1f),
            )
        }
        onEnd?.let {
            WhfinButton(
                label = stringResource(R.string.income_sources_end),
                onClick = it,
                style = WhfinActionStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        onDelete?.let {
            WhfinButton(
                label = stringResource(R.string.income_sources_delete),
                onClick = it,
                style = WhfinActionStyle.DestructiveSecondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val previewState = IncomeSourcesState(
    expectations = listOf(
        IncomeExpectation(
            source = IncomeSourceEntity(
                id = 1,
                label = "Salary",
                amountMinor = 270_000,
                currency = "USDT",
                accountId = 1,
                expectedDayFrom = 5,
                expectedDayTo = 10,
                startedOn = LocalDate.of(2026, 6, 1).toEpochDay(),
                createdAt = 0,
            ),
            receivedMinor = 268_400,
            receivedCount = 1,
            overdue = false,
        ),
    ),
    ended = emptyList(),
    accounts = listOf(
        AccountEntity(id = 1, name = "Wallet", type = AccountType.CRYPTO, currency = "USDT"),
    ),
    month = YearMonth.of(2026, 8),
)

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Preview(showBackground = true, widthDp = 393, heightDp = 852, fontScale = 1.5f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun IncomeSourcesPreview() {
    WhfinTheme {
        IncomeSourcesScreen(previewState, { _, _, _, _, _, _, _, _ -> }, {}, {}, {})
    }
}
