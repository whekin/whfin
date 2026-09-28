package dev.whekin.whfin.ui.feed

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.R
import dev.whekin.whfin.ui.theme.WhfinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeferredCategoryNoticeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun reviewAndLaterAreSeparateOwnerChoices() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var reviewed = 0
        var setAside = 0
        compose.setContent { WhfinTheme {
            DeferredCategoryNotice(2, { reviewed++ }, { setAside++ })
        } }
        compose.onNodeWithText(context.getString(R.string.home_category_review_action)).performClick()
        compose.onNodeWithText(context.getString(R.string.home_category_review_later)).performClick()
        assertEquals(1, reviewed)
        assertEquals(1, setAside)
    }
}
