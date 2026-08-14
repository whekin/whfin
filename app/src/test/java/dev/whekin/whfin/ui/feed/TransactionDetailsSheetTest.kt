package dev.whekin.whfin.ui.feed

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
        compose.onNode(hasText("7.62 €", substring = true)).assertIsDisplayed()
        compose.onNode(hasText("0.00 ₾", substring = true)).assertDoesNotExist()
    }

    @Test
    fun smsTransactionShowsProvenanceWithoutAStatusTask() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val sms = context.getString(R.string.status_sms)
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
                    onChangeStatus = { error("SMS provenance must not open a status task") },
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
                    onChangeStatus = {},
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
                    onChangeStatus = {},
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
                    onChangeStatus = {},
                )
            }
        }

        compose.onNode(hasText(noDescription)).assertDoesNotExist()
        compose.onNode(hasContentDescription(actions)).performClick()
        compose.onNode(hasText(deleteTransaction)).assertIsDisplayed()
    }
}
