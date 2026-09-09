package dev.whekin.whfin.data.importer

import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.data.db.WhfinDatabase
import dev.whekin.whfin.data.statement.BankStatement
import java.time.LocalDate
import java.time.ZoneId

/**
 * The single row that says what the account held before the first imported day.
 *
 * A fiat balance is the sum of its rows, so a ledger needs exactly one such row — and it must
 * describe the *earliest* period ever imported. Importing older history therefore moves this anchor
 * back rather than stacking a second opening balance on top of the rows already there, which would
 * double the account's balance.
 */
internal class OpeningAnchor(private val db: WhfinDatabase, private val zone: ZoneId) {

    suspend fun update(account: AccountEntity, statement: BankStatement,
        origin: dev.whekin.whfin.data.db.StatementImportOrigin = dev.whekin.whfin.data.db.StatementImportOrigin.FILE) {
        val userOrigin = dev.whekin.whfin.data.db.StatementImportOrigin.USER_OPENING
        val history = db.statementImportDao().forAccount(account.id)
            .filter { it.periodFrom != null && it.openingBalanceMinor != null }
            .sortedWith(compareByDescending<dev.whekin.whfin.data.db.StatementImportEntity> { it.importedAt }.thenByDescending { it.id })
        val incoming = statement.periodFrom?.let { date -> statement.openingBalanceMinor?.let { Snapshot(date, it) } }
        val bankSnapshots = listOfNotNull(incoming?.takeIf { origin != userOrigin }) + history.filter { it.origin != userOrigin }.map {
            Snapshot(LocalDate.ofEpochDay(requireNotNull(it.periodFrom)), requireNotNull(it.openingBalanceMinor))
        }
        val userSnapshots = listOfNotNull(incoming?.takeIf { origin == userOrigin }) + history.filter { it.origin == userOrigin }.map {
            Snapshot(LocalDate.ofEpochDay(requireNotNull(it.periodFrom)), requireNotNull(it.openingBalanceMinor))
        }
        var earliest = bankSnapshots.minByOrNull(Snapshot::date) ?: userSnapshots.minByOrNull(Snapshot::date) ?: return
        // A later bank statement replaces the owner's provisional amount without discarding older
        // API rows. Walk the bank opening back across those booked rows to the original start.
        val userStart = userSnapshots.minOfOrNull(Snapshot::date)
        if (bankSnapshots.isNotEmpty() && userStart != null && userStart < earliest.date) {
            val before = db.transactionDao().allStatementRows(account.id).filter { tx ->
                val day = java.time.Instant.ofEpochMilli(tx.postedAt ?: tx.occurredAt).atZone(zone).toLocalDate()
                !tx.isVoided && day >= userStart && day < earliest.date
            }.fold(0L) { sum, tx -> Math.addExact(sum, tx.amountMinor) }
            earliest = Snapshot(userStart, Math.subtractExact(earliest.amountMinor, before))
        }

        val existing = db.transactionDao().openingAnchor(account.id)
        // An account whose history reaches its own opening starts from nothing, and nothing needs no
        // row. Writing one leaves a zero-amount entry filed as an adjustment, which reads in the
        // ledger as a balance the user corrected by hand — a thing they never did.
        if (earliest.amountMinor == 0L) {
            existing?.let { db.transactionDao().delete(it.id) }
            return
        }
        val replacement = TransactionEntity(
            id = existing?.id ?: 0,
            accountId = account.id,
            amountMinor = earliest.amountMinor,
            currency = statement.currency,
            // The day before the period starts, so the first statement day opens with this balance.
            occurredAt = earliest.date.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            status = TxStatus.CONFIRMED,
            source = TxSource.ADJUSTMENT,
            isTransfer = true,
            externalKey = StatementIdentity.of(statement).openingKey(earliest.date),
            createdAt = existing?.createdAt?.takeIf { it != 0L } ?: System.currentTimeMillis(),
        )
        if (existing == null) db.transactionDao().insert(replacement)
        else if (existing != replacement) db.transactionDao().update(replacement)
    }

    private data class Snapshot(val date: LocalDate, val amountMinor: Long)
}
