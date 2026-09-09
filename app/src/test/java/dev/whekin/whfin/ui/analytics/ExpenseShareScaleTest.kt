package dev.whekin.whfin.ui.analytics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import dev.whekin.whfin.core.ui.WhfinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExpenseShareScaleTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sameShare_hasSameLength_despiteDifferentAmountWidths() {
        compose.setContent {
            WhfinTheme {
                Column(Modifier.width(400.dp)) {
                    listOf(100L, 10_000_000L).forEachIndexed { index, amount ->
                        SpendingCategoryRow(
                            value = AnalyticsCategoryValue(index.toLong(), "Category", null, null, amount),
                            scale = AnalyticsScale.MONTH,
                            totalMinor = amount * 2,
                            color = MaterialTheme.colorScheme.primary,
                            selected = false,
                            divider = false,
                            onClick = {},
                        )
                    }
                }
            }
        }
        fun bounds(part: String, id: Int) = compose
            .onNodeWithTag("expense-share-$part-$id", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val first = bounds("track", 0)
        val second = bounds("track", 1)
        assertEquals(first.left, second.left, .5f)
        assertEquals(first.width, second.width, .5f)
        assertEquals(bounds("fill", 0).width, bounds("fill", 1).width, .5f)
        assertEquals(first.width / 2, bounds("fill", 0).width, 1f)
    }
}
