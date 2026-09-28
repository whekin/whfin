package dev.whekin.whfin.data.importer

import androidx.room.withTransaction
import dev.whekin.whfin.data.LedgerCalendar
import dev.whekin.whfin.data.db.*
import dev.whekin.whfin.data.sms.SmsTransactionImporter
import dev.whekin.whfin.data.statement.*
import dev.whekin.whfin.data.tbc.*
import kotlinx.coroutines.CancellationException
import java.time.LocalDate

data class TbcSyncResult(val inserted: Int = 0, val matched: Int = 0, val unchanged: Int = 0,
    val needsStatement: List<TbcLedgerAccount> = emptyList(), val initialHistories: List<TbcInitialHistory> = emptyList(), val errors: List<String> = emptyList(), val reports: List<TbcSyncReport> = emptyList())
/** Counts and masked account labels only; no raw bank payload or authentication data. */
data class TbcSyncReport(val label: String, val received: Int, val alreadyKnown: Int = 0,
    val inserted: Int = 0, val matched: Int = 0, val waitingForBalance: Boolean = false,
    val error: String? = null, val fullHistory: Boolean = false, val stats: TbcHistoryReadStats? = null, val pending: Int = 0,
    /** The product's own figure disagrees with the movements it returned: the list is not the whole story. */
    val bankBalanceDiffers: Boolean = false,
    /** A deposit whose ledger the card history already owns, so its own statement was not read. */
    val readAsLedger: Boolean = false)
data class TbcInitialHistory(val remote: TbcLedgerAccount, val from: LocalDate, val to: LocalDate,
    val rows: List<TbcHistoryRow>, val readAt: Long = System.currentTimeMillis(), val holds: List<TbcHold> = emptyList())
