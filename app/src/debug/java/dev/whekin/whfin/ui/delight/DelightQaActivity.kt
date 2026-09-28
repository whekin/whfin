package dev.whekin.whfin.ui.delight

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.*
import dev.whekin.whfin.data.db.*
import dev.whekin.whfin.data.sync.*
import dev.whekin.whfin.ui.feed.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import dev.whekin.whfin.ui.settings.AboutScreen
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.util.Locale

class DelightQaActivity : ComponentActivity() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base.createConfigurationContext(Configuration(base.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language)); fontScale = textScale
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
        val transparent = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.auto(transparent, transparent) { dark },
            navigationBarStyle = androidx.activity.SystemBarStyle.auto(transparent, transparent) { dark },
        )
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !dark
        setContent { WhfinTheme(darkTheme = dark, dynamicColor = dynamic) {
            val background = androidx.compose.material3.MaterialTheme.colorScheme.background.toArgb()
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = androidx.activity.SystemBarStyle.auto(transparent, transparent) { dark },
                    navigationBarStyle = androidx.activity.SystemBarStyle.auto(transparent, transparent) { dark },
                )
                window.isNavigationBarContrastEnforced = false
            }
            SideEffect { window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(background)) }
            Surface(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize().statusBarsPadding()) {
                when (intent.getStringExtra("page") ?: "about") {
                    "about" -> AboutScreen("QA")
                    "sync" -> {
                        var phase by remember { mutableStateOf(SyncPhase.READING) }
                        var sheet by remember { mutableStateOf(false) }
                        val states = listOf(BankSyncStatus("Credo", 1, phase == SyncPhase.READING, phase))
                        Column(Modifier.padding(20.dp)) {
                            BankSyncIndicator(states) { sheet = true }
                            WhfinButton("Complete", { phase = SyncPhase.COMPLETE })
                            WhfinButton("Attention", { phase = SyncPhase.ATTENTION })
                        }
                        if (sheet) BankSyncSheet(listOf("Credo" to null), { sheet = false }, states) {}
                    }
                    "history" -> WhfinStatePane(WhfinPaneState.Empty, stringResource(R.string.transactions_history_title),
                        stringResource(R.string.feed_empty), illustration = WhfinIllustrationScene.History)
                    "debts" -> dev.whekin.whfin.ui.accounts.DebtLedgerDialog(emptyList(), emptyList(), emptyList(), {}, {}, {})
                    "savings" -> dev.whekin.whfin.ui.savings.SavingsScreen(
                        dev.whekin.whfin.ui.savings.SavingsScreenData("GEL", emptyList(), null, 0, 0, 0, 0, 0, false),
                        { _, _, _ -> }, {})
                    "transfer" -> {
                        val source = AccountEntity(id = 1, name = "Everyday", type = AccountType.BANK, currency = "GEL", groupId = 1)
                        val target = AccountEntity(id = 2, name = "Reserve", type = AccountType.BANK, currency = "USD", groupId = 2)
                        AddTransactionSheet(listOf(source, target), emptyList(), emptyList(), {}, {}, {},
                            editing = FeedItem(TransactionEntity(id = 1, accountId = 1, amountMinor = -27000,
                                currency = "GEL", occurredAt = 1_790_000_000_000, isTransfer = true,
                                status = TxStatus.MANUAL, source = TxSource.MANUAL), null, null, source, null,
                                destinationAccountId = 2, destinationCurrency = "USD", destinationAmountMinor = 10000, day = java.time.LocalDate.of(2026, 9, 29)))
                    }
                    "category" -> {
                        val category = CategoryEntity(id = 1, name = if (language == "ru") "Кафе" else "Cafes",
                            kind = CategoryKind.EXPENSE, icon = "Restaurant", color = androidx.compose.material3.MaterialTheme.colorScheme.primary.toArgb())
                        val account = AccountEntity(id = 1, name = "Cash", type = AccountType.CASH, currency = "GEL")
                        TransactionDetailsSheet(FeedItem(TransactionEntity(id = 1, accountId = 1, amountMinor = -2000,
                            currency = "GEL", occurredAt = 1_790_000_000_000, categoryId = 1,
                            status = TxStatus.MANUAL, source = TxSource.MANUAL), null, category, account, null, day = java.time.LocalDate.of(2026, 9, 29)),
                            {}, {}, null, onEdit = null, onDebt = null, onClearDebt = null, categoryAcknowledgement = 1)
                    }
                }
            }
                WhfinStatusBarProtection(Modifier.align(androidx.compose.ui.Alignment.TopCenter))
            } }
        } }
    }
    companion object { var language = "en"; var textScale = 1f; var dark = false; var dynamic = false }
}
