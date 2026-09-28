package dev.whekin.whfin.ui.setup

import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import dev.whekin.whfin.core.ui.WhfinIllustration
import dev.whekin.whfin.core.ui.WhfinIllustrationScene
import dev.whekin.whfin.core.ui.WhfinLightColorScheme
import dev.whekin.whfin.core.ui.WhfinTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class IllustrationMotionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun settlesWithoutAnIdleLoop_andRecolorsWithTheActivePalette() {
        val palette = mutableStateOf(WhfinLightColorScheme)
        val visible = mutableStateOf(false)
        compose.setContent {
            WhfinTheme(colorScheme = palette.value) {
                Surface {
                    if (visible.value) WhfinIllustration(WhfinIllustrationScene.Gather,
                        Modifier.height(180.dp).testTag("illustration"))
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { visible.value = true }
        compose.mainClock.advanceTimeByFrame()
        val beginning = pixels()
        compose.mainClock.advanceTimeBy(2000)
        val settled = pixels()
        assertNotEquals(beginning, settled)
        compose.mainClock.advanceTimeBy(2000)
        assertEquals(settled, pixels())

        // A palette change while mounted must recolour the vector without replaying its geometry.
        val violet = Color(0xFF6543A5)
        compose.runOnIdle { palette.value = WhfinLightColorScheme.copy(primary = violet) }
        compose.mainClock.advanceTimeByFrame()
        val recolored = pixels()
        assertTrue(recolored.contains(violet))
        assertTrue(!recolored.contains(WhfinLightColorScheme.primary))
        compose.mainClock.advanceTimeBy(2000)
        assertEquals(recolored, pixels())
    }

    private fun pixels(): List<Color> {
        val image = compose.onNodeWithTag("illustration", useUnmergedTree = true).captureToImage().toPixelMap()
        return buildList {
            for (y in 0 until image.height step 3) for (x in 0 until image.width step 3) add(image[x, y])
        }
    }
}
