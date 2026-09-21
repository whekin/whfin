package dev.whekin.whfin.ui.settings

import androidx.compose.foundation.ScrollState
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.layout.fillMaxSize
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
    internal var fraction by mutableFloatStateOf(if (revealed.value) 1f else 0f)
    internal var dragging by mutableStateOf(false)
    fun reset() { dragging = false; fraction = 0f; searchVisible = false; page = ""; query = ""; returnQuery.value = "" }
    fun open(page: String) {
        dragging = false
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
    internal var request by mutableIntStateOf(0)
    internal var handledRequest = 0
    fun reveal(focusKeyboard: Boolean = true) { dragging = false; searchVisible = true; page = ""; returnQuery.value = ""; if (focusKeyboard) request++ }
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

@Composable
internal fun SettingsPage(state: SettingsSearchState, onBack: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
            SettingsSearchHeader(state, onBack)
            Box(Modifier.weight(1f)) { content() }
        }
    }
}

@Composable
internal fun SettingsSearchHeader(state: SettingsSearchState, onBack: () -> Unit) {
    val motion = dev.whekin.whfin.core.ui.WhfinMotion.standard<Float>()
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(state.searchVisible, state.dragging) {
        if (!state.dragging) androidx.compose.animation.core.animate(
            initialValue = state.fraction, targetValue = if (state.searchVisible) 1f else 0f, animationSpec = motion,
        ) { value, _ -> if (!state.dragging) state.fraction = value.coerceIn(0f, 1f) }
    }
    LaunchedEffect(state.searchVisible, state.dragging) {
        if (!state.searchVisible && !state.dragging) { focusManager.clearFocus(); keyboard?.hide() }
    }
    LaunchedEffect(state.request, state.searchVisible) {
        if (state.searchVisible && state.request > state.handledRequest) {
            state.handledRequest = state.request
            state.scroll.scrollTo(0)
            snapshotFlow { state.fraction }.first { it >= .999f }
            if (state.searchVisible) { state.focus.requestFocus(); keyboard?.show() }
        }
    }
    dev.whekin.whfin.core.ui.WhfinSearchHeader(
        title = settingsPageTitle(state), placeholder = stringResource(R.string.settings_search_hint),
        closeDescription = stringResource(R.string.feed_search_close), backDescription = stringResource(R.string.action_back),
        query = state.query, progress = state.fraction, focusRequester = state.focus,
        onQuery = { state.query = it }, onOpen = { state.reveal() },
        onClose = { state.query = ""; state.searchVisible = false }, onBack = onBack,
        onSearch = { keyboard?.hide(); focusManager.clearFocus() },
    )
}

@Composable
internal fun rememberSettingsSearchPull(state: SettingsSearchState): androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
    val travel = with(androidx.compose.ui.platform.LocalDensity.current) { 112.dp.toPx() }
    return remember(state, travel) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (source != androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput || state.query.isNotBlank()) return androidx.compose.ui.geometry.Offset.Zero
                if (available.y < 0 && state.fraction > 0f && state.scroll.value == 0) {
                    val used = available.y.coerceAtLeast(-state.fraction * travel)
                    state.dragging = true
                    state.fraction = (state.fraction + used / travel).coerceIn(0f, 1f)
                    return androidx.compose.ui.geometry.Offset(0f, used)
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
            override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput && available.y > 0 &&
                    state.page.isBlank() && state.scroll.value == 0 && state.query.isBlank() && state.fraction < 1f) {
                    val used = available.y.coerceAtMost((1f - state.fraction) * travel)
                    state.dragging = true
                    state.fraction = (state.fraction + used / travel).coerceIn(0f, 1f)
                    return androidx.compose.ui.geometry.Offset(0f, used)
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                if (state.dragging) { state.searchVisible = state.fraction >= .5f; state.dragging = false }
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }
}
