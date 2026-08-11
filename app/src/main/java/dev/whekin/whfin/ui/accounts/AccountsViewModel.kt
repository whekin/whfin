package dev.whekin.whfin.ui.accounts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.whekin.whfin.R
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.data.crypto.CryptoAddressValidator
import dev.whekin.whfin.data.crypto.CryptoBalanceRepository
import dev.whekin.whfin.data.crypto.CryptoEndpoints
import dev.whekin.whfin.data.crypto.CryptoNetwork
import dev.whekin.whfin.data.crypto.CryptoWalletRepository
import dev.whekin.whfin.data.crypto.HttpCryptoBalanceProvider
import dev.whekin.whfin.data.preferences.UiPreferences
import dev.whekin.whfin.data.preferences.nextDisplayCurrency
import dev.whekin.whfin.data.rates.CoinGeckoPriceProvider
import dev.whekin.whfin.data.rates.ConvertedTotal
import dev.whekin.whfin.data.rates.ExchangeRate
import dev.whekin.whfin.data.rates.MoneyConverter
import dev.whekin.whfin.data.rates.NbgFiatRateProvider
import dev.whekin.whfin.data.rates.NetWorthSource
import dev.whekin.whfin.data.rates.PIVOT_CURRENCY
import dev.whekin.whfin.data.rates.RatesRepository
import dev.whekin.whfin.data.rates.toRate
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.CategorySeeder
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.data.db.FinancialGroupEntity
import dev.whekin.whfin.data.db.FinancialGroupType
import dev.whekin.whfin.data.db.PaymentInstrumentType
import dev.whekin.whfin.data.db.WalletAddressEntity
import dev.whekin.whfin.data.db.CryptoAssetEntity
import dev.whekin.whfin.data.db.StatementSourceEntity
import dev.whekin.whfin.data.db.StatementSourceType
import dev.whekin.whfin.data.db.BankProduct
import dev.whekin.whfin.data.db.FundRole
import androidx.room.withTransaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import dev.whekin.whfin.data.db.*
import dev.whekin.whfin.data.debt.*
import dev.whekin.whfin.data.mutation.TransactionMutationModule

data class DebtCaseUi(
    val debt: DebtCaseEntity,
    val person: PersonEntity,
    val remainingMinor: Long,
    val events: List<DebtEventEntity>,
)

data class AccountWithBalance(
    val account: AccountEntity,
    val balanceMinor: Long,
    val cardMasks: List<String>,
    val virtualCardMasks: List<String> = emptyList(),
    val address: String? = null,
    /** Chain of a watch-only ledger, so the UI can name the network the number came from. */
    val chainId: String? = null,
    val groupName: String? = null,
    /** Watch-only chains report a balance instead of deriving it from transactions. */
    val onChain: OnChainBalance? = null,
)

internal fun accountContainerKey(account: AccountEntity): String =
    "${account.groupId ?: "source"}:${account.iban ?: "account-${account.id}"}"

internal fun buildAccountContainerTotals(
    accounts: List<AccountWithBalance>,
    rates: Map<String, ExchangeRate>,
    displayCurrency: String,
): Map<String, ConvertedTotal> = accounts
    .filterNot { it.account.type == AccountType.CRYPTO }
    .groupBy { accountContainerKey(it.account) }
    .mapValues { (_, container) ->
        val amounts = container.groupBy { it.account.currency.uppercase() }
            .mapValues { (_, rows) ->
                BigDecimal(rows.sumOf { it.balanceMinor }).movePointLeft(2)
            }
        MoneyConverter.convert(amounts, displayCurrency, rates)
    }

/** Last observation of a chain balance; absent means "never refreshed", not zero. */
data class OnChainBalance(
    val baseUnits: String,
    val decimals: Int,
    val observedAt: Long,
    val source: String? = null,
)

sealed interface AccountRowsState {
    data object Loading : AccountRowsState
    data class Ready(val accounts: List<AccountWithBalance>) : AccountRowsState
}

sealed interface AccountsScreenState {
    data object Loading : AccountsScreenState
    data class Ready(
        val accounts: List<AccountWithBalance>,
        val debts: List<DebtCaseUi>,
        val archivedAccounts: List<AccountEntity> = emptyList(),
    ) : AccountsScreenState
}

