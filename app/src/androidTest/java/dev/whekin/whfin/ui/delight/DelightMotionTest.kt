package dev.whekin.whfin.ui.delight

import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.whekin.whfin.core.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DelightMotionTest {
    private val duration = object : MotionDurationScale { override var scaleFactor = 1f }
    @get:Rule val compose = createComposeRule(effectContext = duration)

    @Test fun catRespondsToLongPressThenReturnsToRest_withoutReplayingBusyTaps() = cat(1f)
    @Test fun catIsVisibleButDoesNotMoveWhenAnimationsAreDisabled() = cat(0f)
    private fun cat(scale: Float) {
        duration.scaleFactor = scale
        compose.setContent { WhfinTheme { Surface {
            WhfinCuriousCat("Illustration", "Call cat", Modifier.testTag("scene"))
        } } }
        val idle = pixels()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("scene").performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        compose.mainClock.advanceTimeBy(if (scale == 0f) 300 else 900)
        assertNotEquals(idle, pixels())
        val out = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "delight-qa").apply { mkdirs() }
        File(out, "cat-scale-$scale.png").outputStream().use {
            compose.onNodeWithTag("scene").captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithTag("scene").performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        compose.mainClock.advanceTimeBy(6000)
        assertEquals(idle, pixels())
    }

    @Test fun directionMovesOnceAndRestartsOnlyForANewPair() {
        val visible = mutableStateOf(false)
        val pair = mutableStateOf(1 to 2)
        compose.setContent { WhfinTheme { Surface {
            if (visible.value) WhfinTransferDirection(pair.value, Modifier.testTag("scene"))
        } } }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { visible.value = true }
        compose.mainClock.advanceTimeByFrame()
        val first = pixels()
        compose.mainClock.advanceTimeBy(1500)
        val settled = pixels()
        assertNotEquals(first, settled)
        compose.mainClock.advanceTimeBy(1500)
        assertEquals(settled, pixels())
        compose.runOnIdle { pair.value = 2 to 1 }
        compose.mainClock.advanceTimeByFrame()
        assertNotEquals(settled, pixels())
    }
    private fun pixels(): List<Color> {
        val image = compose.onNodeWithTag("scene", useUnmergedTree = true).captureToImage().toPixelMap()
        return buildList { for (y in 0 until image.height step 2) for (x in 0 until image.width step 2) add(image[x,y]) }
    }
}
