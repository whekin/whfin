package dev.whekin.whfin.ui.settings

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
        data class Checked(val issues: List<IntegrityIssue>) : State
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
    private val _state = MutableStateFlow<State>(State.Checking)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _repairState = MutableStateFlow(RepairState())
    val repairState: StateFlow<RepairState> = _repairState.asStateFlow()

    fun check() {
        _state.value = State.Checking
        viewModelScope.launch {
            _state.value = State.Checked(checker.run().issues)
            _status.value = Status(
                pending = db.transactionDao().pendingCount(),
                unrouted = db.smsDiagnosticDao().observeUnrouted().first().size,
                corrections = db.transactionDao().observeVoidedImported().first().size,
                archivedAccounts = db.accountDao().observeArchived().first().size,
                lastImportAt = db.statementImportDao().observeAll().first().firstOrNull()?.importedAt,
            )
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
            _state.value = State.Checked(report.issues)
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
    code.contains("correction") -> R.string.data_health_family_corrections
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
    viewModel: DataHealthViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val status by viewModel.status.collectAsState()
    val repairState by viewModel.repairState.collectAsState()
    LaunchedEffect(Unit) { viewModel.check() }
    DataHealthScreen(
        state = state,
        status = status,
        repairState = repairState,
        onCheck = viewModel::check,
        onRepairTransfers = viewModel::repairTransfers,
        onOpenCorrections = onOpenCorrections,
        onOpenBackup = onOpenBackup,
    )
}

@Composable
fun DataHealthScreen(
    state: DataHealthViewModel.State,
    status: DataHealthViewModel.Status = DataHealthViewModel.Status(),
    repairState: DataHealthViewModel.RepairState = DataHealthViewModel.RepairState(),
    onCheck: () -> Unit = {},
    onRepairTransfers: () -> Unit = {},
    onOpenCorrections: () -> Unit = {},
    onOpenBackup: () -> Unit = {},
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

        val issues = (state as? DataHealthViewModel.State.Checked)?.issues.orEmpty()
        if (issues.isNotEmpty()) {
            val families = groupedIntegrityIssues(issues)
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
                            WhfinLedgerRow(
                                title = stringResource(family.label),
                                supportingText = pluralStringResource(
                                    R.plurals.data_health_family_count,
                                    family.issues.size,
                                    family.issues.size,
                                ),
                                supportingMaxLines = 2,
                                divider = index < otherFamilies.lastIndex,
                            )
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
