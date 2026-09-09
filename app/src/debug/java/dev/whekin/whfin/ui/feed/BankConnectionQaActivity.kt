package dev.whekin.whfin.ui.feed

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.Density
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.ui.accounts.AddAccountSheet
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.util.Locale

/** Isolated synthetic render host; never creates a bank session or touches a ledger. */
class BankConnectionQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = intent.getBooleanExtra("dark", false)
        val config = Configuration(resources.configuration).apply { setLocale(Locale.forLanguageTag(intent.getStringExtra("language") ?: "en")) }
        val localized = createConfigurationContext(config)
        val font = intent.getFloatExtra("fontScale", 1f)
        val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        setContent {
            var connected by remember { mutableStateOf<String?>(null) }
            CompositionLocalProvider(androidx.compose.ui.platform.LocalResources provides localized.resources, LocalContext provides localized, LocalConfiguration provides config,
                LocalDensity provides Density(LocalDensity.current.density, font)) {
                WhfinTheme(darkTheme = dark) { Surface {
                    if (connected != null) androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().systemBarsPadding(), contentAlignment = androidx.compose.ui.Alignment.Center) { Text("Connected: $connected") }
                    else if (intent.getBooleanExtra("account", false)) AddAccountSheet(
                        onDismiss = { finish() }, onImportStatement = {}, initialType = AccountType.BANK,
                        onConnectBank = { connected = it }, onConfirm = { _, _, _, _, _ -> connected = "manual" })
                    else BankSyncSheet(listOf("Credo" to (System.currentTimeMillis() - 8 * 86_400_000L), "TBC" to null), { finish() }) { connected = it }
                } }
            }
        }
    }
}
