package dev.whekin.whfin.ui.feed

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.R
import dev.whekin.whfin.ui.theme.WhfinTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeNoticesFoldTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun collapsedRow_countsWhatItIsHidingAndExpandsOnTap() {
        var toggled = false
        val label = context.resources.getQuantityString(R.plurals.home_notices_folded, 3, 3)
        compose.setContent {
            WhfinTheme {
                HomeNoticesFold(count = 3, expanded = false, onToggle = { toggled = true })
            }
        }

        compose.onNodeWithText(label).assertIsDisplayed().performClick()
        assertTrue(toggled)
    }

    @Test
    fun expandedRow_offersTheWayBack() {
        compose.setContent {
            WhfinTheme {
                HomeNoticesFold(count = 3, expanded = true, onToggle = {})
            }
        }

        compose.onNodeWithText(context.getString(R.string.home_notices_collapse)).assertIsDisplayed()
    }
}
