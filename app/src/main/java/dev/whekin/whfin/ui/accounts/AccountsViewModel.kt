package dev.whekin.whfin.ui.accounts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.whekin.whfin.WhfinApp
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
)

class AccountsViewModel(app: Application) : AndroidViewModel(app) {

    private val db = (app as WhfinApp).db
    private val debtRepository = DebtRepository(db)
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val containerMetadata = combine(
        db.financialGroupDao().observeActive(),
        db.cryptoDao().observeAddresses(),
    ) { groups, addresses -> groups.associateBy { it.id } to addresses.associateBy { it.id } }

    val accounts: StateFlow<List<AccountWithBalance>> = combine(
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
        val (groupById, addressById) = metadata
        list.map {
            AccountWithBalance(
                it,
                byAccount[it.id] ?: 0L,
                cardsByAccount[it.id].orEmpty().filter { card -> card.type == PaymentInstrumentType.PHYSICAL_CARD }.map { card -> card.last4 },
                cardsByAccount[it.id].orEmpty().filter { card -> card.type == PaymentInstrumentType.VIRTUAL_CARD }.map { card -> card.last4 },
                it.walletAddressId?.let(addressById::get)?.address,
                it.groupId?.let(groupById::get)?.name,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val debts: StateFlow<List<DebtCaseUi>> = combine(
        db.debtDao().observeCases(), db.debtDao().observeEvents(), db.personDao().observeActive(),
    ) { cases, events, people ->
        val personById = people.associateBy { it.id }
        cases.mapNotNull { debt ->
            val caseEvents = events.filter { it.debtCaseId == debt.id }
            personById[debt.personId]?.let { person ->
                DebtCaseUi(debt, person, (debt.originalAmountMinor - caseEvents.sumOf { it.debtValueMinor }).coerceAtLeast(0), caseEvents)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val people: StateFlow<List<PersonEntity>> = db.personDao().observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    fun addAccount(name: String, type: AccountType, currency: String, address: String? = null, bankProvider: String? = null) {
        viewModelScope.launch {
            db.withTransaction {
                val normalizedCurrency = currency.trim().uppercase()
                if (type == AccountType.CRYPTO && address != null) {
                    val chainId = if (address.startsWith("0x", ignoreCase = true)) "eip155:1" else "tron:mainnet"
                    val existingAddress = db.cryptoDao().address(chainId, address.trim())
                    val groupId = existingAddress?.groupId ?: db.financialGroupDao().insert(
                        FinancialGroupEntity(name = name.trim(), type = FinancialGroupType.WALLET, provider = "Trust Wallet"),
                    )
                    val addressId = existingAddress?.id ?: db.cryptoDao().insertAddress(
                        WalletAddressEntity(groupId = groupId, chainId = chainId, address = address.trim()),
                    )
                    val contract = when (chainId to normalizedCurrency) {
                        "eip155:1" to "USDT" -> "0xdac17f958d2ee523a2206206994597c13d831ec7"
                        "tron:mainnet" to "USDT" -> "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"
                        else -> null
                    }
                    val existingAsset = db.cryptoDao().asset(chainId, contract)
                    val assetId = existingAsset?.id ?: db.cryptoDao().insertAsset(
                        CryptoAssetEntity(chainId = chainId, contractAddress = contract, symbol = normalizedCurrency, name = normalizedCurrency, decimals = if (normalizedCurrency == "USDT") 6 else 18),
                    )
                    db.accountDao().insert(AccountEntity(name = name.trim(), type = type, currency = normalizedCurrency, groupId = groupId, walletAddressId = addressId, cryptoAssetId = assetId))
                } else {
                    val groupId = if (type == AccountType.BANK) {
                        val provider = bankProvider ?: name.trim()
                        db.financialGroupDao().byProvider(FinancialGroupType.BANK, provider)?.id
                            ?: db.financialGroupDao().insert(
                                FinancialGroupEntity(name = provider, type = FinancialGroupType.BANK, provider = provider),
                            )
                    } else null
                    db.accountDao().insert(
                        AccountEntity(
                            name = name.trim(), type = type, currency = normalizedCurrency, groupId = groupId,
                            savingsMode = SavingsMode.FLEXIBLE_RESERVE.takeIf { type == AccountType.SAVINGS },
                        ),
                    )
                }
            }
        }
    }

    fun editAccount(account: AccountEntity, name: String, currency: String, address: String?) {
        viewModelScope.launch {
            db.accountDao().update(
                account.copy(
                    name = name.trim(),
                    currency = currency.trim().uppercase(),
                ),
            )
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

    fun toggleReserve(account: AccountEntity) {
        viewModelScope.launch {
            db.accountDao().update(
                account.copy(
                    savingsMode = if (account.savingsMode == null) SavingsMode.FLEXIBLE_RESERVE else null,
                ),
            )
        }
    }
}
