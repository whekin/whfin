package dev.whekin.whfin.widget

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.R
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.CategoryKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QuickExpenseCategoryTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun expenseSavesWithSelectedCategory() {
        val app = ApplicationProvider.getApplicationContext<Application>() as WhfinApp
        val db = app.userDb
        val categoryId = runBlocking {
            db.categoryDao().insert(
                CategoryEntity(name = "QuickTestCat", kind = CategoryKind.EXPENSE, icon = "ShoppingCart", color = 1),
            )
        }
        val context = ApplicationProvider.getApplicationContext<Application>()

        ActivityScenario.launch(QuickExpenseActivity::class.java).use {
            compose.waitUntil(5_000) {
                compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
            }
            // Ряд категорий подгружается асинхронно после первого кадра.
            compose.waitUntil(5_000) {
                compose.onAllNodes(
                    androidx.compose.ui.test.hasContentDescription("QuickTestCat"),
                ).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("QuickTestCat").assertIsDisplayed().performClick()
            compose.onAllNodes(hasSetTextAction())[0].performTextInput("5")
            compose.onNodeWithText(context.getString(R.string.action_save)).performClick()

            val saved = runBlocking {
                val deadline = System.currentTimeMillis() + 5_000
                while (System.currentTimeMillis() < deadline) {
                    val feed = db.transactionDao().observeFeed(limit = 10).first()
                    feed.firstOrNull { it.amountMinor == -500L }?.let { return@runBlocking it }
                    Thread.sleep(50)
                }
                fail("Quick expense was not saved")
                error("unreachable")
            }
            assertEquals(categoryId, saved.categoryId)
        }
    }
}
