package dev.whekin.whfin.ui.feed

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
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h850dp")
class TransactionCategoryPickerTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val item = FeedItem(TransactionEntity(id = 1, accountId = 1, amountMinor = -2113, currency = "GEL", occurredAt = 0,
        status = TxStatus.MANUAL, source = TxSource.MANUAL), null, null, null, null, day = LocalDate.of(2026, 9, 1))
    private val categories = listOf(CategoryEntity(id = 1, name = "Home", kind = CategoryKind.EXPENSE, icon = "Home", color = 0),
        CategoryEntity(id = 2, name = "Rent", parentId = 1, kind = CategoryKind.EXPENSE, icon = "Home", color = 0))

    @Test fun emptySearchExplainsTheResultAndCanBeCleared() {
        compose.setContent { WhfinTheme { CategoryPickerSheet(item, categories, {}, {}, { _, _, _, _ -> }) } }
        compose.onNode(hasSetTextAction()).performTextInput("zzzz")
        compose.onNodeWithText(context.getString(R.string.category_search_empty)).assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.search_clear)).performClick()
        compose.onNodeWithText("Home · Rent").assertExists()
    }

    @Test fun parentAndChildWordsFindTheQualifiedCategory() {
        var selected = 0L
        compose.setContent { WhfinTheme { CategoryPickerSheet(item, categories, {}, { selected = it.id }, { _, _, _, _ -> }) } }
        compose.onNode(hasSetTextAction()).performTextInput("home rent")
        compose.onNodeWithText("Home · Rent").performClick()
        assertEquals(2L, selected)
    }
}
