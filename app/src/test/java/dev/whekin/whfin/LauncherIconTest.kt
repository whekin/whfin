package dev.whekin.whfin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import kotlin.math.hypot
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The launcher foreground doubles as the monochrome layer, so its ink has to stay
 * inside the 66dp adaptive-icon safe zone and stay centred: every launcher mask
 * clips whatever leaves that circle.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherIconTest {

    @Test
    fun `launcher foreground ink stays inside the safe zone and stays centred`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val drawable = requireNotNull(
            ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)
        )

        val size = 108
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(Canvas(bitmap))

        var inked = 0
        var minX = size
        var minY = size
        var maxX = -1
        var maxY = -1
        var worstRadius = 0.0
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (Color.alpha(bitmap.getPixel(x, y)) < 128) continue
                inked++
                if (x < minX) minX = x
                if (y < minY) minY = y
                if (x > maxX) maxX = x
                if (y > maxY) maxY = y
                val radius = hypot(x + 0.5 - 54.0, y + 0.5 - 54.0)
                if (radius > worstRadius) worstRadius = radius
            }
        }

        assertTrue("the mark draws no ink", inked > 500)
        assertTrue("ink leaves the 66dp safe zone: r=$worstRadius", worstRadius <= 33.0)
        val centerX = (minX + maxX + 1) / 2.0
        val centerY = (minY + maxY + 1) / 2.0
        assertTrue("mark is off-centre horizontally: $centerX", kotlin.math.abs(centerX - 54.0) <= 1.5)
        assertTrue("mark is off-centre vertically: $centerY", kotlin.math.abs(centerY - 54.0) <= 2.5)
    }
}
