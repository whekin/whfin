package dev.whekin.whfin.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.data.crypto.CryptoEndpoints
import dev.whekin.whfin.data.crypto.CryptoNetwork
import dev.whekin.whfin.data.crypto.CryptoTransferProvider
import dev.whekin.whfin.data.crypto.HttpCryptoTransferProvider
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.IncomeSourceEntity
import dev.whekin.whfin.data.income.IncomeExpectation
import dev.whekin.whfin.data.income.IncomeExpectations
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class IncomeSourcesState(
    val expectations: List<IncomeExpectation>,
    val ended: List<IncomeSourceEntity>,
    val accounts: List<AccountEntity>,
    val month: YearMonth,
    val isReadingChain: Boolean = false,
)

class IncomeSourcesViewModel(
    app: Application,
    private val transfers: CryptoTransferProvider = HttpCryptoTransferProvider({ CryptoEndpoints() }),
) : AndroidViewModel(app) {
    private val db = (app as WhfinApp).db
    private val zone: ZoneId = ZoneId.systemDefault()

    private val today = LocalDate.now(zone)
    private val month = YearMonth.from(today)
    private val monthStart = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    private val monthEnd = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

    /**
     * What the chain said about a watched ledger this month.
     *
     * Held beside the ledger rather than written into it. A watch-only address is not a ledger WHFIN
     * keeps: its balance is read from the chain, and so is this. Recording arrivals as transactions
     * would make the same money exist twice — once as a row, once in the balance the chain reports.
     */
    private val chainReads = MutableStateFlow<Map<Long, ChainRead>>(emptyMap())
    private val readingChain = MutableStateFlow(false)

    private data class ChainRead(val receivedMinor: Long, val count: Int, val failed: Boolean)

    val state: StateFlow<IncomeSourcesState?> = combine(
        db.incomeSourceDao().observeAll(),
        db.transactionDao().observeIncomeBetween(monthStart, monthEnd),
        db.accountDao().observeActive(),
        chainReads,
        readingChain,
    ) { sources, transactions, accounts, reads, reading ->
        val local = IncomeExpectations.of(
            sources.filter { it.endedOn == null },
            transactions,
            month,
            today,
            zone,
        )
        IncomeSourcesState(
            expectations = local.map { expectation ->
                val read = reads[expectation.source.id] ?: return@map expectation
                expectation.copy(
                    receivedMinor = read.receivedMinor,
                    receivedCount = read.count,
                    overdue = expectation.overdue && !read.failed && read.count == 0,
                    unreadable = read.failed,
                )
            },
            ended = sources.filter { it.endedOn != null },
            accounts = accounts,
            month = month,
            isReadingChain = reading,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Asks each watched address what arrived this month.
     *
     * Foreground and on request: reading an address tells somebody the address interests this
     * device, so it happens when the user is looking at the screen that needs it, not on a timer.
     */
    fun refreshFromChain() {
        if (readingChain.value) return
        readingChain.value = true
        viewModelScope.launch {
            try {
                val sources = db.incomeSourceDao().active()
                val results = mutableMapOf<Long, ChainRead>()
                sources.forEach { source ->
                    val accountId = source.accountId ?: return@forEach
                    val account = db.accountDao().byId(accountId) ?: return@forEach
                    val addressId = account.walletAddressId ?: return@forEach
                    val address = db.cryptoDao().addressById(addressId) ?: return@forEach
                    val network = CryptoNetwork.byChainId(address.chainId) ?: return@forEach
                    val asset = account.cryptoAssetId?.let { db.cryptoDao().assetById(it) }
                    results[source.id] = runCatching {
                        val transfers = transfers.incoming(network, address.address, monthStart)
                            .filter { it.occurredAt in monthStart until monthEnd }
                            .filter { transfer ->
                                asset == null || transfer.symbol.equals(asset.symbol, ignoreCase = true)
                            }
                        ChainRead(
                            receivedMinor = transfers.sumOf { toMinor(it.baseUnits, it.decimals) },
                            count = transfers.size,
                            failed = false,
                        )
                    }.getOrElse { ChainRead(0L, 0, failed = true) }
                }
                chainReads.value = results
            } finally {
                readingChain.value = false
            }
        }
    }

    /**
     * Chain base units restated as the two decimals every amount in WHFIN is stored with.
     *
     * Exact by construction: dividing 2 700 000 000 with six decimals yields 270 000, the same
     * 2 700.00 the user declared. Sub-cent dust is dropped rather than rounded up, so a read can
     * never invent money that did not arrive.
     */
    private fun toMinor(baseUnits: java.math.BigInteger, decimals: Int): Long {
        val scale = decimals - 2
        val value = when {
            scale > 0 -> baseUnits / java.math.BigInteger.TEN.pow(scale)
            scale < 0 -> baseUnits * java.math.BigInteger.TEN.pow(-scale)
            else -> baseUnits
        }
        return value.min(java.math.BigInteger.valueOf(Long.MAX_VALUE)).toLong()
    }

    fun save(
        existing: IncomeSourceEntity?,
        label: String,
        amountMinor: Long,
        currency: String,
        accountId: Long?,
        dayFrom: Int,
        dayTo: Int,
    ) {
        val clean = label.trim().ifEmpty { return }
        if (amountMinor <= 0) return
        val from = dayFrom.coerceIn(1, 28)
        viewModelScope.launch {
            db.incomeSourceDao().upsert(
                IncomeSourceEntity(
                    id = existing?.id ?: 0,
                    label = clean,
                    amountMinor = amountMinor,
                    currency = currency.uppercase(),
                    accountId = accountId,
                    expectedDayFrom = from,
                    expectedDayTo = dayTo.coerceIn(from, 28),
                    // A declaration describes the present onwards. Editing one keeps the day it
                    // started on, so correcting a typo does not silently rewrite which months it
                    // claims to describe.
                    startedOn = existing?.startedOn ?: LocalDate.now(zone).withDayOfMonth(1).toEpochDay(),
                    endedOn = existing?.endedOn,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                ),
            )
        }
    }

    /**
     * Closes an era instead of deleting it. The months it described keep their explanation, and the
     * next declaration starts where this one stopped.
     */
    fun end(source: IncomeSourceEntity) {
        viewModelScope.launch {
            db.incomeSourceDao().upsert(
                source.copy(endedOn = LocalDate.now(zone).toEpochDay()),
            )
        }
    }

    fun delete(source: IncomeSourceEntity) {
        viewModelScope.launch { db.incomeSourceDao().delete(source.id) }
    }
}
