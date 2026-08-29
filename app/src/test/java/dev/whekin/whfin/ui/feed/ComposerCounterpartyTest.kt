package dev.whekin.whfin.ui.feed

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.CategoryKind
import dev.whekin.whfin.data.db.CounterpartyProfile
import dev.whekin.whfin.ui.theme.WhfinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Paying somebody in cash is the one case the ledger never learned from: the name went into a note
 * and the category was chosen by hand every time. These are the two halves of the answer — the name
 * is written on the row, and it brings its usual category with it without ever overruling a choice
 * the person has already made.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class ComposerCounterpartyTest {
    @get:Rule val compose = createComposeRule()

    private val cash = AccountEntity(id = 1, name = "Cash", type = AccountType.CASH, currency = "GEL")
    private val groceries = CategoryEntity(id = 3, name = "Groceries", kind = CategoryKind.EXPENSE, icon = "ShoppingCart", color = 0, sortOrder = 1)
    private val eatingOut = CategoryEntity(id = 7, name = "Eating out", kind = CategoryKind.EXPENSE, icon = "Restaurant", color = 0, sortOrder = 2)
    private val grocer = CounterpartyProfile(
        merchantId = 5,
        displayName = "Sunroom Grocer",
        categoryId = groceries.id,
        expenseCount = 9,
        incomeCount = 0,
        latestAt = System.currentTimeMillis(),
    )

    @Test
    fun theChosenNameIsSavedAndBringsTheCategoryItIsUsuallyFiledUnder() {
        var saved: ManualTransaction? = null
        content { saved = it }

        compose.onNodeWithText("Sunroom Grocer").performClick()
        compose.onNodeWithTag("composer-amount").performTextInput("12.50")
        compose.onNodeWithText("Save").performClick()

        compose.runOnIdle {
            assertEquals("Sunroom Grocer", saved?.counterparty)
            assertEquals(groceries.id, saved?.categoryId)
            assertEquals(-1250L, saved?.amountMinor)
        }
    }

    @Test
    fun aCategoryTheUserAlreadyChoseSurvivesPickingAName() {
        var saved: ManualTransaction? = null
        content { saved = it }

        compose.onNodeWithText("Eating out").performClick()
        compose.onNodeWithText("Sunroom Grocer").performClick()
        compose.onNodeWithTag("composer-amount").performTextInput("12.50")
        compose.onNodeWithText("Save").performClick()

        compose.runOnIdle {
            assertEquals("Sunroom Grocer", saved?.counterparty)
            assertEquals(eatingOut.id, saved?.categoryId)
        }
    }

    /** Naming who was paid is an offer; an offer that cannot be taken back is a trap. */
    @Test
    fun tappingTheChosenNameAgainClearsIt() {
        var saved: ManualTransaction? = null
        content { saved = it }

        compose.onNodeWithText("Sunroom Grocer").performClick()
        compose.onNodeWithText("Sunroom Grocer").performClick()
        compose.onNodeWithTag("composer-amount").performTextInput("12.50")
        compose.onNodeWithText("Save").performClick()

        compose.runOnIdle { assertEquals(null, saved?.counterparty) }
    }

    private fun content(onSave: (ManualTransaction) -> Unit) {
        compose.setContent {
            WhfinTheme {
                AddTransactionSheet(
                    accounts = listOf(cash),
                    categories = listOf(groceries, eatingOut),
                    people = emptyList(),
                    onDismiss = {},
                    onSave = onSave,
                    onSaveDebt = {},
                    counterparties = listOf(grocer),
                )
            }
        }
    }
}
