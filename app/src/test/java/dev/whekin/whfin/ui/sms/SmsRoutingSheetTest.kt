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
import dev.whekin.whfin.data.db.BankProduct
import dev.whekin.whfin.data.db.PaymentInstrumentType
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
    fun cardPaymentExplainsBatchRoutingAndConfirmsInOneAction() {
        var resolved: Pair<Long, PaymentInstrumentType>? = null
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            WhfinTheme {
                SmsRoutingSheet(
                    diagnostic = SmsDiagnosticEntity(
                        id = 80,
                        externalKey = "sms|card",
                        kind = SmsDiagnosticKind.CARD_PAYMENT,
                        outcome = SmsDiagnosticOutcome.NEEDS_CARD_MAPPING,
                        receivedAt = 1_000,
                        occurredAt = 1_000,
                        amountMinor = 2_198,
                        currency = "GEL",
                        balanceCurrency = "GEL",
                        cardLast4 = "0001",
                        counterparty = "EXAMPLE PHARMACY",
                        updatedAt = 1_000,
                    ),
                    accounts = listOf(
                        SmsRoutingAccount(
                            AccountEntity(
                                id = 10,
                                name = "Everyday",
                                type = AccountType.BANK,
                                groupId = 7,
                                currency = "GEL",
                            ),
                            groupName = "Credo",
                        ),
                    ),
                    onDismiss = {},
                    onResolve = { account, type -> resolved = account to type },
                    onResolveGroup = { _, _ -> },
                    onCreateAccount = { _, _, _ -> },
                    onAddGroupedAccount = { _, _ -> },
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.sms_card_routing_resolver_body))
            .assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.sms_link_and_confirm_action))
            .assertIsEnabled()
            .performClick()
        compose.runOnIdle {
            assertEquals(10L to PaymentInstrumentType.PHYSICAL_CARD, resolved)
        }
    }

    @Test
    fun groupedExchangeAppliesAComputedPairInOneTap() {
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
                                id = 13,
                                name = "Deposit",
                                type = AccountType.BANK,
                                groupId = 7,
                                currency = "GEL",
                                bankProduct = BankProduct.DEMAND_DEPOSIT,
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

        compose.onNodeWithText("Everyday → Dollar")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        compose.onNodeWithText("Deposit → Dollar").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.sms_link_and_confirm_action))
            .assertDoesNotExist()
        compose.runOnIdle { assertEquals(11L to 12L, resolved) }
    }

    @Test
    fun exchangePairsOnlyCurrentAccountsWithinOneBank() {
        val diagnostic = SmsDiagnosticEntity(
            id = 90,
            externalKey = "sms|exchange|pairs",
            kind = SmsDiagnosticKind.CURRENCY_EXCHANGE,
            outcome = SmsDiagnosticOutcome.CHOOSE_ACCOUNT,
            receivedAt = 1_000,
            occurredAt = 1_000,
            amountMinor = 5_000,
            currency = "GEL",
            secondaryAmountMinor = 1_800,
            secondaryCurrency = "USD",
            updatedAt = 1_000,
        )
        val accounts = listOf(
            SmsRoutingAccount(AccountEntity(id = 1, name = "Main", type = AccountType.BANK, groupId = 7, currency = "GEL"), "Credo"),
            SmsRoutingAccount(AccountEntity(id = 2, name = "Dollar", type = AccountType.BANK, groupId = 7, currency = "USD"), "Credo"),
            SmsRoutingAccount(AccountEntity(id = 3, name = "Other bank", type = AccountType.BANK, groupId = 8, currency = "USD"), "Other"),
            SmsRoutingAccount(AccountEntity(id = 4, name = "Demand", type = AccountType.BANK, groupId = 7, currency = "GEL", bankProduct = BankProduct.DEMAND_DEPOSIT), "Credo"),
            SmsRoutingAccount(AccountEntity(id = 5, name = "Legacy savings", type = AccountType.SAVINGS, groupId = 7, currency = "GEL"), "Credo"),
        )

        assertEquals(
            listOf(1L to 2L),
            groupedRoutingPairs(diagnostic, accounts).map { it.from.account.id to it.to.account.id },
        )
    }

}
