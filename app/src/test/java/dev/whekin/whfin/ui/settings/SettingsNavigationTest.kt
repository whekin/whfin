package dev.whekin.whfin.ui.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.mutableStateOf
import org.junit.Test
import org.junit.Assert.*

class SettingsNavigationTest {
    private fun state() = SettingsSearchState(ScrollState(0), mutableStateOf(""))
    @Test fun bankBackGoesThroughConnectionsToRoot() {
        val state = state()
        state.open("connections"); state.open("bank:TBC")
        assertTrue(state.back()); assertEquals("connections", state.page)
        assertTrue(state.back()); assertEquals("", state.page)
        assertFalse(state.back())
    }
    @Test fun searchResultReturnsToTheQueryAndFreshEntryResetsIt() {
        val state = state()
        state.query = "TBC push"; state.open("bank:TBC")
        assertEquals("", state.query)
        assertTrue(state.back()); assertEquals("TBC push", state.query)
        assertEquals("", state.page)
        state.open("bank:CREDO"); state.reset()
        assertEquals("", state.query); assertFalse(state.back())
    }
    @Test fun searchStartsHiddenAndCanBeDismissed() {
        val state = state()
        assertFalse(state.searchVisible)
        state.reveal(focusKeyboard = false)
        assertTrue(state.searchVisible); assertEquals(0, state.request)
        assertTrue(state.back()); assertFalse(state.searchVisible)
    }
    @Test fun searchFromChildRevealsGlobalSearch() {
        val state = state(); state.open("data"); state.reveal()
        assertEquals("", state.page)
        assertTrue(state.request > 0)
    }
}
