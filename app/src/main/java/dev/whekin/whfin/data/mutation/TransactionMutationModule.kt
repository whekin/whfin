package dev.whekin.whfin.data.mutation

import androidx.room.withTransaction
import dev.whekin.whfin.data.db.AllocationPurpose
import dev.whekin.whfin.data.db.TransactionAllocationEntity
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.data.db.TransferGroupEntity
import dev.whekin.whfin.data.db.TransferGroupType
import dev.whekin.whfin.data.db.WhfinDatabase

/** A signed manual ledger entry. Transfers use both account ids; other entries use only [accountId]. */
data class ManualMutation(
    val accountId: Long,
    val amountMinor: Long,
    val destinationAccountId: Long? = null,
    val destinationAmountMinor: Long? = null,
    val categoryId: Long? = null,
    val note: String? = null,
    val occurredAt: Long,
)

data class AllocationMutation(
    val amountMinor: Long,
    val categoryId: Long? = null,
    val personId: Long? = null,
    val purpose: AllocationPurpose,
    val note: String? = null,
)

data class MutationSelection(
    val transactionId: Long,
    val transferGroupId: Long? = null,
)

data class MutationReport(
    val changed: Int,
    val skipped: Int,
    /** Why the skipped rows were left alone, when they were all left alone for the same reason. */
    val skippedReason: MutationRejection? = null,
)

/**
 * Why the ledger refused, in terms a person can be told.
 *
 * The exception message names the exact invariant and belongs in a log; these categories are what
 * a screen can honestly show. Keeping them apart stops internal wording from leaking into the UI and
 * stops the UI from having to parse sentences.
 */
enum class MutationRejection {
    /** Bank truth is not edited or deleted, only corrected. */
    IMPORTED_IS_PROTECTED,

    /** The movement belongs to a debt and has to be corrected through it. */
    DEBT_LINKED,

    /** Changing the amount would leave the split describing a different sum. */
    ALLOCATIONS_LOCK_AMOUNT,

    /** There is already a correction hiding this row. */
    ALREADY_CORRECTED,

    /** Sharing is a property of spending, not of money coming in. */
    INCOME_NOT_SHAREABLE,

    /** The screen asked for something the ledger cannot represent. */
    INVALID_INPUT,
}

class TransactionMutationException(
    val rejection: MutationRejection,
    message: String,
) : IllegalArgumentException(message)

/**
 * The single write seam for user-authored transaction state.
 *
 * Importers are deliberately outside this module: they own bank provenance and reconciliation. This
 * module owns the smaller, more dangerous surface exposed by UI, widgets and debt flows. Every public
 * method runs atomically, checks the current row rather than trusting a stale screen snapshot, and
 * refuses to erase imported or debt-linked truth.
 */
class TransactionMutationModule(private val db: WhfinDatabase) {

    suspend fun createManual(input: ManualMutation, allocations: List<AllocationMutation> = emptyList()): Long =
        db.withTransaction {
            val account = account(input.accountId)
            requireNonZero(input.amountMinor)
            val destination = input.destinationAccountId?.let { account(it) }
            if (destination == null && input.destinationAmountMinor != null) {
                reject("A destination amount requires a destination account.")
            }
            if (destination != null && destination.id == account.id) reject("A transfer needs two accounts.")
            if (destination != null && input.amountMinor >= 0) {
                reject("A transfer source must be an expense.")
            }
            val now = System.currentTimeMillis()
            val occurredAt = input.occurredAt
            if (destination != null) {
                val groupId = db.transactionDao().insertTransferGroup(
                    TransferGroupEntity(
                        type = if (account.currency == destination.currency) TransferGroupType.TRANSFER
                        else TransferGroupType.CONVERSION,
                        note = input.note,
                        createdAt = now,
                    ),
                )
                val ids = db.transactionDao().insertAll(
                    listOf(
                        manualRow(
                            accountId = account.id,
                            amountMinor = -kotlin.math.abs(input.amountMinor),
                            currency = account.currency,
                            categoryId = null,
                            note = input.note,
                            occurredAt = occurredAt,
                            transferGroupId = groupId,
                            createdAt = now,
                        ),
                        manualRow(
                            accountId = destination.id,
                            amountMinor = kotlin.math.abs(input.destinationAmountMinor ?: input.amountMinor),
                            currency = destination.currency,
                            categoryId = null,
                            note = input.note,
                            occurredAt = occurredAt,
                            transferGroupId = groupId,
                            createdAt = now,
                        ),
                    ),
                )
                check(ids.size == 2 && ids.all { it > 0 }) { "Could not create both transfer legs." }
                check(allocations.isEmpty()) { "Transfers cannot carry expense allocations." }
                ids.first()
            } else {
                val id = db.transactionDao().insert(
                    manualRow(
                        accountId = account.id,
                        amountMinor = input.amountMinor,
                        currency = account.currency,
                        categoryId = input.categoryId,
                        note = input.note,
                        occurredAt = occurredAt,
                        transferGroupId = null,
                        createdAt = now,
                    ),
                )
                check(id > 0) { "Could not create transaction." }
                replaceAllocationsInternal(id, allocations)
                id
            }
        }

