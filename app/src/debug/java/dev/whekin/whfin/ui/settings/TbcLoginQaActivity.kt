package dev.whekin.whfin.ui.settings

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import dev.whekin.whfin.data.tbc.TbcAccount
import dev.whekin.whfin.ui.SecondaryPage
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.util.Locale

/** Synthetic, non-exported form host. No gateway, session store, credentials or ledger access. */
class TbcLoginQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = intent.getBooleanExtra("dark", false)
        val language = intent.getStringExtra("language") ?: "en"
        val config = Configuration(resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        val localized = createConfigurationContext(config)
        val font = intent.getFloatExtra("fontScale", 1f)
        val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        val accounts = listOf(TbcAccount(1, "GE00TB0000000000000001", "GEL", "Everyday"))
        val initial = TbcLoginState(
            stage = TbcLoginStage.valueOf(intent.getStringExtra("stage") ?: "Login"),
            accounts = accounts, error = intent.getStringExtra("error"),
            hasSaved = intent.getBooleanExtra("saved", false), hasSavedCredentials = intent.getBooleanExtra("credentials", intent.getBooleanExtra("saved", false)), remember = intent.getBooleanExtra("saved", false),
            syncResult = if (intent.getStringExtra("stage") == "Connected") {
                // One run containing every state the result surface has to order: an account the
                // owner must answer, ordinary outcomes, a deposit whose figures disagree, a failed
                // account, and a product family that could not be listed at all.
                if (intent.getBooleanExtra("rich", false)) {
                    val waiting = dev.whekin.whfin.data.tbc.TbcLedgerAccount("12", "GE00TB0000000000000002", "GEL",
                        "Everyday", balanceMinor = 128740L)
                    dev.whekin.whfin.data.importer.TbcSyncResult(inserted = 5, matched = 1, unchanged = 1,
                        needsStatement = listOf(waiting),
                        initialHistories = listOf(dev.whekin.whfin.data.importer.TbcInitialHistory(
                            waiting, java.time.LocalDate.of(2025, 9, 15), java.time.LocalDate.of(2026, 9, 15), emptyList())),
                        errors = listOf("EUR · •0001: HISTORY_FORMAT",
                            "${dev.whekin.whfin.data.importer.TbcHistorySync.DEPOSITS_LABEL}: NETWORK"),
                        reports = listOf(
                            dev.whekin.whfin.data.importer.TbcSyncReport("GEL · •0001", 12, alreadyKnown = 8,
                                inserted = 3, matched = 1, pending = 2,
                                stats = dev.whekin.whfin.data.tbc.TbcHistoryReadStats(pages = 2, parsed = 12)),
                            dev.whekin.whfin.data.importer.TbcSyncReport("USD · •0001", 0,
                                stats = dev.whekin.whfin.data.tbc.TbcHistoryReadStats(pages = 1, firstPageEmpty = true)),
                            dev.whekin.whfin.data.importer.TbcSyncReport("My Safe · GEL · •0009", 2, inserted = 2,
                                fullHistory = true, bankBalanceDiffers = true),
                            dev.whekin.whfin.data.importer.TbcSyncReport("EUR · •0001", 0, error = "HISTORY_FORMAT"),
                            dev.whekin.whfin.data.importer.TbcSyncReport("GEL · •0002", 0, waitingForBalance = true)))
                } else if (intent.getBooleanExtra("initial", false)) dev.whekin.whfin.data.importer.TbcSyncResult(needsStatement = listOf(
                    dev.whekin.whfin.data.tbc.TbcLedgerAccount("10", "GE00TB0000000000000001", "GEL", "Everyday")),
                    initialHistories = listOf(dev.whekin.whfin.data.importer.TbcInitialHistory(
                        dev.whekin.whfin.data.tbc.TbcLedgerAccount("10", "GE00TB0000000000000001", "GEL", "Everyday"),
                        java.time.LocalDate.of(2025, 9, 9), java.time.LocalDate.of(2026, 9, 9), emptyList())))
                else dev.whekin.whfin.data.importer.TbcSyncResult(inserted = 0, matched = 0, unchanged = 2,
                    reports = listOf(
                        dev.whekin.whfin.data.importer.TbcSyncReport("GEL · •0001", 0, fullHistory = true,
                            stats = dev.whekin.whfin.data.tbc.TbcHistoryReadStats(pages = 1, firstPageEmpty = true)),
                        dev.whekin.whfin.data.importer.TbcSyncReport("USD · •0001", 5, alreadyKnown = 5,
                            stats = dev.whekin.whfin.data.tbc.TbcHistoryReadStats(pages = 2, parsed = 5))))
            } else null,
        )
        setContent {
            var state by remember { mutableStateOf(initial) }
            CompositionLocalProvider(androidx.compose.ui.platform.LocalResources provides localized.resources, LocalContext provides localized, LocalConfiguration provides config,
                LocalDensity provides Density(LocalDensity.current.density, font)) {
                WhfinTheme(darkTheme = dark) {
                    androidx.compose.material3.Surface {
                    SecondaryPage(if (language == "ru") "Подключение TBC" else "TBC connection", {}) {
                        TbcLoginScreen(state, canStoreSession = true,
                            onLogin = { _, _ -> state = state.copy(stage = TbcLoginStage.Code) },
                            onCode = { state = state.copy(stage = TbcLoginStage.Connected) },
                            onRemember = { state = state.copy(remember = it) },
                            onRestore = { state = state.copy(stage = TbcLoginStage.Connected) },
                            onForget = { state = state.copy(hasSaved = false, remember = false, stage = TbcLoginStage.Login) },
                            onConfirmBalance = { _, _ -> state = state.copy(syncResult = dev.whekin.whfin.data.importer.TbcSyncResult(inserted = 3)) },
                            onDone = ::finish)
                    }
                    }
                }
            }
        }
    }
}
