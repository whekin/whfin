package dev.whekin.whfin.data.importer

import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.WhfinDatabase
import dev.whekin.whfin.data.statement.BankStatement
import java.time.ZoneId

/** Read-only comparison at the statement cutoff, never a balancing adjustment. */
internal class StatementLedgerAudit(private val db: WhfinDatabase, private val zone: ZoneId) {
    suspend fun needsReview(statement: BankStatement, accountId: Long): Boolean {
        val closing = statement.closingBalanceMinor ?: return false
        val to = statement.periodTo ?: return false
        val cutoff = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val rows = db.transactionDao().activeForAccount(accountId)
        return try {
            rows.asSequence().filter { it.source != TxSource.BANK_HOLD && (it.postedAt ?: it.occurredAt) < cutoff }
                .fold(0L) { sum, row -> Math.addExact(sum, row.amountMinor) } != closing
        } catch (_: ArithmeticException) { true }
    }
}
