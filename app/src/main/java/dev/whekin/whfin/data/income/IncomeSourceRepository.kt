package dev.whekin.whfin.data.income

import androidx.room.withTransaction
import dev.whekin.whfin.data.db.IncomeSourceEntity
import dev.whekin.whfin.data.db.WhfinDatabase

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
}
