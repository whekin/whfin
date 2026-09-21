package dev.whekin.whfin.ui.setup

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
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
        compose.onNodeWithText(context.getString(R.string.setup_next)).performClick()
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
}
