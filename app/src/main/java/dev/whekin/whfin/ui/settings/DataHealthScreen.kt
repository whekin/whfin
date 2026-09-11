package dev.whekin.whfin.ui.settings

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.whekin.whfin.R
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.core.ui.WhfinActionStyle
import dev.whekin.whfin.core.ui.WhfinButton
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinLedgerRow
import dev.whekin.whfin.core.ui.WhfinNotice
import dev.whekin.whfin.core.ui.WhfinNoticeKind
import dev.whekin.whfin.core.ui.WhfinSectionLabel
import dev.whekin.whfin.data.integrity.DataIntegrityChecker
import dev.whekin.whfin.data.integrity.IntegrityIssue
import dev.whekin.whfin.data.importer.StatementImporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import dev.whekin.whfin.data.LedgerCalendar
import dev.whekin.whfin.ui.formatMinor
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import dev.whekin.whfin.data.mutation.TransactionMutationModule
import androidx.compose.ui.platform.testTag

/**
 * What the ledger says about itself.
 *
 * The integrity checks used to speak only to Logcat, which meant a broken invariant was invisible
 * exactly to the person whose money it described. Nothing here writes: it reports, and points at the
 * screens that can actually repair something.
 */
class DataHealthViewModel(app: Application) : AndroidViewModel(app) {
    sealed interface State {
        data object Checking : State
        data class Checked(
            val issues: List<IntegrityIssue>,
            /** What each flagged row actually is, so a finding is findable without hunting an id. */
            val flagged: Map<Long, String> = emptyMap(),
        ) : State
    }

    /** What the ledger currently holds that a person may want to act on. */
    data class Status(
        val pending: Int = 0,
        val unrouted: Int = 0,
        val corrections: Int = 0,
        val archivedAccounts: Int = 0,
        val lastImportAt: Long? = null,
    )

    data class RepairState(
        val repairing: Boolean = false,
        val repaired: Int? = null,
        val remaining: Int = 0,
    )

