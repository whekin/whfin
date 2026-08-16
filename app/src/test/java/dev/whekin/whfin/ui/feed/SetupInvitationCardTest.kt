package dev.whekin.whfin.ui.feed

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
class SetupInvitationCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun theOfferLeadsBackIntoSetup() {
        var resumed = false
        compose.setContent {
            WhfinTheme { SetupInvitationCard(onResume = { resumed = true }, onDismiss = {}) }
        }

        compose.onNodeWithText(context.getString(R.string.home_setup_invitation_action))
            .assertIsDisplayed()
            .performClick()

        assertTrue(resumed)
    }

    /** An offer that cannot be turned off for good is a demand. */
    @Test
    fun theOfferCanBeRefusedOutright() {
        var dismissed = false
        compose.setContent {
            WhfinTheme { SetupInvitationCard(onResume = {}, onDismiss = { dismissed = true }) }
        }

        compose.onNodeWithContentDescription(context.getString(R.string.home_setup_invitation_dismiss))
            .assertIsDisplayed()
            .performClick()

        assertTrue(dismissed)
    }
}