    suspend fun updateManual(transactionId: Long, input: ManualMutation) = db.withTransaction {
        val current = transaction(transactionId)
        requireManual(current)
        if (hasDebtEvent(current)) {
            reject("A debt-linked transaction must be corrected through its debt case.", MutationRejection.DEBT_LINKED)
        }
        val currentAllocations = db.transactionAllocationDao().forTransaction(current.id)
        val account = account(input.accountId)
        requireNonZero(input.amountMinor)
        if (currentAllocations.isNotEmpty() &&
            (input.amountMinor != current.amountMinor || account.currency != current.currency)
        ) {
            reject("Clear allocations before changing amount or currency.", MutationRejection.ALLOCATIONS_LOCK_AMOUNT)
        }
        val destination = input.destinationAccountId?.let { account(it) }
        if (destination == null && input.destinationAmountMinor != null) {
            reject("A destination amount requires a destination account.")
        }
        val groupId = current.transferGroupId
        if (groupId == null) {
            if (destination != null) reject("A non-transfer cannot gain a destination in place.")
            db.transactionDao().update(
                current.copy(
                    accountId = account.id,
                    amountMinor = input.amountMinor,
                    currency = account.currency,
                    categoryId = input.categoryId,
                    note = input.note,
                    occurredAt = input.occurredAt,
                ),
            )
        } else {
            val legs = db.transactionDao().byTransferGroup(groupId)
            require(legs.size == 2 && legs.all { it.source == TxSource.MANUAL }) {
                "Only a complete manual transfer can be edited."
            }
            val transferDestination = destination ?: reject("A transfer needs a destination account.")
            if (transferDestination.id == account.id) reject("A transfer needs two accounts.")
            db.transactionDao().updateTransferGroup(
                groupId,
                if (account.currency == transferDestination.currency) TransferGroupType.TRANSFER else TransferGroupType.CONVERSION,
                input.note,
            )
            val outgoing = legs.firstOrNull { it.amountMinor < 0 } ?: reject("Transfer has no outgoing leg.")
            val incoming = legs.firstOrNull { it.id != outgoing.id } ?: reject("Transfer has no incoming leg.")
            db.transactionDao().update(
                outgoing.copy(
                    accountId = account.id,
                    currency = account.currency,
                    amountMinor = -kotlin.math.abs(input.amountMinor),
                    occurredAt = input.occurredAt,
                    note = input.note,
                ),
            )
            db.transactionDao().update(
                incoming.copy(
                    accountId = transferDestination.id,
                    currency = transferDestination.currency,
                    amountMinor = kotlin.math.abs(input.destinationAmountMinor ?: input.amountMinor),
                    occurredAt = input.occurredAt,
                    note = input.note,
                ),
            )
        }
    }

    /** Only a pending SMS can be confirmed from the feed. Imported/manual truth is immutable here. */
    suspend fun confirmPending(selection: List<MutationSelection>): MutationReport = db.withTransaction {
        val ids = buildSet {
            selection.forEach { selected ->
                selected.transferGroupId?.let { groupId ->
                    db.transactionDao().byTransferGroup(groupId).forEach { add(it.id) }
                } ?: add(selected.transactionId)
            }
        }.toList()
        val rows = ids.mapNotNull { db.transactionDao().byId(it) }
        val eligible = rows.filter { it.source == TxSource.SMS && it.status == TxStatus.PENDING }
        if (eligible.isNotEmpty()) {
            db.transactionDao().updateStatus(eligible.map { it.id }, TxStatus.CONFIRMED)
        }
        MutationReport(changed = eligible.size, skipped = rows.size - eligible.size)
    }