    private val whfinApp = app as WhfinApp
    private val db = whfinApp.db
    private val checker = DataIntegrityChecker(db)
    private val balanceReview = dev.whekin.whfin.data.sms.CredoBalanceReview(db)
    internal val balancePreview = MutableStateFlow<dev.whekin.whfin.data.sms.CredoBalanceReview.Preview?>(null)
    val balanceReviewError = MutableStateFlow(false)
    val balanceReviewBusy = MutableStateFlow(false)
    fun previewCredoBalances() { viewModelScope.launch {
        balanceReviewBusy.value=true; balanceReviewError.value=false
        try { balancePreview.value=balanceReview.preview() }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { balanceReviewError.value=true }
        finally { balanceReviewBusy.value=false }
    } }
    fun confirmCredoBalances() {
        val preview=balancePreview.value ?: return
        if(balanceReviewBusy.value) return
        balanceReviewBusy.value=true
        viewModelScope.launch {
            try { balanceReview.confirm(preview); balancePreview.value=null; check() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { balanceReviewError.value=true }
            finally { balanceReviewBusy.value=false }
        }
    }
    private val duplicateReview = dev.whekin.whfin.data.importer.CredoDuplicateReview(db)
    internal val duplicatePreview = MutableStateFlow<dev.whekin.whfin.data.importer.CredoDuplicateReview.Preview?>(null)
    internal val duplicateUndo = MutableStateFlow<dev.whekin.whfin.data.importer.CredoDuplicateReview.Applied?>(null)
    val duplicateBusy = MutableStateFlow(false)
    val duplicateError = MutableStateFlow(false)
    private fun duplicateAction(action: suspend () -> Unit) {
        if (duplicateBusy.value) return
        duplicateBusy.value = true; duplicateError.value = false
        viewModelScope.launch {
            try { action() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { duplicateError.value = true }
            finally { duplicateBusy.value = false }
        }
    }
    fun previewDuplicates() = duplicateAction { duplicatePreview.value = duplicateReview.preview() }
    fun confirmDuplicate(id: Long) = duplicateAction {
        duplicateUndo.value = duplicateReview.confirm(requireNotNull(duplicatePreview.value), id)
        duplicatePreview.value = null
        check(); whfinApp.refreshIntegrity()
    }
    fun undoDuplicate() = duplicateAction {
        duplicateReview.undo(requireNotNull(duplicateUndo.value))
        duplicateUndo.value = null
        check(); whfinApp.refreshIntegrity()
    }
    private val mutations = TransactionMutationModule(db)
    private val _state = MutableStateFlow<State>(State.Checking)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _repairState = MutableStateFlow(RepairState())
    val repairState: StateFlow<RepairState> = _repairState.asStateFlow()

    private val _mergeState = MutableStateFlow(RepairState())
    val mergeState: StateFlow<RepairState> = _mergeState.asStateFlow()

    /**
     * The human name of a flagged row: the date, who it was with, and how much.
     *
     * A finding used to be a database id. Nobody can find `#4293` in a ledger, so the only way to
     * act on a finding was to scroll looking for something that looked doubled.
     */
    private suspend fun describe(issue: IntegrityIssue): Pair<Long, String>? {
        if (issue.entity != "transactions") return null
        val id = issue.entityId ?: return null
        val row = db.transactionDao().byId(id) ?: return null
        val name = row.rawCounterparty ?: row.note
        val account = db.accountDao().byId(row.accountId)?.name
        return id to listOfNotNull(
            LedgerCalendar.dayOf(row.occurredAt).toString(),
            name,
            account,
            formatMinor(row.amountMinor, row.currency),
        ).joinToString(" · ")
    }

    fun check() {
        _state.value = State.Checking
        viewModelScope.launch {
            val issues = checker.run().issues
            _state.value = State.Checked(issues, issues.mapNotNull { describe(it) }.toMap())
            _status.value = Status(
                pending = db.transactionDao().pendingCount(),
                unrouted = db.smsDiagnosticDao().observeUnrouted().first().size,
                corrections = db.transactionDao().observeVoidedImported().first().size,
                archivedAccounts = db.accountDao().observeArchived().first().size,
                lastImportAt = db.statementImportDao().observeAll().first().firstOrNull()?.importedAt,
            )
        }
    }

    /**
     * Folds each doubled operation into the statement line that describes it.
     *
     * Only an unambiguous pair is folded: one message beside exactly one statement line of the same
     * account, amount and day. Two payments of the same size on one day are indistinguishable, and
     * picking one would retire the wrong copy — those stay listed for a person to look at.
     */
    fun mergeDuplicates() {
        if (_mergeState.value.repairing) return
        viewModelScope.launch {
            _mergeState.value = RepairState(repairing = true)
            val doubled = checker.run().issues
                .filter { it.code == "duplicate_statement_row" }
                .mapNotNull { it.entityId }
                .distinct()
            var merged = 0
            doubled.forEach { smsId ->
                val sms = db.transactionDao().byId(smsId) ?: return@forEach
                val day = LedgerCalendar.dayOf(sms.occurredAt)
                val survivor = db.transactionDao().statementCandidates(
                    sms.accountId,
                    LedgerCalendar.startOfDay(day),
                    LedgerCalendar.startOfDay(day.plusDays(1)) - 1,
                ).filter { it.amountMinor == sms.amountMinor }.singleOrNull() ?: return@forEach
                if (mutations.mergeDuplicate(smsId, survivor.id).changed > 0) merged++
            }
            val report = checker.run()
            _state.value = State.Checked(report.issues, report.issues.mapNotNull { describe(it) }.toMap())
            _mergeState.value = RepairState(
                repaired = merged,
                remaining = report.issues.count { it.code == "duplicate_statement_row" },
            )
            whfinApp.refreshIntegrity()
        }
    }

    fun repairTransfers() {
        if (_repairState.value.repairing) return
        viewModelScope.launch {
            val before = checker.run().issues.count { it.code.contains("transfer_group") }
            _repairState.value = RepairState(repairing = true)
            StatementImporter(db).repairTransferGroups()
            val report = checker.run()
            val remaining = report.issues.count { it.code.contains("transfer_group") }
            _state.value = State.Checked(report.issues, report.issues.mapNotNull { describe(it) }.toMap())
            _repairState.value = RepairState(
                repaired = (before - remaining).coerceAtLeast(0),
                remaining = remaining,
            )
            whfinApp.refreshIntegrity()
        }
    }
}

/** Groups the checker's codes into families a person can act on, instead of leaking raw rule names. */
internal fun integrityFamilyLabel(code: String): Int = when {
    code.startsWith("allocation") || code == "orphan_allocation" -> R.string.data_health_family_allocations
    // Checked before "correction": a merge is not one, and calling it that would send the user
    // looking for an audit record that was never supposed to exist.
    code.contains("merged") -> R.string.data_health_family_merges
    code == "duplicate_statement_row" -> R.string.data_health_family_duplicates
    code.contains("correction") -> R.string.data_health_family_corrections
    code.startsWith("category") || code == "orphan_category_parent" ->
        R.string.data_health_family_categories
    code.contains("transfer_group") -> R.string.data_health_family_transfers
    code.contains("debt") -> R.string.data_health_family_debts
    else -> R.string.data_health_family_links
}

private data class IntegrityFamily(val label: Int, val issues: List<IntegrityIssue>)

private fun groupedIntegrityIssues(issues: List<IntegrityIssue>): List<IntegrityFamily> = issues
    .groupBy { integrityFamilyLabel(it.code) }
    .map { (label, familyIssues) -> IntegrityFamily(label, familyIssues) }

@Composable
fun DataHealthRoute(
    onOpenCorrections: () -> Unit = {},
    onOpenBackup: () -> Unit = {},
    onOpenTransaction: (Long) -> Unit = {},
    viewModel: DataHealthViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val status by viewModel.status.collectAsState()
    val repairState by viewModel.repairState.collectAsState()
    val mergeState by viewModel.mergeState.collectAsState()
    val balancePreview by viewModel.balancePreview.collectAsState()
    val balanceBusy by viewModel.balanceReviewBusy.collectAsState()
    val balanceError by viewModel.balanceReviewError.collectAsState()
    balancePreview?.let { preview -> CredoBalanceReviewSheet(preview,balanceBusy,balanceError,
        { viewModel.balancePreview.value=null },viewModel::confirmCredoBalances) }
    val duplicatePreview by viewModel.duplicatePreview.collectAsStateWithLifecycle()
    val duplicateBusy by viewModel.duplicateBusy.collectAsStateWithLifecycle()
    val duplicateError by viewModel.duplicateError.collectAsStateWithLifecycle()
    val duplicateUndo by viewModel.duplicateUndo.collectAsStateWithLifecycle()
    duplicatePreview?.let { CredoDuplicateReviewSheet(it, duplicateBusy, duplicateError,
        { viewModel.duplicatePreview.value = null }, viewModel::confirmDuplicate) }
    LaunchedEffect(Unit) { viewModel.check() }
    DataHealthScreen(
        state = state,
        status = status,
        repairState = repairState,
        mergeState = mergeState,
        onCheck = viewModel::check,
        onRepairTransfers = viewModel::repairTransfers,
        onMergeDuplicates = viewModel::mergeDuplicates,
        onOpenCorrections = onOpenCorrections,
        onOpenBackup = onOpenBackup,
        onOpenTransaction = onOpenTransaction,
        onReviewCredoBalances = viewModel::previewCredoBalances,
        duplicateReview = {
            WhfinButton(stringResource(R.string.credo_duplicate_review), viewModel::previewDuplicates,
                style = WhfinActionStyle.Secondary, enabled = !duplicateBusy)
            if (duplicateUndo != null) WhfinButton(stringResource(R.string.credo_duplicate_undo), viewModel::undoDuplicate,
                style = WhfinActionStyle.Secondary, enabled = !duplicateBusy)
            if (duplicateError) Text(stringResource(R.string.credo_balance_changed), color = MaterialTheme.colorScheme.error)
        },
        balanceReviewBusy = balanceBusy,
        balanceReviewError = balanceError,
    )
}

@Composable
fun DataHealthScreen(
    state: DataHealthViewModel.State,
    status: DataHealthViewModel.Status = DataHealthViewModel.Status(),
    repairState: DataHealthViewModel.RepairState = DataHealthViewModel.RepairState(),
    mergeState: DataHealthViewModel.RepairState = DataHealthViewModel.RepairState(),
    onCheck: () -> Unit = {},
    onRepairTransfers: () -> Unit = {},
    onMergeDuplicates: () -> Unit = {},
    onOpenCorrections: () -> Unit = {},
    onOpenBackup: () -> Unit = {},
    onOpenTransaction: (Long) -> Unit = {},
    onReviewCredoBalances: (() -> Unit)? = null,
    balanceReviewBusy: Boolean = false,
    balanceReviewError: Boolean = false,
    duplicateReview: @Composable () -> Unit = {},
) {
    var showTechnicalDetails by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            when (state) {
                DataHealthViewModel.State.Checking -> WhfinNotice(
                    title = stringResource(R.string.data_health_checking),
                    body = stringResource(R.string.data_health_checking_body),
                    kind = WhfinNoticeKind.Info,
                    modifier = Modifier.fillMaxWidth(),
                )
                is DataHealthViewModel.State.Checked -> if (state.issues.isEmpty()) {
                    WhfinNotice(
                        title = stringResource(R.string.data_health_ok_title),
                        body = stringResource(R.string.data_health_ok_body),
                        icon = Icons.Default.CheckCircle,
                        kind = WhfinNoticeKind.Info,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    WhfinNotice(
                        title = pluralStringResource(R.plurals.data_health_issues_title, state.issues.size, state.issues.size),
                        body = stringResource(R.string.data_health_issues_body),
                        icon = Icons.Default.ReportProblem,
                        kind = WhfinNoticeKind.Attention,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        if (onReviewCredoBalances != null) item {
            WhfinButton(stringResource(R.string.credo_balance_review),onReviewCredoBalances,
                style=WhfinActionStyle.Secondary,enabled=!balanceReviewBusy)
            if(balanceReviewError) Text(stringResource(R.string.credo_balance_changed),color=MaterialTheme.colorScheme.error)
        }

        item { duplicateReview() }

        val issues = (state as? DataHealthViewModel.State.Checked)?.issues.orEmpty()
        val flagged = (state as? DataHealthViewModel.State.Checked)?.flagged.orEmpty()
        if (issues.isNotEmpty()) {
            val families = groupedIntegrityIssues(issues)
            val doubledIssues = issues.filter { it.code == "duplicate_statement_row" }
            if (doubledIssues.isNotEmpty()) item(key = "duplicate-merge") {
                WhfinNotice(
                    title = stringResource(R.string.data_health_duplicates_title),
                    body = pluralStringResource(
                        R.plurals.data_health_duplicates_body,
                        doubledIssues.size,
                        doubledIssues.size,
                    ),
                    icon = Icons.Default.Restore,
                    kind = WhfinNoticeKind.Info,
                    actionLabel = if (mergeState.repairing) {
                        stringResource(R.string.data_health_merging)
                    } else {
                        stringResource(R.string.data_health_merge_action)
                    },
                    onAction = onMergeDuplicates,
                    modifier = Modifier.fillMaxWidth().testTag("data-health-merge"),
                )
            }
            mergeState.repaired?.let { folded ->
                item(key = "merge-result") {
                    WhfinNotice(
                        title = if (mergeState.remaining == 0) {
                            stringResource(R.string.data_health_merge_done)
                        } else {
                            stringResource(R.string.data_health_merge_partial)
                        },
                        body = stringResource(R.string.data_health_merge_result, folded, mergeState.remaining),
                        icon = Icons.Default.CheckCircle,
                        kind = if (mergeState.remaining == 0) WhfinNoticeKind.Info else WhfinNoticeKind.Attention,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            val transferIssues = issues.filter { it.code.contains("transfer_group") }
            if (transferIssues.isNotEmpty()) item(key = "transfer-repair") {
                WhfinNotice(
                    title = stringResource(R.string.data_health_transfers_title),
                    body = pluralStringResource(
                        R.plurals.data_health_transfers_body,
                        transferIssues.size,
                        transferIssues.size,
                    ),
                    icon = Icons.Default.Restore,
                    kind = WhfinNoticeKind.Info,
                    actionLabel = if (repairState.repairing) {
                        stringResource(R.string.data_health_repairing)
                    } else {
                        stringResource(R.string.data_health_repair_action)
                    },
                    onAction = onRepairTransfers,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            repairState.repaired?.let { repaired ->
                item(key = "repair-result") {
                    WhfinNotice(
                        title = if (repairState.remaining == 0) {
                            stringResource(R.string.data_health_repair_done)
                        } else {
                            stringResource(R.string.data_health_repair_partial)
                        },
                        body = stringResource(
                            R.string.data_health_repair_result,
                            repaired,
                            repairState.remaining,
                        ),
                        icon = Icons.Default.CheckCircle,
                        kind = if (repairState.remaining == 0) WhfinNoticeKind.Info else WhfinNoticeKind.Attention,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            val otherFamilies = families.filterNot { family ->
                family.issues.any { it.code.contains("transfer_group") }
            }
            if (otherFamilies.isNotEmpty()) {
                item { WhfinSectionLabel(stringResource(R.string.data_health_section)) }
                item {
                    WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                        otherFamilies.forEachIndexed { index, family ->
                            // The named rows a finding points at. A finding used to be a database
                            // id, and nobody can find "#4293" in a ledger — the only way to act on
                            // one was to scroll looking for something that looked doubled.
                            val named = family.issues.mapNotNull { issue ->
                                issue.entityId?.let { id -> flagged[id]?.let { id to it } }
                            }
                            WhfinLedgerRow(
                                title = stringResource(family.label),
                                supportingText = pluralStringResource(
                                    R.plurals.data_health_family_count,
                                    family.issues.size,
                                    family.issues.size,
                                ),
                                supportingMaxLines = 2,
                                divider = named.isNotEmpty() || index < otherFamilies.lastIndex,
                            )
                            named.forEachIndexed { row, (id, label) ->
                                WhfinLedgerRow(
                                    title = label,
                                    titleMaxLines = 2,
                                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    onClick = { onOpenTransaction(id) },
                                    divider = row < named.lastIndex || index < otherFamilies.lastIndex,
                                )
                            }
                        }
                    }
                }
            }

            item {
                WhfinButton(
                    label = stringResource(
                        if (showTechnicalDetails) R.string.data_health_hide_details
                        else R.string.data_health_show_details,
                    ),
                    onClick = { showTechnicalDetails = !showTechnicalDetails },
                    style = WhfinActionStyle.Quiet,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (showTechnicalDetails) item {
                WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        issues.forEach { issue ->
                            Text(
                                listOfNotNull(issue.entity, issue.entityId?.let { "#$it" }, issue.code)
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        item {
            WhfinButton(
                label = stringResource(R.string.data_health_check_action),
                onClick = onCheck,
                style = WhfinActionStyle.Secondary,
                enabled = state !is DataHealthViewModel.State.Checking && !repairState.repairing,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Home stays quiet, so the standing state of the ledger is gathered here rather than
        // spread over the screen a person opens to see their money.
        item { WhfinSectionLabel(stringResource(R.string.data_health_status_section)) }
        item {
            WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                WhfinLedgerRow(
                    title = stringResource(R.string.data_health_status_pending),
                    supportingText = stringResource(
                        R.string.data_health_status_pending_body,
                        status.pending,
                        status.unrouted,
                    ),
                    supportingMaxLines = 3,
                    icon = Icons.Default.PendingActions,
                    divider = true,
                )
                WhfinLedgerRow(
                    title = stringResource(R.string.data_health_status_sync),
                    supportingText = status.lastImportAt?.let { millis ->
                        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                            .withLocale(Locale.getDefault())
                            // When the check last ran: the reader's clock, not a ledger day.
                            .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
                    } ?: stringResource(R.string.data_health_status_sync_never),
                    supportingMaxLines = 3,
                    icon = Icons.Default.CloudSync,
                    divider = true,
                )
                WhfinLedgerRow(
                    title = stringResource(R.string.data_health_status_kept),
                    supportingText = stringResource(
                        R.string.data_health_status_kept_body,
                        status.corrections,
                        status.archivedAccounts,
                    ),
                    supportingMaxLines = 3,
                    icon = Icons.Default.Inventory2,
                )
            }
        }

        item { WhfinSectionLabel(stringResource(R.string.data_health_shortcuts_section)) }
        item {
            WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                WhfinLedgerRow(
                    title = stringResource(R.string.corrections_title),
                    supportingText = stringResource(R.string.corrections_settings_summary),
                    supportingMaxLines = 3,
                    icon = Icons.Default.Restore,
                    trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                    onClick = onOpenCorrections,
                    divider = true,
                )
                WhfinLedgerRow(
                    title = stringResource(R.string.backup_title),
                    supportingText = stringResource(R.string.data_health_backup_summary),
                    supportingMaxLines = 3,
                    icon = Icons.Default.SaveAlt,
                    trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                    onClick = onOpenBackup,
                )
            }
        }
    }
}

@Composable
internal fun CredoBalanceReviewSheet(
    preview: dev.whekin.whfin.data.sms.CredoBalanceReview.Preview,
    balanceBusy: Boolean = false, balanceError: Boolean = false,
    onDismiss: () -> Unit, onConfirm: () -> Unit,
) {
        dev.whekin.whfin.ui.components.FormSheet(stringResource(R.string.credo_balance_review),
            { if (!balanceBusy) onDismiss() },stringResource(R.string.credo_balance_apply),
            !balanceBusy && !balanceError && preview.changes.isNotEmpty(),onConfirm) {
            Text(stringResource(R.string.credo_balance_review_body))
            if (preview.changes.isEmpty()) Text(stringResource(R.string.credo_balance_no_changes))
            val byId=preview.rows.associateBy { it.id }
            preview.changes.forEach { change ->
                val currency=byId.getValue(change.id).currency
                val before=change.before?.let { formatMinor(it,currency) } ?: stringResource(R.string.credo_balance_unassigned)
                val after=change.after?.let { formatMinor(it,currency) } ?: stringResource(R.string.credo_balance_unassigned)
                WhfinLedgerRow(title="${LedgerCalendar.dayOf(change.at)} · ${preview.labels[change.accountId]}",supportingText="$before → $after")
            }
            if(balanceError) Text(stringResource(R.string.credo_balance_changed),color=MaterialTheme.colorScheme.error)
        }
}
