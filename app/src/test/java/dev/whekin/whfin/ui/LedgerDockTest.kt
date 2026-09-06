package dev.whekin.whfin.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.whekin.whfin.ui.theme.WhfinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LedgerDockTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun selectionChangesLocallyWithoutMovingTheItems() {
        var selected by mutableIntStateOf(0)
        var addRequests = 0
        compose.setContent {
            WhfinTheme {
                // The dock reads a page position, so a settled tab is that page's whole number.
                LedgerDock(selected.toFloat(), onAdd = { addRequests += 1 }) { selected = it }
            }
        }

        val tags = listOf("dock-feed", "dock-transactions", "dock-accounts", "dock-analytics")
        tags.forEach { compose.onNodeWithTag(it).assertExists() }

        compose.onNodeWithTag("dock-feed").assertIsSelected()
        tags.drop(1).forEach { compose.onNodeWithTag(it).assertIsNotSelected() }

        // Each destination selects itself and nothing else: four peers, one mark.
        tags.forEachIndexed { index, tag ->
            compose.onNodeWithTag(tag).performClick()
            assertEquals(index, selected)
            compose.onNodeWithTag(tag).assertIsSelected()
            tags.filterNot { it == tag }.forEach { other ->
                compose.onNodeWithTag(other).assertIsNotSelected()
            }
        }

        // The create action is not a fifth destination and no longer dresses like one: it is a
        // button, announced by name, carrying no label of its own, and it never takes the selection.
        compose.onNodeWithTag("dock-analytics").assertIsSelected()
        compose.onNodeWithContentDescription("New transaction").assertExists()
        compose.onNodeWithTag("dock-add").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        compose.onNodeWithTag("dock-add").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
        compose.onNodeWithTag("dock-add").performClick()
        assertEquals(3, selected)
        compose.onNodeWithTag("dock-analytics").assertIsSelected()
        assertEquals(1, addRequests)
    }
}
