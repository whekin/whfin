package dev.whekin.whfin.ui.accounts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.ui.feed.FeedItem
import dev.whekin.whfin.ui.feed.applyDebtAllocations
import dev.whekin.whfin.ui.feed.buildBaseFeedItems
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

internal sealed interface AccountTransactionsUiState {
    data object Loading : AccountTransactionsUiState
    data class Empty(val account: AccountEntity, val balanceMinor: Long) : AccountTransactionsUiState
    data class Content(
        val account: AccountEntity,
        val balanceMinor: Long,
        val items: List<FeedItem>,
    ) : AccountTransactionsUiState
    data object Error : AccountTransactionsUiState
}

private data class AccountHistoryBase(
    val account: AccountEntity?,
    val balanceMinor: Long,
    val items: List<FeedItem>,
)

@OptIn(ExperimentalCoroutinesApi::class)
internal class AccountTransactionsViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as WhfinApp).db
    private val accountId = MutableStateFlow<Long?>(null)
    private val zoneId = ZoneId.systemDefault()

    private val cardHints = combine(
        db.paymentInstrumentDao().observeActive(),
        db.paymentInstrumentDao().observeLinks(),
    ) { instruments, links ->
        val byId = instruments.associateBy { it.id }
        links.groupBy { it.accountId }.mapValues { (_, accountLinks) ->
            accountLinks.mapNotNull { byId[it.instrumentId]?.last4 }
        }
    }

    val uiState = accountId.filterNotNull().flatMapLatest { selectedId ->
        val base = combine(
            db.transactionDao().observeByAccount(selectedId),
            db.merchantDao().observeAll(),
            db.categoryDao().observeAll(),
            db.accountDao().observeActive(),
            cardHints,
        ) { transactions, merchants, categories, accounts, masksByAccount ->
            AccountHistoryBase(
                account = accounts.firstOrNull { it.id == selectedId },
                balanceMinor = transactions.sumOf { it.amountMinor },
                items = buildBaseFeedItems(
                    transactions = transactions,
                    merchants = merchants,
                    categories = categories,
                    accounts = accounts,
                    masksByAccount = masksByAccount,
                    zone = zoneId,
                ),
            )
        }
        combine(
            base,
            db.transactionAllocationDao().observeAll(),
            db.personDao().observeActive(),
        ) { history, allocations, people ->
            val account = history.account ?: return@combine AccountTransactionsUiState.Error
            val items = applyDebtAllocations(history.items, allocations, people)
                .sortedByDescending { it.tx.occurredAt }
            if (items.isEmpty()) AccountTransactionsUiState.Empty(account, history.balanceMinor)
            else AccountTransactionsUiState.Content(account, history.balanceMinor, items)
        }
    }.catch {
        emit(AccountTransactionsUiState.Error)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AccountTransactionsUiState.Loading,
    )

    fun bind(value: Long) {
        accountId.value = value
    }
}
