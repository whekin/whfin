package dev.whekin.whfin.ui.settings

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.whekin.whfin.ui.theme.WhfinTheme
import androidx.compose.runtime.*
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.R
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TbcSavedLoginScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun savedSessionDoesNotAskForPasswordAgain() {
        compose.setContent { WhfinTheme { TbcLoginScreen(TbcLoginState(hasSaved = true), true) } }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }
    @Test fun passwordFormRequiresAnExplicitChoiceAndCanReturnToSavedSignIn() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent { WhfinTheme { TbcLoginScreen(TbcLoginState(hasSaved = true), true) } }
        compose.onNodeWithText(context.getString(R.string.tbc_use_password)).performScrollTo().performClick()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(2)
        compose.onNodeWithText(context.getString(R.string.tbc_back_to_saved)).performScrollTo().performClick()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }
    @Test fun rememberLabelAndThumbShareOneSwitchTarget() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        var calls = 0
        compose.setContent {
            var checked by remember { mutableStateOf(false) }
            WhfinTheme { TbcLoginScreen(TbcLoginState(remember = checked), true,
                onRemember = { calls++; checked = it }) }
        }
        compose.onAllNodes(isToggleable()).assertCountEquals(1)
        compose.onNodeWithText(context.getString(R.string.tbc_remember)).performScrollTo().performClick()
        compose.onNode(isToggleable()).assertIsOn()
        assertEquals(1, calls)
    }

}
