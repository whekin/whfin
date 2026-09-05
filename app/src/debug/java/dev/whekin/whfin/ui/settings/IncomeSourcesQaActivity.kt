package dev.whekin.whfin.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import dev.whekin.whfin.data.crypto.CryptoBankTransfer
import dev.whekin.whfin.data.db.*
import dev.whekin.whfin.data.income.IncomeExpectation
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/** Disposable render host; synthetic data and no database access. */
class IncomeSourcesQaActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        val config = android.content.res.Configuration(newBase.resources.configuration)
        config.setLocale(Locale.forLanguageTag(language))
        config.fontScale = fontScale
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !dark
        setContent {
            WhfinTheme(darkTheme = dark) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    if (editor) IncomeSourceSheet(
                        source = source, accounts = accounts, onDismiss = {},
                        onSave = { _, _, _, _, _, _, _ -> }, onEnd = {}, onDelete = {},
                    ) else Box(Modifier.fillMaxSize().statusBarsPadding()) {
                        IncomeSourcesScreen(
                            IncomeSourcesState(
                                expectations = listOf(
                                    IncomeExpectation(
                                        source = source,
                                        received = listOf(confirmedPay),
                                        awaiting = true,
                                        confirmedIds = setOf(confirmedPay.id),
                                        candidates = listOf(unconfirmedCredit),
                                    ),
                                ),
                                ended = emptyList(), accounts = accounts, month = YearMonth.of(2026, 8),
                                transfers = listOf(transfer),
                            ), { _, _, _, _, _, _, _, _ -> }, {}, {}, {},
                        )
                    }
                }
            }
        }
    }
    companion object {
        var language = "en"
        var fontScale = 1f
        var dark = false
        var editor = false
        val accounts = listOf(
            AccountEntity(id = 1, name = "Daily cash", type = AccountType.CASH, currency = "USD"),
            AccountEntity(id = 2, name = "My wallet", type = AccountType.CRYPTO, currency = "USDT"),
            AccountEntity(id = 3, name = "Bank", type = AccountType.BANK, currency = "GEL"),
        )
        val source = IncomeSourceEntity(id = 1, label = "Salary", amountMinor = 180000, currency = "USDT",
            accountId = 2, expectedDayFrom = 5, expectedDayTo = 5,
            startedOn = LocalDate.of(2026, 7, 1).toEpochDay(), createdAt = 0)
        /** A part payment already vouched for, beside a credit nobody has answered about yet. */
        val confirmedPay = TransactionEntity(id = 11, accountId = 2, amountMinor = 100000,
            currency = "USDT", occurredAt = 1785902400000, source = TxSource.CRYPTO,
            status = TxStatus.CONFIRMED, rawCounterparty = "TExampleEmployerAddress0000000000")
        val unconfirmedCredit = TransactionEntity(id = 12, accountId = 2, amountMinor = 40000,
            currency = "USDT", occurredAt = 1786075200000, source = TxSource.CRYPTO,
            status = TxStatus.CONFIRMED, rawCounterparty = "TExampleOtherSender00000000000000")
        private val out = TransactionEntity(id = 1, accountId = 2, amountMinor = -180000, currency = "USDT",
            occurredAt = 1785902400000, source = TxSource.CRYPTO, status = TxStatus.CONFIRMED)
        val transfer = CryptoBankTransfer(out, out.copy(id = 2, accountId = 3, amountMinor = 450000,
            currency = "GEL", source = TxSource.STATEMENT, rawCounterparty = "Example exchange"))
    }
}
