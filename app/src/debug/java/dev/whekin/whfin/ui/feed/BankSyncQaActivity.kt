package dev.whekin.whfin.ui.feed

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.core.ui.*
import dev.whekin.whfin.data.sync.*
import dev.whekin.whfin.ui.theme.WhfinTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import java.util.Locale

/** Non-exported synthetic UI and service probe. Never logs in to a bank. */
class BankSyncQaActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = intent.getBooleanExtra("dark", false)
        val locale = Locale.forLanguageTag(intent.getStringExtra("language") ?: "en")
        val config = Configuration(resources.configuration).apply { setLocale(locale) }
        val localized = createConfigurationContext(config)
        val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        setContent {
            CompositionLocalProvider(LocalContext provides localized, LocalResources provides localized.resources,
                LocalConfiguration provides config, LocalDensity provides Density(LocalDensity.current.density, intent.getFloatExtra("fontScale", 1f))) {
                WhfinTheme(darkTheme = dark) {
                    Surface(Modifier.fillMaxSize()) {
                        var sheet by remember { mutableStateOf(false) }
                        var selected by remember { mutableStateOf<String?>(null) }
                        val states = listOf(BankSyncStatus("Credo", 1, true, SyncPhase.READING, 2, 9),
                            BankSyncStatus("TBC", 2, false, SyncPhase.ATTENTION))
                        Column(Modifier.safeDrawingPadding()) {
                            WhfinContextHeader("Available", "123.45", valueSymbol = "₾") {
                                BankSyncIndicator(true) { sheet = true }
                            }
                            Text(if (locale.language == "ru") "Главная" else "Home", Modifier.padding(20.dp), style = MaterialTheme.typography.headlineLarge)
                            selected?.let { Text("Opened $it", Modifier.padding(20.dp)) }
                            if (intent.getBooleanExtra("probe", false)) WhfinButton(label = "Start synthetic sync", onClick = {
                                (application as WhfinApp).bankSync.launch("Credo", Dispatchers.IO, {}) {
                                    while (!cacheDir.resolve("bank-sync-release").exists()) delay(100)
                                    cacheDir.resolve("bank-sync-probe").writeText("complete")
                                }
                            })
                        }
                        if (sheet) BankSyncSheet(listOf("Credo" to null, "TBC" to null), { sheet = false }, states) {
                            selected = it
                            sheet = false
                        }
                    }
                }
            }
        }
    }
}
