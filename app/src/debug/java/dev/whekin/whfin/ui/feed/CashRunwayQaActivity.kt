package dev.whekin.whfin.ui.feed

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.WhfinContextHeader
import dev.whekin.whfin.data.recurring.RecurringCharge
import dev.whekin.whfin.data.recurring.RecurringOccurrence
import dev.whekin.whfin.ui.theme.WhfinTheme
import dev.whekin.whfin.ui.bank.SupportedBankApp
import dev.whekin.whfin.ui.demo.DemoWorkspaceProvider
import java.time.LocalDate
import java.util.Locale

/** Debug-only, non-exported render host. Synthetic values only; never opens or modifies a database. */
class CashRunwayQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = intent.getBooleanExtra("dark", false)
        val language = intent.getStringExtra("language") ?: "en"
        Locale.setDefault(Locale.forLanguageTag(language))
        val fontScale = intent.getFloatExtra("fontScale", 1f)
        val enough = intent.getBooleanExtra("enough", false)
        val weekendSample = intent.getBooleanExtra("weekendSample", false)
        val cardNotice = intent.getBooleanExtra("cardNotice", false)
        val demo = intent.getBooleanExtra("demo", false)
        val bankFailure = intent.getBooleanExtra("bankFailure", false)
        val alertsEnabled = intent.getBooleanExtra("alertsEnabled", true)
        val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        val configuration = Configuration(resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        val context = createConfigurationContext(configuration)
        val charge = RecurringCharge("merchant:1", if (language == "ru") "Аренда квартиры" else "Rent",
            120_000, 3, LocalDate.of(2026, 8, 3))
        val available = if (weekendSample) 80_000L else if (enough) 260_000L else 115_000L
        val salary = dev.whekin.whfin.data.db.IncomeSourceEntity(
            id = 1, label = "Salary", amountMinor = 400_000, currency = "GEL", accountId = 1,
            expectedDayFrom = 5, expectedDayTo = 10, startedOn = LocalDate.of(2026, 1, 1).toEpochDay(),
            createdAt = 0,
        )
        val runway = homeRunway(
            available, if (weekendSample) 9_000 else 7_200, listOf(salary), LocalDate.of(2026, 8, 28),
            recurringOccurrences = if (weekendSample) emptyList() else listOf(
                RecurringOccurrence(charge, LocalDate.of(2026, 9, 3)),
                RecurringOccurrence(charge.copy(key = "merchant:2", label = "Northwind Hosting",
                    typicalMinor = 3_900), LocalDate.of(2026, 9, 7)),
            ),
        )!!
        setContent {
            var accountsRequested by androidx.compose.runtime.remember { mutableStateOf(false) }
            var bankRequested by androidx.compose.runtime.remember { mutableStateOf(false) }
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides configuration,
                LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                WhfinTheme(darkTheme = dark) {
                  DemoWorkspaceProvider(active = demo, busy = false, problem = null, onUsePersonal = {}) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState())) {
                            if (accountsRequested) Text("Accounts callback received") else if (bankRequested) Text("Bank callback received") else {
                                WhfinContextHeader(context.getString(R.string.home_spendable),
                                    dev.whekin.whfin.ui.formatMinor(
                                        if (weekendSample) 80_000 else if (enough) 260_000 else 115_000,
                                        "GEL",
                                    )) {}
                                if (cardNotice) HomePhysicalCardBalance(
                                    balances = listOf(PhysicalCardHomeBalance(1, "Everyday", 8_600, listOf("0001"), "Credo", SupportedBankApp.CREDO)),
                                    notificationsEnabled = alertsEnabled,
                                    onOpenAccounts = { accountsRequested = true },
                                    onEnableNotifications = {},
                                    isBankLaunchable = { true },
                                    onOpenBank = { bankRequested = !bankFailure; !bankFailure },
                                ) else HomeRunwayRow(runway) { accountsRequested = true }
                            }
                        }
                    }
                  }
                }
            }
        }
    }
}
