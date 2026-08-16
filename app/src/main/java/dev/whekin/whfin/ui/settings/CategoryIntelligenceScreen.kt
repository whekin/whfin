package dev.whekin.whfin.ui.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.WhfinActionMenu
import dev.whekin.whfin.core.ui.WhfinActionStyle
import dev.whekin.whfin.core.ui.WhfinButton
import dev.whekin.whfin.core.ui.WhfinChoiceRail
import dev.whekin.whfin.core.ui.WhfinConfirmDialog
import dev.whekin.whfin.core.ui.WhfinField
import dev.whekin.whfin.core.ui.WhfinFieldLabel
import dev.whekin.whfin.core.ui.WhfinFilterPill
import dev.whekin.whfin.core.ui.WhfinIconButton
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinLedgerRow
import dev.whekin.whfin.core.ui.WhfinNotice
import dev.whekin.whfin.core.ui.WhfinNoticeKind
import dev.whekin.whfin.core.ui.WhfinPaneState
import dev.whekin.whfin.core.ui.WhfinSectionLabel
import dev.whekin.whfin.core.ui.WhfinStatePane
import dev.whekin.whfin.data.categorization.CategoryCatalog
import dev.whekin.whfin.data.categorization.CategoryPacks
import dev.whekin.whfin.data.db.CategoryCoverage
import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.CategoryKind
import dev.whekin.whfin.data.db.PersonEntity
import dev.whekin.whfin.data.db.UncategorizedCounterparty
import dev.whekin.whfin.data.db.UncategorizedMerchant
import dev.whekin.whfin.ui.components.CategoryGrid
import dev.whekin.whfin.ui.formatMinor
import dev.whekin.whfin.ui.theme.WhfinTheme

/**
 * One list of things still to answer, opened from the index rather than stacked below it.
 *
 * A synced ledger produces hundreds of merchants and dozens of recipients. Rendered as sections of
 * a single screen, whichever section came last could not be reached at all — the queue with the most
 * rows buried the ones after it. The index names each queue and its size; only one is ever open.
 */
enum class CategoryQueue { Merchants, Transfers, Income, Rules }

@Composable
fun categoryQueueTitle(queue: CategoryQueue): String = stringResource(
    when (queue) {
        CategoryQueue.Merchants -> R.string.category_intelligence_review_title
        CategoryQueue.Transfers -> R.string.category_intelligence_transfers_title
        CategoryQueue.Income -> R.string.category_intelligence_income_title
        CategoryQueue.Rules -> R.string.category_rules_title
    },
)

@Composable
fun CategoryIntelligenceRoute(
    queue: CategoryQueue? = null,
    onOpenQueue: (CategoryQueue) -> Unit = {},
    viewModel: CategoryIntelligenceViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    CategoryIntelligenceScreen(
        state = state,
        queue = queue,
        onOpenQueue = onOpenQueue,
        onCheckLocalRules = viewModel::checkLocalRules,
        onAssignCategory = viewModel::assignCategory,
        onAssignCounterparty = viewModel::assignCounterparty,
        onDismissCounterparty = viewModel::dismissCounterparty,
        onUpdateRule = viewModel::updateRule,
        onDeleteRule = viewModel::deleteRule,
        onCreateCategories = viewModel::createCategories,
        onAddPack = viewModel::addPack,
    )
}

