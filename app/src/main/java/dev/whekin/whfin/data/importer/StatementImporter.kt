package dev.whekin.whfin.data.importer

import androidx.room.withTransaction
import dev.whekin.whfin.data.db.StatementImportOrigin
import dev.whekin.whfin.data.db.WhfinDatabase
import dev.whekin.whfin.data.statement.BankStatement
import dev.whekin.whfin.data.statement.StatementFile
import dev.whekin.whfin.data.statement.StatementParsers
import java.io.InputStream
import java.time.ZoneId

/**
 * Bank-neutral pipeline: statement file -> ledger.
 *
 * Parsing one bank's format lives behind [StatementParsers]; what remains here is the order of
 * events, and only the order. Each step is its own object with one job: [BankLedgerResolver] finds
 * the currency ledger, [ImportPlanner] decides what would happen, [ImportApplier] writes exactly
 * that, and [TransferPairing] joins movements whose other half lives in a different file.
 *
 * The decision and the writing are deliberately separate. An import used to accumulate its counts
 * while rows were already landing, so what a user was told afterwards could not be checked against
 * anything; now the plan exists first, can be inspected without touching the ledger, and the numbers
 * reported are the plan's own.
 */
class StatementImporter(private val db: WhfinDatabase) {

    enum class Phase { READING, IMPORTING, RECONCILING, VERIFYING }

    data class Result(
        val accountId: Long,
        val accountCreated: Boolean,
        val totalRows: Int,
        val inserted: Int,
        val duplicates: Int,
        val reconciled: Int,
        val importId: Long,
        val reviewCount: Int,
    )

    /** What an import would do to the ledger as it stands right now, without writing anything. */
    data class Preview(
        val statement: BankStatement,
        val ledgerEffect: LedgerEffect,
        /** The ledger this file belongs to — existing, or the one [createsAccount] would add. */
        val ledgerName: String,
        val totalRows: Int,
        val inserted: Int,
        val duplicates: Int,
        val reconciled: Int,
        val reviewCount: Int,
    ) {
        /** A wrong file picked here leaves an account behind, so this is worth confirming. */
        val createsAccount: Boolean get() = ledgerEffect == LedgerEffect.CREATED

        /**
         * True when importing this file would leave everything exactly as it is.
         *
         * The ledger counts. A statement with no rows still creates the account it describes and
         * anchors its opening balance, so "no rows to add" is not the same as "no change".
         */
        val changesNothing: Boolean
            get() = ledgerEffect == LedgerEffect.UNCHANGED &&
                inserted == 0 && reconciled == 0 && reviewCount == 0
    }

    private val zone = ZoneId.of("Asia/Tbilisi")

    /** Re-runs pairing when a newly imported currency ledger makes old conversions matchable. */
    suspend fun repairTransferGroups() = TransferPairing(db, zone).repairAll()

    /**
     * Reads a file and reports what importing it would change.
     *
     * Nothing is written, including the ledger the statement belongs to: a file the user has not
     * accepted yet must not leave an account behind. When that ledger does not exist, the file is
     * planned against an empty history, which is exactly what importing it would find.
     */
    suspend fun preview(input: InputStream, fileName: String? = null): Preview {
        val statement = StatementParsers.parse(StatementFile.read(input, fileName))
        StatementValidator.validate(statement)
        val ledger = BankLedgerResolver(db).plan(statement)
        val account = ledger.account ?: return Preview(
            statement = statement,
            ledgerEffect = ledger.effect,
            ledgerName = ledger.ledgerName,
            totalRows = statement.rows.size,
            inserted = statement.rows.size,
            duplicates = 0,
            reconciled = 0,
            reviewCount = 0,
        )
        val plan = ImportPlanner(db, zone).plan(statement, account, accountCreated = false, accountAdopted = false)
        return Preview(
            statement = statement,
            ledgerEffect = ledger.effect,
            ledgerName = ledger.ledgerName,
            totalRows = plan.totalRows,
            inserted = plan.inserted,
            duplicates = plan.duplicates,
            reconciled = plan.reconciled,
            reviewCount = plan.reviewCandidateIds.size,
        )
    }

    suspend fun import(
        input: InputStream,
        fileName: String? = null,
        origin: StatementImportOrigin = StatementImportOrigin.FILE,
        onPhase: (Phase) -> Unit = {},
    ): Result {
        onPhase(Phase.READING)
        val statement = StatementParsers.parse(StatementFile.read(input, fileName))
        onPhase(Phase.VERIFYING)
        StatementValidator.validate(statement)
        onPhase(Phase.IMPORTING)
        // One SQLite transaction for the whole file: a thousand separate commits take tens of
        // seconds, and a failure halfway would leave a balance that matches no bank document.
        return db.withTransaction {
            val resolved = BankLedgerResolver(db).resolve(statement)
            val plan = ImportPlanner(db, zone).plan(
                statement = statement,
                account = resolved.account,
                accountCreated = resolved.created,
                accountAdopted = resolved.adopted,
            )
            onPhase(Phase.RECONCILING)
            val importId = ImportApplier(db, zone).apply(plan, resolved.account, fileName, origin)
            Result(
                accountId = plan.accountId,
                accountCreated = plan.accountCreated,
                totalRows = plan.totalRows,
                inserted = plan.inserted,
                duplicates = plan.duplicates,
                reconciled = plan.reconciled,
                importId = importId,
                reviewCount = plan.reviewCandidateIds.size,
            )
        }
    }
}
