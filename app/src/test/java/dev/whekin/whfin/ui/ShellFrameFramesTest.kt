package dev.whekin.whfin.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.platform.testTag
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Writes the frames of a section change to `build/shell-frames/`, with the clock held still.
 *
 * The middle of a 200ms transition is the only place the shell's motion can be judged, and nothing
 * on a device could reach it: the emulator's screen recorder returns an empty stream, `screencap` in
 * a loop takes longer per frame than the transition lasts, and the Compose input dispatcher does not
 * start on this API level. Stepping the clock by hand gives the same frames on every machine.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShellFrameFramesTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun framesOfARootChange() {
        var target by mutableStateOf(ShellTarget(ShellScene.Home))
        compose.setContent {
            WhfinTheme(darkTheme = true) {
                ShellFrame(target = target, dockSelection = 0f, onSelectRoot = {}, onAdd = {}) { shell ->
                    val tint = if (shell.scene == ShellScene.Home) Color(0xFF1E3A2E) else Color(0xFF3A2A1E)
                    Box(
                        Modifier.fillMaxSize().background(tint).testTag("pane"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(shell.scene.name, style = MaterialTheme.typography.displaySmall)
                    }
                }
            }
        }
        compose.waitForIdle()
        save("00-resting")

        compose.mainClock.autoAdvance = false
        target = ShellTarget(ShellScene.Analytics)
        repeat(6) { step ->
            compose.mainClock.advanceTimeBy(32)
            save("%02d-mid".format(step + 1))
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        save("99-settled")
    }

    private fun save(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = File(System.getProperty("whfin.frames.dir") ?: "build/shell-frames")
            .apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
