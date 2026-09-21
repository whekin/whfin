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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
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

    @Test fun russianLabelsFitAtDouble() = assertLabelsFit(2f, "ru")

    private fun assertLabelsFit(fontScale: Float, language: String = "en") {
        check(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish") { "Disposable emulator only" }
        compose.setContent {
            val context = androidx.compose.ui.platform.LocalContext.current
            val configuration = android.content.res.Configuration(context.resources.configuration).apply {
                setLocale(java.util.Locale.forLanguageTag(language))
            }
            CompositionLocalProvider(
                androidx.compose.ui.platform.LocalContext provides context.createConfigurationContext(configuration),
                androidx.compose.ui.platform.LocalConfiguration provides configuration,
                LocalDensity provides Density(LocalDensity.current.density, fontScale = fontScale),
            ) {
                WhfinTheme {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.align(Alignment.BottomCenter)) {
                            LedgerDock(selection = 0f, onAdd = {}, onSelect = {})
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val directory = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "dock-qa").apply { mkdirs() }
        val device = androidx.test.uiautomator.UiDevice.getInstance(instrumentation)
        device.wait(androidx.test.uiautomator.Until.hasObject(
            androidx.test.uiautomator.By.text(if (language == "ru") "Главная" else "Home")), 5000)
        android.os.SystemClock.sleep(350)
        device.takeScreenshot(java.io.File(directory, "$language-$fontScale.png"))
        listOf("dock-feed", "dock-transactions", "dock-accounts", "dock-analytics").forEach { tag ->
            val results = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag(tag, useUnmergedTree = true)
                .onChildren()
                .filterToOne(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult))
                .fetchSemanticsNode()
                .config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
            val result = results.first()
            val description = "$tag ${result.layoutInput.text} at scale $fontScale"
            assertFalse("$description loses height", result.didOverflowHeight)
            assertFalse("$description is ellipsized", result.isLineEllipsized(0))
            assertEquals("$description loses characters", result.layoutInput.text.length, result.getLineEnd(0, visibleEnd = true))
            // The cached paragraph keeps the full slot width while Text wraps to its glyphs:
            // hasVisualOverflow compares those two different boxes. Check the actual drawn line.
            val drawnWidth = result.getLineRight(0) - result.getLineLeft(0)
            assertTrue("$description drawn width $drawnWidth exceeds ${result.size.width}",
                drawnWidth <= result.size.width + 1f)
        }
    }
}
