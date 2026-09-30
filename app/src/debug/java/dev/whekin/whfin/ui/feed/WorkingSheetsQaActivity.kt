package dev.whekin.whfin.ui.feed

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.Density
import dev.whekin.whfin.data.db.*
import dev.whekin.whfin.data.mutation.ExpenseBeneficiary
import dev.whekin.whfin.ui.FormSaveState
import dev.whekin.whfin.ui.settings.*
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.time.LocalDate
import java.util.Locale

/** Disposable render host; all rows are synthetic and no repository or bank is opened. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
class WorkingSheetsQaActivity : ComponentActivity() {
    var saved: ExpenseBeneficiary? = null
    var deleted = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = intent.getBooleanExtra("dark", false)
        val locale = Locale.forLanguageTag(intent.getStringExtra("language") ?: "en")
        Locale.setDefault(locale)
        val config = Configuration(resources.configuration).apply { setLocale(locale) }
        val localized = createConfigurationContext(config)
        val bars = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        val people = (1..intent.getIntExtra("people", 3)).map { PersonEntity(id = it.toLong(),
            name = if (it == 1) "Mira" else "Person $it", color = 0xFF78906F.toInt()) }
        val item = FeedItem(TransactionEntity(id = 1, accountId = 1, amountMinor = -2113, currency = "GEL", occurredAt = 1000,
            rawCounterparty = "Example Cafe", status = TxStatus.MANUAL, source = TxSource.MANUAL), null, null, null, null,
            day = LocalDate.of(2026, 9, 1))
        setContent {
            CompositionLocalProvider(LocalContext provides localized, LocalResources provides localized.resources,
                LocalConfiguration provides config, LocalDensity provides Density(LocalDensity.current.density, intent.getFloatExtra("fontScale", 1f))) {
                WhfinTheme(darkTheme = dark) {
                    Surface(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                        var closed by remember { mutableStateOf(false) }
                        var state by remember { mutableStateOf(FormSaveState()) }
                        if (closed) Text("Closed")
                        else when (intent.getStringExtra("mode")) {
                            "debt" -> DebtPersonSheet(item, people, { closed = true }, { saved = it; state = FormSaveState(completed = 1) }, state)
                            "review" -> {
                                var rows by remember { mutableStateOf((1..30).map { index -> ReconciliationIssueWithTransaction(
                                    ReconciliationIssueEntity(index.toLong(), 1, index.toLong(), 1, createdAt = 0),
                                    item.tx.copy(id = index.toLong(), rawCounterparty = "Example receipt $index", source = TxSource.SMS, status = TxStatus.PENDING)) }) }
                                ReviewSheet(AccountStatementHistory(AccountEntity(id = 1, name = "Demo bank", type = AccountType.BANK, currency = "GEL"), emptyList(), rows),
                                    { closed = true }, { chosen -> rows = rows.filterNot { it.issue.id == chosen.issue.id } },
                                    { chosen -> deleted++; rows = rows.filterNot { it.issue.id == chosen.issue.id } })
                            }
                            else -> SplitSheet(item, people, { closed = true }, { value, _ ->
                                saved = value
                                state = if (intent.getBooleanExtra("fail", false)) FormSaveState(failed = true) else FormSaveState(completed = 1)
                            }, state)
                        }
                    }
                }
            }
        }
    }
}
