package dev.whekin.whfin.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.whekin.whfin.ui.theme.WhfinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LedgerDockTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun selectionChangesLocallyWithoutMovingTheItems() {
        var selected by mutableIntStateOf(0)
        compose.setContent {
            WhfinTheme {
                LedgerDock(selected) { selected = it }
            }
        }

        compose.onNodeWithTag("dock-feed").assertIsSelected()
        compose.onNodeWithTag("dock-accounts").assertIsNotSelected().performClick()
        compose.onNodeWithTag("dock-feed").assertIsNotSelected()
        compose.onNodeWithTag("dock-accounts").assertIsSelected()
        assertEquals(1, selected)
    }
}
