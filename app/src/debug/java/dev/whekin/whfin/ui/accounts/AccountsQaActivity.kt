package dev.whekin.whfin.ui.accounts

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.BankProduct
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.util.Locale

/**
 * Debug-only, non-exported render host for the account cards. Synthetic values only; never opens or
 * modifies a database.
 *
 * The shapes worth rendering are the ones real data produces and the demo fixture does not: three
 * currencies under one imported account name, a five-figure balance inside a narrow cell, and a card
 * ledger that has run out.
 */
class AccountsQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = intent.getBooleanExtra("dark", false)
        val language = intent.getStringExtra("language") ?: "en"
        Locale.setDefault(Locale.forLanguageTag(language))
        val fontScale = intent.getFloatExtra("fontScale", 1f)
        val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        val configuration = Configuration(resources.configuration)
            .apply { setLocale(Locale.forLanguageTag(language)) }
        val context = createConfigurationContext(configuration)
        setContent {
            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
            ) {
                WhfinTheme(darkTheme = dark) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Column(
                            Modifier
                                .safeDrawingPadding()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AccountGroupCard(
                                name = "Credo",
                                accounts = QA_ACCOUNTS,
                                onOpenTransactions = {},
                                onOpenAccountDetails = {},
                                onOpenGroupDetails = {},
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One imported account with three currencies and an empty card ledger, one renamed by hand, and a
 * deposit whose lari balance is the widest number the strip has to hold.
 */
private val QA_ACCOUNTS = listOf(
    AccountWithBalance(
        AccountEntity(id = 1, name = "Credo GEL •0001", type = AccountType.BANK, groupId = 1, currency = "GEL", iban = "GE00CD0000000000000001"),
        5_761, listOf("0002"), primaryCardMasks = listOf("0002"), primaryCardConfigured = true,
        groupName = "Credo",
    ),
    AccountWithBalance(
        AccountEntity(id = 2, name = "Credo EUR •0001", type = AccountType.BANK, groupId = 1, currency = "EUR", iban = "GE00CD0000000000000001"),
        0, emptyList(), groupName = "Credo",
    ),
    AccountWithBalance(
        AccountEntity(id = 3, name = "Credo USD •0001", type = AccountType.BANK, groupId = 1, currency = "USD", iban = "GE00CD0000000000000001"),
        497, emptyList(), groupName = "Credo",
    ),
    AccountWithBalance(
        AccountEntity(
            id = 4, name = "Term deposit", type = AccountType.SAVINGS, groupId = 1,
            currency = "GEL", iban = "GE00CD0000000000000002",
            bankProduct = BankProduct.TERM_DEPOSIT,
        ),
        2_448_830, emptyList(), groupName = "Credo",
    ),
    AccountWithBalance(
        AccountEntity(
            id = 5, name = "Term deposit", type = AccountType.SAVINGS, groupId = 1,
            currency = "USD", iban = "GE00CD0000000000000002",
            bankProduct = BankProduct.TERM_DEPOSIT,
        ),
        255_000, emptyList(), groupName = "Credo",
    ),
    AccountWithBalance(
        AccountEntity(
            id = 6, name = "Travel", type = AccountType.BANK, groupId = 1,
            currency = "EUR", iban = "GE00CD0000000000000003",
            bankProduct = BankProduct.CURRENT_ACCOUNT,
        ),
        16_930, emptyList(), groupName = "Credo",
    ),
)
