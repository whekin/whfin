package dev.whekin.whfin.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.StatementImportEntity
import dev.whekin.whfin.data.db.ReconciliationIssueWithTransaction
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.data.db.StatementSourceEntity
import dev.whekin.whfin.data.db.PaymentInstrumentEntity
import dev.whekin.whfin.data.importer.StatementImporter
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountStatementHistory(
    val account: AccountEntity,
    val imports: List<StatementImportEntity>,
    val reviewItems: List<ReconciliationIssueWithTransaction>,
)

data class CardStatementHistory(
    val source: StatementSourceEntity,
    val instrument: PaymentInstrumentEntity,
    val imports: List<StatementImportEntity>,
)

sealed interface StatementImportUiState {
    data object Idle : StatementImportUiState
    data class Running(
        val phase: StatementImporter.Phase,
        val fileName: String?,
        val fileNumber: Int,
        val totalFiles: Int,
    ) : StatementImportUiState
    data class FileResult(
        val fileName: String?,
        val result: StatementImporter.Result? = null,
        val error: String? = null,
    )
    data class Success(val files: List<FileResult>) : StatementImportUiState
    data class Error(val message: String) : StatementImportUiState
}

class BankStatementsViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as WhfinApp).db

    val histories: StateFlow<List<AccountStatementHistory>> = combine(
        db.accountDao().observeActive(),
        db.statementImportDao().observeAll(),
        db.reconciliationIssueDao().observeOpenWithTransactions(),
    ) { accounts, imports, issues ->
        accounts.filter { it.type == dev.whekin.whfin.data.db.AccountType.BANK }.map { account ->
            AccountStatementHistory(
                account,
                imports.filter { it.accountId == account.id },
                issues.filter { it.issue.accountId == account.id },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val cardHistories: StateFlow<List<CardStatementHistory>> = combine(
        db.statementSourceDao().observeAll(),
        db.statementImportDao().observeAll(),
        db.paymentInstrumentDao().observeActive(),
    ) { sources, imports, instruments ->
        val byId = instruments.associateBy { it.id }
        sources.mapNotNull { source ->
            val instrument = source.instrumentId?.let(byId::get) ?: return@mapNotNull null
            CardStatementHistory(source, instrument, imports.filter { it.sourceId == source.id })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _importState = MutableStateFlow<StatementImportUiState>(StatementImportUiState.Idle)
    val importState: StateFlow<StatementImportUiState> = _importState

    fun importStatement(fileName: String?, open: () -> InputStream?) {
        importStatements(listOf(fileName to open))
    }

    fun importStatements(files: List<Pair<String?, () -> InputStream?>>) {
        if (files.isEmpty()) return
        if (_importState.value is StatementImportUiState.Running) return
        viewModelScope.launch(Dispatchers.IO) {
            val results = files.mapIndexed { index, (fileName, open) ->
                _importState.value = StatementImportUiState.Running(
                    StatementImporter.Phase.READING, fileName, index + 1, files.size,
                )
                try {
                    val result = open()?.use { input ->
                        StatementImporter(db).import(input, fileName) { phase ->
                            _importState.value = StatementImportUiState.Running(
                                phase, fileName, index + 1, files.size,
                            )
                        }
                    } ?: error("Cannot open file")
                    StatementImportUiState.FileResult(fileName, result = result)
                } catch (e: Exception) {
                    StatementImportUiState.FileResult(fileName, error = e.message ?: "Unknown error")
                }
            }
            _importState.value = StatementImportUiState.Success(results)
        }
    }

    fun dismissResult() {
        if (_importState.value !is StatementImportUiState.Running) {
            _importState.value = StatementImportUiState.Idle
        }
    }

    fun keepIssue(item: ReconciliationIssueWithTransaction) {
        viewModelScope.launch(Dispatchers.IO) {
            db.withTransaction {
                db.reconciliationIssueDao().keep(item.issue.id)
                db.transactionDao().update(item.transaction.copy(status = TxStatus.MANUAL))
            }
        }
    }

    fun deleteDraft(item: ReconciliationIssueWithTransaction) {
        viewModelScope.launch(Dispatchers.IO) { db.transactionDao().delete(item.transaction.id) }
    }

    fun removeNoEffectImport(item: StatementImportEntity) {
        if (!item.canRemoveFromHistory) return
        viewModelScope.launch(Dispatchers.IO) { db.statementImportDao().deleteIfNoEffect(item.id) }
    }
}

val StatementImportEntity.canRemoveFromHistory: Boolean
    get() = inserted == 0 && reconciled == 0 && reviewCount == 0
