package dev.whekin.whfin.ui.feed

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import dev.whekin.whfin.data.db.*
import dev.whekin.whfin.ui.theme.WhfinTheme

/** Synthetic composer host; never touches the ledger. */
class ComposerQaActivity : ComponentActivity() {
    var result: ManualTransaction? = null
        private set
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var saved by remember { mutableStateOf<ManualTransaction?>(null) }
            WhfinTheme(darkTheme = intent.getBooleanExtra("dark", false)) {
                if (saved != null) Box(Modifier.fillMaxSize().safeDrawingPadding()) { Text("Saved expense") } else if (intent.getBooleanExtra("quick", false)) dev.whekin.whfin.widget.QuickExpenseScreen(
                    initialCurrency = "GEL", sourceLabel = "Cash", sourceAccountId = 1,
                    categories = emptyList(), suggester = null, onDismiss = { finish() },
                    people = listOf(PersonEntity(id = 1, name = "Mira", color = 0)),
                    onSave = { amount, _, _, _, _, beneficiary ->
                        result = ManualTransaction(1, amountMinor = -amount, categoryId = null, note = null, day = java.time.LocalDate.now(), beneficiary = beneficiary)
                        saved = result
                    })
                else AddTransactionSheet(
                    accounts = listOf(AccountEntity(id = 1, name = "Cash", type = AccountType.CASH, currency = "GEL")),
                    categories = listOf(CategoryEntity(id = 1, name = "Coffee", kind = CategoryKind.EXPENSE, icon = "Restaurant", color = 0)),
                    people = listOf(PersonEntity(id = 1, name = "Mira", color = 0)),
                    onDismiss = { finish() }, onSave = { result = it; saved = it }, onSaveDebt = {})
            }
        }
    }
}
