package dev.whekin.whfin.ui

import android.os.Build
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import dev.whekin.whfin.ui.theme.WhfinTheme
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/**
 * A destination whose name is cut in half is not named at all.
 *
 * Four names share a phone's width with the create action, and at a large text size the Russian
 * ones are the first to run out of room. The verdict comes from the layout itself rather than from
 * a guess at glyph widths — but it has to be taken on a device: Robolectric stubs text measurement,
 * so the same assertion off-device reports overflow for "Home" at the ordinary size.
 */
class LedgerDockLabelsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test fun labelsFitAtTheOrdinaryTextSize() = assertLabelsFit(1f)
    @Test fun labelsFitAtOneAndAHalf() = assertLabelsFit(1.5f)
    @Test fun labelsFitAtDouble() = assertLabelsFit(2f)

    private fun assertLabelsFit(fontScale: Float) {
        check(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish") { "Disposable emulator only" }
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale = fontScale),
            ) {
                WhfinTheme { LedgerDock(selection = 0f, onAdd = {}, onSelect = {}) }
            }
        }
        listOf("dock-feed", "dock-transactions", "dock-accounts", "dock-analytics").forEach { tag ->
            val results = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag(tag, useUnmergedTree = true)
                .onChildren()
                .filterToOne(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult))
                .fetchSemanticsNode()
                .config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
            assertFalse("$tag label is cut off at font scale $fontScale", results.first().hasVisualOverflow)
        }
    }
}
