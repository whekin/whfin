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

    private val db = (app as WhfinApp).db
    private val checker = DataIntegrityChecker(db)
    private val _state = MutableStateFlow<State>(State.Checking)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

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
}

/** Groups the checker's codes into families a person can act on, instead of leaking raw rule names. */
internal fun integrityFamilyLabel(code: String): Int = when {
    code.startsWith("allocation") || code == "orphan_allocation" -> R.string.data_health_family_allocations
    code.contains("correction") -> R.string.data_health_family_corrections
    code.contains("transfer_group") -> R.string.data_health_family_transfers
    code.contains("debt") -> R.string.data_health_family_debts
    else -> R.string.data_health_family_links
}

@Composable
fun DataHealthRoute(
    onOpenCorrections: () -> Unit = {},
    onOpenBackup: () -> Unit = {},
    viewModel: DataHealthViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val status by viewModel.status.collectAsState()
    LaunchedEffect(Unit) { viewModel.check() }
    DataHealthScreen(
        state = state,
        status = status,
        onCheck = viewModel::check,
        onOpenCorrections = onOpenCorrections,
        onOpenBackup = onOpenBackup,
    )
}

@Composable
fun DataHealthScreen(
    state: DataHealthViewModel.State,
    status: DataHealthViewModel.Status = DataHealthViewModel.Status(),
    onCheck: () -> Unit = {},
    onOpenCorrections: () -> Unit = {},
    onOpenBackup: () -> Unit = {},
) {
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
                        kind = WhfinNoticeKind.Error,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        item {
            WhfinButton(
                label = stringResource(R.string.data_health_check_action),
                onClick = onCheck,
                style = WhfinActionStyle.Secondary,
                enabled = state !is DataHealthViewModel.State.Checking,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        val issues = (state as? DataHealthViewModel.State.Checked)?.issues.orEmpty()
        if (issues.isNotEmpty()) {
            item { WhfinSectionLabel(stringResource(R.string.data_health_section)) }
            items(issues.size) { index ->
                val issue = issues[index]
                WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            stringResource(integrityFamilyLabel(issue.code)),
                            style = MaterialTheme.typography.titleMedium,
                        )
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
