package dev.whekin.whfin.data.importer

import androidx.room.withTransaction
import dev.whekin.whfin.data.LedgerCalendar
import dev.whekin.whfin.data.db.*
import dev.whekin.whfin.data.statement.BankProfile
import dev.whekin.whfin.data.statement.BankStatement
import java.time.LocalDate

/** Editing an owner-entered opening is distinct from recording a new unexplained movement today. */
class UserOpeningCorrection(private val db: WhfinDatabase) {
    data class Snapshot internal constructor(
        val account: AccountEntity,
        internal val bank: BankProfile,
        internal val seed: StatementImportEntity,
        internal val rows: List<TransactionEntity>,
    ) {
        val balanceMinor: Long get() = rows.fold(0L) { total, row -> Math.addExact(total, row.amountMinor) }
    }
    class Changed : IllegalStateException("Opening or ledger changed")

    suspend fun read(accountId: Long): Snapshot? = db.withTransaction { readCurrent(accountId) }

    private suspend fun readCurrent(accountId: Long): Snapshot? {
        val account = db.accountDao().byId(accountId) ?: return null
        if (account.iban == null || account.isArchived) return null
        val group = account.groupId?.let { db.financialGroupDao().byId(it) } ?: return null
        val provider = group.provider ?: return null
        val history = db.statementImportDao().forAccount(accountId)
        if (history.any { it.origin != StatementImportOrigin.USER_OPENING && it.openingBalanceMinor != null }) return null
        val seed = history.singleOrNull { it.origin == StatementImportOrigin.USER_OPENING } ?: return null
        if (seed.periodFrom == null || seed.openingBalanceMinor == null) return null
        return Snapshot(account, BankProfile(provider, group.name), seed, db.transactionDao().activeForAccount(accountId))
    }

    /** The desired balance belongs to the displayed ledger snapshot, not to a fresh bank read. */
    suspend fun correct(snapshot: Snapshot, desiredBalanceMinor: Long) = db.withTransaction {
        if (readCurrent(snapshot.account.id) != snapshot) throw Changed()
        val delta = Math.subtractExact(desiredBalanceMinor, snapshot.balanceMinor)
        if (delta == 0L) return@withTransaction
        val opening = Math.addExact(requireNotNull(snapshot.seed.openingBalanceMinor), delta)
        check(db.statementImportDao().replaceUserOpening(snapshot.seed.id, opening) == 1)
        val statement = BankStatement(snapshot.bank, requireNotNull(snapshot.account.iban), snapshot.account.currency,
            LocalDate.ofEpochDay(requireNotNull(snapshot.seed.periodFrom)), snapshot.seed.periodTo?.let(LocalDate::ofEpochDay),
            opening, opening, emptyList())
        OpeningAnchor(db, LedgerCalendar.zone).update(snapshot.account, statement, StatementImportOrigin.USER_OPENING)
        check(db.transactionDao().sumByAccount(snapshot.account.id) == desiredBalanceMinor)
    }
}
