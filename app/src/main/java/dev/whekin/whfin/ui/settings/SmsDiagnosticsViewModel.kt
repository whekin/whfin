package dev.whekin.whfin.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.PaymentInstrumentType
import dev.whekin.whfin.data.db.PaymentInstrumentEntity
import dev.whekin.whfin.data.db.SmsDiagnosticEntity
import dev.whekin.whfin.data.db.SmsDiagnosticOutcome
import dev.whekin.whfin.data.sms.HistoricalSms
import dev.whekin.whfin.data.sms.SmsHistoryReader
import dev.whekin.whfin.data.sms.SmsTransactionImporter
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SmsAccountOption(
    val account: AccountEntity,
    val groupName: String?,
) {
    val label: String
        get() = listOfNotNull(groupName, account.name.takeUnless { it == groupName })
            .joinToString(" · ")
}

data class SmsDiagnosticsData(
    val diagnostics: List<SmsDiagnosticEntity> = emptyList(),
    val accounts: List<SmsAccountOption> = emptyList(),
    val cardFamilies: List<SmsCardFamily> = emptyList(),
    val cardMappings: List<SmsCardMapping> = emptyList(),
)

data class SmsCardMapping(
    val instrument: PaymentInstrumentEntity,
    val family: SmsCardFamily,
)

data class SmsCardFamily(
    val primaryAccountId: Long,
    val groupName: String,
    val iban: String?,
    val accounts: List<AccountEntity>,
) {
    val currencies: List<String> = accounts.map(AccountEntity::currency).distinct().sorted()
}

sealed interface SmsMessageState {
    data object Hidden : SmsMessageState
    data object Loading : SmsMessageState
    data class Content(val body: String, val receivedAt: Long) : SmsMessageState
    data object Unavailable : SmsMessageState
    data object Error : SmsMessageState
}

sealed interface SmsDiagnosticsLoadState {
    data object Loading : SmsDiagnosticsLoadState
    data class Content(val data: SmsDiagnosticsData) : SmsDiagnosticsLoadState
    data object Error : SmsDiagnosticsLoadState
}

data class SmsScanSummary(
    val total: Int,
    val importable: Int,
    val duplicates: Int,
    val needsAttention: Int,
    val ignored: Int,
)

sealed interface SmsScanState {
    data object Idle : SmsScanState
    data object Scanning : SmsScanState
    data class Preview(val summary: SmsScanSummary) : SmsScanState
    data object Importing : SmsScanState
    data class Complete(val imported: Int, val needsAttention: Int) : SmsScanState
    data object Error : SmsScanState
}

class SmsDiagnosticsViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as WhfinApp).db
    private val importer = SmsTransactionImporter(db)
    private val historyReader = SmsHistoryReader(app.contentResolver)
    private var pendingHistory: List<HistoricalSms> = emptyList()

    val loadState: StateFlow<SmsDiagnosticsLoadState> = combine(
        db.smsDiagnosticDao().observeRecent(),
        db.accountDao().observeActive(),
        db.financialGroupDao().observeActive(),
        db.paymentInstrumentDao().observeActive(),
        db.paymentInstrumentDao().observeLinks(),
    ) { diagnostics, accounts, groups, instruments, links ->
        val groupNames = groups.associate { it.id to it.name }
        val options = accounts
            .filter { it.type == AccountType.BANK || it.type == AccountType.SAVINGS }
            .map { SmsAccountOption(it, it.groupId?.let(groupNames::get)) }
        val instrumentsById = instruments.associateBy { it.id }
        val families = options.filter { it.account.groupId != null }.groupBy { option ->
            val account = option.account
            requireNotNull(account.groupId) to (account.iban ?: "account:${account.id}")
        }.values.map { familyOptions ->
            val first = familyOptions.first()
            SmsCardFamily(
                primaryAccountId = first.account.id,
                groupName = first.groupName ?: first.account.name,
                iban = first.account.iban,
                accounts = familyOptions.map(SmsAccountOption::account),
            )
        }
        val familyByAccountId = families.flatMap { family ->
            family.accounts.map { it.id to family }
        }.toMap()
        SmsDiagnosticsLoadState.Content(SmsDiagnosticsData(
            diagnostics = diagnostics,
            accounts = options,
            cardFamilies = families,
            cardMappings = links.groupBy { it.instrumentId }.mapNotNull { (instrumentId, instrumentLinks) ->
                val instrument = instrumentsById[instrumentId] ?: return@mapNotNull null
                val family = instrumentLinks.firstNotNullOfOrNull { familyByAccountId[it.accountId] }
                    ?: return@mapNotNull null
                SmsCardMapping(instrument, family)
            },
        )) as SmsDiagnosticsLoadState
    }.catch { emit(SmsDiagnosticsLoadState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SmsDiagnosticsLoadState.Loading)

    private val _scanState = MutableStateFlow<SmsScanState>(SmsScanState.Idle)
    val scanState: StateFlow<SmsScanState> = _scanState

    private val _messageState = MutableStateFlow<SmsMessageState>(SmsMessageState.Hidden)
    val messageState: StateFlow<SmsMessageState> = _messageState

    fun scanHistory() {
        if (_scanState.value == SmsScanState.Scanning || _scanState.value == SmsScanState.Importing) return
        viewModelScope.launch(Dispatchers.IO) {
            _scanState.value = SmsScanState.Scanning
            runCatching {
                val since = Instant.now().minus(Duration.ofDays(90)).toEpochMilli()
                val messages = historyReader.credoCandidates(since)
                val outcomes = messages.map { importer.preview(it.body, it.receivedAt).outcome }
                pendingHistory = messages
                SmsScanSummary(
                    total = messages.size,
                    importable = outcomes.count { it == SmsDiagnosticOutcome.IMPORTED },
                    duplicates = outcomes.count { it == SmsDiagnosticOutcome.DUPLICATE },
                    needsAttention = outcomes.count {
                        it == SmsDiagnosticOutcome.NEEDS_CARD_MAPPING ||
                            it == SmsDiagnosticOutcome.CHOOSE_ACCOUNT ||
                            it == SmsDiagnosticOutcome.UNRECOGNIZED ||
                            it == SmsDiagnosticOutcome.ERROR
                    },
                    ignored = outcomes.count { it == SmsDiagnosticOutcome.IGNORED },
                )
            }.fold(
                onSuccess = { _scanState.value = SmsScanState.Preview(it) },
                onFailure = {
                    pendingHistory = emptyList()
                    _scanState.value = SmsScanState.Error
                },
            )
        }
    }

    fun confirmHistoryImport() {
        val messages = pendingHistory
        if (messages.isEmpty() && (_scanState.value as? SmsScanState.Preview)?.summary?.total != 0) return
        viewModelScope.launch(Dispatchers.IO) {
            _scanState.value = SmsScanState.Importing
            runCatching {
                val results = messages.map { importer.import(it.body, it.receivedAt) }
                SmsScanState.Complete(
                    imported = results.count { it.outcome == SmsDiagnosticOutcome.IMPORTED },
                    needsAttention = results.count {
                        it.outcome == SmsDiagnosticOutcome.NEEDS_CARD_MAPPING ||
                            it.outcome == SmsDiagnosticOutcome.CHOOSE_ACCOUNT ||
                            it.outcome == SmsDiagnosticOutcome.UNRECOGNIZED ||
                            it.outcome == SmsDiagnosticOutcome.ERROR
                    },
                )
            }.fold(
                onSuccess = { _scanState.value = it },
                onFailure = { _scanState.value = SmsScanState.Error },
            )
            pendingHistory = emptyList()
        }
    }

    fun cancelHistoryImport() {
        pendingHistory = emptyList()
        _scanState.value = SmsScanState.Idle
    }

    fun resolve(
        diagnosticId: Long,
        accountId: Long,
        cardType: PaymentInstrumentType,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            importer.resolveDiagnostic(diagnosticId, accountId, cardType)
        }
    }

    fun addCardMapping(
        familyAccountId: Long,
        last4: String,
        cardType: PaymentInstrumentType,
    ) {
        val normalized = last4.filter(Char::isDigit)
        if (normalized.length != 4) return
        viewModelScope.launch(Dispatchers.IO) {
            val account = db.accountDao().byId(familyAccountId) ?: return@launch
            if (account.type != AccountType.BANK && account.type != AccountType.SAVINGS) return@launch
            val family = requireNotNull(account.groupId).let { db.accountDao().byGroup(it) }.filter { candidate ->
                if (account.iban != null) candidate.iban == account.iban else candidate.id == account.id
            }
            db.paymentInstrumentDao().linkForAccounts(family.ifEmpty { listOf(account) }, normalized, cardType)
        }
    }

    fun loadMessage(diagnostic: SmsDiagnosticEntity) {
        _messageState.value = SmsMessageState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            _messageState.value = runCatching {
                historyReader.findByExternalKey(diagnostic.externalKey, diagnostic.receivedAt)
                    ?.let { SmsMessageState.Content(it.body, it.receivedAt) }
                    ?: SmsMessageState.Unavailable
            }.getOrElse { SmsMessageState.Error }
        }
    }

    fun dismissMessage() {
        _messageState.value = SmsMessageState.Hidden
    }
}
