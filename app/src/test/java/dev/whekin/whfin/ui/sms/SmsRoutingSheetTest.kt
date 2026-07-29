package dev.whekin.whfin.ui.sms

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.R
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.SmsDiagnosticEntity
import dev.whekin.whfin.data.db.SmsDiagnosticKind
import dev.whekin.whfin.data.db.SmsDiagnosticOutcome
import dev.whekin.whfin.ui.theme.WhfinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SmsRoutingSheetTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun groupedExchangeRequiresAndSubmitsBothLedgerSides() {
        var resolved: Pair<Long, Long>? = null
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            WhfinTheme {
                SmsRoutingSheet(
                    diagnostic = SmsDiagnosticEntity(
                        id = 81,
                        externalKey = "sms|exchange",
                        kind = SmsDiagnosticKind.CURRENCY_EXCHANGE,
                        outcome = SmsDiagnosticOutcome.CHOOSE_ACCOUNT,
                        receivedAt = 1_000,
                        occurredAt = 1_000,
                        amountMinor = 5_000,
                        currency = "GEL",
                        secondaryAmountMinor = 1_800,
                        secondaryCurrency = "USD",
                        balanceMinor = 1_800,
                        balanceCurrency = "USD",
                        updatedAt = 1_000,
                    ),
                    accounts = listOf(
                        SmsRoutingAccount(
                            AccountEntity(
                                id = 11,
                                name = "Everyday",
                                type = AccountType.BANK,
                                groupId = 7,
                                currency = "GEL",
                            ),
                            groupName = "Credo",
                        ),
                        SmsRoutingAccount(
                            AccountEntity(
                                id = 12,
                                name = "Dollar",
                                type = AccountType.BANK,
                                groupId = 7,
                                currency = "USD",
                            ),
                            groupName = "Credo",
                        ),
                    ),
                    onDismiss = {},
                    onResolve = { _, _ -> },
                    onResolveGroup = { from, to -> resolved = from to to },
                    onCreateAccount = { _, _, _ -> },
                    onAddGroupedAccount = { _, _ -> },
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.sms_from_account, "GEL"))
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.sms_to_account, "USD"))
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.action_done)).assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(11L to 12L, resolved) }
    }
}
