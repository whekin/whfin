package dev.whekin.whfin.ui.settings

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.Density
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.PaymentInstrumentEntity
import dev.whekin.whfin.data.db.PaymentInstrumentType
import dev.whekin.whfin.data.db.SmsDiagnosticEntity
import dev.whekin.whfin.data.db.SmsDiagnosticKind
import dev.whekin.whfin.data.db.SmsDiagnosticOutcome
import dev.whekin.whfin.data.db.SmsDiagnosticReason
import dev.whekin.whfin.ui.SecondaryPage
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.util.Locale

/** Synthetic Bank SMS surface with a long journal. No database, SMS provider or bank connection. */
class BankSmsQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
        val language = intent.getStringExtra("language") ?: "en"
        val dark = intent.getBooleanExtra("dark", false)
        val font = intent.getFloatExtra("fontScale", 1f)
        val config = Configuration(resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        val localized = createConfigurationContext(config)
        enableEdgeToEdge()
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        val account = AccountEntity(id = 1, name = "Everyday", type = AccountType.BANK,
            groupId = 1, currency = "GEL", iban = "GE00CD0000000000000001")
        val family = SmsCardFamily(1, "Credo", account.iban, listOf(account))
        val recent = (0 until 20).map { index -> SmsDiagnosticEntity(
            id = index + 10L, externalKey = "sms|qa-$index", kind = SmsDiagnosticKind.CARD_PAYMENT,
            outcome = SmsDiagnosticOutcome.IMPORTED, receivedAt = 1_788_000_000_000L - index * 60_000L,
            occurredAt = 1_788_000_000_000L - index * 60_000L,
            amountMinor = 100 + index * 10L, currency = "GEL", cardLast4 = "0001",
            counterparty = "Example shop $index", updatedAt = 1_788_000_000_000L,
        ) }
        val needsCard = SmsDiagnosticEntity(id = 2, externalKey = "sms|qa-needs-card",
            kind = SmsDiagnosticKind.CARD_PAYMENT, outcome = SmsDiagnosticOutcome.NEEDS_CARD_MAPPING,
            reason = SmsDiagnosticReason.NO_CARD_MAPPING, receivedAt = 1_788_000_100_000L,
            amountMinor = 450, currency = "GEL", cardLast4 = "2222", counterparty = "Example market",
            updatedAt = 1_788_000_100_000L)
        val waiting = SmsDiagnosticEntity(id = 3, externalKey = "sms|qa-waiting",
            kind = SmsDiagnosticKind.BILL_PAYMENT, outcome = SmsDiagnosticOutcome.CHOOSE_ACCOUNT,
            reason = SmsDiagnosticReason.STATEMENT_COVERS_PERIOD, receivedAt = 1_788_000_050_000L,
            amountMinor = 100, currency = "GEL", counterparty = "Example internet",
            updatedAt = 1_788_000_050_000L)
        val matchedGroup = SmsDiagnosticEntity(id = 4, externalKey = "sms|qa-matched-cohort",
            kind = SmsDiagnosticKind.OWN_TRANSFER, outcome = SmsDiagnosticOutcome.MATCHED_GROUP,
            receivedAt = 1_788_000_050_000L, occurredAt = 1_788_000_050_000L,
            amountMinor = 700, currency = "GEL", updatedAt = 1_788_000_050_000L)
        val cohortMode = intent.getBooleanExtra("matchedGroup", false)
        val data = SmsDiagnosticsData(
            diagnostics = if (cohortMode) listOf(matchedGroup) else listOf(needsCard, waiting) + recent,
            accounts = listOf(SmsAccountOption(account, "Credo")),
            cardFamilies = listOf(family),
            cardMappings = listOf(SmsCardMapping(PaymentInstrumentEntity(
                id = 1, groupId = 1, type = PaymentInstrumentType.PHYSICAL_CARD, last4 = "0001"), family)),
        )
        setContent {
            CompositionLocalProvider(LocalContext provides localized, LocalResources provides localized.resources,
                LocalConfiguration provides config, LocalDensity provides Density(LocalDensity.current.density, font)) {
                WhfinTheme(darkTheme = dark) {
                    Surface { SecondaryPage(if (language == "ru") "SMS банка" else "Bank SMS", {}) {
                        SmsDiagnosticsScreen(loadState = SmsDiagnosticsLoadState.Content(data),
                            scanState = SmsScanState.Idle, messageState = SmsMessageState.Hidden,
                            smsImportEnabled = true, hasReceivePermission = true,
                            hasHistoryPermission = true, canRequestHistoryPermission = true,
                            onScanHistory = {}, onConfirmHistoryImport = {}, onCancelHistoryImport = {},
                            onResolve = { _, _, _ -> }, onAddCardMapping = { _, _, _ -> },
                            onViewMessage = {}, onDismissMessage = {})
                    } }
                }
            }
        }
    }
}
