package dev.whekin.whfin.ui.demo

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.whekin.whfin.ui.theme.WhfinTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DemoWorkspaceUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun workspaceStrip_keepsPersonalExitVisible() {
        var exited = false
        compose.setContent {
            WhfinTheme {
                DemoWorkspaceProvider(
                    active = true,
                    busy = false,
                    problem = null,
                    onUsePersonal = { exited = true },
                ) {
                    DemoWorkspaceFrame { Text("Destination") }
                }
            }
        }

        compose.onNodeWithText("Demo workspace").assertIsDisplayed()
        compose.onNodeWithText("Synthetic data").assertIsDisplayed()
        compose.onNodeWithText("Use my data").assertIsDisplayed().performClick()
        assertTrue(exited)
    }

    @Test
    fun personalWorkspace_hasNoDemoChrome() {
        compose.setContent {
            WhfinTheme {
                DemoWorkspaceProvider(
                    active = false,
                    busy = false,
                    problem = null,
                    onUsePersonal = {},
                ) {
                    DemoWorkspaceFrame { Text("Personal destination") }
                }
            }
        }

        compose.onNodeWithText("Personal destination").assertIsDisplayed()
        compose.onNodeWithText("Demo workspace").assertDoesNotExist()
        compose.onNodeWithText("Use my data").assertDoesNotExist()
    }
}
