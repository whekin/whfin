package dev.whekin.whfin.data.debt

import androidx.room.withTransaction
import dev.whekin.whfin.data.db.*

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
    suspend fun open(input: NewDebt): Long = db.withTransaction {
        require(input.amountMinor > 0)
        val personId = input.personId ?: db.personDao().insert(
            PersonEntity(name = requireNotNull(input.personName).trim(), color = 0xFF5F8068.toInt()),
        )
        val txId = input.accountId?.let { accountId ->
            val signed = if (input.direction == DebtDirection.THEY_OWE_ME) -input.amountMinor else input.amountMinor
            db.transactionDao().insert(
                TransactionEntity(accountId = accountId, amountMinor = signed, currency = input.currency,
                    occurredAt = input.occurredAt, note = input.note, status = TxStatus.MANUAL,
                    source = TxSource.MANUAL, createdAt = System.currentTimeMillis()),
            ).also { id ->
                db.transactionAllocationDao().insertAll(listOf(TransactionAllocationEntity(
                    transactionId = id, amountMinor = signed, personId = personId, purpose = AllocationPurpose.LOAN,
                )))
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
        val alreadyCredited = db.debtDao().eventsForCase(debt.id).sumOf { it.debtValueMinor }
        val remaining = (debt.originalAmountMinor - alreadyCredited).coerceAtLeast(0)
        val credit = if (input.close) remaining else requireNotNull(input.debtValueMinor).coerceIn(0, remaining)
        val txId = if (input.accountId != null && input.actualAmountMinor != null && input.actualCurrency != null) {
            val signed = if (debt.direction == DebtDirection.THEY_OWE_ME) input.actualAmountMinor else -input.actualAmountMinor
            db.transactionDao().insert(TransactionEntity(
                accountId = input.accountId, amountMinor = signed, currency = input.actualCurrency,
                occurredAt = input.occurredAt, note = input.note, status = TxStatus.MANUAL,
                source = TxSource.MANUAL, createdAt = System.currentTimeMillis(),
            )).also { id -> db.transactionAllocationDao().insertAll(listOf(TransactionAllocationEntity(
                transactionId = id, amountMinor = signed, personId = debt.personId, purpose = AllocationPurpose.REPAYMENT,
            ))) }
        } else null
        db.debtDao().insertEvent(DebtEventEntity(
            debtCaseId = debt.id, kind = if (input.close) DebtEventKind.CLOSED else DebtEventKind.SETTLEMENT,
            actualAmountMinor = input.actualAmountMinor, actualCurrency = input.actualCurrency,
            accountId = input.accountId, transactionId = txId, debtValueMinor = credit,
            closesCase = input.close, occurredAt = input.occurredAt, note = input.note,
        ))
        if (input.close) db.debtDao().updateCase(debt.copy(status = DebtStatus.CLOSED, closedAt = input.occurredAt))
    }
}
