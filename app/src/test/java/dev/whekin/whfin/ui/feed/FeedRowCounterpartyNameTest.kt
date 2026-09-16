package dev.whekin.whfin.ui.feed

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.ui.LocalLatinCounterparties
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The switch has to reach the rows themselves, not just the function it calls. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FeedRowCounterpartyNameTest {
    @get:Rule
    val compose = createComposeRule()

    @Test fun aGeorgianCounterpartyIsReadInLatin() {
        compose.setContent {
            CompositionLocalProvider(LocalLatinCounterparties provides true) {
                WhfinTheme { FeedRow(item = georgianRow(), onClick = {}) }
            }
        }

        compose.onNodeWithText("Giorgi Kharadze").assertIsDisplayed()
    }

    @Test fun switchedOffTheRowShowsWhatTheBankWrote() {
        compose.setContent {
            CompositionLocalProvider(LocalLatinCounterparties provides false) {
                WhfinTheme { FeedRow(item = georgianRow(), onClick = {}) }
            }
        }

        compose.onNodeWithText("გიორგი ხარაძე").assertIsDisplayed()
    }

    private fun georgianRow() = FeedItem(
        tx = TransactionEntity(
            id = 7,
            accountId = 1,
            amountMinor = -4_500,
            currency = "GEL",
            occurredAt = 1_000,
            rawCounterparty = "გიორგი ხარაძე",
            status = TxStatus.CONFIRMED,
            source = TxSource.STATEMENT,
        ),
        merchant = null,
        category = null,
        account = null,
        cardHint = null,
        day = LocalDate.of(2026, 9, 14),
    )
}
