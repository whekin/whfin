package dev.whekin.whfin.ui.accounts

import dev.whekin.whfin.ui.FormSaver
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.whekin.whfin.R
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.data.crypto.CryptoAddressValidator
import dev.whekin.whfin.data.crypto.CryptoHistoryRepository
import dev.whekin.whfin.data.crypto.HttpCryptoTransferProvider
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
import dev.whekin.whfin.data.rates.MoneySplit
import dev.whekin.whfin.data.rates.MoneySplitSource
import dev.whekin.whfin.data.rates.NbgFiatRateProvider
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
import dev.whekin.whfin.data.backup.LedgerRestoreState
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
    val primaryCardMasks: List<String> = emptyList(),
    val primaryCardConfigured: Boolean = false,
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
    private val formSaver = FormSaver(viewModelScope)
    val formSaveState = formSaver.state
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
        val primaryCardConfigured = links.any { link -> instrumentsById[link.instrumentId]?.isPrimary == true }
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
                primaryCardMasks = cardsByAccount[it.id].orEmpty()
                    .filter(PaymentInstrumentEntity::isPrimary)
                    .map(PaymentInstrumentEntity::last4),
                primaryCardConfigured = primaryCardConfigured,
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

    /** True while a restore is replacing this database's contents. */
    internal val restoring: StateFlow<Boolean> = LedgerRestoreState.active

    val people: StateFlow<List<PersonEntity>> = db.personDao().observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val preferences = UiPreferences(getApplication<Application>())

    /**
     * One reading of the money, in the currency the person last chose — the same one Home reads.
     *
     * The headline here and the headline there are cut from this single split, so "available" is one
     * number in the whole app instead of one per screen.
     */
    internal val moneySplit: StateFlow<MoneySplit?> = MoneySplitSource(db, preferences).observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val displayCurrency: StateFlow<String> = preferences.displayCurrency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PIVOT_CURRENCY)

    /** Everything the overview screen explains, cut from the same reading as the headline. */
    internal val overview: StateFlow<AccountOverviewData?> = combine(
        accountRows,
        db.exchangeRateDao().observeAll(),
        preferences.displayCurrency,
    ) { rows, rateRows, display ->
        accountOverviewData(rows, rateRows.map(::toRate).associateBy { it.code }, display)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

    private val historyRepository = CryptoHistoryRepository(db, HttpCryptoTransferProvider({ endpoints }))

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
        if (_cryptoRefreshing.value || getApplication<WhfinApp>().isDemoMode) return
        viewModelScope.launch {
            _cryptoRefreshing.value = true
            val app = getApplication<Application>()
            try {
                val (history, balances) = withContext(Dispatchers.IO) {
                    dev.whekin.whfin.data.backup.LedgerRestoreState.reading {
                    runCatching { ratesRepository.refresh() }
                    runCatching { walletRepository.discoverNewAssets() }
                    // Import before reading balances so assets with history but zero holdings are read too.
                    historyRepository.refreshAll() to balanceRepository.refreshAll()
                    }
                }
                _message.value = app.getString(
                    R.string.crypto_history_result, history.imported, history.failed + balances.failed,
                ) + if (history.unsupported > 0) " " + app.getString(R.string.crypto_history_scope) else ""
            } catch (_: dev.whekin.whfin.data.backup.LedgerBusyException) {
                _message.value = app.getString(R.string.ledger_restore_in_progress)
            } finally {
                _cryptoRefreshing.value = false
            }
        }
    }

    fun openDebt(input: NewDebt) = formSaver.save {
        runCatching { debtRepository.open(input) }
            .onSuccess { _message.value = getApplication<Application>().getString(R.string.debt_added) }
            .onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                _message.value = getApplication<Application>().getString(R.string.debt_save_failed)
                throw it
            }
    }

    fun settleDebt(input: DebtSettlement) = formSaver.save {
        runCatching { debtRepository.settle(input) }
            .onSuccess { _message.value = getApplication<Application>().getString(if (input.close) R.string.debt_closed_message else R.string.debt_repayment_added) }
            .onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                _message.value = getApplication<Application>().getString(R.string.debt_save_failed)
                throw it
            }
    }

    /**
     * A watch-only wallet is added by address alone: which assets it holds is a question for the
     * chain, not for the person, so the ledgers appear from the reading.
     */
    fun addCryptoWallet(name: String?, network: CryptoNetwork, address: String) = formSaver.save {
        check(!_cryptoRefreshing.value && !getApplication<WhfinApp>().isDemoMode)
        _cryptoRefreshing.value = true
        val outcome = try {
            withContext(Dispatchers.IO) {
                // Quotes are optional; cancellation is not a failed quote.
                try { ratesRepository.refreshIfStale() }
                catch (error: kotlinx.coroutines.CancellationException) { throw error }
                catch (_: Exception) { }
                walletRepository.addWallet(name, network, address)
            }
        } finally { _cryptoRefreshing.value = false }
        val app = getApplication<Application>()
        _message.value = when (outcome) {
            is CryptoWalletRepository.AddResult.InvalidAddress -> when (outcome.problem) {
                CryptoAddressValidator.Problem.CHECKSUM -> app.getString(R.string.account_address_checksum)
                else -> app.getString(R.string.account_address_invalid, network.displayName)
            }
            CryptoWalletRepository.AddResult.UnsupportedNetwork -> app.getString(R.string.account_asset_unsupported)
            is CryptoWalletRepository.AddResult.Tracked -> walletAddedMessage(outcome)
        }
        check(outcome is CryptoWalletRepository.AddResult.Tracked) { "Wallet was not added" }
        refreshCryptoBalances()
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

    /**
     * @param openingMinor what the account already holds, if the person said so.
     *
     * It is recorded exactly as a statement's opening balance is — an adjustment row marked as a
     * transfer, so the money counts towards the balance without ever reading as income earned this
     * month. Money that existed before WHFIN did is not a thing that happened in it.
     */
    fun addAccount(
        name: String,
        type: AccountType,
        currency: String,
        bankProvider: String? = null,
        openingMinor: Long? = null,
        bankProduct: BankProduct? = null,
    ) {
        formSaver.save {
            db.withTransaction {
                val normalizedCurrency = currency.trim().uppercase()
                val normalizedName = if (type == AccountType.CASH) name.trim().ifBlank { "Cash" } else name.trim()
                if (type == AccountType.CASH) {
                    // First-run seeds one zero-balance GEL cash ledger so the app can show a
                    // meaningful source immediately. The onboarding Cash step edits that ledger
                    // instead of creating a duplicate, while still using the same opening-balance
                    // provenance as a newly created cash account.
                    val existingCash = db.accountDao().allActive().firstOrNull {
                        it.type == AccountType.CASH && it.currency == normalizedCurrency
                    }
                    if (existingCash != null) {
                        db.accountDao().update(
                            existingCash.copy(
                                name = normalizedName,
                                fundRole = FundRole.AVAILABLE,
                            ),
                        )
                        openingMinor?.let { desired ->
                            val current = db.transactionDao().sumByAccount(existingCash.id)
                            val delta = Math.subtractExact(desired, current)
                            if (delta == 0L) return@let
                            transactionMutations.createOpeningBalance(
                                accountId = existingCash.id,
                                amountMinor = delta,
                                occurredAt = System.currentTimeMillis(),
                            )
                        }
                        return@withTransaction
                    }
                }
                val groupId = if (type == AccountType.BANK) {
                    val provider = bankProvider ?: normalizedName
                    db.financialGroupDao().byProvider(FinancialGroupType.BANK, provider)?.id
                        ?: db.financialGroupDao().insert(
                            FinancialGroupEntity(name = provider, type = FinancialGroupType.BANK, provider = provider),
                        )
                } else null
                val accountId = db.accountDao().insert(
                    AccountEntity(
                        name = normalizedName, type = type, currency = normalizedCurrency, groupId = groupId,
                        fundRole = if (type == AccountType.SAVINGS) FundRole.RESERVE else FundRole.AVAILABLE,
                        bankProduct = bankProduct.takeIf { type == AccountType.BANK },
                    ),
                )
                openingMinor?.takeIf { it != 0L }?.let { amount ->
                    transactionMutations.createOpeningBalance(
                        accountId = accountId,
                        amountMinor = amount,
                        occurredAt = System.currentTimeMillis(),
                    )
                }
            }
        }
    }

    fun editAccount(
        account: AccountEntity,
        name: String,
        currency: String,
        address: String?,
        fundRole: FundRole,
    ) {
        formSaver.save {
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
                return@save
            }
            // A bank/IBAN is the user-facing container. Name and fund role are the owner's
            // profile fields; bank product belongs exclusively to Bank details and must survive
            // editing any currency row in this sheet.
            if (groupId != null && iban != null) {
                // One answer for the whole container, so the name has to be one the container can
                // hold. An import's own "<Bank> <CUR> •<last4>" speaks for a single currency; copied
                // across the IBAN it renames the USD ledger after the GEL one. Left empty, each
                // ledger goes back to being named by its bank, number and product.
                val containerName = normalizedName
                    .takeUnless { dev.whekin.whfin.data.db.isGeneratedLedgerName(it, iban) }
                    .orEmpty()
                db.accountDao().updateIbanProfile(groupId, iban, containerName, fundRole)
            } else {
                db.accountDao().update(
                    account.copy(
                        name = normalizedName,
                        currency = currency.trim().uppercase(),
                        fundRole = fundRole,
                    ),
                )
            }
        }
    }

    fun updateBankMapping(
        accounts: List<AccountEntity>,
        name: String,
        fundRole: FundRole,
        iban: String?,
        bankProduct: BankProduct?,
        cardMasks: List<String>,
        virtualCards: List<String>,
        primaryLast4: String?,
    ) {
        formSaver.save {
            try {
                require(accounts.isNotEmpty())
                val normalizedName = name.trim().ifBlank { accounts.first().name }
                // One IBAN, its cards and their statement sources describe a single account: applied
                // apart, a failure halfway leaves cards pointing at an account that never got its
                // IBAN, and SMS routing then lands the money in the wrong ledger.
                db.withTransaction {
                    // Name, fund role, IBAN and bank product are all container metadata: one IBAN
                    // is one account, and its currency rows only differ in the money they hold.
                    // They are written by row rather than by IBAN key because this same save may
                    // be the one changing that key.
                    val updatedAccounts = accounts.map { account ->
                        account.copy(
                            name = normalizedName,
                            fundRole = fundRole,
                            iban = iban,
                            bankProduct = bankProduct,
                        )
                    }
                    updatedAccounts.forEach { account -> db.accountDao().update(account) }
                    db.paymentInstrumentDao().replaceForAccounts(
                        updatedAccounts,
                        cardMasks.map { it to PaymentInstrumentType.PHYSICAL_CARD } +
                            virtualCards.map { it to PaymentInstrumentType.VIRTUAL_CARD },
                        primaryLast4,
                    )
                    db.paymentInstrumentDao().forAccount(updatedAccounts.first().id)
                        .filter { it.type == PaymentInstrumentType.VIRTUAL_CARD }
                        .forEach { instrument ->
                            if (db.statementSourceDao().forInstrument(instrument.id) == null) {
                                db.statementSourceDao().insert(
                                    StatementSourceEntity(
                                        groupId = requireNotNull(updatedAccounts.first().groupId),
                                        type = StatementSourceType.CARD,
                                        instrumentId = instrument.id,
                                        label = "Virtual card ••••${instrument.last4}",
                                    ),
                                )
                            }
                        }
                }
                _message.value = getApplication<Application>().getString(R.string.bank_details_saved)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _message.value = getApplication<Application>().getString(R.string.bank_details_save_failed)
                throw e
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
