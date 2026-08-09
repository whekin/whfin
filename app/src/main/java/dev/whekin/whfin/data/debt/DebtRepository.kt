package dev.whekin.whfin.data.debt

import androidx.room.withTransaction
import dev.whekin.whfin.data.db.*
import dev.whekin.whfin.data.mutation.AllocationMutation
import dev.whekin.whfin.data.mutation.ManualMutation
import dev.whekin.whfin.data.mutation.TransactionMutationModule

data class NewDebt(
    val personId: Long? = null,
    val personName: String? = null,
    val direction: DebtDirection,
    val amountMinor: Long,
    val currency: String,
    val accountId: Long? = null,
    val occurredAt: Long,
    val note: String? = null,
)

data class DebtSettlement(
    val debtCaseId: Long,
    val actualAmountMinor: Long? = null,
    val actualCurrency: String? = null,
    val accountId: Long? = null,
    /** Null means credit the entire remaining debt when closing. */
    val debtValueMinor: Long? = null,
    val close: Boolean = true,
    val occurredAt: Long,
    val note: String? = null,
)

class DebtRepository(private val db: WhfinDatabase) {
    private val transactionMutations = TransactionMutationModule(db)

    suspend fun open(input: NewDebt): Long = db.withTransaction {
        require(input.amountMinor > 0)
        val personId = input.personId ?: db.personDao().insert(
            PersonEntity(name = requireNotNull(input.personName).trim(), color = 0xFF5F8068.toInt()),
        )
        val txId = input.accountId?.let { accountId ->
            val signed = if (input.direction == DebtDirection.THEY_OWE_ME) -input.amountMinor else input.amountMinor
            transactionMutations.createManual(
                ManualMutation(
                    accountId = accountId,
                    amountMinor = signed,
                    occurredAt = input.occurredAt,
                    note = input.note,
                ),
            ).also { id ->
                transactionMutations.replaceAllocations(
                    id,
                    listOf(AllocationMutation(
                        amountMinor = signed,
                        personId = personId,
                        purpose = AllocationPurpose.LOAN,
                    )),
                )
            }
        }
        val caseId = db.debtDao().insertCase(DebtCaseEntity(
            personId = personId, direction = input.direction, originalAmountMinor = input.amountMinor,
            currency = input.currency, openedAt = input.occurredAt, note = input.note,
        ))
        db.debtDao().insertEvent(DebtEventEntity(
            debtCaseId = caseId, kind = DebtEventKind.OPENED, actualAmountMinor = input.amountMinor,
            actualCurrency = input.currency, accountId = input.accountId, transactionId = txId,
            occurredAt = input.occurredAt, note = input.note,
        ))
        caseId
    }

    suspend fun settle(input: DebtSettlement) = db.withTransaction {
        val debt = requireNotNull(db.debtDao().caseById(input.debtCaseId))
        require(debt.status == DebtStatus.OPEN)
        val alreadyCredited = db.debtDao().eventsForCase(debt.id)
            .filterNot { it.isVoided }
            .sumOf { it.debtValueMinor }
        val remaining = (debt.originalAmountMinor - alreadyCredited).coerceAtLeast(0)
        val credit = if (input.close) remaining else requireNotNull(input.debtValueMinor).coerceIn(0, remaining)
        val txId = if (input.accountId != null && input.actualAmountMinor != null && input.actualCurrency != null) {
            val signed = if (debt.direction == DebtDirection.THEY_OWE_ME) input.actualAmountMinor else -input.actualAmountMinor
            transactionMutations.createManual(
                ManualMutation(
                    accountId = input.accountId,
                    amountMinor = signed,
                    occurredAt = input.occurredAt,
                    note = input.note,
                ),
            ).also { id ->
                transactionMutations.replaceAllocations(
                    id,
                    listOf(AllocationMutation(
                        amountMinor = signed,
                        personId = debt.personId,
                        purpose = AllocationPurpose.REPAYMENT,
                    )),
                )
            }
        } else null
        db.debtDao().insertEvent(DebtEventEntity(
            debtCaseId = debt.id, kind = if (input.close) DebtEventKind.CLOSED else DebtEventKind.SETTLEMENT,
            actualAmountMinor = input.actualAmountMinor, actualCurrency = input.actualCurrency,
            accountId = input.accountId, transactionId = txId, debtValueMinor = credit,
            closesCase = input.close, occurredAt = input.occurredAt, note = input.note,
        ))
        if (input.close) db.debtDao().updateCase(debt.copy(status = DebtStatus.CLOSED, closedAt = input.occurredAt))
    }

    /**
     * Corrects a mistaken settlement/adjustment without deleting the event history.  The source
     * event and its inverse audit row stay in the database but are both excluded from debt totals.
     */
    suspend fun correctEvent(eventId: Long, reason: String? = null, occurredAt: Long = System.currentTimeMillis()): Long =
        db.withTransaction {
            val event = requireNotNull(db.debtDao().eventById(eventId)) {
                "Debt event $eventId was not found"
            }
            require(event.kind != DebtEventKind.OPENED) { "The opening event cannot be corrected; correct the debt case instead." }
            require(!event.isVoided && event.correctionOfEventId == null) { "This debt event is already corrected." }

            db.debtDao().updateEvent(event.copy(isVoided = true))
            val correctionId = db.debtDao().insertEvent(
                DebtEventEntity(
                    debtCaseId = event.debtCaseId,
                    kind = DebtEventKind.ADJUSTMENT,
                    actualAmountMinor = event.actualAmountMinor,
                    actualCurrency = event.actualCurrency,
                    accountId = event.accountId,
                    transactionId = event.transactionId,
                    debtValueMinor = -event.debtValueMinor,
                    occurredAt = occurredAt,
                    note = reason?.trim()?.takeIf(String::isNotEmpty)?.let { "Correction: $it" }
                        ?: "Correction for debt event ${event.id}",
                    isVoided = true,
                    correctionOfEventId = event.id,
                ),
            )
            refreshCaseStatus(event.debtCaseId, occurredAt)
            correctionId
        }

    /** Reopens a closed case only when its active events leave a positive outstanding balance. */
    suspend fun reopen(caseId: Long, occurredAt: Long = System.currentTimeMillis()) = db.withTransaction {
        val debt = requireNotNull(db.debtDao().caseById(caseId))
        require(debt.status == DebtStatus.CLOSED) { "The debt case is already open." }
        val remaining = outstanding(debt)
        require(remaining > 0) { "Correct the closing event before reopening this debt." }
        db.debtDao().updateCase(debt.copy(status = DebtStatus.OPEN, closedAt = null))
    }

    private suspend fun refreshCaseStatus(caseId: Long, changedAt: Long) {
        val debt = requireNotNull(db.debtDao().caseById(caseId))
        val remaining = outstanding(debt)
        val closed = remaining <= 0L
        db.debtDao().updateCase(
            debt.copy(
                status = if (closed) DebtStatus.CLOSED else DebtStatus.OPEN,
                closedAt = if (closed) debt.closedAt ?: changedAt else null,
            ),
        )
    }

    private suspend fun outstanding(debt: DebtCaseEntity): Long =
        (debt.originalAmountMinor - db.debtDao().eventsForCase(debt.id)
            .filterNot { it.isVoided }
            .sumOf { it.debtValueMinor }).coerceAtLeast(0L)
}
