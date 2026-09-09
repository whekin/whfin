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
    val needsStatement: List<TbcLedgerAccount> = emptyList(), val errors: List<String> = emptyList())
class TbcHistorySync(private val db: WhfinDatabase) {
    suspend fun sync(gateway: TbcGateway, today: LocalDate = LocalDate.now(LedgerCalendar.zone),
        progress: (Int, Int) -> Unit = { _, _ -> }): TbcSyncResult {
        val accounts = gateway.ledgerAccounts()
        var inserted = 0; var matched = 0; var unchanged = 0
        val missing = mutableListOf<TbcLedgerAccount>()
        val errors = mutableListOf<String>()
        data class Ready(val remote: TbcLedgerAccount, val account: AccountEntity, val from: LocalDate, val rows: List<TbcHistoryRow>)
        val ready = mutableListOf<Ready>()
        for ((index, remote) in accounts.withIndex()) {
            progress(index + 1, accounts.size)
            val account = db.accountDao().byIbanAndCurrency(remote.iban, remote.currency)
            val opening = account?.let { db.statementImportDao().earliestWithOpeningBalance(it.id) }
            val from = maxOf(today.minusYears(1), opening?.periodFrom?.let(LocalDate::ofEpochDay) ?: today.minusYears(1))
            val needsOpening = account == null || opening?.periodFrom == null
            if (needsOpening) missing += remote
            try {
                val rows = gateway.history(remote, from, today)
                if (account == null || opening?.periodFrom == null) {
                    if (rows.isEmpty() && remote.balanceMinor == 0L) missing.remove(remote)
                } else ready += Ready(remote, account, from, rows)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val code = (e as? TbcException)?.code
                if (code in setOf("SESSION", "PROTECTION", "RATE_LIMIT")) throw e
                errors += remote.label + ": " + (code ?: "HISTORY_FORMAT")
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
                    if (!plan.isNoOp) {
                        ImportApplier(db, LedgerCalendar.zone).apply(plan, item.account, null, StatementImportOrigin.TBC_SYNC)
                    }
                    SmsTransactionImporter(db).attachUnroutedToStatements()
                    plan
                }
                if (committed.isNoOp) unchanged++ else { inserted += committed.inserted; matched += committed.reconciled }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                errors += item.remote.label + ": " + (if (e is InvalidStatementException) "HISTORY_CONFLICT" else "HISTORY_FORMAT")
            }
        }
        return TbcSyncResult(inserted, matched, unchanged, missing, errors)
    }
}
