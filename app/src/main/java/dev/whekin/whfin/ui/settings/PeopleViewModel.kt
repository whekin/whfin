package dev.whekin.whfin.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.data.db.DebtDirection
import dev.whekin.whfin.data.db.DebtStatus
import dev.whekin.whfin.data.db.PersonEntity
import dev.whekin.whfin.data.db.PersonRole
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PersonListRow(
    val person: PersonEntity,
    /** Остаток открытых долгов, где должны мне, по валютам. */
    val owesMe: List<Pair<String, Long>>,
    /** Остаток открытых долгов, где должен я, по валютам. */
    val iOwe: List<Pair<String, Long>>,
    /** SHARED/GIFT за текущий месяц, по валютам. */
    val spentThisMonth: List<Pair<String, Long>>,
    val hasOpenDebts: Boolean,
)

class PeopleViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as WhfinApp).db
    private val zone = ZoneId.systemDefault()

    private val monthBounds = YearMonth.now(zone).let { month ->
        val from = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        from to to
    }

    val rows: StateFlow<List<PersonListRow>?> = combine(
        db.personDao().observeActive(),
        db.debtDao().observeCases(),
        db.debtDao().observeEvents(),
        db.transactionAllocationDao().observePersonSpending(monthBounds.first, monthBounds.second),
    ) { people, cases, events, spending ->
        val creditedByCase = events.groupBy { it.debtCaseId }
            .mapValues { (_, list) -> list.sumOf { it.debtValueMinor } }
        people.map { person ->
            val open = cases.filter { it.personId == person.id && it.status == DebtStatus.OPEN }
            fun remaining(direction: DebtDirection) = open
                .filter { it.direction == direction }
                .groupBy { it.currency }
                .mapValues { (_, sameCurrency) ->
                    sameCurrency.sumOf { debt ->
                        (debt.originalAmountMinor - (creditedByCase[debt.id] ?: 0L)).coerceAtLeast(0)
                    }
                }
                .filterValues { it > 0 }
                .toList()
            PersonListRow(
                person = person,
                owesMe = remaining(DebtDirection.THEY_OWE_ME),
                iOwe = remaining(DebtDirection.I_OWE_THEM),
                spentThisMonth = spending
                    .filter { it.personId == person.id && it.spentMinor != 0L }
                    .map { it.currency to it.spentMinor },
                hasOpenDebts = open.isNotEmpty(),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun create(name: String, role: PersonRole?, color: Int) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            db.personDao().insert(PersonEntity(name = clean, role = role, color = color))
        }
    }

    fun update(person: PersonEntity, name: String, role: PersonRole?, color: Int) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            db.personDao().update(person.copy(name = clean, role = role, color = color))
        }
    }

    /** Архив, не удаление: allocations и долги остаются связанными с человеком. */
    fun archive(person: PersonEntity) {
        viewModelScope.launch { db.personDao().update(person.copy(isArchived = true)) }
    }
}
