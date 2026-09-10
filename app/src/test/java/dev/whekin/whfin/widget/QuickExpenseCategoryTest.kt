package dev.whekin.whfin.widget

import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.R
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.CategoryKind
import dev.whekin.whfin.ui.theme.WhfinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QuickExpenseCategoryTest {
    @get:Rule
    val compose = createComposeRule()

    @Test fun recipientWorksWithSystemKeyboardAndKeepsTheAmount() {
        var saved: Long? = null
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent { WhfinTheme {
            QuickExpenseScreen("GEL", "Cash", 7, emptyList(), null, quickExpenseKeypadEnabled = false,
                onDismiss = {}, people = listOf(dev.whekin.whfin.data.db.PersonEntity(id = 3, name = "Mira", color = 0)),
                onSave = { amount, _, _, _, _, share -> saved = amount; assertEquals(3L, share?.personId) })
        } }
        compose.onNodeWithTag("quick-expense-system-amount").performTextInput("12.50")
        compose.onNodeWithTag("quick-expense-beneficiary").performScrollTo().performClick()
        compose.onNodeWithText("Mira").performClick()
        compose.onNodeWithText(context.getString(R.string.action_done)).performClick()
        compose.onNodeWithTag("quick-expense-save").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1250L, saved) }
    }

    @Test fun recipientSelectionKeepsCalculatorAndSavesTheFinalHalfShare() {
        var savedAmount: Long? = null
        var savedShare: dev.whekin.whfin.data.mutation.ExpenseBeneficiary? = null
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent { WhfinTheme {
            QuickExpenseScreen("GEL", "Cash", 7, emptyList(), null,
                onDismiss = {}, people = listOf(dev.whekin.whfin.data.db.PersonEntity(id = 3, name = "Mira", color = 0)),
                onSave = { amount, _, _, _, _, share -> savedAmount = amount; savedShare = share })
        } }
        compose.onNodeWithTag("whfin-amount-key-DIGIT_5").performScrollTo().performClick()
        compose.onNodeWithTag("quick-expense-beneficiary").performScrollTo().performClick()
        compose.onNodeWithText("Mira").performClick()
        compose.onNodeWithText(context.getString(R.string.split_half)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.action_done)).performClick()
        org.junit.Assert.assertNull(savedAmount)
        compose.onNodeWithTag("whfin-amount-key-DIGIT_0").performScrollTo().performClick()
        compose.onNodeWithTag("quick-expense-save").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(5000L, savedAmount)
            assertEquals(3L, savedShare?.personId)
            assertEquals(2500L, savedShare?.shareMinor)
        }
    }

    @Test
    fun expenseSavesWithSelectedCategory() {
        val category = CategoryEntity(
            id = 42,
            name = "QuickTestCat",
            kind = CategoryKind.EXPENSE,
            icon = "ShoppingCart",
            color = 1,
        )
        var savedAmount: Long? = null
        var savedCategory: Long? = null
        compose.setContent {
            WhfinTheme {
                QuickExpenseScreen(
                    initialCurrency = "GEL",
                    sourceLabel = "Cash",
                    sourceAccountId = null,
                    categories = listOf(category),
                    suggester = null,
                    onDismiss = {},
                    onSave = { amount, _, _, _, categoryId, _ ->
                        savedAmount = amount
                        savedCategory = categoryId
                    },
                )
            }
        }
        compose.onNodeWithContentDescription("QuickTestCat").assertIsDisplayed().performClick()
        compose.onNodeWithTag("whfin-amount-key-DIGIT_5").performScrollTo().performClick()
        compose.onAllNodes(hasContentDescription("5 GEL", substring = true))[0].fetchSemanticsNode()
        compose.onNodeWithTag("quick-expense-save")
            .assertIsEnabled()
            .performScrollTo()
            .performClick()
        compose.waitForIdle()

        compose.runOnIdle {
            assertEquals(500L, savedAmount)
            assertEquals(category.id, savedCategory)
        }
    }

    @Test
    fun systemKeyboardModeReplacesCalculatorAndSavesTypedAmount() {
        var savedAmount: Long? = null
        compose.setContent {
            WhfinTheme {
                QuickExpenseScreen(
                    initialCurrency = "GEL",
                    sourceLabel = "Cash",
                    sourceAccountId = null,
                    categories = emptyList(),
                    suggester = null,
                    quickExpenseKeypadEnabled = false,
                    onDismiss = {},
                    onSave = { amount, _, _, _, _, _ -> savedAmount = amount },
                )
            }
        }

        compose.onNodeWithTag("whfin-amount-key-DIGIT_5").assertDoesNotExist()
        compose.onNodeWithTag("quick-expense-system-amount")
            .assertIsDisplayed()
            .performTextInput("12,50")
        compose.onNodeWithTag("quick-expense-save")
            .assertIsEnabled()
            .performScrollTo()
            .performClick()

        compose.runOnIdle { assertEquals(1_250L, savedAmount) }
    }
}
