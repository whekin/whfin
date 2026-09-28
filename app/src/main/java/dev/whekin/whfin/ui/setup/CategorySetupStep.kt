package dev.whekin.whfin.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.WhfinActionStyle
import dev.whekin.whfin.core.ui.WhfinBackButton
import dev.whekin.whfin.core.ui.WhfinButton
import dev.whekin.whfin.core.ui.WhfinLedgerRow
import dev.whekin.whfin.core.ui.WhfinNotice
import dev.whekin.whfin.core.ui.WhfinNoticeKind
import dev.whekin.whfin.core.ui.WhfinSectionLabel
import dev.whekin.whfin.data.categorization.CategoryCatalog
import dev.whekin.whfin.data.categorization.CategoryPacks
import dev.whekin.whfin.data.categorization.CategoryProposals
import dev.whekin.whfin.data.db.CategoryKind
import dev.whekin.whfin.ui.settings.CategoryIntelligenceViewModel

/**
 * The categories this ledger has earned, offered once the history is in.
 *
 * Setup used to end without ever mentioning categories, and the proposals — the one part of this
 * built from the user's own spending — were reachable only by finding a settings screen afterwards.
 * They belong here: the evidence for them exists exactly once the bank has just been read, and a
 * fixed preset chosen before any history arrived would describe somebody else's life.
 */
@Composable
internal fun CategorySetupStep(
    onContinue: () -> Unit,
    onBack: () -> Unit,
    bankHistoryPending: Boolean = false,
    viewModel: CategoryIntelligenceViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val isRussian = LocalConfiguration.current.locales[0].language == "ru"
    val app = LocalContext.current.applicationContext as dev.whekin.whfin.WhfinApp
    var firstSnapshotSeen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (!firstSnapshotSeen && state != null) {
            app.deferredCategoryReview.markSeen(state?.proposals.orEmpty())
            firstSnapshotSeen = true
        }
    }
    if (state == null) {
        PersonalSetupSecondaryPage(stringResource(R.string.category_setup_title), onBack) {
            dev.whekin.whfin.core.ui.WhfinLoadingIndicator()
        }
        return
    }
    CategorySetupStep(
        proposals = state?.proposals.orEmpty(),
        packs = state?.packs.orEmpty(),
        existingCategoryKeys = state?.categories.orEmpty().map { it.icon to it.kind }.toSet(),
        onAccept = { viewModel.createCategories(it, isRussian) },
        onAddPacks = { viewModel.addPacks(it, isRussian) },
        onContinue = onContinue,
        onBack = onBack,
        bankHistoryPending = bankHistoryPending,
        operationFailed = state?.operationFailed == true,
    )
}

@Composable
internal fun CategorySetupStep(
    proposals: List<CategoryProposals.Proposal>,
    packs: List<CategoryPacks.Pack> = emptyList(),
    existingCategoryKeys: Set<Pair<String, CategoryKind>> = emptySet(),
    onAccept: (List<CategoryCatalog.Definition>) -> Unit,
    onAddPacks: (List<CategoryPacks.Pack>) -> Unit = {},
    onContinue: () -> Unit,
    onBack: () -> Unit,
    bankHistoryPending: Boolean = false,
    operationFailed: Boolean = false,
) {
    BackHandler(onBack = onBack)
    val isRussian = LocalConfiguration.current.locales[0].language == "ru"
    val compact = LocalConfiguration.current.screenHeightDp < 700
    var selectedPackIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(packs) { selectedPackIds = selectedPackIds.filter { id -> packs.any { it.id == id } } }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = if (compact) 12.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 20.dp),
            ) {
                WhfinBackButton(stringResource(R.string.action_back), onBack)
                Text(
                    stringResource(R.string.category_setup_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    if (bankHistoryPending && proposals.isEmpty()) stringResource(R.string.category_setup_pending_empty)
                    else if (proposals.isEmpty() && packs.isEmpty()) stringResource(R.string.category_setup_none)
                    else if (proposals.isEmpty()) stringResource(R.string.category_setup_packs_only)
                    else stringResource(R.string.category_setup_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (bankHistoryPending && proposals.isNotEmpty()) Text(
                    stringResource(R.string.category_setup_pending_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (operationFailed) WhfinNotice(
                    title = stringResource(R.string.category_setup_add_failed_title),
                    body = stringResource(R.string.category_setup_add_failed),
                    kind = WhfinNoticeKind.Attention,
                )
                if (proposals.isNotEmpty()) {
                    WhfinSectionLabel(stringResource(R.string.category_proposals_title))
                    Column(Modifier.fillMaxWidth()) {
                        proposals.forEach { proposal ->
                            WhfinLedgerRow(
                                title = proposal.definition.name(isRussian),
                                supportingText = pluralStringResource(
                                    R.plurals.category_proposals_evidence,
                                    proposal.transactionCount,
                                    proposal.transactionCount,
                                ),
                                icon = Icons.Default.Add,
                                onClick = { onAccept(listOf(proposal.definition)) },
                                divider = proposal != proposals.last(),
                            )
                        }
                    }
                }
                // The other half of a clean install. History can only propose what it has already
                // seen, so an interest that never reached a card statement — or one whose merchants
                // no rule recognizes — would otherwise have to be typed in by hand afterwards.
                if (packs.isNotEmpty()) {
                    WhfinSectionLabel(stringResource(R.string.category_packs_title))
                    Text(
                        stringResource(R.string.category_packs_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(Modifier.fillMaxWidth()) {
                        packs.forEach { pack ->
                            val chosen = pack.id in selectedPackIds
                            WhfinLedgerRow(
                                title = pack.name(isRussian),
                                supportingText = CategoryPacks.definitions(pack)
                                    .filterNot { it.icon to it.kind in existingCategoryKeys }
                                    .joinToString(" · ") { it.name(isRussian) },
                                supportingMaxLines = 4,
                                icon = if (chosen) Icons.Default.Check else Icons.Default.Add,
                                modifier = Modifier.semantics { selected = chosen },
                                onClick = {
                                    selectedPackIds = if (chosen) selectedPackIds - pack.id else selectedPackIds + pack.id
                                },
                                divider = pack != packs.last(),
                            )
                        }
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (proposals.isNotEmpty()) {
                    WhfinButton(
                        label = stringResource(R.string.category_proposals_accept_all),
                        onClick = { onAccept(proposals.map { it.definition }) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        leadingIcon = Icons.Default.Check,
                    )
                }
                if (selectedPackIds.isNotEmpty()) WhfinButton(
                    label = stringResource(R.string.category_packs_add_selected, selectedPackIds.size),
                    onClick = {
                        onAddPacks(packs.filter { it.id in selectedPackIds })
                    },
                    modifier = Modifier.fillMaxWidth(),
                    style = if (proposals.isEmpty()) WhfinActionStyle.Primary else WhfinActionStyle.Secondary,
                )
                WhfinButton(
                    label = stringResource(R.string.category_setup_continue),
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (proposals.isEmpty()) Modifier.padding(top = 8.dp) else Modifier),
                    style = if (proposals.isEmpty() && selectedPackIds.isEmpty()) WhfinActionStyle.Primary
                        else WhfinActionStyle.Quiet,
                )
            }
        }
    }
}