@Composable
fun CategoryIntelligenceScreen(
    state: CategoryIntelligenceState?,
    queue: CategoryQueue? = null,
    onOpenQueue: (CategoryQueue) -> Unit = {},
    onCheckLocalRules: () -> Unit,
    onAssignCategory: (Long, Long) -> Unit,
    onAssignCounterparty: (String, String, Long, Long?, String?) -> Unit = { _, _, _, _, _ -> },
    onDismissCounterparty: (String, String) -> Unit = { _, _ -> },
    onUpdateRule: (CounterpartyRuleView, Long) -> Unit = { _, _ -> },
    onDeleteRule: (CounterpartyRuleView) -> Unit = {},
    onCreateCategories: (List<CategoryCatalog.Definition>) -> Unit = {},
    onAddPack: (CategoryPacks.Pack) -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<UncategorizedMerchant?>(null) }
    var selectedCounterparty by remember { mutableStateOf<UncategorizedCounterparty?>(null) }
    var selectedSender by remember { mutableStateOf<UncategorizedCounterparty?>(null) }
    var selectedRule by remember { mutableStateOf<CounterpartyRuleView?>(null) }

    if (state == null) {
        WhfinStatePane(
            state = WhfinPaneState.Loading,
            title = stringResource(R.string.category_intelligence_title),
            body = stringResource(R.string.category_intelligence_loading),
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (queue) {
            null -> indexSection(
                state = state,
                onCheckLocalRules = onCheckLocalRules,
                onCreateCategories = onCreateCategories,
                onAddPack = onAddPack,
                onOpenQueue = onOpenQueue,
            )
            CategoryQueue.Merchants -> merchantQueue(
                merchants = state.unresolved,
                query = query,
                onQueryChange = { query = it },
                onSelect = { selected = it },
            )
            CategoryQueue.Transfers -> counterpartyQueue(
                repeated = state.counterparties,
                once = state.counterpartiesOnce,
                icon = Icons.Default.SwapHoriz,
                bodyRes = R.string.category_intelligence_transfers_body,
                onSelect = { selectedCounterparty = it },
            )
            CategoryQueue.Income -> counterpartyQueue(
                repeated = state.incomeSenders,
                once = state.incomeSendersOnce,
                icon = Icons.Default.SouthWest,
                bodyRes = R.string.category_intelligence_income_body_short,
                onSelect = { selectedSender = it },
            )
            CategoryQueue.Rules -> ruleQueue(state.rules) { selectedRule = it }
        }
    }

    selectedSender?.let { sender ->
        val name = sender.displayName
            ?: stringResource(R.string.category_intelligence_transfer_unnamed)
        CounterpartyCategorySheet(
            counterparty = sender,
            name = name,
            // Naming a person is a question about who benefited from a payment; money arriving
            // asks nothing of the kind, so the sheet stays a category choice.
            people = emptyList(),
            categories = state.incomeCategories,
            onDismiss = { selectedSender = null },
            onSelect = { category, _, _ ->
                onAssignCounterparty(sender.iban, name, category.id, null, null)
                selectedSender = null
            },
            onNotWorthARule = {
                onDismissCounterparty(sender.iban, name)
                selectedSender = null
            },
            askWhoItIs = false,
        )
    }

    selectedCounterparty?.let { counterparty ->
        val name = counterparty.displayName
            ?: stringResource(R.string.category_intelligence_transfer_unnamed)
        CounterpartyCategorySheet(
            counterparty = counterparty,
            name = name,
            people = state.people,
            categories = state.categories,
            onDismiss = { selectedCounterparty = null },
            onSelect = { category, personId, personName ->
                onAssignCounterparty(counterparty.iban, name, category.id, personId, personName)
                selectedCounterparty = null
            },
            onNotWorthARule = {
                onDismissCounterparty(counterparty.iban, name)
                selectedCounterparty = null
            },
        )
    }

    selectedRule?.let { rule ->
        CounterpartyRuleSheet(
            rule = rule,
            categories = if (rule.categoryId != null &&
                state.incomeCategories.any { it.id == rule.categoryId }
            ) state.incomeCategories else state.categories,
            onDismiss = { selectedRule = null },
            onSelect = { category ->
                onUpdateRule(rule, category.id)
                selectedRule = null
            },
            onDelete = {
                onDeleteRule(rule)
                selectedRule = null
            },
        )
    }

    selected?.let { merchant ->
        MerchantCategorySheet(
            merchant = merchant,
            categories = state.categories,
            onDismiss = { selected = null },
            onSelect = { category ->
                onAssignCategory(merchant.merchantId, category.id)
                selected = null
            },
        )
    }
}

/**
 * The screen itself: what WHFIN did on its own, what it offers to add, and how much is left.
 *
 * Everything here is short by construction — a coverage figure, a handful of proposals, and one row
 * per queue. What the queues contain grows with the ledger; this does not.
 */
