package dev.whekin.whfin.ui.savings

import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import dev.whekin.whfin.core.ui.WhfinSavingsBalanceChart
import dev.whekin.whfin.core.ui.WhfinSavingsBalancePoint
import dev.whekin.whfin.core.ui.WhfinTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

class SavingsChartMotionTest {
    private val durationScale = object : MotionDurationScale { override var scaleFactor = 1f }
    @get:Rule val compose = createComposeRule(effectContext = durationScale)

    @Test fun editsInterpolateAndRetargetToTheExactStaticGeometry() {
        val end = mutableStateOf(40_000L)
        val animate = mutableStateOf(true)
        compose.setContent {
            WhfinTheme { Surface {
                WhfinSavingsBalanceChart(listOf(
                    WhfinSavingsBalancePoint("Jan", 10_000, "100", position = 0),
                    WhfinSavingsBalancePoint("Feb", 20_000, "200", position = 31),
                    WhfinSavingsBalancePoint("Mar", end.value, end.value.toString(), isProjected = true, position = 59),
                ), Modifier.testTag("chart"), goalMinor = 60_000, selectedIndex = 2,
                    onPointSelected = {}, animateChanges = animate.value)
            } }
        }
        val original = pixels()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { end.value = 50_000 }
        compose.mainClock.advanceTimeBy(64)
        val moving = pixels()
        assertNotEquals(original, moving)
        // Another edit while moving must settle on the latest input, not the first request.
        compose.runOnIdle { end.value = 30_000 }
        compose.mainClock.advanceTimeBy(1500)
        val settled = pixels()
        assertNotEquals(moving, settled)
        compose.mainClock.advanceTimeBy(1500)
        assertEquals(settled, pixels())
        compose.runOnIdle { animate.value = false }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(settled, pixels())
    }

    @Test fun disabledMotionShowsTheFinalGeometryImmediately() {
        durationScale.scaleFactor = 0f
        val end = mutableStateOf(40_000L)
        val animate = mutableStateOf(true)
        compose.setContent {
            WhfinTheme { Surface {
                WhfinSavingsBalanceChart(listOf(
                    WhfinSavingsBalancePoint("Now", 10_000, "100"),
                    WhfinSavingsBalancePoint("Next", end.value, end.value.toString(), isProjected = true),
                ), Modifier.testTag("chart"), goalMinor = 60_000, animateChanges = animate.value)
            } }
        }
        val original = pixels()
        compose.runOnIdle { end.value = 50_000 }
        val changed = pixels()
        assertNotEquals(original, changed)
        compose.runOnIdle { animate.value = false }
        assertEquals(changed, pixels())
    }

    private fun pixels(): List<Color> {
        val pixels = compose.onNodeWithTag("chart").captureToImage().toPixelMap()
        return buildList {
            for (y in 0 until pixels.height step 2) for (x in 0 until pixels.width step 2) add(pixels[x, y])
        }
    }
}
