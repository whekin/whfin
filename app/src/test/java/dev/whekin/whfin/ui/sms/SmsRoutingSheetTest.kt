package dev.whekin.whfin.ui.sms

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.R
import dev.whekin.whfin.data.db.FundRole
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.BankProduct
import dev.whekin.whfin.data.db.PaymentInstrumentType
import dev.whekin.whfin.data.db.SmsDiagnosticEntity
import dev.whekin.whfin.data.db.SmsDiagnosticKind
import dev.whekin.whfin.data.db.SmsDiagnosticOutcome
import dev.whekin.whfin.ui.formatMinor
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

    /**
     * A transfer names no card, so nothing is linked here and the balance is the only thing that
     * tells the two ledgers apart.
     */
    @Test
    fun transferShowsTheStatedBalanceAndPromisesNoLink() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            WhfinTheme {
                SmsRoutingSheet(
                    diagnostic = SmsDiagnosticEntity(
                        id = 81,
                        externalKey = "sms|transfer",
                        kind = SmsDiagnosticKind.OUTGOING_TRANSFER,
                        outcome = SmsDiagnosticOutcome.CHOOSE_ACCOUNT,
                        receivedAt = 1_000,
                        occurredAt = 1_000,
                        amountMinor = 10_000,
                        currency = "GEL",
                        balanceMinor = 123_456,
                        balanceCurrency = "GEL",
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
                        SmsRoutingAccount(
                            AccountEntity(
                                id = 11,
                                name = "Second",
                                type = AccountType.BANK,
                                groupId = 7,
                                currency = "GEL",
                            ),
                            groupName = "Credo",
                        ),
                    ),
                    onDismiss = {},
                    onResolve = { _, _ -> },
                    onResolveGroup = { _, _ -> },
                    onCreateAccount = { _, _, _ -> },
                    onAddGroupedAccount = { _, _ -> },
                )
            }
        }

        compose.onNodeWithText(
            context.getString(R.string.sms_stated_balance, formatMinor(123_456, "GEL")),
        ).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.transaction_confirm)).assertIsDisplayed()
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


    /**
     * Interest is paid on a deposit, and which accounts are deposits has already been stated. Offering
     * the current accounts too asked the owner to answer again what they had answered once.
     */
    @Test
    fun interestOffersDepositsOnlyAndNamesTheDepositItIsAbout() {
        var resolved: Long? = null
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            WhfinTheme {
                SmsRoutingSheet(
                    diagnostic = SmsDiagnosticEntity(
                        id = 90,
                        externalKey = "sms|interest",
                        kind = SmsDiagnosticKind.INTEREST,
                        outcome = SmsDiagnosticOutcome.CHOOSE_ACCOUNT,
                        receivedAt = 1_000,
                        occurredAt = 1_000,
                        amountMinor = 531,
                        currency = "GEL",
                        balanceMinor = 64_028,
                        balanceCurrency = "GEL",
                        depositNumber = "10000002",
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
                                bankProduct = BankProduct.CURRENT_ACCOUNT,
                            ),
                            groupName = "Credo",
                        ),
                        SmsRoutingAccount(
                            AccountEntity(
                                id = 12,
                                name = "Credo GEL •0002",
                                type = AccountType.BANK,
                                groupId = 7,
                                currency = "GEL",
                                iban = "GE00CD0000000000000002",
                                fundRole = FundRole.AVAILABLE,
                                bankProduct = BankProduct.DEMAND_DEPOSIT,
                            ),
                            groupName = "Credo",
                        ),
                    ),
                    onDismiss = {},
                    onResolve = { account, _ -> resolved = account },
                    onResolveGroup = { _, _ -> },
                    onCreateAccount = { _, _, _ -> },
                    onAddGroupedAccount = { _, _ -> },
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.sms_deposit_number, "10000002"))
            .assertIsDisplayed()
        compose.onNodeWithText("Credo · Everyday").assertDoesNotExist()
        // The bank named the ledger "Credo GEL •0002", so a row printing that under a heading that
        // has said the bank, and again under itself, said the bank twice, the number twice and the
        // currency twice. What is left is the number, once, said as an account number rather than in
        // the card mask this app uses for cards — a deposit has no card.
        compose.onNodeWithText("Credo · Credo GEL •0002").assertDoesNotExist()
        compose.onNodeWithText("••0002 · GEL").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.account_iban_short, "0002"))
            .assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.account_product_demand_deposit))
            .assertIsDisplayed()
        // The one deposit is preselected, and answering leaves the number behind, so the action says so.
        compose.onNodeWithText(context.getString(R.string.sms_link_and_confirm_action))
            .assertIsEnabled()
            .performClick()
        compose.runOnIdle { assertEquals(12L, resolved) }
    }
}
