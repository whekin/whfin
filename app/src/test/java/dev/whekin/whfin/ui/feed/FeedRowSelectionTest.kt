package dev.whekin.whfin.ui.feed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.R
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FeedRowSelectionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun longPress_selectsRowAndExposesAccessibleSelectedState() {
        var selected by mutableStateOf(false)
        var longPressed = false
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            WhfinTheme {
                FeedRow(
                    item = sampleItem(),
                    selected = selected,
                    onClick = {},
                    onLongClick = {
                        longPressed = true
                        selected = true
                    },
                )
            }
        }

        compose.onNodeWithTag("feed-row-42").performSemanticsAction(SemanticsActions.OnLongClick)
        compose.runOnIdle { assertTrue(longPressed) }
        compose.onNodeWithContentDescription(context.getString(R.string.transactions_selected)).assertExists()
    }

    private fun sampleItem() = FeedItem(
        tx = TransactionEntity(
            id = 42,
            accountId = 1,
            amountMinor = -1_250,
            currency = "GEL",
            occurredAt = 1_000,
            rawCounterparty = "Example",
            status = TxStatus.PENDING,
            source = TxSource.SMS,
        ),
        merchant = null,
        category = null,
        account = null,
        cardHint = null,
        day = LocalDate.of(2026, 7, 14),
    )
}
