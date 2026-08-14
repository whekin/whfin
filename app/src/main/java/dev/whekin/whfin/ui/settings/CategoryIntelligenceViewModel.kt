package dev.whekin.whfin.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.data.categorization.GeorgiaMerchantPreset
import dev.whekin.whfin.data.db.CategoryCoverage
import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.CategoryKind
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
    val categories: List<CategoryEntity>,
    val isChecking: Boolean = false,
    val lastCheckMatches: Int? = null,
    val operationFailed: Boolean = false,
)

class CategoryIntelligenceViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as WhfinApp).db
    private val operation = MutableStateFlow(CategoryIntelligenceOperation())

    val state: StateFlow<CategoryIntelligenceState?> = combine(
        db.transactionDao().observeCategoryCoverage(),
        db.transactionDao().observeUncategorizedMerchants(),
        db.categoryDao().observeAll(),
        operation,
    ) { coverage, unresolved, categories, operation ->
        CategoryIntelligenceState(
            coverage = coverage,
            unresolved = unresolved,
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
                val changed = GeorgiaMerchantPreset.applyToUncategorized(db)
                operation.value = CategoryIntelligenceOperation(lastCheckMatches = changed)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                operation.value = CategoryIntelligenceOperation(failed = true)
            }
        }
    }

    fun assignCategory(merchantId: Long, categoryId: Long) {
        viewModelScope.launch {
            try {
                db.withTransaction {
                    db.merchantDao().setCategory(merchantId, categoryId)
                    db.transactionDao().categorizeUnassignedForMerchant(merchantId, categoryId)
                }
                operation.value = operation.value.copy(failed = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                operation.value = operation.value.copy(failed = true)
            }
        }
    }
}