data class TbcInitializationResult(val inserted: Int, val reconciled: Int)
class TbcHistorySync(private val db: WhfinDatabase) {
    suspend fun sync(gateway: TbcGateway, today: LocalDate = LocalDate.now(LedgerCalendar.zone),
        progress: (Int, Int) -> Unit = { _, _ -> }): TbcSyncResult {
        val accounts = gateway.ledgerAccounts()
        var inserted = 0; var matched = 0; var unchanged = 0
        val initial = mutableListOf<TbcInitialHistory>()
        val missing = mutableListOf<TbcLedgerAccount>()
        val errors = mutableListOf<String>()
        val reports = linkedMapOf<String, TbcSyncReport>()
        // Deposits are a separate product family; failing to list them must not cost the card accounts.
        val deposits = try { gateway.deposits() } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            val code = (e as? TbcException)?.code
            if (code in setOf("SESSION", "PROTECTION", "RATE_LIMIT")) throw e
            errors += DEPOSITS_LABEL + ": " + (code ?: "DEPOSIT_FORMAT")
            emptyList()
        }
        val total = accounts.size + deposits.size
        val depositKeys = deposits.map { it.key }.toSet()
        data class Ready(val remote: TbcLedgerAccount, val account: AccountEntity, val from: LocalDate, val rows: List<TbcHistoryRow>, val fullHistory: Boolean, val holds: List<TbcHold>)
        val ready = mutableListOf<Ready>()
        for ((index, remote) in accounts.withIndex()) {
            progress(index + 1, total)
            val account = db.accountDao().byIbanAndCurrency(remote.iban, remote.currency)
            // The bank files this product as a deposit, and that listing prints a running balance
            // this history does not, so the deposit pass owns it and supplies the opening instead
            // of asking the owner for a booked balance. A ledger this history has already written
            // stays with this history: the two sources name rows differently, and handing it over
            // would either duplicate the movements or leave the ledger with no source at all.
            if (remote.key in depositKeys && !readByThisHistory(account)) continue
            val opening = account?.let { db.statementImportDao().earliestWithOpeningBalance(it.id) }
            val imports = account?.let { db.statementImportDao().forAccount(it.id) }.orEmpty()
            val fullHistory = imports.none { it.origin == StatementImportOrigin.TBC_HISTORY }
            val lastThrough = imports.filter { it.origin in setOf(StatementImportOrigin.TBC_SYNC, StatementImportOrigin.TBC_HISTORY) }
                .mapNotNull { it.periodTo?.let(LocalDate::ofEpochDay) }.maxOrNull() ?: today.minusMonths(1)
            // MIN is only a local paging boundary; no impossible date is sent to the bank.
            val requestedFrom = if (fullHistory) LocalDate.MIN else minOf(today.minusMonths(1), lastThrough)
            val needsOpening = account == null || opening?.periodFrom == null
            if (needsOpening) missing += remote
            try {
                val rows = gateway.history(remote, requestedFrom, today)
                val holds = gateway.pendingHolds()
                reports[remote.key] = TbcSyncReport(remote.label, rows.size, waitingForBalance = needsOpening,
                    fullHistory = fullHistory, stats = gateway.historyReadStats(), pending = holds.size)
                val from = if (fullHistory) minOf(rows.minOfOrNull { it.row.postedDate } ?: today,
                    opening?.periodFrom?.let(LocalDate::ofEpochDay) ?: today) else requestedFrom
                if (account == null || opening?.periodFrom == null) {
                    initial += TbcInitialHistory(remote, from, today, rows, holds = holds)
                } else ready += Ready(remote, account, from, rows, fullHistory, holds)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val code = (e as? TbcException)?.code
                if (code in setOf("SESSION", "PROTECTION", "RATE_LIMIT")) throw e
                errors += remote.label + ": " + (code ?: "HISTORY_FORMAT")
                reports[remote.key] = TbcSyncReport(remote.label, 0, error = code ?: "HISTORY_FORMAT",
                    fullHistory = fullHistory, stats = gateway.historyReadStats())
            }
        }
        val own = ready.flatMap { item -> item.rows.filter { it.row.operation.isOwnMovement }.map { item.remote to it } }
            .groupBy { it.second.transactionId }
        for (item in ready) {
            val rows = item.rows.map { row ->
                val pair = own[row.transactionId].orEmpty()
                val peer = pair.singleOrNull { it.first.key != item.remote.key }
                val proven = row.row.operation.isOwnMovement && pair.size == 2 && peer != null &&
                    row.row.operation == peer.second.row.operation &&
                    kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(row.row.postedDate, peer.second.row.postedDate)) <= 3 &&
                    (row.row.amountMinor < 0) != (peer.second.row.amountMinor < 0) &&
                    (if (row.row.operation == StatementOperation.CURRENCY_EXCHANGE) item.remote.currency != peer.first.currency
                     else item.remote.currency == peer.first.currency && row.row.amountMinor == -peer.second.row.amountMinor)
                if (proven) row.row.copy(beneficiaryAccount = peer.first.iban) else row.row
            }
            val statement = BankStatement(BankProfile("TBC", "TBC"), item.remote.iban, item.remote.currency,
                item.from, today, null, null, rows.sortedBy { it.postedDate })
            try {
                StatementValidator.validate(statement)
                val committed = db.withTransaction {
                    val plan = ImportPlanner(db, LedgerCalendar.zone).plan(statement, item.account, false, false, collectReview = false)
                    if (!plan.isNoOp || item.fullHistory) {
                        ImportApplier(db, LedgerCalendar.zone).apply(plan, item.account, null,
                            if (item.fullHistory) StatementImportOrigin.TBC_HISTORY else StatementImportOrigin.TBC_SYNC)
                    }
                    val held = TbcHoldImporter(db).apply(item.account, item.holds)
                    SmsTransactionImporter(db, dev.whekin.whfin.data.sms.BankSmsBank.TBC).attachUnroutedToHolds()
                    SmsTransactionImporter(db).attachUnroutedToStatements()
                    plan to held
                }
                val (booked, held) = committed
                inserted += held.inserted; matched += held.attached
                reports[item.remote.key] = requireNotNull(reports[item.remote.key]).copy(alreadyKnown = booked.duplicates,
                    inserted = booked.inserted, matched = booked.reconciled)
                if (booked.isNoOp && held.inserted == 0 && held.attached == 0) unchanged++ else { inserted += booked.inserted; matched += booked.reconciled }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val code = (e as? TbcException)?.code ?: if (e is InvalidStatementException) "HISTORY_CONFLICT" else "HISTORY_FORMAT"
                errors += item.remote.label + ": " + code
                reports[item.remote.key] = requireNotNull(reports[item.remote.key]).copy(error = code)
            }
        }
        for ((index, deposit) in deposits.withIndex()) {
            progress(accounts.size + index + 1, total)
            try {
                if (!deposit.accountNo.matches(IBAN)) throw TbcException("DEPOSIT_ACCOUNT")
                // An older build may have already read this product through the card history. Its
                // rows carry mobile IDs the deposit statement cannot name, so the card history goes
                // on owning it; importing the same money from the other source would duplicate it.
                if (readByThisHistory(db.accountDao().byIbanAndCurrency(deposit.accountNo, deposit.currency))) {
                    reports[deposit.key] = TbcSyncReport(deposit.label, 0, readAsLedger = true)
                    continue
                }
                val read = gateway.depositStatement(deposit)
                if (read.rows.isEmpty()) {
                    reports[deposit.key] = TbcSyncReport(deposit.label, 0, fullHistory = true)
                    continue
                }
                val statement = BankStatement(BankProfile("TBC", "TBC"), deposit.accountNo, deposit.currency,
                    read.rows.first().postedDate, read.rows.last().postedDate, read.openingMinor, read.closingMinor, read.rows)
                StatementValidator.validate(statement)
                val plan = db.withTransaction {
                    val existing = db.accountDao().byIbanAndCurrency(deposit.accountNo, deposit.currency)
                    val account = existing ?: createDepositLedger(deposit)
                    val first = db.statementImportDao().forAccount(account.id)
                        .none { it.origin == StatementImportOrigin.TBC_HISTORY }
                    val plan = ImportPlanner(db, LedgerCalendar.zone)
                        .plan(statement, account, existing == null, false, collectReview = false)
                    if (!plan.isNoOp || first) ImportApplier(db, LedgerCalendar.zone).apply(plan, account, null,
                        if (first) StatementImportOrigin.TBC_HISTORY else StatementImportOrigin.TBC_SYNC)
                    plan
                }
                reports[deposit.key] = TbcSyncReport(deposit.label, read.rows.size, alreadyKnown = plan.duplicates,
                    inserted = plan.inserted, matched = plan.reconciled, fullHistory = true,
                    bankBalanceDiffers = deposit.balanceMinor != null && deposit.balanceMinor != read.closingMinor)
                if (plan.isNoOp) unchanged++ else { inserted += plan.inserted; matched += plan.reconciled }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val code = (e as? TbcException)?.code
                    ?: if (e is InvalidStatementException) "DEPOSIT_CHAIN" else "DEPOSIT_FORMAT"
                if (code in setOf("SESSION", "PROTECTION", "RATE_LIMIT")) throw e
                errors += deposit.label + ": " + code
                reports[deposit.key] = TbcSyncReport(deposit.label, 0, error = code, fullHistory = true)
            }
        }
        return TbcSyncResult(inserted, matched, unchanged, missing, initial, errors, reports.values.toList())
    }

    /** Whether the mobile card history has already written rows into this ledger, by their own IDs. */
    private suspend fun readByThisHistory(account: AccountEntity?): Boolean = account != null &&
        db.transactionDao().allStatementRows(account.id)
            .any { TbcRowIdentity.mobileFromKey(it.externalKey.orEmpty()) != null }

    /**
     * Brings a deposit ledger into existence.
     *
     * Unlike a statement import this never adopts an IBAN-less ledger: those are created by SMS from
     * card spending, and handing one to a deposit would move a savings history onto an everyday
     * account. The product type is the bank's own answer about further top-ups, not a reading of the
     * owner's Available/Reserve choice.
     */
    private suspend fun createDepositLedger(deposit: TbcDepositAccount): AccountEntity {
        val groupId = db.financialGroupDao().byProvider(FinancialGroupType.BANK, "TBC")?.id
            ?: db.financialGroupDao().insert(FinancialGroupEntity(name = "TBC", type = FinancialGroupType.BANK, provider = "TBC"))
        val id = db.accountDao().insert(AccountEntity(
            name = deposit.name.ifBlank { "TBC ${deposit.currency} •${deposit.accountNo.takeLast(4)}" },
            type = AccountType.BANK, groupId = groupId, currency = deposit.currency, iban = deposit.accountNo,
            bankProduct = deposit.acceptsTopUp?.let { if (it) BankProduct.DEMAND_DEPOSIT else BankProduct.TERM_DEPOSIT }))
        return requireNotNull(db.accountDao().byId(id))
    }
    /** Uses the exact displayed read, never a fresh download after the owner enters its balance. */
    suspend fun initialize(initial: TbcInitialHistory, bookedBalanceMinor: Long): TbcInitializationResult {
        val prepared = prepareInitialization(initial, bookedBalanceMinor)
        return db.withTransaction { applyInitialization(prepared) }
    }

    /** All reviewed balances either enter the ledger together or none of them do. */
    suspend fun initializeBatch(balances: List<Pair<TbcInitialHistory, Long>>): List<TbcInitializationResult> {
        require(balances.isNotEmpty())
        require(balances.map { it.first.remote.key }.distinct().size == balances.size)
        val prepared = balances.map { (initial, booked) -> prepareInitialization(initial, booked) }
        return db.withTransaction { prepared.map { applyInitialization(it) } }
    }

    private data class PreparedInitialization(val initial: TbcInitialHistory, val opening: Long,
        val statement: BankStatement)

    private fun prepareInitialization(initial: TbcInitialHistory, bookedBalanceMinor: Long): PreparedInitialization {
        if (System.currentTimeMillis() - initial.readAt > 15 * 60_000L) throw TbcException("HISTORY_CHANGED")
        val remote = initial.remote
        val net = initial.rows.fold(0L) { sum, row -> Math.addExact(sum, row.row.amountMinor) }
        val opening = Math.subtractExact(bookedBalanceMinor, net)
        val statement = BankStatement(BankProfile("TBC", "TBC"), remote.iban, remote.currency,
            initial.from, initial.to, null, null, initial.rows.map { it.row }.sortedBy { it.postedDate })
        StatementValidator.validate(statement)
        return PreparedInitialization(initial, opening, statement)
    }

    private suspend fun applyInitialization(prepared: PreparedInitialization): TbcInitializationResult {
        val (initial, opening, statement) = prepared
        val resolved = BankLedgerResolver(db).resolve(statement)
        if (db.statementImportDao().earliestWithOpeningBalance(resolved.account.id) != null) throw TbcException("HISTORY_CHANGED")
        val plan = ImportPlanner(db, LedgerCalendar.zone).plan(statement, resolved.account, resolved.created, resolved.adopted, collectReview = false)
        val seed = statement.copy(rows = emptyList(), openingBalanceMinor = opening, closingBalanceMinor = opening)
        StatementValidator.validate(seed)
        ImportApplier(db, LedgerCalendar.zone).apply(ImportPlan(seed, resolved.account.id, resolved.created, resolved.adopted, emptyList(), emptyList()),
            resolved.account, null, StatementImportOrigin.USER_OPENING)
        ImportApplier(db, LedgerCalendar.zone).apply(plan, resolved.account, null, StatementImportOrigin.TBC_HISTORY)
        val held = TbcHoldImporter(db).apply(resolved.account, initial.holds)
        SmsTransactionImporter(db, dev.whekin.whfin.data.sms.BankSmsBank.TBC).attachUnroutedToHolds()
        SmsTransactionImporter(db).attachUnroutedToStatements()
        return TbcInitializationResult(plan.inserted + held.inserted, plan.reconciled + held.attached)
    }

    companion object {
        private val IBAN = Regex("GE[0-9]{2}TB[0-9]{16}")

        /**
         * Names the deposit listing itself rather than one account.
         *
         * A stable key, not a word: every other label in a report is the bank's own account naming,
         * and this one is WHFIN speaking, so the screen says it in the reader's language.
         */
        const val DEPOSITS_LABEL = "tbc:deposits"
    }
}