private fun LazyListScope.indexSection(
    state: CategoryIntelligenceState,
    onCheckLocalRules: () -> Unit,
    onCreateCategories: (List<CategoryCatalog.Definition>) -> Unit,
    onAddPack: (CategoryPacks.Pack) -> Unit,
    onOpenQueue: (CategoryQueue) -> Unit,
) {
    item { CoverageBlock(state.coverage) }
    item {
        val localBody = when {
            state.operationFailed -> stringResource(R.string.category_intelligence_error)
            state.isChecking -> stringResource(R.string.category_intelligence_checking)
            state.lastCheckMatches == null -> stringResource(R.string.category_intelligence_local_body)
            state.lastCheckMatches == 0 -> stringResource(R.string.category_intelligence_no_matches)
            else -> stringResource(R.string.category_intelligence_matches, state.lastCheckMatches)
        }
        WhfinNotice(
            title = stringResource(R.string.category_intelligence_local_title),
            body = localBody,
            icon = Icons.Default.AutoAwesome,
            kind = if (state.operationFailed) WhfinNoticeKind.Attention else WhfinNoticeKind.Info,
            actionLabel = stringResource(R.string.category_intelligence_check_action),
            onAction = onCheckLocalRules,
        )
    }
    if (state.proposals.isNotEmpty()) {
        item { WhfinSectionLabel(stringResource(R.string.category_proposals_title)) }
        item {
            Text(
                stringResource(R.string.category_proposals_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(state.proposals, key = { "new:" + it.definition.icon }) { proposal ->
            val isRussian = java.util.Locale.getDefault().language == "ru"
            WhfinLedgerRow(
                title = proposal.definition.name(isRussian),
                supportingText = pluralStringResource(
                    R.plurals.category_proposals_evidence,
                    proposal.transactionCount,
                    proposal.transactionCount,
                ),
                icon = Icons.Default.Add,
                onClick = { onCreateCategories(listOf(proposal.definition)) },
                divider = proposal != state.proposals.last(),
            )
        }
        item {
            WhfinButton(
                label = stringResource(R.string.category_proposals_accept_all),
                onClick = { onCreateCategories(state.proposals.map { it.definition }) },
                style = WhfinActionStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (state.packs.isNotEmpty()) {
        item { WhfinSectionLabel(stringResource(R.string.category_packs_title)) }
        item {
            Text(
                stringResource(R.string.category_packs_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(state.packs, key = { "pack:" + it.id }) { pack ->
            val isRussian = java.util.Locale.getDefault().language == "ru"
            WhfinLedgerRow(
                title = pack.name(isRussian),
                supportingText = CategoryPacks.definitions(pack)
                    .joinToString(" · ") { it.name(isRussian) },
                icon = Icons.Default.Add,
                onClick = { onAddPack(pack) },
                divider = pack != state.packs.last(),
            )
        }
    }

    val queues = buildList {
        if (state.unresolved.isNotEmpty()) {
            add(Triple(CategoryQueue.Merchants, state.unresolved.size, Icons.Default.Category))
        }
        val transfers = state.counterparties.size + state.counterpartiesOnce.size
        if (transfers > 0) {
            add(Triple(CategoryQueue.Transfers, transfers, Icons.Default.SwapHoriz))
        }
        val senders = state.incomeSenders.size + state.incomeSendersOnce.size
        if (senders > 0) {
            add(Triple(CategoryQueue.Income, senders, Icons.Default.SouthWest))
        }
    }
    if (queues.isEmpty() && state.rules.isEmpty()) {
        item {
            WhfinStatePane(
                state = WhfinPaneState.Empty,
                title = stringResource(R.string.category_intelligence_complete_title),
                body = stringResource(R.string.category_intelligence_complete_body),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (queues.isNotEmpty()) {
        item { WhfinSectionLabel(stringResource(R.string.category_intelligence_queues_title)) }
        items(queues, key = { "queue:" + it.first.name }) { (queue, count, icon) ->
            WhfinLedgerRow(
                title = categoryQueueTitle(queue),
                supportingText = pluralStringResource(
                    when (queue) {
                        CategoryQueue.Merchants -> R.plurals.category_queue_merchants
                        CategoryQueue.Transfers -> R.plurals.category_queue_recipients
                        else -> R.plurals.category_queue_senders
                    },
                    count,
                    count,
                ),
                icon = icon,
                trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                onClick = { onOpenQueue(queue) },
                divider = queue != queues.last().first,
            )
        }
    }
    if (state.rules.isNotEmpty()) {
        item { WhfinSectionLabel(stringResource(R.string.category_rules_section)) }
        item {
            WhfinLedgerRow(
                title = stringResource(R.string.category_rules_title),
                supportingText = pluralStringResource(
                    R.plurals.category_rules_count,
                    state.rules.size,
                    state.rules.size,
                ),
                icon = Icons.Default.AutoAwesome,
                trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                onClick = { onOpenQueue(CategoryQueue.Rules) },
            )
        }
    }
    if (state.coverage.withoutMerchant > 0) item {
        WhfinNotice(
            title = stringResource(R.string.category_intelligence_without_merchant_title),
            body = pluralStringResource(
                R.plurals.category_intelligence_without_merchant_body,
                state.coverage.withoutMerchant,
                state.coverage.withoutMerchant,
            ),
            kind = WhfinNoticeKind.Unavailable,
        )
    }
}

/**
 * The merchant queue, which on a synced ledger is hundreds of rows long.
 *
 * The rows stay as `items` rather than a Column inside one item: the whole reason this queue has a
 * screen to itself is that it is too long to compose at once.
 */
private fun LazyListScope.merchantQueue(
    merchants: List<UncategorizedMerchant>,
    query: String,
    onQueryChange: (String) -> Unit,
    onSelect: (UncategorizedMerchant) -> Unit,
) {
    item {
        Text(
            pluralStringResource(
                R.plurals.category_intelligence_review_body,
                merchants.size,
                merchants.size,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (merchants.size > 8) item {
        WhfinField(
            value = query,
            onValueChange = { onQueryChange(it.take(80)) },
            label = null,
            placeholder = stringResource(R.string.category_intelligence_search),
            leadingIcon = Icons.Default.Search,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    val needle = query.trim().lowercase()
    val visible = if (needle.isEmpty()) merchants
    else merchants.filter { it.displayName.lowercase().contains(needle) }
    if (visible.isEmpty()) item {
        Text(
            stringResource(R.string.category_intelligence_search_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else items(visible, key = { it.merchantId }) { merchant ->
        WhfinLedgerRow(
            title = merchant.displayName,
            supportingText = pluralStringResource(
                R.plurals.category_intelligence_transactions,
                merchant.transactionCount,
                merchant.transactionCount,
            ),
            icon = Icons.Default.Category,
            trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
            onClick = { onSelect(merchant) },
            divider = merchant != visible.last(),
        )
    }
}

/**
 * Recipients that repeat, then the ones seen once.
 *
 * The order is the argument: a rule is only worth writing where there is a pattern to describe, and
 * a single transfer is better answered on the transaction itself. Both stay reachable, but the list
 * no longer opens with dozens of one-off payments that will never recur.
 */
private fun LazyListScope.counterpartyQueue(
    repeated: List<UncategorizedCounterparty>,
    once: List<UncategorizedCounterparty>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    bodyRes: Int,
    onSelect: (UncategorizedCounterparty) -> Unit,
) {
    item {
        Text(
            stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    items(repeated, key = { "many:" + it.iban + it.currency }) { counterparty ->
        CounterpartyRow(counterparty, icon, repeated.last() != counterparty, onSelect)
    }
    if (once.isNotEmpty()) {
        item { WhfinSectionLabel(stringResource(R.string.category_intelligence_once_title)) }
        item {
            Text(
                stringResource(R.string.category_intelligence_once_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(once, key = { "one:" + it.iban + it.currency }) { counterparty ->
            CounterpartyRow(counterparty, icon, once.last() != counterparty, onSelect)
        }
    }
}

@Composable
private fun CounterpartyRow(
    counterparty: UncategorizedCounterparty,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    divider: Boolean,
    onSelect: (UncategorizedCounterparty) -> Unit,
) {
    WhfinLedgerRow(
        title = counterparty.displayName
            ?: stringResource(R.string.category_intelligence_transfer_unnamed),
        supportingText = pluralStringResource(
            R.plurals.category_intelligence_transactions,
            counterparty.transactionCount,
            counterparty.transactionCount,
        ),
        icon = icon,
        trailing = {
            Text(
                formatMinor(counterparty.totalMinor, counterparty.currency),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        onClick = { onSelect(counterparty) },
        divider = divider,
    )
}

private fun LazyListScope.ruleQueue(
    rules: List<CounterpartyRuleView>,
    onSelect: (CounterpartyRuleView) -> Unit,
) {
    item {
        Text(
            stringResource(R.string.category_rules_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    items(rules, key = { "rule:" + it.id }) { rule ->
        val dismissed = stringResource(R.string.category_rules_dismissed)
        val supporting = listOfNotNull(
            if (rule.isDismissed) dismissed else rule.categoryName,
            rule.personName,
        ).joinToString(" · ")
        WhfinLedgerRow(
            title = rule.displayName,
            supportingText = supporting.takeIf { it.isNotEmpty() },
            icon = Icons.Default.SwapHoriz,
            trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
            onClick = { onSelect(rule) },
            divider = rule != rules.last(),
        )
    }
}

@Composable
private fun CoverageBlock(coverage: CategoryCoverage) {
    val ratio = if (coverage.totalExpenses == 0) 0f
    else coverage.categorizedExpenses.toFloat() / coverage.totalExpenses
    val percent = (ratio * 100).toInt()
    WhfinLedgerGroup(Modifier.fillMaxWidth(), tonal = true) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (coverage.totalExpenses == 0) stringResource(R.string.category_intelligence_no_expenses)
                else stringResource(R.string.category_intelligence_percent, percent),
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                if (coverage.totalExpenses == 0) stringResource(R.string.category_intelligence_no_expenses_body)
                else stringResource(
                    R.string.category_intelligence_coverage,
                    coverage.categorizedExpenses,
                    coverage.totalExpenses,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MerchantCategorySheet(
    merchant: UncategorizedMerchant,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSelect: (CategoryEntity) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(merchant.displayName, style = MaterialTheme.typography.titleLarge)
            Text(
                pluralStringResource(
                    R.plurals.category_intelligence_apply_body,
                    merchant.transactionCount,
                    merchant.transactionCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CategoryGrid(
                categories = categories,
                selectedId = null,
                onSelect = onSelect,
                maxHeight = 420.dp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * One recipient account, answered once.
 *
 * Naming the person is offered before the category because it is the question the row actually
 * raises — the bank prints a different spelling of them every month — but it stays optional: a
 * transfer can be filed without ever deciding who the recipient is.
 *
 * Refusing is offered alongside, because for most recipients it is the true answer. Transfers to one
 * person are often unrelated to each other, and a category covering all of them would be a guess
 * dressed as a rule; the individual transactions can still be categorized in the feed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CounterpartyCategorySheet(
    counterparty: UncategorizedCounterparty,
    name: String,
    people: List<PersonEntity>,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSelect: (CategoryEntity, Long?, String?) -> Unit,
    onNotWorthARule: () -> Unit = {},
    askWhoItIs: Boolean = true,
) {
    var personId by remember { mutableStateOf<Long?>(null) }
    var createPerson by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(name, style = MaterialTheme.typography.titleLarge)
            Text(
                pluralStringResource(
                    R.plurals.category_intelligence_apply_body,
                    counterparty.transactionCount,
                    counterparty.transactionCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (askWhoItIs) {
                WhfinFieldLabel(stringResource(R.string.category_intelligence_person_label))
                val noneLabel = stringResource(R.string.category_intelligence_person_none)
                val createLabel = stringResource(R.string.category_intelligence_person_create, name)
                val alreadyKnown = people.any { it.name.equals(name, ignoreCase = true) }
                WhfinChoiceRail {
                    item {
                        WhfinFilterPill(
                            label = noneLabel,
                            selected = personId == null && !createPerson,
                            onClick = {
                                personId = null
                                createPerson = false
                            },
                        )
                    }
                    if (!alreadyKnown) item {
                        WhfinFilterPill(
                            label = createLabel,
                            selected = createPerson,
                            onClick = {
                                createPerson = true
                                personId = null
                            },
                        )
                    }
                    items(people, key = { it.id }) { person ->
                        WhfinFilterPill(
                            label = person.name,
                            selected = personId == person.id,
                            onClick = {
                                personId = person.id
                                createPerson = false
                            },
                        )
                    }
                }
            }
            WhfinFieldLabel(stringResource(R.string.category_intelligence_category_label))
            CategoryGrid(
                categories = categories,
                selectedId = null,
                onSelect = { category ->
                    onSelect(category, personId, name.takeIf { createPerson })
                },
                maxHeight = 360.dp,
                modifier = Modifier.fillMaxWidth(),
            )
            WhfinButton(
                label = stringResource(R.string.category_intelligence_not_a_rule),
                onClick = onNotWorthARule,
                style = WhfinActionStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.category_intelligence_not_a_rule_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A decision, reopened.
 *
 * Choosing another category moves the transfers this rule filed, so a mistake is corrected
 * everywhere at once rather than left behind in the history it already wrote. Deleting it returns
 * the recipient to being an open question, which is what it was before the rule existed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CounterpartyRuleSheet(
    rule: CounterpartyRuleView,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSelect: (CategoryEntity) -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var actionsExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    rule.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                // Deleting a rule is rare and irreversible for the history it filed, while changing
                // its category is the reason this sheet is opened. As a full-width destructive
                // button it was the largest thing here, which is the wrong way round.
                Box {
                    WhfinIconButton(
                        icon = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.category_rules_actions),
                        onClick = { actionsExpanded = true },
                        outlined = false,
                    )
                    WhfinActionMenu(
                        expanded = actionsExpanded,
                        onDismissRequest = { actionsExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.category_rules_delete),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                actionsExpanded = false
                                confirmDelete = true
                            },
                        )
                    }
                }
            }
            Text(
                if (rule.isDismissed) stringResource(R.string.category_rules_dismissed_body)
                else pluralStringResource(
                    R.plurals.category_rules_applies,
                    rule.transactionCount,
                    rule.transactionCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            WhfinFieldLabel(stringResource(R.string.category_rules_change_label))
            CategoryGrid(
                categories = categories,
                selectedId = rule.categoryId,
                onSelect = onSelect,
                maxHeight = 360.dp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (confirmDelete) {
        WhfinConfirmDialog(
            title = stringResource(R.string.category_rules_delete_title),
            body = if (rule.categoryId == null) {
                stringResource(R.string.category_rules_delete_body_plain)
            } else {
                pluralStringResource(
                    R.plurals.category_rules_delete_body,
                    rule.transactionCount,
                    rule.transactionCount,
                )
            },
            confirmLabel = stringResource(R.string.action_delete),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

private val previewState = CategoryIntelligenceState(
    coverage = CategoryCoverage(400, 250, 4),
    unresolved = listOf(
        UncategorizedMerchant(1, "EXAMPLE TRADE LTD", 18, 0),
        UncategorizedMerchant(2, "SAMPLE STATION", 9, 0),
        UncategorizedMerchant(3, "DEMO RIDERS", 5, 0),
    ),
    counterparties = listOf(
        UncategorizedCounterparty("GE00XX0000000000000001", "Example Person", 12, -84000, "GEL", 0),
        UncategorizedCounterparty("GE00XX0000000000000002", "Sample Recipient", 4, -21000, "GEL", 0),
    ),
    counterpartiesOnce = listOf(
        UncategorizedCounterparty("GE00XX0000000000000003", "One Off", 1, -3500, "GEL", 0),
    ),
    rules = listOf(
        CounterpartyRuleView(1, "GE00XX0000000000000004", "Example Landlord", 7, "Rent", null, 24, false),
    ),
    people = emptyList(),
    categories = listOf(
        CategoryEntity(1, "Transport", kind = CategoryKind.EXPENSE, icon = "DirectionsBus", color = 0xff5d7f91.toInt()),
        CategoryEntity(2, "Bike", kind = CategoryKind.EXPENSE, icon = "PedalBike", color = 0xff78906f.toInt()),
        CategoryEntity(3, "Subscriptions", kind = CategoryKind.EXPENSE, icon = "Subscriptions", color = 0xff9c6a89.toInt()),
    ),
)

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Preview(showBackground = true, widthDp = 393, heightDp = 852, fontScale = 1.5f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CategoryIntelligencePreview() {
    WhfinTheme {
        CategoryIntelligenceScreen(
            state = previewState,
            onCheckLocalRules = {},
            onAssignCategory = { _, _ -> },
        )
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun CategoryRulesPreview() {
    WhfinTheme {
        CategoryIntelligenceScreen(
            state = previewState,
            queue = CategoryQueue.Rules,
            onCheckLocalRules = {},
            onAssignCategory = { _, _ -> },
        )
    }
}
