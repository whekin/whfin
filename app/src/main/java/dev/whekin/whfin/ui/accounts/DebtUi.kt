package dev.whekin.whfin.ui.accounts

import android.content.res.Configuration
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
import androidx.compose.ui.tooling.preview.Preview
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
import dev.whekin.whfin.core.ui.WhfinDialogSystemBars
import dev.whekin.whfin.core.ui.WhfinIconButton
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinLedgerRow
import dev.whekin.whfin.core.ui.WhfinPaneState
import dev.whekin.whfin.core.ui.WhfinSectionLabel
import dev.whekin.whfin.core.ui.WhfinStatePane
import dev.whekin.whfin.core.ui.WhfinField
import dev.whekin.whfin.core.ui.WhfinChoiceRail
import dev.whekin.whfin.core.ui.WhfinFilterPill
import dev.whekin.whfin.core.ui.WhfinSwitch
import dev.whekin.whfin.ui.components.FormSheet
import dev.whekin.whfin.ui.theme.WhfinTheme
import dev.whekin.whfin.ui.demo.DemoWorkspaceFrame

@Composable
fun DebtsSummary(debts: List<DebtCaseUi>, onClick: () -> Unit) {
    val open = debts.filter { it.debt.status == DebtStatus.OPEN }
    val mine = open.filter { it.debt.direction == DebtDirection.THEY_OWE_ME }.groupBy { it.debt.currency }
    val theirs = open.filter { it.debt.direction == DebtDirection.I_OWE_THEM }.groupBy { it.debt.currency }
    Column(Modifier.fillMaxWidth().padding(top = 16.dp).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        WhfinSectionLabel(stringResource(R.string.debts_title))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (open.isEmpty()) stringResource(R.string.debts_none) else stringResource(R.string.debts_open_count, open.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
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
        DemoWorkspaceFrame {
            DebtLedgerContent(
                debts = debts,
                onDismiss = onDismiss,
                onAdd = { adding = true },
                onSettle = { settling = it },
            )
        }
    }
    if (adding) NewDebtDialog(people, accounts, { adding = false }) { onOpen(it); adding = false }
    settling?.let { item -> SettlementDialog(item, accounts, { settling = null }) { onSettle(it); settling = null } }
}

@Composable
private fun DebtLedgerContent(
    debts: List<DebtCaseUi>,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onSettle: (DebtCaseUi) -> Unit,
) {
    val open = debts.filter { it.debt.status == DebtStatus.OPEN }
    val closed = debts.filter { it.debt.status == DebtStatus.CLOSED }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WhfinIconButton(
                    Icons.Default.Close,
                    stringResource(R.string.action_cancel),
                    onDismiss,
                    outlined = false,
                )
                Text(
                    stringResource(R.string.debts_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                WhfinIconButton(
                    Icons.Default.Add,
                    stringResource(R.string.new_debt),
                    onAdd,
                    outlined = false,
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (open.isEmpty()) item {
                    WhfinStatePane(
                        WhfinPaneState.Empty,
                        stringResource(R.string.debts_none),
                        stringResource(R.string.debt_empty_body),
                        actionLabel = stringResource(R.string.debt_empty_action),
                        onAction = onAdd,
                    )
                }
                items(open, key = { it.debt.id }) { item ->
                    WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                        WhfinLedgerRow(
                            title = item.person.name,
                            supportingText = stringResource(
                                if (item.debt.direction == DebtDirection.THEY_OWE_ME) {
                                    R.string.debt_owes_you
                                } else {
                                    R.string.debts_you_owe
                                },
                            ),
                            trailing = {
                                Text(
                                    formatMinor(item.remainingMinor, item.debt.currency),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            },
                            onClick = { onSettle(item) },
                        )
                    }
                }
                if (closed.isNotEmpty()) {
                    item {
                        WhfinSectionLabel(
                            stringResource(R.string.debt_history),
                            Modifier.padding(top = 18.dp),
                        )
                    }
                    items(closed, key = { "closed-${it.debt.id}" }) {
                        Text(
                            "${it.person.name} · ${formatMinor(it.debt.originalAmountMinor, it.debt.currency)} · " +
                                stringResource(R.string.debt_closed),
                        )
                    }
                }
            }
        }
    }
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
        WhfinChoiceRail {
            item {
                WhfinFilterPill(
                    label = stringResource(R.string.debt_they_owe),
                    selected = direction == DebtDirection.THEY_OWE_ME,
                    onClick = { direction = DebtDirection.THEY_OWE_ME },
                )
            }
            item {
                WhfinFilterPill(
                    label = stringResource(R.string.debt_i_owe),
                    selected = direction == DebtDirection.I_OWE_THEM,
                    onClick = { direction = DebtDirection.I_OWE_THEM },
                )
            }
        }
        if (people.isNotEmpty()) WhfinChoiceRail {
            items(people, key = { it.id }) { person ->
                WhfinFilterPill(
                    label = person.name,
                    selected = personId == person.id,
                    onClick = { personId = person.id; personName = "" },
                )
            }
        }
        WhfinField(
            personName,
            { personName = it; if (it.isNotBlank()) personId = null },
            stringResource(R.string.debt_new_person),
            modifier = Modifier.fillMaxWidth(),
        )
        WhfinField(
            amount,
            { amount = it },
            stringResource(R.string.tx_amount),
            suffix = currency,
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.fillMaxWidth(),
        )
        WhfinChoiceRail {
            items(listOf("GEL", "USD", "EUR"), key = { it }) { code ->
                WhfinFilterPill(
                    label = code,
                    selected = currency == code,
                    onClick = { currency = code; accountId = null },
                )
            }
        }
        WhfinSectionLabel(stringResource(R.string.debt_money_movement))
        WhfinLedgerGroup(Modifier.fillMaxWidth()) {
            WhfinLedgerRow(
                title = stringResource(R.string.debt_no_movement),
                supportingText = stringResource(R.string.debt_no_movement_hint),
                icon = Icons.Default.Block,
                trailing = if (accountId == null) {{ Icon(Icons.Default.Check, null) }} else null,
                onClick = { accountId = null },
                divider = accounts.any { it.currency == currency },
            )
            accounts.filter { it.currency == currency }.forEachIndexed { index, account ->
                val matching = accounts.filter { it.currency == currency }
                WhfinLedgerRow(
                    title = account.name,
                    supportingText = stringResource(R.string.debt_account_movement_hint, currency),
                    icon = accountTypeIcon(account.type),
                    trailing = if (accountId == account.id) {{ Icon(Icons.Default.Check, null) }} else null,
                    onClick = { accountId = account.id },
                    divider = index != matching.lastIndex,
                )
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
            WhfinChoiceRail {
                item {
                    WhfinFilterPill(
                        label = stringResource(R.string.debt_through_account),
                        selected = movement,
                        onClick = { movement = true },
                    )
                }
                item {
                    WhfinFilterPill(
                        label = stringResource(R.string.debt_no_movement),
                        selected = !movement,
                        onClick = { movement = false },
                    )
                }
            }
            if (movement) {
                WhfinField(
                    amount,
                    { amount = it },
                    stringResource(R.string.debt_actual_paid),
                    suffix = currency,
                    keyboardType = KeyboardType.Decimal,
                )
                WhfinChoiceRail {
                    items(listOf("GEL", "USD", "EUR"), key = { it }) { code ->
                        WhfinFilterPill(
                            label = code,
                            selected = currency == code,
                            onClick = {
                                currency = code
                                accountId = accounts.firstOrNull { it.currency == code }?.id
                            },
                        )
                    }
                }
                val matchingAccounts = accounts.filter { it.currency == currency }
                if (matchingAccounts.isNotEmpty()) WhfinChoiceRail {
                    items(matchingAccounts, key = { it.id }) { account ->
                        WhfinFilterPill(
                            label = account.name,
                            selected = accountId == account.id,
                            onClick = { accountId = account.id },
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.debt_close_completely),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                WhfinSwitch(
                    checked = close,
                    onCheckedChange = { close = it },
                    contentDescription = stringResource(R.string.debt_close_completely),
                )
            }
            if (!close) WhfinField(
                debtCredit,
                { debtCredit = it },
                stringResource(R.string.debt_credit_toward, item.debt.currency),
            )
            Text(stringResource(R.string.debt_close_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview(name = "Debt ledger light", widthDp = 400, heightDp = 800, showBackground = true)
@Preview(name = "Debt ledger dark", widthDp = 400, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Debt ledger font 1.5", widthDp = 400, heightDp = 900, fontScale = 1.5f, showBackground = true)
@Composable
private fun DebtLedgerContentPreview() {
    val luka = PersonEntity(id = 1, name = "Luka", color = 0xFF6D806F.toInt())
    val nino = PersonEntity(id = 2, name = "Nino", color = 0xFF9A6A55.toInt())
    val maya = PersonEntity(id = 3, name = "Maya", color = 0xFF6D6A91.toInt())
    val debts = listOf(
        DebtCaseUi(
            debt = DebtCaseEntity(
                id = 1,
                personId = luka.id,
                direction = DebtDirection.I_OWE_THEM,
                originalAmountMinor = 15_000,
                currency = "GEL",
                openedAt = 0,
            ),
            person = luka,
            remainingMinor = 15_000,
            events = emptyList(),
        ),
        DebtCaseUi(
            debt = DebtCaseEntity(
                id = 2,
                personId = nino.id,
                direction = DebtDirection.THEY_OWE_ME,
                originalAmountMinor = 20_000,
                currency = "GEL",
                openedAt = 0,
            ),
            person = nino,
            remainingMinor = 20_000,
            events = emptyList(),
        ),
        DebtCaseUi(
            debt = DebtCaseEntity(
                id = 3,
                personId = maya.id,
                direction = DebtDirection.THEY_OWE_ME,
                originalAmountMinor = 20_000,
                currency = "GEL",
                openedAt = 0,
                status = DebtStatus.CLOSED,
            ),
            person = maya,
            remainingMinor = 0,
            events = emptyList(),
        ),
    )
    WhfinTheme {
        DebtLedgerContent(debts, {}, {}, {})
    }
}

@Preview(name = "Debt ledger empty compact", widthDp = 400, heightDp = 520, showBackground = true)
@Composable
private fun DebtLedgerEmptyPreview() {
    WhfinTheme {
        DebtLedgerContent(emptyList(), {}, {}, {})
    }
}

@Preview(name = "New debt light", widthDp = 400, heightDp = 800, showBackground = true)
@Preview(name = "New debt dark", widthDp = 400, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "New debt font 1.5", widthDp = 400, heightDp = 900, fontScale = 1.5f, showBackground = true)
@Preview(name = "New debt compact", widthDp = 400, heightDp = 520, showBackground = true)
@Composable
private fun NewDebtDialogPreview() {
    WhfinTheme {
        NewDebtDialog(
            people = emptyList(),
            accounts = listOf(
                AccountEntity(id = 1, name = "Pocket money", type = AccountType.CASH, currency = "GEL"),
                AccountEntity(id = 2, name = "Hot deposit", type = AccountType.SAVINGS, currency = "GEL"),
            ),
            dismiss = {},
            save = {},
        )
    }
}
