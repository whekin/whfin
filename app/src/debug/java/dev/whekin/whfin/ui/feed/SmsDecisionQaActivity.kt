package dev.whekin.whfin.ui.feed

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dev.whekin.whfin.data.db.SmsDiagnosticEntity
import dev.whekin.whfin.data.db.SmsDiagnosticKind
import dev.whekin.whfin.data.db.SmsDiagnosticOutcome
import dev.whekin.whfin.data.db.SmsDiagnosticReason
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.time.LocalDate
import java.util.Locale

/** Synthetic rows only: no inbox, ledger, routing callback or credential access. */
class SmsDecisionQaActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
            fontScale = scale
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
        val bars = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        setContent {
            WhfinTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                        UnroutedOperationRow(operation(1, SmsDiagnosticReason.STATEMENT_COVERS_PERIOD), {})
                        UnroutedOperationRow(operation(2, SmsDiagnosticReason.MULTIPLE_ACCOUNTS), {})
                    }
                }
            }
        }
    }

    private fun operation(id: Long, reason: SmsDiagnosticReason) = UnroutedOperation(
        diagnostic = SmsDiagnosticEntity(
            id = id, externalKey = "sms|qa-$id", kind = SmsDiagnosticKind.OUTGOING_TRANSFER,
            outcome = SmsDiagnosticOutcome.CHOOSE_ACCOUNT, reason = reason,
            receivedAt = 1_780_000_000_000L, occurredAt = 1_780_000_000_000L,
            amountMinor = 1_000, currency = "GEL", updatedAt = 1_780_000_000_000L,
        ),
        day = LocalDate.of(2026, 9, 28),
    )

    companion object {
        var language = "en"
        var scale = 1f
        var dark = false
    }
}
