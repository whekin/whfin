package dev.whekin.whfin.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.data.categorization.CategoryCatalog
import dev.whekin.whfin.data.categorization.CategoryMaintenance
import dev.whekin.whfin.data.categorization.CategoryPacks
import dev.whekin.whfin.data.categorization.CategoryProposals
import dev.whekin.whfin.data.db.CategoryCoverage
import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.CategoryKind
import dev.whekin.whfin.data.db.CounterpartyRuleEntity
import dev.whekin.whfin.data.db.CounterpartyUsage
import dev.whekin.whfin.data.db.MerchantEntity
import dev.whekin.whfin.data.db.MerchantUsage
import dev.whekin.whfin.data.db.PersonEntity
import dev.whekin.whfin.data.db.UncategorizedCounterparty
import dev.whekin.whfin.data.db.UncategorizedMerchant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private data class CategoryIntelligenceOperation(
    val isChecking: Boolean = false,
    val lastCheckMatches: Int? = null,
    val failed: Boolean = false,
)

/**
 * A decision the user has already made about one recipient account, shown back to them.
 *
 * Rules were writable before they were readable, which meant a wrong one could not be corrected at
 * all: the row it explained left the list the moment it was answered, taking the only way back to it.
 */
data class CounterpartyRuleView(
    val id: Long,
    val iban: String,
    val displayName: String,
    val categoryId: Long?,
    val categoryName: String?,
    val personName: String?,
    val transactionCount: Int,
    val isDismissed: Boolean,
)

data class CategoryIntelligenceState(
    val coverage: CategoryCoverage,
    val unresolved: List<UncategorizedMerchant>,
    /** Recipients seen more than once — the only ones a standing rule can honestly describe. */
    val counterparties: List<UncategorizedCounterparty> = emptyList(),
    /** Recipients seen exactly once, kept apart so a one-off does not read as a pattern. */
    val counterpartiesOnce: List<UncategorizedCounterparty> = emptyList(),
    val incomeCoverage: CategoryCoverage = CategoryCoverage(0, 0, 0),
    val incomeSenders: List<UncategorizedCounterparty> = emptyList(),
    val incomeSendersOnce: List<UncategorizedCounterparty> = emptyList(),
    val rules: List<CounterpartyRuleView> = emptyList(),
    val people: List<PersonEntity> = emptyList(),
    val categories: List<CategoryEntity>,
    val incomeCategories: List<CategoryEntity> = emptyList(),
    val proposals: List<CategoryProposals.Proposal> = emptyList(),
    val packs: List<CategoryPacks.Pack> = emptyList(),
    val isChecking: Boolean = false,
    val lastCheckMatches: Int? = null,
    val operationFailed: Boolean = false,
)

class CategoryIntelligenceViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as WhfinApp).db
    private val operation = MutableStateFlow(CategoryIntelligenceOperation())

    private data class Spending(
        val coverage: CategoryCoverage,
        val merchants: List<UncategorizedMerchant>,
        val counterparties: List<UncategorizedCounterparty>,
    )

    private data class Earning(
        val coverage: CategoryCoverage,
        val senders: List<UncategorizedCounterparty>,
    )

    private data class Earned(
        val merchants: List<MerchantEntity>,
        val usage: List<MerchantUsage>,
    )

    private val earned = combine(
        db.merchantDao().observeAll(),
        db.transactionDao().observeMerchantUsage(),
        ::Earned,
    )

    private val spending = combine(
        db.transactionDao().observeCategoryCoverage(),
        db.transactionDao().observeUncategorizedMerchants(),
        db.transactionDao().observeUncategorizedCounterparties(),
        ::Spending,
    )

    private val earning = combine(
        db.transactionDao().observeIncomeCoverage(),
        db.transactionDao().observeUncategorizedIncomeCounterparties(),
        ::Earning,
    )

    private data class Rules(
        val rules: List<CounterpartyRuleEntity>,
        val usage: List<CounterpartyUsage>,
    )

    private val rules = combine(
        db.counterpartyRuleDao().observeAll(),
        db.transactionDao().observeCounterpartyUsage(),
        ::Rules,
    )

    private data class Library(
        val earned: Earned,
        val categories: List<CategoryEntity>,
        val people: List<PersonEntity>,
        val rules: Rules,
    )

    // Combined in two steps because the typed builder stops at five flows.
    private val library = combine(
        earned,
        db.categoryDao().observeAll(),
        db.personDao().observeActive(),
        rules,
        ::Library,
    )

    val state: StateFlow<CategoryIntelligenceState?> = combine(
        spending,
        earning,
        library,
        operation,
    ) { spending, earning, library, operation ->
        val earned = library.earned
        val categories = library.categories
        val people = library.people
        val countByIban = library.rules.usage.associate { it.iban to it.transactionCount }
        CategoryIntelligenceState(
            coverage = spending.coverage,
            unresolved = spending.merchants,
            counterparties = spending.counterparties.filter { it.transactionCount >= REPEATED },
            counterpartiesOnce = spending.counterparties.filter { it.transactionCount < REPEATED },
            incomeCoverage = earning.coverage,
            incomeSenders = earning.senders.filter { it.transactionCount >= REPEATED },
            incomeSendersOnce = earning.senders.filter { it.transactionCount < REPEATED },
            rules = library.rules.rules.map { rule ->
                CounterpartyRuleView(
                    id = rule.id,
                    iban = rule.iban,
                    displayName = rule.displayName,
                    categoryId = rule.categoryId,
                    categoryName = categories.firstOrNull { it.id == rule.categoryId }?.name,
                    personName = people.firstOrNull { it.id == rule.personId }?.name,
                    transactionCount = countByIban[rule.iban] ?: 0,
                    isDismissed = rule.dismissedAt != null,
                )
            },
            people = people,
            categories = categories.filter { it.kind == CategoryKind.EXPENSE && !it.isSystem },
            incomeCategories = categories.filter { it.kind == CategoryKind.INCOME && !it.isSystem },
            proposals = CategoryProposals.from(
                merchants = earned.merchants,
                usageByMerchantId = earned.usage.associate { it.merchantId to it.transactionCount },
                existing = categories,
            ),
            packs = CategoryPacks.all.filter { pack ->
                // A pack whose categories all exist has nothing left to offer.
                CategoryPacks.definitions(pack).any { definition ->
                    categories.none { it.icon == definition.icon && it.kind == definition.kind }
                }
            },
            isChecking = operation.isChecking,
            lastCheckMatches = operation.lastCheckMatches,
            operationFailed = operation.failed,
        )
    }
        // Reading proposals walks every merchant in the ledger, and these flows re-emit on every
        // write the maintenance pass makes. Computed where the collector runs, that is the main
        // thread rebuilding the screen's state thousands of times while the pass is still going.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

    /**
     * Creates the categories the user accepted, then lets the local rules fill them.
     *
     * The backfill is the point: a proposal was made because recognized merchants were waiting for
     * this category, so accepting it should file that history immediately rather than leave the
     * user to press a second button for the result they just asked for.
     */
    fun createCategories(definitions: List<CategoryCatalog.Definition>) {
        if (definitions.isEmpty()) return
        val isRussian = java.util.Locale.getDefault().language == "ru"
        viewModelScope.launch {
            try {
                db.withTransaction {
                    val present = db.categoryDao().all().toMutableList()
                    var order = (present.maxOfOrNull { it.sortOrder } ?: 0) + 1
                    definitions.forEach { definition ->
                        val alreadyThere = present.any {
                            it.icon == definition.icon && it.kind == definition.kind
                        }
                        if (alreadyThere) return@forEach
                        // A parent named by the pack may have been created a moment ago in this same
                        // transaction, so the list grows as we go rather than being read once.
                        val parent = definition.parentIcon?.let { icon ->
                            present.firstOrNull { it.icon == icon && it.kind == definition.kind }
                        }
                        val category = CategoryEntity(
                            name = definition.name(isRussian),
                            kind = definition.kind,
                            icon = definition.icon,
                            color = definition.color.toInt(),
                            sortOrder = order++,
                            // A parent that is itself a child would make two levels, which the tree
                            // does not have. Landing at the top level is the honest fallback.
                            parentId = parent?.takeIf { it.parentId == null }?.id,
                        )
                        present += category.copy(id = db.categoryDao().insert(category))
                    }
                }
                CategoryMaintenance.run(db)
                operation.value = operation.value.copy(failed = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                operation.value = operation.value.copy(failed = true)
            }
        }
    }

    fun addPack(pack: CategoryPacks.Pack) = createCategories(CategoryPacks.definitions(pack))

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

    /**
     * Records that this recipient should not be asked about again.
     *
     * Most transfers to a person are unrelated to each other — a shared taxi, a returned loan, a
     * gift — and a category covering all of them would be invented rather than observed. Saying so
     * has to be storable, otherwise the only way to clear the row is to answer it wrongly.
     */
    fun dismissCounterparty(iban: String, displayName: String) {
        mutate {
            db.counterpartyRuleDao().upsert(
                CounterpartyRuleEntity(
                    id = db.counterpartyRuleDao().byIban(iban)?.id ?: 0,
                    iban = iban,
                    displayName = displayName,
                    categoryId = null,
                    personId = null,
                    dismissedAt = System.currentTimeMillis(),
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    /**
     * Corrects a rule, including the history it already filed.
     *
     * Only the rows still carrying the category this rule chose are moved: anything the user
     * categorized by hand since is their answer, not this rule's, and stays where they put it.
     */
    fun updateRule(rule: CounterpartyRuleView, categoryId: Long) {
        if (rule.categoryId == categoryId) return
        mutate {
            val stored = db.counterpartyRuleDao().byIban(rule.iban) ?: return@mutate
            db.counterpartyRuleDao().upsert(
                stored.copy(categoryId = categoryId, dismissedAt = null),
            )
            if (rule.categoryId != null) {
                db.transactionDao().recategorizeForCounterparty(rule.iban, rule.categoryId, categoryId)
            }
            db.transactionDao().categorizeUnassignedForCounterparty(rule.iban, categoryId)
        }
    }

    /**
     * Forgets a decision completely, leaving the recipient as unanswered as before it was made.
     *
     * The rows the rule categorized are cleared with it. A deletion that left them labelled would
     * look like nothing happened, and the label would have no remaining explanation.
     */
    fun deleteRule(rule: CounterpartyRuleView) {
        mutate {
            rule.categoryId?.let { db.transactionDao().clearCategoryForCounterparty(rule.iban, it) }
            db.counterpartyRuleDao().delete(rule.id)
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

        /**
         * How many transfers a recipient needs before a standing rule about them is worth offering.
         *
         * One transfer is an event, not a pattern: naming it teaches WHFIN a rule that may never
         * apply again, and asking about every one of them buries the recipients that do repeat.
         * Single transfers stay reachable, they are simply not the question the screen leads with.
         */
        const val REPEATED = 2
    }
}