    /** Status picker policy: pending SMS may be confirmed or explicitly kept for review. */
    suspend fun setReviewStatus(
        selection: List<MutationSelection>,
        status: TxStatus,
    ): MutationReport = db.withTransaction {
        if (status == TxStatus.PENDING) reject("A reviewed transaction cannot become pending again.")
        val ids = buildSet {
            selection.forEach { selected ->
                selected.transferGroupId?.let { groupId ->
                    db.transactionDao().byTransferGroup(groupId).forEach { add(it.id) }
                } ?: add(selected.transactionId)
            }
        }.toList()
        val rows = ids.mapNotNull { db.transactionDao().byId(it) }
        val eligible = rows.filter {
            it.source == TxSource.SMS && it.status == TxStatus.PENDING &&
                status in setOf(TxStatus.CONFIRMED, TxStatus.MANUAL)
        }
        if (eligible.isNotEmpty()) db.transactionDao().updateStatus(eligible.map { it.id }, status)
        MutationReport(
            changed = eligible.size,
            skipped = rows.size - eligible.size,
            skippedReason = MutationRejection.IMPORTED_IS_PROTECTED.takeIf { eligible.size < rows.size },
        )
    }

    /** Deletes only manual rows. Debt movements stay until their debt event is corrected explicitly. */
    suspend fun delete(selection: List<MutationSelection>): MutationReport = db.withTransaction {
        var changed = 0
        var skipped = 0
        val reasons = mutableSetOf<MutationRejection>()
        val seenGroups = mutableSetOf<Long>()
        selection.forEach { selected ->
            val row = db.transactionDao().byId(selected.transactionId)
            if (row == null) {
                skipped++
                reasons += MutationRejection.INVALID_INPUT
                return@forEach
            }
            // A selection carries the group it was shown with, but the row is the authority: acting
            // on a stale group id would delete a different movement than the one that was picked.
            val groupId = row.transferGroupId
            if (groupId != null) {
                if (!seenGroups.add(groupId)) return@forEach
                val legs = db.transactionDao().byTransferGroup(groupId)
                val debtLinked = legs.any { leg -> hasDebtEvent(leg) }
                if (legs.size != 2 || legs.any { it.source != TxSource.MANUAL } || debtLinked) {
                    skipped++
                    reasons += if (debtLinked) MutationRejection.DEBT_LINKED
                    else MutationRejection.IMPORTED_IS_PROTECTED
                } else {
                    // Both legs or neither: half a transfer is money appearing from nowhere.
                    db.transactionDao().deleteByTransferGroupIds(listOf(groupId))
                    db.transactionDao().deleteTransferGroups(listOf(groupId))
                    changed += legs.size
                }
            } else if (row.source != TxSource.MANUAL || hasDebtEvent(row)) {
                skipped++
                reasons += if (hasDebtEvent(row)) MutationRejection.DEBT_LINKED
                else MutationRejection.IMPORTED_IS_PROTECTED
            } else {
                db.transactionDao().delete(row.id)
                changed++
            }
        }
        MutationReport(changed, skipped, reasons.singleOrNull())
    }

    /** Review queue drafts may be withdrawn only while they are still provisional SMS rows. */
    suspend fun deleteDraft(transactionId: Long): MutationReport = db.withTransaction {
        val row = db.transactionDao().byId(transactionId)
            ?: return@withTransaction MutationReport(changed = 0, skipped = 1)
        val legs = row.transferGroupId?.let { db.transactionDao().byTransferGroup(it) }.orEmpty()
        val rows = if (legs.isEmpty()) listOf(row) else legs
        var debtLinked = false
        for (candidate in rows) if (hasDebtEvent(candidate)) debtLinked = true
        val safe = rows.all { it.source == TxSource.SMS && it.status == TxStatus.PENDING } && !debtLinked
        if (!safe) return@withTransaction MutationReport(
            changed = 0,
            skipped = rows.size,
            skippedReason = if (debtLinked) MutationRejection.DEBT_LINKED
            else MutationRejection.IMPORTED_IS_PROTECTED,
        )
        row.transferGroupId?.let { groupId ->
            db.transactionDao().deleteByTransferGroupIds(listOf(groupId))
            db.transactionDao().deleteTransferGroups(listOf(groupId))
        } ?: db.transactionDao().delete(row.id)
        MutationReport(changed = rows.size, skipped = 0)
    }

