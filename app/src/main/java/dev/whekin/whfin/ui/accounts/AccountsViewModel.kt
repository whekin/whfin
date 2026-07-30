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
import dev.whekin.whfin.data.crypto.HttpCryptoBalanceProvider
import dev.whekin.whfin.data.preferences.UiPreferences
import dev.whekin.whfin.data.preferences.nextDisplayCurrency
import dev.whekin.whfin.data.rates.CoinGeckoPriceProvider
import dev.whekin.whfin.data.rates.ConvertedTotal
import dev.whekin.whfin.data.rates.NbgFiatRateProvider
import dev.whekin.whfin.data.rates.NetWorthSource
import dev.whekin.whfin.data.rates.PIVOT_CURRENCY
import dev.whekin.whfin.data.rates.RatesRepository
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
import dev.whekin.whfin.data.db.SavingsMode
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
import dev.whekin.whfin.data.db.*
import dev.whekin.whfin.data.debt.*

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
    val groupName: String? = null,
    /** Watch-only chains report a balance instead of deriving it from transactions. */
    val onChain: OnChainBalance? = null,
)

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
            AccountWithBalance(
                it,
                byAccount[it.id] ?: 0L,
                cardsByAccount[it.id].orEmpty().filter { card -> card.type == PaymentInstrumentType.PHYSICAL_CARD }.map { card -> card.last4 },
                cardsByAccount[it.id].orEmpty().filter { card -> card.type == PaymentInstrumentType.VIRTUAL_CARD }.map { card -> card.last4 },
                it.walletAddressId?.let(addressById::get)?.address,
                it.groupId?.let(groupById::get)?.name,
                balanceByAccount[it.id]?.let { row ->
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
                DebtCaseUi(debt, person, (debt.originalAmountMinor - caseEvents.sumOf { it.debtValueMinor }).coerceAtLeast(0), caseEvents)
            }
        }
    }

    val accountRowsState: StateFlow<AccountRowsState> = accountRows
        .map<List<AccountWithBalance>, AccountRowsState>(AccountRowsState::Ready)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountRowsState.Loading)

    val screenState: StateFlow<AccountsScreenState> = combine(accountRows, debtRows) { accounts, debts ->
        AccountsScreenState.Ready(accounts, debts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsScreenState.Loading)

    val people: StateFlow<List<PersonEntity>> = db.personDao().observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val preferences = UiPreferences(getApplication<Application>())

    /** One reading of everything owned, in the currency the person last chose. */
    val netWorth: StateFlow<ConvertedTotal?> = NetWorthSource(db, preferences).observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val displayCurrency: StateFlow<String> = preferences.displayCurrency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PIVOT_CURRENCY)

    private val ratesRepository = RatesRepository(
        db = db,
        providers = listOf(NbgFiatRateProvider(), CoinGeckoPriceProvider()),
    )

    private val balanceRepository = CryptoBalanceRepository(
        db = db,
        provider = HttpCryptoBalanceProvider(endpoints = { endpoints }),
    )

    @Volatile
    private var endpoints = CryptoEndpoints()

    private val _cryptoRefreshing = MutableStateFlow(false)
    val cryptoRefreshing: StateFlow<Boolean> = _cryptoRefreshing

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
            val result = withContext(Dispatchers.IO) {
                // A wallet total is only meaningful with a price next to it, so both move together.
                ratesRepository.refresh()
                balanceRepository.refreshAll()
            }
            _cryptoRefreshing.value = false
            _message.value = when {
                result.isEmpty -> null
                result.failed == 0 -> app.getString(R.string.crypto_refresh_done, result.refreshed)
                result.refreshed == 0 -> app.getString(R.string.crypto_refresh_failed)
                else -> app.getString(R.string.crypto_refresh_partial, result.refreshed, result.failed)
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

    fun addAccount(
        name: String,
        type: AccountType,
        currency: String,
        address: String? = null,
        bankProvider: String? = null,
        network: CryptoNetwork? = null,
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
                if (type == AccountType.CRYPTO && address != null) {
                    // The network is a user choice, never guessed from the address shape: a typo
                    // must fail here instead of silently creating a ledger on the wrong chain.
                    val chain = network ?: return@withTransaction fail(R.string.account_network_required)
                    val checked = CryptoAddressValidator.check(chain, address)
                    val validAddress = (checked as? CryptoAddressValidator.Result.Valid)?.address
                        ?: return@withTransaction failAddress(chain, checked)
                    val asset = chain.asset(normalizedCurrency)
                        ?: return@withTransaction fail(R.string.account_asset_unsupported)

                    val existingAddress = db.cryptoDao().address(chain.chainId, validAddress)
                    val groupId = existingAddress?.groupId ?: db.financialGroupDao().insert(
                        FinancialGroupEntity(
                            name = normalizedName,
                            type = FinancialGroupType.WALLET,
                            provider = chain.chainId,
                        ),
                    )
                    val addressId = existingAddress?.id ?: db.cryptoDao().insertAddress(
                        WalletAddressEntity(groupId = groupId, chainId = chain.chainId, address = validAddress),
                    )
                    val existingAsset = db.cryptoDao().asset(chain.chainId, asset.contractAddress)
                    val assetId = existingAsset?.id ?: db.cryptoDao().insertAsset(
                        CryptoAssetEntity(
                            chainId = chain.chainId,
                            contractAddress = asset.contractAddress,
                            symbol = asset.symbol,
                            name = asset.name,
                            decimals = asset.decimals,
                        ),
                    )
                    db.accountDao().insert(
                        AccountEntity(
                            name = normalizedName,
                            type = type,
                            currency = asset.symbol,
                            groupId = groupId,
                            walletAddressId = addressId,
                            cryptoAssetId = assetId,
                        ),
                    )
                } else {
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
                            savingsMode = SavingsMode.FLEXIBLE_RESERVE.takeIf { type == AccountType.SAVINGS },
                        ),
                    )
                }
            }
        }
    }

    private fun fail(messageRes: Int) {
        _message.value = getApplication<Application>().getString(messageRes)
    }

    private fun failAddress(network: CryptoNetwork, result: CryptoAddressValidator.Result) {
        val app = getApplication<Application>()
        _message.value = when ((result as? CryptoAddressValidator.Result.Invalid)?.problem) {
            CryptoAddressValidator.Problem.CHECKSUM -> app.getString(R.string.account_address_checksum)
            else -> app.getString(R.string.account_address_invalid, network.displayName)
        }
    }

    fun editAccount(
        account: AccountEntity,
        name: String,
        currency: String,
        address: String?,
        savingsMode: SavingsMode?,
    ) {
        viewModelScope.launch {
            val normalizedName = name.trim().ifBlank { if (account.type == AccountType.CASH) "Cash" else account.name }
            val groupId = account.groupId
            val iban = account.iban
            if (groupId != null && iban != null &&
                (account.type == AccountType.BANK || account.type == AccountType.SAVINGS)
            ) {
                db.accountDao().updateIbanContainer(groupId, iban, normalizedName, savingsMode)
            } else {
                db.accountDao().update(
                    account.copy(
                        name = normalizedName,
                        currency = currency.trim().uppercase(),
                        savingsMode = savingsMode,
                    ),
                )
            }
        }
    }

    fun adjustBalance(item: AccountWithBalance, deltaMinor: Long) {
        viewModelScope.launch {
            val unaccounted = db.categoryDao().systemByName(CategorySeeder.UNACCOUNTED)
            db.transactionDao().insert(
                TransactionEntity(
                    accountId = item.account.id,
                    amountMinor = deltaMinor,
                    currency = item.account.currency,
                    occurredAt = System.currentTimeMillis(),
                    categoryId = unaccounted?.id,
                    status = TxStatus.MANUAL,
                    source = TxSource.ADJUSTMENT,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun updateBankMapping(account: AccountEntity, iban: String?, cardMasks: List<String>, virtualCards: List<String>) {
        viewModelScope.launch {
            try {
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
                _message.value = "Bank details saved"
            } catch (e: Exception) {
                _message.value = e.message ?: "Could not save bank details"
            }
        }
    }

    fun consumeMessage() { _message.value = null }

    fun deleteAccount(account: AccountEntity) {
        viewModelScope.launch {
            db.withTransaction {
                val groupId = account.groupId
                db.accountDao().delete(account.id)
                if (groupId != null && db.accountDao().countInGroup(groupId) == 0) {
                    db.financialGroupDao().delete(groupId)
                }
            }
            _message.value = "Account deleted"
        }
    }

    fun deleteAccountContainer(accounts: List<AccountEntity>) {
        if (accounts.isEmpty()) return
        viewModelScope.launch {
            db.withTransaction {
                val groupIds = accounts.mapNotNull(AccountEntity::groupId).distinct()
                accounts.forEach { db.accountDao().delete(it.id) }
                groupIds.forEach { groupId ->
                    if (db.accountDao().countInGroup(groupId) == 0) db.financialGroupDao().delete(groupId)
                }
            }
            _message.value = "Account deleted"
        }
    }

}
