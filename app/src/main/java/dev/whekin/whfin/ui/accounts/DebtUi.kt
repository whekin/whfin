package dev.whekin.whfin.ui.accounts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.whekin.whfin.data.db.*
import dev.whekin.whfin.data.debt.*
import dev.whekin.whfin.ui.formatMinor
import dev.whekin.whfin.ui.parseToMinor
import java.time.LocalDate
import java.time.ZoneId
import androidx.compose.ui.res.stringResource
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.WhfinActionStyle
import dev.whekin.whfin.core.ui.WhfinButton
import dev.whekin.whfin.core.ui.WhfinDialogSystemBars
import dev.whekin.whfin.core.ui.WhfinIconButton
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinLedgerRow
import dev.whekin.whfin.core.ui.WhfinPaneState
import dev.whekin.whfin.core.ui.WhfinSectionLabel
import dev.whekin.whfin.core.ui.WhfinStatePane
import dev.whekin.whfin.ui.components.FormSheet

@Composable
fun DebtsSummary(debts: List<DebtCaseUi>, onClick: () -> Unit) {
    val open = debts.filter { it.debt.status == DebtStatus.OPEN }
    val mine = open.filter { it.debt.direction == DebtDirection.THEY_OWE_ME }.groupBy { it.debt.currency }
    val theirs = open.filter { it.debt.direction == DebtDirection.I_OWE_THEM }.groupBy { it.debt.currency }
    Column(Modifier.fillMaxWidth().padding(top = 16.dp).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.debts_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (open.isEmpty()) stringResource(R.string.debts_none) else stringResource(R.string.debts_open_count, open.size),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Default.ChevronRight, null)
        }
        WhfinLedgerGroup {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                DebtTotal(stringResource(R.string.debts_owed_to_you), mine, Modifier.weight(1f))
                DebtTotal(stringResource(R.string.debts_you_owe), theirs, Modifier.weight(1f))
            }
        }
    }
}

@Composable private fun DebtTotal(label: String, groups: Map<String, List<DebtCaseUi>>, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(groups.entries.joinToString(" · ") { (currency, list) -> formatMinor(list.sumOf { it.remainingMinor }, currency) }.ifBlank { "—" },
            style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun DebtLedgerDialog(
    debts: List<DebtCaseUi>, people: List<PersonEntity>, accounts: List<AccountEntity>, onDismiss: () -> Unit,
    onOpen: (NewDebt) -> Unit, onSettle: (DebtSettlement) -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf<DebtCaseUi?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        WhfinDialogSystemBars()
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    WhfinIconButton(Icons.Default.Close, stringResource(R.string.action_cancel), onDismiss, outlined = false)
                    Text(stringResource(R.string.debts_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    WhfinButton(stringResource(R.string.new_debt), { adding = true }, style = WhfinActionStyle.Quiet)
                }
                LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val open = debts.filter { it.debt.status == DebtStatus.OPEN }
                    if (open.isEmpty()) item {
                        WhfinStatePane(
                            WhfinPaneState.Empty,
                            stringResource(R.string.debts_none),
                            stringResource(R.string.debt_empty_body),
                        )
                    }
                    items(open, key = { it.debt.id }) { item ->
                        WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                            WhfinLedgerRow(
                                title = item.person.name,
                                supportingText = stringResource(
                                    if (item.debt.direction == DebtDirection.THEY_OWE_ME) R.string.debt_owes_you else R.string.debts_you_owe,
                                ),
                                trailing = { Text(formatMinor(item.remainingMinor, item.debt.currency), style = MaterialTheme.typography.titleMedium) },
                                onClick = { settling = item },
                            )
                        }
                    }
                    val closed = debts.filter { it.debt.status == DebtStatus.CLOSED }
                    if (closed.isNotEmpty()) {
                        item { WhfinSectionLabel(stringResource(R.string.debt_history), Modifier.padding(top = 18.dp)) }
                        items(closed, key = { "closed-${it.debt.id}" }) {
                            Text("${it.person.name} · ${formatMinor(it.debt.originalAmountMinor, it.debt.currency)} · ${stringResource(R.string.debt_closed)}")
                        }
                    }
                }
            }
        }
    }
    if (adding) NewDebtDialog(people, accounts, { adding = false }) { onOpen(it); adding = false }
    settling?.let { item -> SettlementDialog(item, accounts, { settling = null }) { onSettle(it); settling = null } }
}

