package dev.whekin.whfin.widget

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
                    onSave = { amount, _, _, _, categoryId ->
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
                    onSave = { amount, _, _, _, _ -> savedAmount = amount },
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
