package dev.whekin.whfin.data.importer

import androidx.room.withTransaction
import dev.whekin.whfin.data.LedgerCalendar
import dev.whekin.whfin.data.credo.*
import dev.whekin.whfin.data.db.*
import dev.whekin.whfin.data.statement.*
import java.time.LocalDate

/** Initial creation remains an automatically downloaded, balance-proven bank statement. */
class CredoHistorySync(private val db: WhfinDatabase) {
    suspend fun sync(gateway: CredoGateway, session: CredoSession, remote: CredoRemoteAccount,
        from: LocalDate, to: LocalDate): ImportPlan? {
        val account = db.accountDao().byIbanAndCurrency(remote.accountNumber, remote.currency) ?: return null
        val opening = db.statementImportDao().earliestWithOpeningBalance(account.id) ?: return null
        val start = maxOf(from, opening.periodFrom?.let(LocalDate::ofEpochDay) ?: return null)
        val rows = gateway.history(session, remote, start, to)
        val statement = BankStatement(BankProfile("Credo", "Credo"), remote.accountNumber, remote.currency,
            start, to, null, null, rows)
        StatementValidator.validate(statement)
        return db.withTransaction {
            val plan = ImportPlanner(db, LedgerCalendar.zone).plan(statement, account, false, false, collectReview = false)
            if (!plan.isNoOp) ImportApplier(db, LedgerCalendar.zone).apply(plan, account, null, StatementImportOrigin.CREDO_API)
            plan
        }
    }
}
