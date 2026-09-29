package dev.whekin.whfin.ui.setup

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import dev.whekin.whfin.R
import dev.whekin.whfin.ui.theme.WhfinTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PersonalSetupFlowTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun `choosing either bank opens its form without completing the stage`() {
        var chosen = ""
        var advanced = false
        compose.setContent { WhfinTheme {
            SetupStageScreen(SetupStage.Banks,
                listOf(SetupAction("Credo") { chosen = "Credo" }, SetupAction("TBC") { chosen = "TBC" }),
                {}, { advanced = true })
        } }
        compose.onNodeWithText("TBC").performClick()
        assertEquals("TBC", chosen)
        assertFalse(advanced)
        compose.onNodeWithText("Credo").performClick()
        assertEquals("Credo", chosen)
        assertFalse(advanced)
    }

    @Test fun `optional stage continues without creating anything`() {
        var opened = false
        var advanced = false
        compose.setContent { WhfinTheme {
            SetupStageScreen(SetupStage.Income, listOf(SetupAction("Add income") { opened = true }),
                {}, { advanced = true })
        } }
        compose.onNodeWithText(context.getString(R.string.setup_skip_income)).performClick()
        assertTrue(advanced)
        assertFalse(opened)
    }

    @Test fun `reviewing a stage on ready does not finish setup`() {
        var reviewed = false
        var finished = false
        compose.setContent { WhfinTheme {
            SetupStageScreen(SetupStage.Ready, listOf(SetupAction("Banks") { reviewed = true }),
                {}, { finished = true })
        } }
        compose.onNodeWithText("Banks").performClick()
        assertTrue(reviewed)
        assertFalse(finished)
        compose.onNodeWithText(context.getString(R.string.personal_setup_continue_action)).performClick()
        assertTrue(finished)
    }
    @Test fun primaryConfigurationAndPostponementHaveDifferentEffects() {
        var configured = 0
        var advanced = 0
        compose.setContent { WhfinTheme {
            SetupStageScreen(SetupStage.Income, emptyList(), {}, { advanced++ },
                primaryAction = SetupAction("Set up income") { configured++ })
        } }
        compose.onNodeWithText("Set up income").performClick()
        assertEquals(1, configured)
        assertEquals(0, advanced)
        compose.onNodeWithText(context.getString(R.string.setup_skip_income)).performClick()
        assertEquals(1, configured)
        assertEquals(1, advanced)
    }

    @Test fun advancedDestinationsAreAvailableOnDemand() {
        var opened = false
        compose.setContent { WhfinTheme {
            SetupStageScreen(SetupStage.Banks, listOf(SetupAction("TBC") {}), {}, {},
                additionalActions = listOf(SetupAction("Restore a backup") { opened = true }))
        } }
        compose.onNodeWithText("Restore a backup").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.setup_more_options)).performScrollTo().performClick()
        compose.onNodeWithText("Restore a backup").performScrollTo().performClick()
        assertTrue(opened)
    }

    @Test fun stepMapNavigatesWithoutCompletingSetup() {
        var finished = false
        compose.setContent {
            var stage by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(SetupStage.Banks) }
            WhfinTheme {
                SetupStageScreen(stage, emptyList(), {}, { finished = true }, onSelectStage = { stage = it })
            }
        }
        compose.onNodeWithText(context.getString(R.string.setup_guide)).performClick()
        compose.onNodeWithText("5. " + context.getString(R.string.setup_income_title)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.setup_skip_income)).assertIsDisplayed()
        assertFalse(finished)
    }

}
