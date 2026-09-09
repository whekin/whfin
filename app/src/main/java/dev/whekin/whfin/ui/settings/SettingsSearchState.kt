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
class SettingsSearchState internal constructor(val scroll: ScrollState, private val text: MutableState<String>) {
    var query: String by text
    internal val focus = FocusRequester()
    internal var fieldHeight by mutableIntStateOf(0)
    internal var topPadding by mutableIntStateOf(0)
    internal var request by mutableIntStateOf(0)
    internal var handledRequest = 0
    val collapsed: Boolean get() = fieldHeight > 0 && scroll.value >= fieldHeight + topPadding
    fun reveal() { request++ }
}

@Composable
fun rememberSettingsSearchState(): SettingsSearchState {
    val scroll = rememberScrollState()
    val text = rememberSaveable { mutableStateOf("") }
    return remember(scroll, text) { SettingsSearchState(scroll, text) }
}

@Composable
internal fun SettingsSearchAction(state: SettingsSearchState) {
    val collapsed by remember(state) { derivedStateOf { state.collapsed } }
    // Reserved space keeps the title's width/height unchanged when the icon appears.
    Box(Modifier.size(48.dp)) {
        androidx.compose.animation.AnimatedVisibility(collapsed,
            enter = androidx.compose.animation.fadeIn(dev.whekin.whfin.core.ui.WhfinMotion.quick()),
            exit = androidx.compose.animation.fadeOut(dev.whekin.whfin.core.ui.WhfinMotion.quick())) {
            WhfinIconButton(Icons.Default.Search, stringResource(R.string.settings_search_hint), state::reveal, outlined = false)
        }
    }
}
