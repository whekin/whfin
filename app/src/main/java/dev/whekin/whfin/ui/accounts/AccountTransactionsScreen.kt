package dev.whekin.whfin.ui.accounts

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.WhfinBackButton
import dev.whekin.whfin.core.ui.WhfinPaneState
import dev.whekin.whfin.core.ui.WhfinSectionHeader
import dev.whekin.whfin.core.ui.WhfinSectionLabel
import dev.whekin.whfin.core.ui.WhfinStatePane
import dev.whekin.whfin.core.ui.WhfinStatusBarProtection
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.ui.feed.DayHeader
import dev.whekin.whfin.ui.feed.FeedItem
import dev.whekin.whfin.ui.feed.FeedRow
import dev.whekin.whfin.ui.feed.TransactionDetailsSheet
import dev.whekin.whfin.ui.formatMinor
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.time.LocalDate
import java.time.ZoneId

@Composable
internal fun AccountTransactionsScreen(
    accountId: Long,
    onBack: () -> Unit,
    viewModel: AccountTransactionsViewModel = viewModel(),
) {
    LaunchedEffect(accountId) { viewModel.bind(accountId) }
    val state by viewModel.uiState.collectAsState()
    when (val value = state) {
        AccountTransactionsUiState.Loading -> AccountTransactionsState(
            paneState = WhfinPaneState.Loading,
            title = stringResource(R.string.account_transactions_loading),
            body = stringResource(R.string.account_transactions_loading_body),
            onBack = onBack,
        )
        is AccountTransactionsUiState.Empty -> AccountTransactionsState(
            account = value.account,
            balanceMinor = value.balanceMinor,
            paneState = WhfinPaneState.Empty,
            title = stringResource(R.string.account_transactions_empty),
            body = stringResource(R.string.account_transactions_empty_body),
            onBack = onBack,
        )
        is AccountTransactionsUiState.Content -> AccountTransactionsContent(
            account = value.account,
            balanceMinor = value.balanceMinor,
            items = value.items,
            onBack = onBack,
        )
        AccountTransactionsUiState.Error -> AccountTransactionsState(
            paneState = WhfinPaneState.Error,
            title = stringResource(R.string.account_transactions_error),
            body = stringResource(R.string.account_transactions_error_body),
            onBack = onBack,
        )
    }
}

@Composable
private fun AccountTransactionsState(
    paneState: WhfinPaneState,
    title: String,
    body: String,
    onBack: () -> Unit,
    account: AccountEntity? = null,
    balanceMinor: Long = 0,
) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item { AccountTransactionsHeader(onBack) }
            if (account != null) item { AccountTransactionsScope(account, balanceMinor) }
            item { WhfinStatePane(paneState, title, body, Modifier.fillMaxWidth()) }
        }
        WhfinStatusBarProtection(Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun AccountTransactionsContent(
    account: AccountEntity,
    balanceMinor: Long,
    items: List<FeedItem>,
    onBack: () -> Unit,
) {
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var details by remember { mutableStateOf<FeedItem?>(null) }
    var expandedDays by remember { mutableStateOf(emptySet<LocalDate>()) }
    val grouped = items.groupBy { it.day }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = navigationBottom + 24.dp),
        ) {
            item { AccountTransactionsHeader(onBack) }
            item { AccountTransactionsScope(account, balanceMinor) }
            grouped.forEach { (day, dayItems) ->
                item(key = "account-transactions-day-$day") {
                    val expenses = dayItems.groupBy { it.tx.currency }
                        .mapValues { (_, values) -> -values.sumOf { it.tx.amountMinor.coerceAtMost(0L) } }
                        .filterValues { it > 0L }
                    DayHeader(
                        day = day,
                        expensesByCurrency = expenses,
                        gelFromConversions = 0L,
                        expanded = day in expandedDays,
                        onToggle = {
                            expandedDays = if (day in expandedDays) expandedDays - day else expandedDays + day
                        },
                    )
                }
                items(dayItems, key = { "account-transaction-${it.tx.id}" }) { item ->
                    FeedRow(item, onClick = { details = item })
                }
            }
        }
        WhfinStatusBarProtection(Modifier.align(Alignment.TopCenter))
    }
    details?.let { item ->
        TransactionDetailsSheet(
            item = item,
            onDismiss = { details = null },
            onChangeCategory = null,
            onDelete = null,
            onEdit = null,
            onDebt = null,
            onClearDebt = null,
        )
    }
}

@Composable
private fun AccountTransactionsHeader(onBack: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 6.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WhfinBackButton(stringResource(R.string.action_back), onBack)
                Text(stringResource(R.string.account_transactions_title), style = MaterialTheme.typography.headlineSmall)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun AccountTransactionsScope(account: AccountEntity, balanceMinor: Long) {
    val accountDetail = account.iban?.let {
        stringResource(R.string.account_transactions_iban_currency, it.takeLast(4), account.currency)
    } ?: account.currency
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WhfinSectionHeader(title = account.name, supportingText = accountDetail)
        WhfinSectionLabel(stringResource(R.string.account_transactions_balance))
        Text(
            formatMinor(balanceMinor, account.currency),
            style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Preview(name = "Account transactions", widthDp = 400, heightDp = 880, showBackground = true)
@Preview(name = "Account transactions dark", widthDp = 400, heightDp = 880, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Account transactions font 1.5", widthDp = 400, heightDp = 1080, fontScale = 1.5f, showBackground = true)
@Composable
private fun AccountTransactionsPreview() {
    val account = AccountEntity(
        id = 1,
        name = "EVO",
        type = AccountType.BANK,
        groupId = 1,
        currency = "GEL",
        iban = "GE00CD0000000000000001",
    )
    val now = LocalDate.of(2026, 7, 14).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val items = listOf(
        FeedItem(
            tx = TransactionEntity(
                id = 1,
                accountId = 1,
                amountMinor = -4_800,
                currency = "GEL",
                occurredAt = now,
                rawCounterparty = "Coffee Lab",
                status = TxStatus.CONFIRMED,
                source = TxSource.STATEMENT,
                createdAt = now,
            ),
            merchant = null,
            category = null,
            account = account,
            cardHint = "2533",
            day = LocalDate.of(2026, 7, 14),
        ),
    )
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AccountTransactionsContent(account, 163_18, items, {})
        }
    }
}
