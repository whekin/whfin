package dev.whekin.whfin.ui.feed

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
class SmsOnboardingCardTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun dismissAction_isAccessibleAndClickable() {
        var dismissed = false
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val description = context.getString(R.string.sms_onboarding_dismiss)
        compose.setContent {
            WhfinTheme {
                SmsOnboardingCard(onEnable = {}, onDismiss = { dismissed = true })
            }
        }

        compose.onNodeWithContentDescription(description).assertIsDisplayed().performClick()
        assertTrue(dismissed)
    }
}
