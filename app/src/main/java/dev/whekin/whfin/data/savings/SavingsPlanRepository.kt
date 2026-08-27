package dev.whekin.whfin.data.savings

import androidx.room.withTransaction
import dev.whekin.whfin.data.db.SavingsPlanEntity
import dev.whekin.whfin.data.db.WhfinDatabase
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

data class SavingsPlanDraft(
    val currency: String,
    val monthlyTargetMinor: Long,
    val goalMinor: Long? = null,
    val goalBy: LocalDate? = null,
)

class SavingsPlanRepository(
    private val db: WhfinDatabase,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    suspend fun set(
        draft: SavingsPlanDraft,
        effectiveMonth: YearMonth = YearMonth.now(clock),
    ): Long = db.withTransaction {
        val currency = draft.currency.trim().uppercase()
        require(currency.matches(Regex("[A-Z0-9]{2,12}"))) { "Unsupported savings-plan currency." }
        require(draft.monthlyTargetMinor > 0L) { "Monthly savings target must be positive." }
        require(draft.goalMinor == null || draft.goalMinor > 0L) { "Savings goal must be positive." }
        require(draft.goalBy == null || draft.goalMinor != null) { "A desired date needs a savings goal." }

        val startedOn = effectiveMonth.atDay(1).toEpochDay()
        val active = db.savingsPlanDao().active(currency)
        val replacement = SavingsPlanEntity(
            id = active?.takeIf { it.startedOn == startedOn }?.id ?: 0L,
            currency = currency,
            monthlyTargetMinor = draft.monthlyTargetMinor,
            goalMinor = draft.goalMinor,
            goalBy = draft.goalBy?.toEpochDay(),
            startedOn = startedOn,
            createdAt = active?.takeIf { it.startedOn == startedOn }?.createdAt ?: clock.millis(),
        )
        if (active?.startedOn == startedOn) {
            db.savingsPlanDao().update(replacement)
            active.id
        } else {
            active?.let { previous ->
                require(previous.startedOn < startedOn) { "Savings plan cannot rewrite a future period." }
                db.savingsPlanDao().finish(previous.id, effectiveMonth.minusMonths(1).atEndOfMonth().toEpochDay())
            }
            db.savingsPlanDao().insert(replacement)
        }
    }

    suspend fun clear(
        currency: String,
        effectiveMonth: YearMonth = YearMonth.now(clock),
    ) = db.withTransaction {
        val normalized = currency.trim().uppercase()
        val active = db.savingsPlanDao().active(normalized) ?: return@withTransaction
        val startedOn = effectiveMonth.atDay(1).toEpochDay()
        when {
            active.startedOn == startedOn -> db.savingsPlanDao().delete(active.id)
            active.startedOn < startedOn -> db.savingsPlanDao()
                .finish(active.id, effectiveMonth.minusMonths(1).atEndOfMonth().toEpochDay())
            else -> error("Savings plan cannot be cleared before it starts.")
        }
    }
}
