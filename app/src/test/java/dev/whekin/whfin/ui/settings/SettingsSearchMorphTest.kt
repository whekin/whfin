package dev.whekin.whfin.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import dev.whekin.whfin.core.ui.WhfinSearchHeader
import dev.whekin.whfin.ui.theme.WhfinTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h850dp")
class SettingsSearchMorphTest {
    @get:Rule val compose = createComposeRule()

    @Test fun closingDuringRevealCannotFocusALaterPull() {
        lateinit var state: SettingsSearchState
        compose.setContent { WhfinTheme {
            state = rememberSettingsSearchState()
            SettingsPage(state, {}) { androidx.compose.material3.Text("Catalogue") }
        } }
        compose.runOnIdle { state.reveal(); state.dragging = true; state.fraction = .4f }
        compose.runOnIdle { assertEquals(state.request, state.handledRequest) }
        compose.runOnIdle { state.searchVisible = false; state.dragging = false; state.fraction = 0f }
        compose.waitForIdle()
        compose.runOnIdle { state.reveal(focusKeyboard = false); state.dragging = true; state.fraction = 1f }
        compose.onNodeWithTag("settings-search").assertIsNotFocused()
    }

    @Test fun oneGlyphMovesContinuouslyInsideAnExpandingFieldWithoutMovingTheList() {
        val progress = mutableFloatStateOf(0f)
        compose.setContent { WhfinTheme {
            Column(Modifier.fillMaxSize()) {
                WhfinSearchHeader("Settings", "Find a setting", "Close", "Back", "", progress.floatValue,
                    remember { FocusRequester() }, {}, {}, {}, {}, {})
            }
        } }
        fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val closed = bounds("settings-search-morph")
        val header = bounds("secondary-topbar")
        val start = bounds("settings-search-glyph")
        compose.runOnIdle { progress.floatValue = .5f }
        val midway = bounds("settings-search-morph")
        val middle = bounds("settings-search-glyph")
        compose.onAllNodesWithTag("settings-search-glyph", useUnmergedTree = true).assertCountEquals(1)
        compose.runOnIdle { progress.floatValue = 1f }
        val open = bounds("settings-search-morph")
        val finish = bounds("settings-search-glyph")
        assertTrue(closed.width < midway.width && midway.width < open.width)
        assertTrue(start.center.x > middle.center.x && middle.center.x > finish.center.x)
        assertEquals(header.height, bounds("secondary-topbar").height)
        compose.onNodeWithTag("settings-search").assertExists()
    }
}
