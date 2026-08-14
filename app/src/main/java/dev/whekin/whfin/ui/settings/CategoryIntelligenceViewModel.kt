package dev.whekin.whfin.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.data.categorization.CategoryMaintenance
import dev.whekin.whfin.data.db.CategoryCoverage
import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.CategoryKind
import dev.whekin.whfin.data.db.CounterpartyRuleEntity
import dev.whekin.whfin.data.db.PersonEntity
import dev.whekin.whfin.data.db.UncategorizedCounterparty
import dev.whekin.whfin.data.db.UncategorizedMerchant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private data class CategoryIntelligenceOperation(
    val isChecking: Boolean = false,
    val lastCheckMatches: Int? = null,
    val failed: Boolean = false,
)

data class CategoryIntelligenceState(
    val coverage: CategoryCoverage,
    val unresolved: List<UncategorizedMerchant>,
    val counterparties: List<UncategorizedCounterparty> = emptyList(),
    val people: List<PersonEntity> = emptyList(),
    val categories: List<CategoryEntity>,
    val isChecking: Boolean = false,
    val lastCheckMatches: Int? = null,
    val operationFailed: Boolean = false,
)

class CategoryIntelligenceViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as WhfinApp).db
    private val operation = MutableStateFlow(CategoryIntelligenceOperation())

    private val ledger = combine(
        db.transactionDao().observeCategoryCoverage(),
        db.transactionDao().observeUncategorizedMerchants(),
        db.transactionDao().observeUncategorizedCounterparties(),
    ) { coverage, merchants, counterparties -> Triple(coverage, merchants, counterparties) }

    val state: StateFlow<CategoryIntelligenceState?> = combine(
        ledger,
        db.categoryDao().observeAll(),
        db.personDao().observeActive(),
        operation,
    ) { (coverage, merchants, counterparties), categories, people, operation ->
        CategoryIntelligenceState(
            coverage = coverage,
            unresolved = merchants,
            counterparties = counterparties,
            people = people,
            categories = categories.filter { it.kind == CategoryKind.EXPENSE && !it.isSystem },
            isChecking = operation.isChecking,
            lastCheckMatches = operation.lastCheckMatches,
            operationFailed = operation.failed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun checkLocalRules() {
        if (operation.value.isChecking) return
        operation.value = CategoryIntelligenceOperation(isChecking = true)
        viewModelScope.launch {
            try {
                val result = CategoryMaintenance.run(db)
                operation.value = CategoryIntelligenceOperation(lastCheckMatches = result.total)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                operation.value = CategoryIntelligenceOperation(failed = true)
            }
        }
    }

    fun assignCategory(merchantId: Long, categoryId: Long) {
        mutate {
            db.merchantDao().setCategory(merchantId, categoryId)
            db.transactionDao().categorizeUnassignedForMerchant(merchantId, categoryId)
        }
    }

    /**
     * Files every transfer to one recipient account at once, and remembers the decision.
     *
     * [personName] creates the person when the user asked for one that does not exist yet; naming a
     * recipient only labels them, so no allocation or debt is written on their behalf.
     */
    fun assignCounterparty(
        iban: String,
        displayName: String,
        categoryId: Long,
        personId: Long? = null,
        personName: String? = null,
    ) {
        mutate {
            val person = personId ?: personName?.trim()?.takeIf { it.isNotEmpty() }?.let { name ->
                db.personDao().insert(PersonEntity(name = name, color = PERSON_COLOR))
            }
            db.counterpartyRuleDao().upsert(
                CounterpartyRuleEntity(
                    id = db.counterpartyRuleDao().byIban(iban)?.id ?: 0,
                    iban = iban,
                    displayName = displayName,
                    categoryId = categoryId,
                    personId = person,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            db.transactionDao().categorizeUnassignedForCounterparty(iban, categoryId)
        }
    }

    private fun mutate(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                db.withTransaction { block() }
                operation.value = operation.value.copy(failed = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                operation.value = operation.value.copy(failed = true)
            }
        }
    }

    private companion object {
        const val PERSON_COLOR = 0xFF78906F.toInt()
    }
}
