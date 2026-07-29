package dev.whekin.whfin.ui.feed

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.R
import dev.whekin.whfin.data.db.SmsDiagnosticEntity
import dev.whekin.whfin.data.db.SmsDiagnosticKind
import dev.whekin.whfin.data.db.SmsDiagnosticOutcome
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
class UnroutedOperationRowTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rowShowsFinancialMeaningAndOpensRouting() {
        var clicked = false
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            WhfinTheme {
                UnroutedOperationRow(
                    operation = UnroutedOperation(
                        diagnostic = SmsDiagnosticEntity(
                            id = 73,
                            externalKey = "sms|preview",
                            kind = SmsDiagnosticKind.CARD_PAYMENT,
                            outcome = SmsDiagnosticOutcome.NEEDS_CARD_MAPPING,
                            receivedAt = 1_000,
                            occurredAt = 1_000,
                            amountMinor = 7_490,
                            currency = "USD",
                            balanceCurrency = "GEL",
                            cardLast4 = "0001",
                            counterparty = "OPENAI *CHATGPT SUBSCR",
                            updatedAt = 1_000,
                        ),
                        day = LocalDate.of(2026, 7, 29),
                    ),
                    onClick = { clicked = true },
                )
            }
        }

        compose.onNodeWithText("Openai *Chatgpt Subscr").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.feed_unrouted_choose_account))
            .assertIsDisplayed()
        compose.onNodeWithTag("unrouted-operation-73").performClick()
        compose.runOnIdle { assertTrue(clicked) }
    }
}
