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
    val error: String? = null, val fullHistory: Boolean = false, val stats: TbcHistoryReadStats? = null)
data class TbcInitialHistory(val remote: TbcLedgerAccount, val from: LocalDate, val to: LocalDate,
    val rows: List<TbcHistoryRow>, val readAt: Long = System.currentTimeMillis())
class TbcHistorySync(private val db: WhfinDatabase) {
    suspend fun sync(gateway: TbcGateway, today: LocalDate = LocalDate.now(LedgerCalendar.zone),
        progress: (Int, Int) -> Unit = { _, _ -> }): TbcSyncResult {
        val accounts = gateway.ledgerAccounts()
        var inserted = 0; var matched = 0; var unchanged = 0
        val initial = mutableListOf<TbcInitialHistory>()
        val missing = mutableListOf<TbcLedgerAccount>()
        val errors = mutableListOf<String>()
        val reports = linkedMapOf<String, TbcSyncReport>()
        data class Ready(val remote: TbcLedgerAccount, val account: AccountEntity, val from: LocalDate, val rows: List<TbcHistoryRow>, val fullHistory: Boolean)
        val ready = mutableListOf<Ready>()
        for ((index, remote) in accounts.withIndex()) {
            progress(index + 1, accounts.size)
            val account = db.accountDao().byIbanAndCurrency(remote.iban, remote.currency)
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
                reports[remote.key] = TbcSyncReport(remote.label, rows.size, waitingForBalance = needsOpening,
                    fullHistory = fullHistory, stats = gateway.historyReadStats())
                val from = if (fullHistory) minOf(rows.minOfOrNull { it.row.postedDate } ?: today,
                    opening?.periodFrom?.let(LocalDate::ofEpochDay) ?: today) else requestedFrom
                if (account == null || opening?.periodFrom == null) {
                    initial += TbcInitialHistory(remote, from, today, rows)
                } else ready += Ready(remote, account, from, rows, fullHistory)
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
                    SmsTransactionImporter(db).attachUnroutedToStatements()
                    plan
                }
                reports[item.remote.key] = requireNotNull(reports[item.remote.key]).copy(alreadyKnown = committed.duplicates,
                    inserted = committed.inserted, matched = committed.reconciled)
                if (committed.isNoOp) unchanged++ else { inserted += committed.inserted; matched += committed.reconciled }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val code = if (e is InvalidStatementException) "HISTORY_CONFLICT" else "HISTORY_FORMAT"
                errors += item.remote.label + ": " + code
                reports[item.remote.key] = requireNotNull(reports[item.remote.key]).copy(error = code)
            }
        }
        return TbcSyncResult(inserted, matched, unchanged, missing, initial, errors, reports.values.toList())
    }
    /** Uses the exact displayed read, never a fresh download after the owner enters its balance. */
    suspend fun initialize(initial: TbcInitialHistory, bookedBalanceMinor: Long): ImportPlan {
        if (System.currentTimeMillis() - initial.readAt > 15 * 60_000L) throw TbcException("HISTORY_CHANGED")
        val remote = initial.remote
        val net = initial.rows.fold(0L) { sum, row -> Math.addExact(sum, row.row.amountMinor) }
        val opening = Math.subtractExact(bookedBalanceMinor, net)
        val statement = BankStatement(BankProfile("TBC", "TBC"), remote.iban, remote.currency,
            initial.from, initial.to, null, null, initial.rows.map { it.row }.sortedBy { it.postedDate })
        StatementValidator.validate(statement)
        return db.withTransaction {
            val resolved = BankLedgerResolver(db).resolve(statement)
            if (db.statementImportDao().earliestWithOpeningBalance(resolved.account.id) != null) throw TbcException("HISTORY_CHANGED")
            val plan = ImportPlanner(db, LedgerCalendar.zone).plan(statement, resolved.account, resolved.created, resolved.adopted, collectReview = false)
            val seed = statement.copy(rows = emptyList(), openingBalanceMinor = opening, closingBalanceMinor = opening)
            StatementValidator.validate(seed)
            ImportApplier(db, LedgerCalendar.zone).apply(ImportPlan(seed, resolved.account.id, resolved.created, resolved.adopted, emptyList(), emptyList()),
                resolved.account, null, StatementImportOrigin.USER_OPENING)
            ImportApplier(db, LedgerCalendar.zone).apply(plan, resolved.account, null, StatementImportOrigin.TBC_HISTORY)
            SmsTransactionImporter(db).attachUnroutedToStatements()
            plan
        }
    }

}
