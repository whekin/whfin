package dev.whekin.whfin.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import dev.whekin.whfin.R
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.StatementImportEntity
import dev.whekin.whfin.data.db.ReconciliationIssueWithTransaction
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.data.db.StatementSourceEntity
import dev.whekin.whfin.data.db.PaymentInstrumentEntity
import dev.whekin.whfin.data.importer.StatementBatchPlan
import dev.whekin.whfin.data.importer.StatementImporter
import dev.whekin.whfin.data.importer.planStatementBatch
import dev.whekin.whfin.data.rates.NbgHistoricalRateProvider
import dev.whekin.whfin.data.rates.TransactionValuationRepository
import dev.whekin.whfin.data.statement.UnsupportedStatementException
import dev.whekin.whfin.data.mutation.TransactionMutationModule
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

    /** Reading the picked files to find out what they would change. Nothing is written yet. */
    data class Checking(
        val fileName: String?,
        val fileNumber: Int,
        val totalFiles: Int,
    ) : StatementImportUiState

    /** The whole batch waits on one question: these ledgers do not exist yet. */
    data class Confirming(val newLedgers: List<String>) : StatementImportUiState

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
    /** [unchanged] files were skipped because importing them would have added nothing. */
    data class Success(val files: List<FileResult>, val unchanged: Int = 0) : StatementImportUiState
    data class Error(val message: String) : StatementImportUiState
}

/** The import owns the screen: picking more files now would race the run in progress. */
val StatementImportUiState.isBusy: Boolean
    get() = this is StatementImportUiState.Checking ||
        this is StatementImportUiState.Confirming ||
        this is StatementImportUiState.Running

/** Reading and writing may not be interrupted; a pending question may. */
val StatementImportUiState.blocksDismissal: Boolean
    get() = this is StatementImportUiState.Checking || this is StatementImportUiState.Running

class BankStatementsViewModel internal constructor(
    app: Application,
    private val importDispatcher: kotlinx.coroutines.CoroutineDispatcher,
) : AndroidViewModel(app) {
    constructor(app: Application) : this(app, Dispatchers.IO)

    private val db = (app as WhfinApp).db
    private val transactionMutations = TransactionMutationModule(db)

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

    private data class CheckedFile(
        val fileName: String?,
        val open: () -> InputStream?,
        /** Null when the file could not be read: let the import itself report why. */
        val preview: StatementImporter.Preview?,
    )

    private var awaitingConfirmation: StatementBatchPlan<CheckedFile>? = null

    fun importStatement(fileName: String?, open: () -> InputStream?) {
        importStatements(listOf(fileName to open))
    }

    /**
     * Reads every picked file before writing any of them.
     *
     * Batch import is routine, so it stays one gesture: the run is interrupted only when a file would
     * create a ledger that does not exist yet, and then once for the whole batch rather than per file.
     * Files that would change nothing are dropped here instead of being imported into a "0 added"
     * history row.
     */
    fun importStatements(files: List<Pair<String?, () -> InputStream?>>) {
        if (files.isEmpty()) return
        if (_importState.value.isBusy) return
        viewModelScope.launch(importDispatcher) {
            val checked = files.mapIndexed { index, (fileName, open) ->
                _importState.value = StatementImportUiState.Checking(fileName, index + 1, files.size)
                val preview = runCatching {
                    open()?.use { StatementImporter(db).preview(it, fileName) }
                }.getOrNull()
                CheckedFile(fileName, open, preview)
            }
            val plan = planStatementBatch(checked) { it.preview }
            if (plan.confirmLedgers.isEmpty()) {
                runImport(plan)
            } else {
                awaitingConfirmation = plan
                _importState.value = StatementImportUiState.Confirming(plan.confirmLedgers)
            }
        }
    }

    fun confirmImport() {
        val plan = awaitingConfirmation ?: return
        awaitingConfirmation = null
        viewModelScope.launch(importDispatcher) { runImport(plan) }
    }

    fun cancelImport() {
        if (awaitingConfirmation == null) return
        awaitingConfirmation = null
        _importState.value = StatementImportUiState.Idle
    }

    private suspend fun runImport(plan: StatementBatchPlan<CheckedFile>) {
        val files = plan.toImport
        val results = files.mapIndexed { index, file ->
            val fileName = file.fileName
            _importState.value = StatementImportUiState.Running(
                StatementImporter.Phase.READING, fileName, index + 1, files.size,
            )
            try {
                val result = file.open()?.use { input ->
                    StatementImporter(db).import(input, fileName) { phase ->
                        _importState.value = StatementImportUiState.Running(
                            phase, fileName, index + 1, files.size,
                        )
                    }
                } ?: error("Cannot open file")
                StatementImportUiState.FileResult(fileName, result = result)
            } catch (e: UnsupportedStatementException) {
                StatementImportUiState.FileResult(
                    fileName,
                    error = getApplication<Application>().getString(R.string.statements_unsupported),
                )
            } catch (e: Exception) {
                StatementImportUiState.FileResult(fileName, error = e.message ?: "Unknown error")
            }
        }
        _importState.value = StatementImportUiState.Success(results, plan.unchanged)
        // A statement can add a year of foreign rows at once; value them while the user reads
        // the result, so statistics are already complete when they open it.
        runCatching {
            TransactionValuationRepository(db, NbgHistoricalRateProvider()).backfill()
        }
    }

    fun dismissResult() {
        cancelImport()
        if (!_importState.value.blocksDismissal) {
            _importState.value = StatementImportUiState.Idle
        }
    }

    fun keepIssue(item: ReconciliationIssueWithTransaction) {
        viewModelScope.launch(Dispatchers.IO) {
            db.withTransaction {
                db.reconciliationIssueDao().keep(item.issue.id)
                transactionMutations.keepReviewDraft(item.transaction.id)
            }
        }
    }

    fun deleteDraft(item: ReconciliationIssueWithTransaction) {
        viewModelScope.launch(Dispatchers.IO) {
            transactionMutations.deleteDraft(item.transaction.id)
        }
    }

    fun removeNoEffectImport(item: StatementImportEntity) {
        if (!item.canRemoveFromHistory) return
        viewModelScope.launch(Dispatchers.IO) { db.statementImportDao().deleteIfNoEffect(item.id) }
    }
}

val StatementImportEntity.canRemoveFromHistory: Boolean
    get() = inserted == 0 && reconciled == 0 && reviewCount == 0