    /**
     * Voids statement/SMS truth by adding a balancing adjustment and hiding the source row from all
     * active projections. The source and the adjustment remain in the database for audit/backup.
     */
    suspend fun voidTransaction(transactionId: Long, reason: String? = null): MutationReport = db.withTransaction {
        val rows = correctionRows(transactionId)
        if (rows.any { it.isVoided || it.source !in setOf(TxSource.STATEMENT, TxSource.SMS) }) {
            reject("Only active statement or SMS transactions can be corrected.", MutationRejection.IMPORTED_IS_PROTECTED)
        }
        for (row in rows) {
            if (hasDebtEvent(row)) reject("A debt-linked transaction must be corrected through its debt case.", MutationRejection.DEBT_LINKED)
            if (db.transactionDao().activeCorrectionsFor(row.id).isNotEmpty()) {
                reject("This transaction already has a correction.", MutationRejection.ALREADY_CORRECTED)
            }
        }
        val now = System.currentTimeMillis()
        rows.forEachIndexed { index, row ->
            val correctionId = db.transactionDao().insert(
                TransactionEntity(
                    accountId = row.accountId,
                    amountMinor = -row.amountMinor,
                    currency = row.currency,
                    occurredAt = row.occurredAt,
                    note = reason?.trim()?.takeIf(String::isNotEmpty)?.let { "Correction: $it" }
                        ?: "Correction for transaction ${row.id}",
                    status = TxStatus.MANUAL,
                    source = TxSource.ADJUSTMENT,
                    isTransfer = true,
                    // This is an audit record, not a second active ledger movement. Keep it
                    // excluded from balances and feed projections together with its source row.
                    isVoided = true,
                    correctionOfTransactionId = row.id,
                    externalKey = "correction|${row.id}|$now|$index",
                    createdAt = now,
                ),
            )
            check(correctionId > 0) { "Could not create correction." }
            db.transactionDao().update(row.copy(isVoided = true))
        }
        MutationReport(changed = rows.size, skipped = 0)
    }

    /**
     * Restores a voided source by revoking its correction, preserving both audit rows.
     *
     * The correction is not deleted: it is the record that this row was once withdrawn. Marking it
     * revoked is also what lets the same transaction be corrected again later — an existing
     * correction row would otherwise block that forever.
     */
    suspend fun restoreTransaction(transactionId: Long): MutationReport = db.withTransaction {
        val rows = correctionRows(transactionId)
        if (rows.any { !it.isVoided || it.source !in setOf(TxSource.STATEMENT, TxSource.SMS) }) {
            reject("Only voided statement or SMS transactions can be restored.", MutationRejection.IMPORTED_IS_PROTECTED)
        }
        val corrections = rows.map { row ->
            db.transactionDao().activeCorrectionsFor(row.id).firstOrNull()
                ?: reject("Voided transaction has no correction.")
        }
        val now = System.currentTimeMillis()
        corrections.forEach { correction ->
            db.transactionDao().update(correction.copy(correctionRevokedAt = now))
        }
        rows.forEach { row -> db.transactionDao().update(row.copy(isVoided = false)) }
        MutationReport(changed = rows.size, skipped = 0)
    }

    suspend fun replaceAllocations(
        transactionId: Long,
        allocations: List<AllocationMutation>,
    ) = db.withTransaction {
        transaction(transactionId)
        replaceAllocationsInternal(transactionId, allocations)
    }

    suspend fun assignCategory(transactionId: Long, categoryId: Long) = db.withTransaction {
        val row = transaction(transactionId)
        db.transactionDao().update(row.copy(categoryId = categoryId))
    }