private data class ContainerMetadata(
    val groups: Map<Long, FinancialGroupEntity>,
    val addresses: Map<Long, WalletAddressEntity>,
    val balances: Map<Long, CryptoBalanceEntity>,
)

class AccountsViewModel(app: Application) : AndroidViewModel(app) {

    private val db = (app as WhfinApp).db
    private val debtRepository = DebtRepository(db)
    private val transactionMutations = TransactionMutationModule(db)
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val containerMetadata = combine(
        db.financialGroupDao().observeActive(),
        db.cryptoDao().observeAddresses(),
        db.cryptoDao().observeBalances(),
    ) { groups, addresses, balances ->
        ContainerMetadata(
            groups.associateBy { it.id },
            addresses.associateBy { it.id },
            balances.associateBy { it.accountId },
        )
    }

    private val accountRows = combine(
        db.accountDao().observeActive(),
        db.transactionDao().observeAccountBalances(),
        db.paymentInstrumentDao().observeActive(),
        db.paymentInstrumentDao().observeLinks(),
        containerMetadata,
    ) { list, balances, instruments, links, metadata ->
        val byAccount = balances.associate { it.accountId to it.totalMinor }
        val instrumentsById = instruments.associateBy { it.id }
        val cardsByAccount = links.groupBy { it.accountId }.mapValues { (_, value) ->
            value.mapNotNull { instrumentsById[it.instrumentId] }
        }
        val (groupById, addressById, balanceByAccount) = metadata
        list.map {
            val walletAddress = it.walletAddressId?.let(addressById::get)
            AccountWithBalance(
                account = it,
                balanceMinor = byAccount[it.id] ?: 0L,
                cardMasks = cardsByAccount[it.id].orEmpty()
                    .filter { card -> card.type == PaymentInstrumentType.PHYSICAL_CARD }
                    .map { card -> card.last4 },
                virtualCardMasks = cardsByAccount[it.id].orEmpty()
                    .filter { card -> card.type == PaymentInstrumentType.VIRTUAL_CARD }
                    .map { card -> card.last4 },
                address = walletAddress?.address,
                chainId = walletAddress?.chainId,
                groupName = it.groupId?.let(groupById::get)?.name,
                onChain = balanceByAccount[it.id]?.let { row ->
                    OnChainBalance(row.baseUnits, row.decimals, row.observedAt, row.source)
                },
            )
        }
    }

    private val debtRows = combine(
        db.debtDao().observeCases(), db.debtDao().observeEvents(), db.personDao().observeActive(),
    ) { cases, events, people ->
        val personById = people.associateBy { it.id }
        cases.mapNotNull { debt ->
            val caseEvents = events.filter { it.debtCaseId == debt.id }
            personById[debt.personId]?.let { person ->
                DebtCaseUi(
                    debt,
                    person,
                    (debt.originalAmountMinor - caseEvents.filterNot { it.isVoided }.sumOf { it.debtValueMinor })
                        .coerceAtLeast(0),
                    caseEvents,
                )
            }
        }
    }

