package dev.whekin.whfin.ui.transfer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.data.transfer.OwnTransferSide
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.time.ZoneId
import java.util.Locale

/** Disposable render host; synthetic data and no database access. */
class OwnTransferQaActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        val config = android.content.res.Configuration(newBase.resources.configuration)
        config.setLocale(Locale.forLanguageTag(language))
        config.fontScale = fontScale
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        androidx.core.view.WindowCompat
            .getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !dark
        setContent {
            WhfinTheme(darkTheme = dark) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize()) {
                        OwnTransferSheet(
                            transaction = withdrawal,
                            candidates = if (empty) emptyList() else candidates,
                            accounts = accounts,
                            onDismiss = {},
                            onConfirm = {},
                            zone = ZoneId.of("UTC"),
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

        /** No offered rows is the state that sends the owner straight to writing one down. */
        var empty = false
        private const val AT = 1_785_902_400_000L
        val accounts = listOf(
            AccountEntity(id = 1, name = "My wallet", type = AccountType.CRYPTO, currency = "USDT"),
            AccountEntity(id = 2, name = "Everyday", type = AccountType.BANK, currency = "GEL"),
            AccountEntity(id = 3, name = "Pocket money", type = AccountType.CASH, currency = "GEL"),
        )
        val withdrawal = TransactionEntity(
            id = 1, accountId = 1, amountMinor = -270_000, currency = "USDT",
            occurredAt = AT, status = TxStatus.CONFIRMED, source = TxSource.CRYPTO,
            rawCounterparty = "TExampleExchangeAddress0000000000",
        )
        val candidates = listOf(
            OwnTransferSide(
                TransactionEntity(
                    id = 2, accountId = 2, amountMinor = 500_000, currency = "GEL",
                    occurredAt = AT + 86_400_000, status = TxStatus.CONFIRMED,
                    source = TxSource.STATEMENT, rawCounterparty = "Example exchange",
                ),
                accounts[1],
            ),
            OwnTransferSide(
                TransactionEntity(
                    id = 3, accountId = 3, amountMinor = 231_000, currency = "GEL",
                    occurredAt = AT + 2 * 86_400_000, status = TxStatus.MANUAL, source = TxSource.MANUAL,
                ),
                accounts[2],
            ),
        )
    }
}
