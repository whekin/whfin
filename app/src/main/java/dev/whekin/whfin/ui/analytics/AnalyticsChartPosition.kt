package dev.whekin.whfin.ui.analytics

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** Keep the control the owner used stationary when optional sections above it appear/disappear. */
internal class AnalyticsChartPosition(private val list: LazyListState, private val key: String) {
    private data class Anchor(var applied: AnalyticsPeriod, val to: AnalyticsPeriod, val offset: Int)
    private var pending: Anchor? = null

    fun change(from: AnalyticsPeriod, to: AnalyticsPeriod, chartAction: Boolean = false, action: () -> Unit) {
        val periodControlsVisible = list.layoutInfo.visibleItemsInfo.any { it.key == "analytics-period" && it.offset + it.size > 0 }
        pending = if ((!chartAction && periodControlsVisible) || (from == to && pending == null) || to.start > java.time.YearMonth.now(dev.whekin.whfin.data.LedgerCalendar.zone)) null else list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
            ?.let { Anchor(from, to, it.offset) }
        action()
    }

    fun restore(model: AnalyticsUiModel, index: Int) {
        val anchor = pending ?: return
        val content = model.state as? AnalyticsUiState.Content ?: return
        if (content.data.period != model.period) return
        if (model.period != anchor.applied) {
            list.requestScrollToItem(index, -anchor.offset)
            anchor.applied = model.period
        }
        // A calculation already in flight may arrive between two fast taps. Keep the requested
        // viewport through that intermediate snapshot until the latest destination is ready.
        if (model.period == anchor.to) pending = null
    }
}

@Composable
internal fun rememberAnalyticsChartPosition(list: LazyListState, model: AnalyticsUiModel, key: String, index: Int): AnalyticsChartPosition {
    val anchor = remember(list, key) { AnalyticsChartPosition(list, key) }
    // requestScrollToItem takes effect in the upcoming measure, not a visible frame after it.
    anchor.restore(model, index)
    return anchor
}
