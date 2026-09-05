package dev.whekin.whfin.data.income

import androidx.room.withTransaction
import dev.whekin.whfin.data.db.IncomeSourceEntity
import dev.whekin.whfin.data.db.IncomeSourcePaymentEntity
import dev.whekin.whfin.data.db.WhfinDatabase
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** A change of receiving account starts a new era instead of moving earlier pay to another account. */
class IncomeSourceRepository(private val db: WhfinDatabase) {
    suspend fun save(source: IncomeSourceEntity): Long = db.withTransaction {
        require(source.label.isNotBlank() && source.amountMinor > 0 && source.currency.isNotBlank())
        require(source.expectedDayFrom in 1..31)
        require(source.endedOn == null || source.endedOn >= source.startedOn)
        val previous = db.incomeSourceDao().byId(source.id)
        val changedAccount = previous?.accountId != null && previous.accountId != source.accountId
        if (changedAccount) {
            require(source.startedOn > previous.startedOn) { "A new receiving account needs a new start date" }
            require(previous.endedOn == null)
            db.incomeSourceDao().upsert(previous.copy(endedOn = source.startedOn - 1))
            db.incomeSourceDao().upsert(source.copy(id = 0))
        } else {
            db.incomeSourceDao().upsert(source)
        }
    }

    /**
     * Records the owner's answer that one credit was this source's pay.
     *
     * Only a row the reading already offered as a candidate can be confirmed: the same account,
     * currency, direction and era it would have to satisfy to be counted at all. That keeps the
     * confirmation from becoming a back door around the rules the reading enforces, and it means a
     * stale screen cannot attach a row the ledger has since changed.
     */
    suspend fun confirmPayment(
        sourceId: Long,
        transactionId: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone),
    ) = db.withTransaction {
        val source = requireNotNull(db.incomeSourceDao().byId(sourceId)) { "No such income source" }
        val transaction = requireNotNull(db.transactionDao().byId(transactionId)) { "No such transaction" }
        val month = YearMonth.from(
            java.time.Instant.ofEpochMilli(transaction.occurredAt).atZone(zone).toLocalDate(),
        )
        require(
            IncomeExpectations.of(listOf(source), listOf(transaction), month, today, zone)
                .singleOrNull()?.candidates?.any { it.id == transactionId } == true,
        ) { "That movement is not a payment this source could have received" }
        db.incomeSourceDao().attach(
            IncomeSourcePaymentEntity(
                transactionId = transactionId,
                incomeSourceId = sourceId,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    /** Takes the answer back. The transaction itself was never changed, so nothing is undone. */
    suspend fun forgetPayment(transactionId: Long) = db.incomeSourceDao().detach(transactionId)
}