    suspend fun createAdjustment(accountId: Long, amountMinor: Long, categoryId: Long?, occurredAt: Long): Long =
        db.withTransaction {
            requireNonZero(amountMinor)
            val account = account(accountId)
            val id = db.transactionDao().insert(
                TransactionEntity(
                    accountId = account.id,
                    amountMinor = amountMinor,
                    currency = account.currency,
                    occurredAt = occurredAt,
                    categoryId = categoryId,
                    status = TxStatus.MANUAL,
                    source = TxSource.ADJUSTMENT,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            check(id > 0)
            id
        }

    /**
     * What an account already held when the person added it by hand.
     *
     * Shaped exactly like a statement's opening balance rather than like a correction the user made:
     * it counts towards the balance but is marked as a transfer, so money that existed before WHFIN
     * did never turns up as income earned this month. Written once, at creation; everything
     * afterwards is an ordinary adjustment.
     */
    suspend fun createOpeningBalance(accountId: Long, amountMinor: Long, occurredAt: Long): Long =
        db.withTransaction {
            requireNonZero(amountMinor)
            val account = account(accountId)
            val id = db.transactionDao().insert(
                TransactionEntity(
                    accountId = account.id,
                    amountMinor = amountMinor,
                    currency = account.currency,
                    occurredAt = occurredAt,
                    status = TxStatus.CONFIRMED,
                    source = TxSource.ADJUSTMENT,
                    isTransfer = true,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            check(id > 0)
            id
        }

    suspend fun keepReviewDraft(transactionId: Long) = db.withTransaction {
        val row = transaction(transactionId)
        if (row.source !in setOf(TxSource.SMS, TxSource.MANUAL)) {
            reject("A statement row cannot be kept as a draft.", MutationRejection.IMPORTED_IS_PROTECTED)
        }
        if (row.status == TxStatus.PENDING) {
            db.transactionDao().update(row.copy(status = TxStatus.MANUAL))
        }
    }

    private suspend fun replaceAllocationsInternal(transactionId: Long, allocations: List<AllocationMutation>) {
        val row = transaction(transactionId)
        if (allocations.isEmpty()) {
            db.transactionAllocationDao().deleteForTransaction(transactionId)
            return
        }
        // Borrowing money and being repaid are inflows, and both are debt movements that must carry
        // their person. Only the "who benefited" split of a purchase is expense-shaped.
        val debtOnly = allocations.all {
            it.purpose in setOf(AllocationPurpose.LOAN, AllocationPurpose.REPAYMENT)
        }
        if (row.amountMinor > 0 && !debtOnly) reject("Only an expense can be shared between people.", MutationRejection.INCOME_NOT_SHAREABLE)
        if (allocations.any { it.amountMinor == 0L || (it.amountMinor < 0) != (row.amountMinor < 0) }) {
            reject("Allocation signs must match the transaction.")
        }
        if (allocations.sumOf { it.amountMinor } != row.amountMinor) {
            reject("Allocations must sum exactly to the parent transaction.")
        }
        if (allocations.any { it.purpose in setOf(AllocationPurpose.LOAN, AllocationPurpose.REPAYMENT) && it.personId == null }) {
            reject("Debt allocations require a person.")
        }
        db.transactionAllocationDao().replaceForTransaction(
            transactionId,
            allocations.map { allocation ->
                TransactionAllocationEntity(
                    transactionId = transactionId,
                    amountMinor = allocation.amountMinor,
                    categoryId = allocation.categoryId,
                    personId = allocation.personId,
                    purpose = allocation.purpose,
                    note = allocation.note,
                )
            },
        )
    }

    private suspend fun account(id: Long) = db.accountDao().byId(id)
        ?: reject("Account $id does not exist.")

    private suspend fun correctionRows(transactionId: Long): List<TransactionEntity> {
        val row = transaction(transactionId)
        return row.transferGroupId?.let { db.transactionDao().byTransferGroup(it) } ?: listOf(row)
    }

    private suspend fun transaction(id: Long) = db.transactionDao().byId(id)
        ?: reject("Transaction $id does not exist.")

    private suspend fun hasDebtEvent(row: TransactionEntity): Boolean =
        db.debtDao().eventsForTransaction(row.id).isNotEmpty()

    private fun requireManual(row: TransactionEntity) {
        if (row.source != TxSource.MANUAL || row.status != TxStatus.MANUAL) {
            reject("Only a manual transaction can be edited.", MutationRejection.IMPORTED_IS_PROTECTED)
        }
    }

    private fun manualRow(
        accountId: Long,
        amountMinor: Long,
        currency: String,
        categoryId: Long?,
        note: String?,
        occurredAt: Long,
        transferGroupId: Long?,
        createdAt: Long,
    ) = TransactionEntity(
        accountId = accountId,
        amountMinor = amountMinor,
        currency = currency,
        occurredAt = occurredAt,
        categoryId = categoryId,
        note = note,
        status = TxStatus.MANUAL,
        source = TxSource.MANUAL,
        transferGroupId = transferGroupId,
        isTransfer = transferGroupId != null,
        createdAt = createdAt,
    )

    private fun requireNonZero(amountMinor: Long) {
        if (amountMinor == 0L) reject("A transaction cannot be zero.")
    }

    private fun reject(
        message: String,
        rejection: MutationRejection = MutationRejection.INVALID_INPUT,
    ): Nothing = throw TransactionMutationException(rejection, message)
}
