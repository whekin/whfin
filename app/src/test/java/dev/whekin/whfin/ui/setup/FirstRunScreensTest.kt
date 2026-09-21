package dev.whekin.whfin.ui.setup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.R
import dev.whekin.whfin.ui.theme.WhfinTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FirstRunScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun welcomeOffersExactlyTheTwoProductChoices() {
        var personal = false
        var demo = false
        compose.setContent {
            WhfinTheme {
                WelcomeChoiceScreen(
                    busy = false,
                    problem = null,
                    onSetUpPersonal = { personal = true },
                    onExploreDemo = { demo = true },
                    onExit = {},
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.welcome_personal_action))
            .assertIsDisplayed()
            .performClick()
        compose.runOnIdle {
            assertTrue(personal)
            assertFalse(demo)
        }
        compose.onNodeWithText(context.getString(R.string.welcome_demo_action)).performClick()
        compose.runOnIdle { assertTrue(demo) }
    }

}
