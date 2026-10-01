package dev.whekin.whfin.ui.settings

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.R
import dev.whekin.whfin.data.db.*
import dev.whekin.whfin.ui.theme.WhfinTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h850dp")
class CategoryDraftTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val home = CategoryEntity(id = 1, name = "Home", kind = CategoryKind.EXPENSE, icon = "Home", color = 0, sortOrder = 0)
    private val coffee = home.copy(id = 2, name = "Coffee", icon = "Restaurant", sortOrder = 1)

    @Test fun groupIsADraftAndCloseRequiresExplicitDiscard() {
        var writes = 0
        var closed = false
        compose.setContent { WhfinTheme { EditCategorySheet(CategoryRow(coffee, 2), listOf(CategoryRow(home, 1), CategoryRow(coffee, 2)),
            { closed = true }, { _, _, _, _, _, _ -> writes++ }, {}) } }
        compose.onNodeWithText("Home").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, writes) }
        compose.onNodeWithContentDescription(context.getString(dev.whekin.whfin.core.ui.R.string.whfin_close)).performClick()
        compose.onNodeWithText(context.getString(R.string.form_discard_title)).assertExists()
        compose.runOnIdle { assertFalse(closed) }
        compose.onNodeWithText(context.getString(R.string.action_cancel)).performClick()
        compose.onNodeWithText(context.getString(R.string.action_save)).performClick()
        compose.runOnIdle { assertEquals(1, writes) }
    }

    @Test fun positionAndNameAreSubmittedTogether() {
        var payload: Pair<String, Int>? = null
        compose.setContent { WhfinTheme { EditCategorySheet(CategoryRow(coffee, 2), listOf(CategoryRow(home, 1), CategoryRow(coffee, 2)),
            {}, { _, name, _, _, _, move -> payload = name to move }, {}) } }
        compose.onNode(hasSetTextAction()).performTextReplacement("Cafe")
        compose.onNodeWithText(context.getString(R.string.categories_move_up)).performScrollTo().performClick()
        compose.runOnIdle { assertNull(payload) }
        compose.onNodeWithText(context.getString(R.string.action_save)).performClick()
        compose.runOnIdle { assertEquals("Cafe" to -1, payload) }
    }

    @Test fun changedDefinitionIsNotSilentlyOverwritten() {
        val row = mutableStateOf(CategoryRow(coffee, 2))
        compose.setContent { WhfinTheme { EditCategorySheet(row.value, listOf(CategoryRow(home, 1), row.value), {},
            { _, _, _, _, _, _ -> error("Do not overwrite a changed category") }, {}) } }
        compose.onNode(hasSetTextAction()).performTextReplacement("My coffee")
        compose.runOnIdle { row.value = row.value.copy(category = coffee.copy(name = "Updated elsewhere")) }
        compose.onNodeWithText(context.getString(R.string.action_save)).assertIsNotEnabled()
        compose.onNodeWithText("My coffee").assertExists()
        compose.onNodeWithText(context.getString(R.string.category_changed)).assertExists()
    }
}