    val accountRowsState: StateFlow<AccountRowsState> = accountRows
        .map<List<AccountWithBalance>, AccountRowsState>(AccountRowsState::Ready)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountRowsState.Loading)

    val screenState: StateFlow<AccountsScreenState> = combine(
        accountRows,
        debtRows,
        db.accountDao().observeArchived(),
    ) { accounts, debts, archived ->
        AccountsScreenState.Ready(accounts, debts, archived)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsScreenState.Loading)

    val people: StateFlow<List<PersonEntity>> = db.personDao().observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val preferences = UiPreferences(getApplication<Application>())

    /** One reading of everything owned, in the currency the person last chose. */
    val netWorth: StateFlow<ConvertedTotal?> = NetWorthSource(db, preferences).observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val displayCurrency: StateFlow<String> = preferences.displayCurrency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PIVOT_CURRENCY)

    val accountContainerTotals: StateFlow<Map<String, ConvertedTotal>> = combine(
        accountRows,
        db.exchangeRateDao().observeAll(),
        preferences.displayCurrency,
    ) { rows, rateRows, display ->
        buildAccountContainerTotals(rows, rateRows.map(::toRate).associateBy { it.code }, display)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val ratesRepository = RatesRepository(
        db = db,
        providers = listOf(NbgFiatRateProvider(), CoinGeckoPriceProvider()),
    )

    private val chainProvider = HttpCryptoBalanceProvider(endpoints = { endpoints })

    private val balanceRepository = CryptoBalanceRepository(db = db, provider = chainProvider)

    private val walletRepository = CryptoWalletRepository(db = db, provider = chainProvider)

    @Volatile
    private var endpoints = CryptoEndpoints()

    private val _cryptoRefreshing = MutableStateFlow(false)
    val cryptoRefreshing: StateFlow<Boolean> = _cryptoRefreshing

    /**
     * Chain holdings read as one portfolio: a ticker held in three wallets is one number, and the
     * subtotal follows the same display currency as the headline.
     */
    val cryptoPortfolio: StateFlow<CryptoPortfolio?> = combine(
        accountRows,
        db.exchangeRateDao().observeAll(),
        preferences.displayCurrency,
    ) { rows, rateRows, display ->
        buildCryptoPortfolio(rows, rateRows.map(::toRate).associateBy { it.code }, display)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            preferences.cryptoEndpoints.collect { endpoints = it }
        }
        // Official rates move once per banking day, so a visit re-reads them only when they aged out.
        viewModelScope.launch { withContext(Dispatchers.IO) { ratesRepository.refreshIfStale() } }
    }

    /** Reads the same money in the next currency; storage keeps every account in its own currency. */
    fun rotateDisplayCurrency() {
        viewModelScope.launch {
            preferences.setDisplayCurrency(nextDisplayCurrency(displayCurrency.value))
        }
    }

    /**
     * Manual, foreground refresh. A partial result is reported honestly instead of pretending the
     * whole wallet is up to date.
     */
    fun refreshCryptoBalances() {
        if (_cryptoRefreshing.value) return
        viewModelScope.launch {
            _cryptoRefreshing.value = true
            val app = getApplication<Application>()
            val outcome = withContext(Dispatchers.IO) {
                // A wallet total is only meaningful with a price next to it, so both move together.
                ratesRepository.refresh()
                // An asset that arrived after the wallet was added has no ledger yet, so a refresh
                // looks for it before re-reading the ones already known.
                val discovered = runCatching { walletRepository.discoverNewAssets() }.getOrNull()
                discovered to balanceRepository.refreshAll()
            }
            val (discovered, result) = outcome
            _cryptoRefreshing.value = false
            // A search for new assets that could not reach the chain counts as a failed read too:
            // otherwise a silent `updated 1` would stand in for "USDT never answered".
            val failed = result.failed + (discovered?.failed ?: 0)
            _message.value = when {
                discovered != null && discovered.created.isNotEmpty() -> app.getString(
                    R.string.crypto_assets_discovered,
                    discovered.created.joinToString(" · "),
                )
                result.isEmpty -> null
                failed == 0 -> app.getString(R.string.crypto_refresh_done, result.refreshed)
                result.refreshed == 0 -> app.getString(R.string.crypto_refresh_failed)
                else -> app.getString(R.string.crypto_refresh_partial, result.refreshed, failed)
            }
        }
    }

    fun openDebt(input: NewDebt) = viewModelScope.launch {
        runCatching { debtRepository.open(input) }
            .onSuccess { _message.value = "Debt added" }
            .onFailure { _message.value = it.message ?: "Could not add debt" }
    }

    fun settleDebt(input: DebtSettlement) = viewModelScope.launch {
        runCatching { debtRepository.settle(input) }
            .onSuccess { _message.value = if (input.close) "Debt closed" else "Repayment added" }
            .onFailure { _message.value = it.message ?: "Could not update debt" }
    }

    /**
     * A watch-only wallet is added by address alone: which assets it holds is a question for the
     * chain, not for the person, so the ledgers appear from the reading.
     */
    fun addCryptoWallet(name: String?, network: CryptoNetwork, address: String) {
        if (_cryptoRefreshing.value) return
        viewModelScope.launch {
            _cryptoRefreshing.value = true
            val app = getApplication<Application>()
            val result = withContext(Dispatchers.IO) {
                // A wallet without prices reads as a bare token count, so quotes come along.
                runCatching { ratesRepository.refreshIfStale() }
                runCatching { walletRepository.addWallet(name, network, address) }
            }
            _cryptoRefreshing.value = false
            _message.value = result.fold(
                onSuccess = { outcome ->
                    when (outcome) {
                        is CryptoWalletRepository.AddResult.InvalidAddress -> when (outcome.problem) {
                            CryptoAddressValidator.Problem.CHECKSUM ->
                                app.getString(R.string.account_address_checksum)
                            else -> app.getString(R.string.account_address_invalid, network.displayName)
                        }
                        CryptoWalletRepository.AddResult.UnsupportedNetwork ->
                            app.getString(R.string.account_asset_unsupported)
                        is CryptoWalletRepository.AddResult.Tracked -> walletAddedMessage(outcome)
                    }
                },
                onFailure = { app.getString(R.string.crypto_wallet_add_failed) },
            )
        }
    }

    /**
     * Names what the chain actually said about every asset.
     *
     * "Added: TRX" alone made a failed USDT read look like an empty wallet, which is the one thing a
     * watch-only balance must never do: an asset that answered zero and an asset that did not answer
     * are different facts, and only the second one is worth retrying.
     */
    private fun walletAddedMessage(outcome: CryptoWalletRepository.AddResult.Tracked): String {
        val app = getApplication<Application>()
        if (outcome.funded.isEmpty()) {
            return when {
                outcome.unread.isNotEmpty() -> app.getString(R.string.crypto_wallet_added_unread)
                else -> app.getString(R.string.crypto_wallet_added_empty)
            }
        }
        return buildList {
            add(app.getString(R.string.crypto_wallet_added, outcome.funded.joinToString(" · ")))
            if (outcome.empty.isNotEmpty()) {
                add(app.getString(R.string.crypto_wallet_empty_assets, outcome.empty.joinToString(", ")))
            }
            if (outcome.unread.isNotEmpty()) {
                add(app.getString(R.string.crypto_wallet_unread_assets, outcome.unread.joinToString(", ")))
            }
        }.joinToString(" · ")
    }

    fun addAccount(
        name: String,
        type: AccountType,
        currency: String,
        bankProvider: String? = null,
    ) {
        viewModelScope.launch {
            db.withTransaction {
                val normalizedCurrency = currency.trim().uppercase()
                val normalizedName = if (type == AccountType.CASH) name.trim().ifBlank { "Cash" } else name.trim()
                if (type == AccountType.CASH && db.accountDao().allActive().any {
                        it.type == AccountType.CASH && it.currency == normalizedCurrency
                    }) {
                    _message.value = getApplication<Application>().getString(
                        dev.whekin.whfin.R.string.cash_account_exists,
                        normalizedCurrency,
                    )
                    return@withTransaction
                }
                val groupId = if (type == AccountType.BANK) {
                    val provider = bankProvider ?: normalizedName
                    db.financialGroupDao().byProvider(FinancialGroupType.BANK, provider)?.id
                        ?: db.financialGroupDao().insert(
                            FinancialGroupEntity(name = provider, type = FinancialGroupType.BANK, provider = provider),
                        )
                } else null
                db.accountDao().insert(
                    AccountEntity(
                        name = normalizedName, type = type, currency = normalizedCurrency, groupId = groupId,
                        fundRole = if (type == AccountType.SAVINGS) FundRole.RESERVE else FundRole.AVAILABLE,
                    ),
                )
            }
        }
    }

    fun editAccount(
        account: AccountEntity,
        name: String,
        currency: String,
        address: String?,
        fundRole: FundRole,
        bankProduct: BankProduct?,
    ) {
        viewModelScope.launch {
            val normalizedName = name.trim().ifBlank { if (account.type == AccountType.CASH) "Cash" else account.name }
            val groupId = account.groupId
            val iban = account.iban
            // A wallet is one address with several asset ledgers under it, and the name belongs to
            // the wallet: renaming one asset row and leaving the others is not a state worth having.
            if (account.type == AccountType.CRYPTO) {
                db.withTransaction {
                    if (groupId != null) {
                        db.financialGroupDao().byId(groupId)?.let { group ->
                            db.financialGroupDao().update(group.copy(name = normalizedName))
                        }
                        db.accountDao().byGroup(groupId).forEach { row ->
                            db.accountDao().update(row.copy(name = normalizedName))
                        }
                    } else {
                        db.accountDao().update(account.copy(name = normalizedName))
                    }
                }
                return@launch
            }
            if (groupId != null && iban != null &&
                (account.type == AccountType.BANK || account.type == AccountType.SAVINGS)
            ) {
                db.accountDao().updateIbanContainer(groupId, iban, normalizedName, fundRole, bankProduct)
            } else {
                db.accountDao().update(
                    account.copy(
                        name = normalizedName,
                        currency = currency.trim().uppercase(),
                        fundRole = fundRole,
                        bankProduct = bankProduct,
                    ),
                )
            }
        }
    }

    fun adjustBalance(item: AccountWithBalance, deltaMinor: Long) {
        viewModelScope.launch {
            val unaccounted = db.categoryDao().systemByName(CategorySeeder.UNACCOUNTED)
            transactionMutations.createAdjustment(
                accountId = item.account.id,
                amountMinor = deltaMinor,
                categoryId = unaccounted?.id,
                occurredAt = System.currentTimeMillis(),
            )
        }
    }

    fun updateBankMapping(account: AccountEntity, iban: String?, cardMasks: List<String>, virtualCards: List<String>) {
        viewModelScope.launch {
            try {
                // One IBAN, its cards and their statement sources describe a single account: applied
                // apart, a failure halfway leaves cards pointing at an account that never got its
                // IBAN, and SMS routing then lands the money in the wrong ledger.
                db.withTransaction {
                    db.accountDao().update(account.copy(iban = iban))
                    db.paymentInstrumentDao().replaceForAccount(
                        account,
                        cardMasks.map { it to PaymentInstrumentType.PHYSICAL_CARD } +
                            virtualCards.map { it to PaymentInstrumentType.VIRTUAL_CARD },
                    )
                    db.paymentInstrumentDao().forAccount(account.id)
                        .filter { it.type == PaymentInstrumentType.VIRTUAL_CARD }
                        .forEach { instrument ->
                            if (db.statementSourceDao().forInstrument(instrument.id) == null) {
                                db.statementSourceDao().insert(
                                    StatementSourceEntity(
                                        groupId = requireNotNull(account.groupId),
                                        type = StatementSourceType.CARD,
                                        instrumentId = instrument.id,
                                        label = "Virtual card ••••${instrument.last4}",
                                    ),
                                )
                            }
                        }
                }
                _message.value = "Bank details saved"
            } catch (e: Exception) {
                _message.value = e.message ?: "Could not save bank details"
            }
        }
    }

    fun consumeMessage() { _message.value = null }

    fun archiveAccount(account: AccountEntity) {
        viewModelScope.launch {
            db.withTransaction {
                db.accountDao().archive(account.id)
            }
            _message.value = getApplication<Application>().getString(R.string.account_archived)
        }
    }

    /**
     * Deleting one asset row of a watch-only wallet would be undone by the next discovery pass, so
     * the address goes as a whole: its ledgers and observations follow it by CASCADE.
     */
    fun archiveCryptoWallet(account: AccountEntity) {
        viewModelScope.launch {
            db.withTransaction {
                val addressId = account.walletAddressId
                if (addressId == null) {
                    db.accountDao().archive(account.id)
                } else {
                    db.accountDao().archiveWallet(addressId)
                }
            }
            _message.value = getApplication<Application>().getString(R.string.crypto_wallet_archived)
        }
    }

    fun archiveAccountContainer(accounts: List<AccountEntity>) {
        if (accounts.isEmpty()) return
        viewModelScope.launch {
            db.withTransaction {
                accounts.forEach { db.accountDao().archive(it.id) }
            }
            _message.value = getApplication<Application>().getString(R.string.account_archived)
        }
    }

    fun restoreAccount(account: AccountEntity) {
        viewModelScope.launch {
            db.accountDao().restore(account.id)
            _message.value = getApplication<Application>().getString(R.string.account_restored)
        }
    }

}
