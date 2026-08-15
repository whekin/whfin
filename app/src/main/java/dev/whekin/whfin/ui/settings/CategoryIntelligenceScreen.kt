package dev.whekin.whfin.ui.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.WhfinChoiceRail
import dev.whekin.whfin.core.ui.WhfinField
import dev.whekin.whfin.core.ui.WhfinFieldLabel
import dev.whekin.whfin.core.ui.WhfinFilterPill
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinLedgerRow
import dev.whekin.whfin.core.ui.WhfinNotice
import dev.whekin.whfin.core.ui.WhfinNoticeKind
import dev.whekin.whfin.core.ui.WhfinPaneState
import dev.whekin.whfin.core.ui.WhfinSectionLabel
import dev.whekin.whfin.core.ui.WhfinStatePane
import dev.whekin.whfin.core.ui.WhfinActionStyle
import dev.whekin.whfin.core.ui.WhfinButton
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

@Composable
fun CategoryIntelligenceRoute(viewModel: CategoryIntelligenceViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    CategoryIntelligenceScreen(
        state = state,
        onCheckLocalRules = viewModel::checkLocalRules,
        onAssignCategory = viewModel::assignCategory,
        onAssignCounterparty = viewModel::assignCounterparty,
        onCreateCategories = viewModel::createCategories,
        onAddPack = viewModel::addPack,
    )
}

@Composable
fun CategoryIntelligenceScreen(
    state: CategoryIntelligenceState?,
    onCheckLocalRules: () -> Unit,
    onAssignCategory: (Long, Long) -> Unit,
    onAssignCounterparty: (String, String, Long, Long?, String?) -> Unit = { _, _, _, _, _ -> },
    onCreateCategories: (List<CategoryCatalog.Definition>) -> Unit = {},
    onAddPack: (CategoryPacks.Pack) -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<UncategorizedMerchant?>(null) }
    var selectedCounterparty by remember { mutableStateOf<UncategorizedCounterparty?>(null) }
    var selectedSender by remember { mutableStateOf<UncategorizedCounterparty?>(null) }

    if (state == null) {
        WhfinStatePane(
            state = WhfinPaneState.Loading,
            title = stringResource(R.string.category_intelligence_title),
            body = stringResource(R.string.category_intelligence_loading),
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    val visible = remember(state.unresolved, query) {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) state.unresolved else state.unresolved.filter {
            it.displayName.lowercase().contains(needle)
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 12.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
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
        if (state.incomeSenders.isNotEmpty()) {
            item { WhfinSectionLabel(stringResource(R.string.category_intelligence_income_title)) }
            item {
                Text(
                    stringResource(
                        R.string.category_intelligence_income_body,
                        state.incomeCoverage.totalExpenses - state.incomeCoverage.categorizedExpenses,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(state.incomeSenders, key = { "in:" + it.iban + it.currency }) { sender ->
                WhfinLedgerRow(
                    title = sender.displayName
                        ?: stringResource(R.string.category_intelligence_transfer_unnamed),
                    supportingText = pluralStringResource(
                        R.plurals.category_intelligence_transactions,
                        sender.transactionCount,
                        sender.transactionCount,
                    ),
                    icon = Icons.Default.SouthWest,
                    trailing = {
                        Text(
                            formatMinor(sender.totalMinor, sender.currency),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = { selectedSender = sender },
                    divider = sender != state.incomeSenders.last(),
                )
            }
        }
        if (state.counterparties.isNotEmpty()) {
            item { WhfinSectionLabel(stringResource(R.string.category_intelligence_transfers_title)) }
            item {
                Text(
                    stringResource(R.string.category_intelligence_transfers_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(state.counterparties, key = { it.iban + it.currency }) { counterparty ->
                WhfinLedgerRow(
                    title = counterparty.displayName
                        ?: stringResource(R.string.category_intelligence_transfer_unnamed),
                    supportingText = pluralStringResource(
                        R.plurals.category_intelligence_transactions,
                        counterparty.transactionCount,
                        counterparty.transactionCount,
                    ),
                    icon = Icons.Default.SwapHoriz,
                    trailing = {
                        Text(
                            formatMinor(counterparty.totalMinor, counterparty.currency),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = { selectedCounterparty = counterparty },
                    divider = counterparty != state.counterparties.last(),
                )
            }
        }
        if (state.unresolved.isEmpty()) {
            if (state.counterparties.isEmpty()) item {
                WhfinStatePane(
                    state = WhfinPaneState.Empty,
                    title = stringResource(R.string.category_intelligence_complete_title),
                    body = stringResource(R.string.category_intelligence_complete_body),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            item {
                WhfinSectionLabel(stringResource(R.string.category_intelligence_review_title))
            }
            item {
                Text(
                    pluralStringResource(
                        R.plurals.category_intelligence_review_body,
                        state.unresolved.size,
                        state.unresolved.size,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.unresolved.size > 8) item {
                WhfinField(
                    value = query,
                    onValueChange = { query = it.take(80) },
                    label = null,
                    placeholder = stringResource(R.string.category_intelligence_search),
                    leadingIcon = Icons.Default.Search,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
                    onClick = { selected = merchant },
                    divider = merchant != visible.last(),
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
        }
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
    WhfinTheme { CategoryIntelligenceScreen(previewState, {}, { _, _ -> }) }
}
