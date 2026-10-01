package dev.whekin.whfin.ui.feed

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.R
import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.CategoryKind
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.time.LocalDate
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TransactionDetailsSheetTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun foreignCardPaymentUsesKnownOriginalAmountUntilBankChargeArrives() {
        val transaction = TransactionEntity(
                id = 10,
                accountId = 1,
                amountMinor = 0,
                currency = "GEL",
                origAmountMinor = 762,
                origCurrency = "EUR",
                occurredAt = 1_000,
                status = TxStatus.CONFIRMED,
                source = TxSource.SMS,
            )
        val amount = transactionPresentationAmount(transaction)

        assertEquals(762L, amount.minor)
        assertEquals("EUR", amount.currency)

        compose.setContent {
            WhfinTheme {
                TransactionDetailsSheet(
                    item = FeedItem(
                        tx = transaction,
                        merchant = null,
                        category = null,
                        account = null,
                        cardHint = null,
                        day = LocalDate.of(2026, 8, 13),
                    ),
                    onDismiss = {},
                    onChangeCategory = null,
                    onDelete = null,
                    onEdit = null,
                    onDebt = null,
                    onClearDebt = null,
                )
            }
        }
        compose.onNode(hasText("7.62 €", substring = true)).assertIsDisplayed()
        compose.onNode(hasText("0.00 ₾", substring = true)).assertDoesNotExist()
    }

    @Test
    fun smsTransactionShowsProvenanceWithoutAStatusTask() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val sms = context.getString(R.string.status_waiting_statement)
        compose.setContent {
            WhfinTheme {
                TransactionDetailsSheet(
                    item = FeedItem(
                        tx = TransactionEntity(
                            id = 1,
                            accountId = 1,
                            amountMinor = -1_250,
                            currency = "GEL",
                            occurredAt = 1_000,
                            rawCounterparty = "Example",
                            status = TxStatus.CONFIRMED,
                            source = TxSource.SMS,
                        ),
                        merchant = null,
                        category = null,
                        account = null,
                        cardHint = null,
                        day = LocalDate.of(2026, 7, 14),
                    ),
                    onDismiss = {},
                    onChangeCategory = null,
                    onDelete = null,
                    onEdit = null,
                    onDebt = null,
                    onClearDebt = null,
                )
            }
        }

        compose.onNode(hasText(sms)).assertIsDisplayed()
        compose.onNode(hasText(sms) and hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun smsTransactionDoesNotOfferAConfirmAction() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val confirm = context.getString(R.string.transaction_confirm)
        compose.setContent {
            WhfinTheme {
                TransactionDetailsSheet(
                    item = FeedItem(
                        tx = TransactionEntity(
                            id = 1,
                            accountId = 1,
                            amountMinor = -1_250,
                            currency = "GEL",
                            occurredAt = 1_000,
                            rawCounterparty = "Example",
                            status = TxStatus.CONFIRMED,
                            source = TxSource.SMS,
                        ),
                        merchant = null,
                        category = null,
                        account = null,
                        cardHint = null,
                        day = LocalDate.of(2026, 7, 14),
                    ),
                    onDismiss = {},
                    onChangeCategory = null,
                    onDelete = null,
                    onEdit = null,
                    onDebt = null,
                    onClearDebt = null,
                    onConfirm = { error("SMS provenance must not offer confirmation") },
                )
            }
        }

        compose.onNode(hasText(confirm)).assertDoesNotExist()
    }

    @Test
    fun confirmedTransaction_hidesConfirmAction() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val confirm = context.getString(R.string.transaction_confirm)
        compose.setContent {
            WhfinTheme {
                TransactionDetailsSheet(
                    item = FeedItem(
                        tx = TransactionEntity(
                            id = 3,
                            accountId = 1,
                            amountMinor = -1_250,
                            currency = "GEL",
                            occurredAt = 1_000,
                            rawCounterparty = "Example",
                            status = TxStatus.CONFIRMED,
                            source = TxSource.STATEMENT,
                        ),
                        merchant = null,
                        category = null,
                        account = null,
                        cardHint = null,
                        day = LocalDate.of(2026, 7, 14),
                    ),
                    onDismiss = {},
                    onChangeCategory = null,
                    onDelete = null,
                    onEdit = null,
                    onDebt = null,
                    onClearDebt = null,
                    onConfirm = {},
                )
            }
        }

        compose.onNode(hasText(confirm)).assertDoesNotExist()
    }

    @Test
    fun missingDescription_usesCategoryAndKeepsDeleteInOverflow() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val noDescription = context.getString(R.string.feed_no_description)
        val actions = context.getString(R.string.transaction_actions)
        val deleteTransaction = context.getString(R.string.transaction_delete)
        val category = CategoryEntity(
            id = 7,
            name = "Eating out",
            kind = CategoryKind.EXPENSE,
            icon = "Restaurant",
            color = 0xFFC45D3A.toInt(),
        )

        compose.setContent {
            WhfinTheme {
                TransactionDetailsSheet(
                    item = FeedItem(
                        tx = TransactionEntity(
                            id = 2,
                            accountId = 1,
                            amountMinor = -2_000,
                            currency = "GEL",
                            occurredAt = 1_000,
                            categoryId = category.id,
                            status = TxStatus.MANUAL,
                            source = TxSource.MANUAL,
                        ),
                        merchant = null,
                        category = category,
                        account = null,
                        cardHint = null,
                        day = LocalDate.of(2026, 7, 19),
                    ),
                    onDismiss = {},
                    onChangeCategory = {},
                    onDelete = {},
                    onEdit = {},
                    onDebt = null,
                    onClearDebt = null,
                )
            }
        }

        compose.onNode(hasText(noDescription)).assertDoesNotExist()
        compose.onNode(hasContentDescription(actions)).performClick()
        compose.onNode(hasText(deleteTransaction)).assertIsDisplayed()
    }

    @Test
    fun pendingSmsKeepsBankEvidenceSeparateFromOwnerReview() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val sms = context.getString(R.string.status_waiting_statement)
        val markReviewed = context.getString(R.string.transaction_mark_reviewed)
        var confirmed = false
        compose.setContent {
            WhfinTheme {
                TransactionDetailsSheet(
                    item = FeedItem(
                        tx = TransactionEntity(
                            id = 1,
                            accountId = 1,
                            amountMinor = -1_270,
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
                        day = LocalDate.of(2026, 9, 1),
                    ),
                    onDismiss = {},
                    onChangeCategory = null,
                    onDelete = null,
                    onEdit = null,
                    onDebt = null,
                    onClearDebt = null,
                    onConfirm = { confirmed = true },
                )
            }
        }

        compose.onNode(hasText(sms)).assertIsDisplayed()
        compose.onNode(hasText(sms) and hasClickAction()).assertDoesNotExist()
        compose.onNode(hasText(markReviewed)).performClick()
        assertEquals(true, confirmed)
    }

    @Test
    fun everyAnswerIsVisibleWithoutDraggingTheRow() = assertEveryActionVisible()

    @Test
    @Config(sdk = [35], qualifiers = "ru")
    fun everyAnswerSurvivesTheLongerLanguage() = assertEveryActionVisible()

    @Test
    fun manualStatusIsReadOnlyAndOwnTransferLivesInOverflow() {
        var linked = false
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent { WhfinTheme {
            TransactionDetailsSheet(FeedItem(TransactionEntity(id = 1, accountId = 1, amountMinor = -2113, currency = "GEL",
                occurredAt = 1000, status = TxStatus.MANUAL, source = TxSource.MANUAL), null, null, null, null,
                day = LocalDate.of(2026, 9, 1)), {}, null, null, onEdit = null, onDebt = null, onClearDebt = null,
                onOwnTransfer = { linked = true })
        } }
        compose.onNode(hasText(context.getString(R.string.status_manual)) and hasClickAction()).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.own_transfer_action)).assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.transaction_actions)).performClick()
        compose.onNodeWithText(context.getString(R.string.own_transfer_action)).performClick()
        compose.runOnIdle { org.junit.Assert.assertTrue(linked) }
    }

    @Test
    fun savedAllocationCanBeEditedAndClearingRequiresConfirmation() {
        var edited = false
        var cleared = false
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent { WhfinTheme {
            TransactionDetailsSheet(FeedItem(TransactionEntity(id = 1, accountId = 1, amountMinor = -2113, currency = "GEL",
                occurredAt = 1000, status = TxStatus.MANUAL, source = TxSource.MANUAL), null, null, null, null,
                splitOnPeople = listOf("Mira" to 1056L), day = LocalDate.of(2026, 9, 1)), {}, null, null,
                onEdit = null, onDebt = null, onClearDebt = null, onSplit = { edited = true }, onClearSplit = { cleared = true })
        } }
        compose.onNodeWithText(context.getString(R.string.split_edit)).performClick()
        compose.runOnIdle { org.junit.Assert.assertTrue(edited) }
        compose.onNodeWithContentDescription(context.getString(R.string.transaction_actions)).performClick()
        compose.onNodeWithText(context.getString(R.string.split_clear)).performClick()
        compose.runOnIdle { org.junit.Assert.assertFalse(cleared) }
        compose.onNodeWithText(context.getString(R.string.split_clear_title)).assertExists()
        compose.onNodeWithText(context.getString(R.string.split_clear)).performClick()
        compose.runOnIdle { org.junit.Assert.assertTrue(cleared) }
    }

    private fun assertEveryActionVisible() {
        // The actions used to ride a horizontally scrolling rail, so whether an answer existed
        // depended on whether the reader thought to drag it: at ordinary phone density the fourth
        // one sat past the right edge. They wrap now, and the labels must survive large text in the
        // language whose words are longer.
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 1.5f),
            ) {
                WhfinTheme {
                    TransactionDetailsSheet(
                        item = FeedItem(
                            tx = TransactionEntity(
                                id = 1,
                                accountId = 1,
                                amountMinor = -1_270,
                                currency = "GEL",
                                occurredAt = 1_000,
                                rawCounterparty = "Courtyard Coffee",
                                status = TxStatus.PENDING,
                                source = TxSource.SMS,
                            ),
                            merchant = null,
                            category = null,
                            account = null,
                            cardHint = null,
                            day = LocalDate.of(2026, 9, 1),
                        ),
                        onDismiss = {},
                        onChangeCategory = {},
                        onDelete = null,
                        onCorrect = {},
                        onEdit = null,
                        onDebt = {},
                        onClearDebt = null,
                        onSplit = {},
                        onClearSplit = null,
                            onConfirm = {},
                        onOwnTransfer = {},
                    )
                }
            }
        }

        listOf(
            R.string.transaction_mark_reviewed,
            R.string.debt_action_short,
            R.string.split_action_short,
        ).forEach { label ->
            compose.onNode(hasText(context.getString(label))).assertIsDisplayed()
        }
        // The long repair label belongs to the overflow, not to the row of everyday answers.
        compose.onNode(hasText(context.getString(R.string.transaction_correct))).assertDoesNotExist()
    }
}
