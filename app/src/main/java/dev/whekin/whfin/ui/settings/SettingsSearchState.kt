package dev.whekin.whfin.ui.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.WhfinIconButton

@Stable
class SettingsSearchState internal constructor(val scroll: ScrollState, private val text: MutableState<String>, private val location: MutableState<String> = mutableStateOf(""), private val returnQuery: MutableState<String> = mutableStateOf(""), private val revealed: MutableState<Boolean> = mutableStateOf(false)) {
    var query: String by text
    var page: String by location
    var searchVisible: Boolean by revealed
    fun reset() { searchVisible = false; page = ""; query = ""; returnQuery.value = "" }
    fun open(page: String) {
        if (query.isNotBlank()) returnQuery.value = query
        query = ""; searchVisible = false; this.page = page
    }
    fun back(): Boolean {
        if (returnQuery.value.isNotBlank()) { searchVisible = true; page = ""; query = returnQuery.value; returnQuery.value = ""; return true }
        if (query.isNotBlank()) { query = ""; return true }
        if (page.isBlank()) { if (searchVisible) { searchVisible = false; return true }; return false }
        page = if (page.startsWith("bank:") || page == "add-bank") "connections" else ""
        return true
    }
    internal val focus = FocusRequester()
    internal var fieldHeight by mutableIntStateOf(0)
    internal var topPadding by mutableIntStateOf(0)
    internal var request by mutableIntStateOf(0)
    internal var handledRequest = 0
    val collapsed: Boolean get() = fieldHeight > 0 && scroll.value >= fieldHeight + topPadding
    fun reveal(focusKeyboard: Boolean = true) { searchVisible = true; page = ""; returnQuery.value = ""; if (focusKeyboard) request++ }
}

@Composable
fun rememberSettingsSearchState(): SettingsSearchState {
    val scroll = rememberScrollState()
    val text = rememberSaveable { mutableStateOf("") }
    val location = rememberSaveable { mutableStateOf("") }
    val returnQuery = rememberSaveable { mutableStateOf("") }
    val revealed = rememberSaveable { mutableStateOf(false) }
    return remember(scroll, text, location, returnQuery) { SettingsSearchState(scroll, text, location, returnQuery, revealed) }
}

@Composable
internal fun SettingsSearchAction(state: SettingsSearchState) {
    val collapsed by remember(state) { derivedStateOf { !state.searchVisible || state.collapsed || state.page.isNotBlank() } }
    // Reserved space keeps the title's width/height unchanged when the icon appears.
    Box(Modifier.size(48.dp)) {
        androidx.compose.animation.AnimatedVisibility(collapsed,
            enter = androidx.compose.animation.fadeIn(dev.whekin.whfin.core.ui.WhfinMotion.quick()),
            exit = androidx.compose.animation.fadeOut(dev.whekin.whfin.core.ui.WhfinMotion.quick())) {
            WhfinIconButton(Icons.Default.Search, stringResource(R.string.settings_search_hint), { state.reveal() }, outlined = false)
        }
    }
}

@Composable
internal fun settingsPageTitle(state: SettingsSearchState): String = when (state.page) {
    "connections" -> stringResource(R.string.settings_connections)
    "catalog" -> stringResource(R.string.settings_accounting)
    "app" -> stringResource(R.string.settings_application)
    "data" -> stringResource(R.string.settings_data_security)
    "about" -> stringResource(R.string.about_title)
    "add-bank" -> stringResource(R.string.settings_add_bank)
    "bank:TBC" -> "TBC"
    "bank:CREDO" -> "Credo"
    else -> stringResource(R.string.settings_title)
}
