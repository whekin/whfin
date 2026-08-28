package dev.whekin.whfin.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

/**
 * A header that gets out of the way, for content the header is not part of.
 *
 * The screens with a metric at the top already do this through [WhfinContextHeader] and Material's
 * `enterAlways` behaviour. A search field is the same problem without a top bar to hang it on: it is
 * worth its height while you are looking for something and worth nothing while you are reading the
 * list, and on a long screen under a title bar and a status bar it was the third fixed strip before
 * any content began.
 *
 * The behaviour itself is the platform's, driven by the same [TopAppBarScrollBehavior] the rest of
 * WHFIN scrolls with, so a collapsing search field and a collapsing balance settle identically —
 * gone on the way down, back on the first movement up. [content] is handed the header's own height
 * as top padding and must apply it *inside* its scroll container, so the content starts below the
 * header and passes under it rather than being clipped by it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhfinCollapsingHeader(
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(),
    header: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    var headerHeight by remember { mutableIntStateOf(0) }
    // The limit is what the behaviour is allowed to hide, and only this layout knows it. Material's
    // own top bar sets it the same way, from the height it just measured.
    SideEffect { scrollBehavior.state.heightOffsetLimit = -headerHeight.toFloat() }
    val headerDp = with(LocalDensity.current) { headerHeight.toDp() }

    // Clipped, because a header that travels upward travels out of this layout and into whatever
    // sits above it — on Settings, straight over the screen's own title.
    Box(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
    ) {
        content(PaddingValues(top = headerDp))
        Box(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, scrollBehavior.state.heightOffset.roundToInt()) }
                .onSizeChanged { headerHeight = it.height },
        ) {
            header()
        }
    }
}