@Composable private fun NewDebtDialog(people: List<PersonEntity>, accounts: List<AccountEntity>, dismiss: () -> Unit, save: (NewDebt) -> Unit) {
    var direction by remember { mutableStateOf(DebtDirection.THEY_OWE_ME) }
    var personId by remember { mutableStateOf(people.firstOrNull()?.id) }
    var personName by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf(accounts.firstOrNull { it.currency == "GEL" }?.currency ?: "GEL") }
    var accountId by remember { mutableStateOf<Long?>(null) }
    val minor = parseToMinor(amount)
    FormSheet(
        title = stringResource(R.string.new_debt),
        onDismiss = dismiss,
        primaryLabel = stringResource(R.string.action_save),
        primaryEnabled = minor != null && (personId != null || personName.isNotBlank()),
        onPrimary = {
            save(NewDebt(personId, personName.takeIf { personId == null }, direction, minor!!, currency, accountId, LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()))
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row { FilterChip(direction == DebtDirection.THEY_OWE_ME, { direction = DebtDirection.THEY_OWE_ME }, { Text(stringResource(R.string.debt_they_owe)) }); Spacer(Modifier.width(8.dp)); FilterChip(direction == DebtDirection.I_OWE_THEM, { direction = DebtDirection.I_OWE_THEM }, { Text(stringResource(R.string.debt_i_owe)) }) }
            if (people.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { people.take(3).forEach { p -> FilterChip(personId == p.id, { personId = p.id; personName = "" }, { Text(p.name) }) } }
            OutlinedTextField(personName, { personName = it; if (it.isNotBlank()) personId = null }, label = { Text(stringResource(R.string.debt_new_person)) }, singleLine = true)
            OutlinedTextField(amount, { amount = it }, label = { Text(stringResource(R.string.tx_amount)) }, suffix = { Text(currency) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("GEL", "USD", "EUR").forEach { c -> FilterChip(currency == c, { currency = c; accountId = null }, { Text(c) }) } }
            WhfinSectionLabel(stringResource(R.string.debt_money_movement))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(accountId == null, { accountId = null }, { Text(stringResource(R.string.debt_no_movement)) })
                accounts.filter { it.currency == currency }.take(2).forEach { a -> FilterChip(accountId == a.id, { accountId = a.id }, { Text(a.name) }) }
            }
        }
    }
}

@Composable private fun SettlementDialog(item: DebtCaseUi, accounts: List<AccountEntity>, dismiss: () -> Unit, save: (DebtSettlement) -> Unit) {
    var movement by remember { mutableStateOf(true) }
    var amount by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf(item.debt.currency) }
    var accountId by remember { mutableStateOf(accounts.firstOrNull { it.currency == currency }?.id) }
    var close by remember { mutableStateOf(true) }
    var debtCredit by remember { mutableStateOf("") }
    val actual = parseToMinor(amount)
    val credit = parseToMinor(debtCredit)
    FormSheet(
        title = stringResource(R.string.debt_repayment_from, item.person.name),
        onDismiss = dismiss,
        primaryLabel = stringResource(if (close) R.string.debt_close_action else R.string.debt_repayment_action),
        primaryEnabled = (!movement || actual != null && accountId != null) && (close || credit != null),
        onPrimary = {
            save(DebtSettlement(item.debt.id, actual.takeIf { movement }, currency.takeIf { movement }, accountId.takeIf { movement }, credit.takeIf { !close }, close, System.currentTimeMillis()))
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.debt_outstanding, formatMinor(item.remainingMinor, item.debt.currency)))
            Row { FilterChip(movement, { movement = true }, { Text(stringResource(R.string.debt_through_account)) }); Spacer(Modifier.width(8.dp)); FilterChip(!movement, { movement = false }, { Text(stringResource(R.string.debt_no_movement)) }) }
            if (movement) {
                OutlinedTextField(amount, { amount = it }, label = { Text(stringResource(R.string.debt_actual_paid)) }, suffix = { Text(currency) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("GEL", "USD", "EUR").forEach { c -> FilterChip(currency == c, { currency = c; accountId = accounts.firstOrNull { it.currency == c }?.id }, { Text(c) }) } }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { accounts.filter { it.currency == currency }.take(3).forEach { a -> FilterChip(accountId == a.id, { accountId = a.id }, { Text(a.name) }) } }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(close, { close = it }); Text(stringResource(R.string.debt_close_completely)) }
            if (!close) OutlinedTextField(debtCredit, { debtCredit = it }, label = { Text(stringResource(R.string.debt_credit_toward, item.debt.currency)) })
            Text(stringResource(R.string.debt_close_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
