package dev.whekin.whfin.ui.sms

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.BankProduct
import dev.whekin.whfin.data.db.FundRole
import dev.whekin.whfin.data.db.SmsDiagnosticEntity
import dev.whekin.whfin.data.db.SmsDiagnosticKind
import dev.whekin.whfin.data.db.SmsDiagnosticOutcome
import dev.whekin.whfin.data.db.SmsDiagnosticReason
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.util.Locale

/**
 * Debug-only, non-exported render host for the SMS routing question. Synthetic values only; never
 * opens or modifies a database.
 *
 * The shape worth rendering is the one real data produces and the demo fixture does not: several
 * deposits whose names the bank wrote itself as "<Bank> <CUR> •<last4>", which is what made the rows
 * print the bank, the number and the currency twice each.
 */
class SmsRoutingQaActivity : ComponentActivity() {
    /**
     * Locale and font scale are applied to the activity's own base context, not through composition
     * locals: the sheet composes inside its own window, which resolves resources and density from the
     * context rather than inheriting them from the composition that opened it.
     */
    override fun attachBaseContext(newBase: android.content.Context) {
        val locale = Locale.forLanguageTag(language)
        val scale = fontScale
        val configuration = android.content.res.Configuration(newBase.resources.configuration)
        configuration.setLocale(locale)
        configuration.fontScale = scale
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Locale.setDefault(Locale.forLanguageTag(language))
        enableEdgeToEdge()
        setContent {
            WhfinTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SmsRoutingSheet(
                        diagnostic = INTEREST,
                        accounts = DEPOSITS,
                        onDismiss = {},
                        onResolve = { _, _ -> },
                        onResolveGroup = { _, _ -> },
                        onCreateAccount = { _, _, _ -> },
                        onAddGroupedAccount = { _, _ -> },
                    )
                }
            }
        }
    }

    companion object {
        /**
         * Set by the render test before launching. Extras cannot be read in [attachBaseContext],
         * which is the only place a configuration override can still be applied.
         */
        var language: String = "en"
        var fontScale: Float = 1f

        val INTEREST = SmsDiagnosticEntity(
            id = 1,
            externalKey = "qa|interest",
            kind = SmsDiagnosticKind.INTEREST,
            outcome = SmsDiagnosticOutcome.CHOOSE_ACCOUNT,
            reason = SmsDiagnosticReason.MULTIPLE_ACCOUNTS,
            receivedAt = 0,
            occurredAt = 0,
            amountMinor = 435,
            currency = "GEL",
            balanceMinor = 15_435,
            balanceCurrency = "GEL",
            depositNumber = "10000002",
            updatedAt = 0,
        )

        private fun deposit(id: Long, tail: String, product: BankProduct, own: String? = null) =
            SmsRoutingAccount(
                AccountEntity(
                    id = id,
                    // Exactly how a statement names what it creates.
                    name = own ?: "Credo GEL •$tail",
                    type = AccountType.BANK,
                    groupId = 1,
                    currency = "GEL",
                    iban = "GE00CD00000000000$tail",
                    fundRole = if (product == BankProduct.TERM_DEPOSIT) {
                        FundRole.RESERVE
                    } else {
                        FundRole.AVAILABLE
                    },
                    bankProduct = product,
                ),
                groupName = "Credo",
            )

        val DEPOSITS = listOf(
            deposit(1, "0001", BankProduct.DEMAND_DEPOSIT),
            deposit(2, "0002", BankProduct.TERM_DEPOSIT),
            deposit(3, "0003", BankProduct.TERM_DEPOSIT, own = "Credo GEL Travel •0003"),
            // A current account, to prove the question no longer offers one.
            deposit(4, "0004", BankProduct.CURRENT_ACCOUNT),
        )
    }
}
