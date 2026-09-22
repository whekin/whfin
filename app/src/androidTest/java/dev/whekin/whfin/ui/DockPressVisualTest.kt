package dev.whekin.whfin.ui

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class DockPressVisualTest {
    @Test fun lightPressHasNoHighlight() = checkPress(false)
    @Test fun darkPressHasNoHighlight() = checkPress(true)
    private fun checkPress(dark: Boolean) {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val context = instrumentation.targetContext
        val output = File(context.getExternalFilesDir(null), "dock-press").apply { mkdirs() }
        ActivityScenario.launch<DockQaActivity>(Intent(context, DockQaActivity::class.java).putExtra("dark", dark)).use { scenario ->
            assertNotNull(device.wait(Until.findObject(By.res("dock-transactions")), 10000))
            for (tag in listOf("dock-transactions", "dock-add")) {
                device.waitForIdle(1000); SystemClock.sleep(300)
                val rect = device.findObject(By.res(tag)).visibleBounds
                fun capture(): Bitmap {
                    val full = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                    return Bitmap.createBitmap(full, rect.left, rect.top, rect.width(), rect.height()).also { full.recycle() }
                }
                val before = capture()
                val downTime = SystemClock.uptimeMillis()
                fun touch(action: Int) {
                    val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, rect.exactCenterX(), rect.exactCenterY(), 0)
                    event.source = InputDevice.SOURCE_TOUCHSCREEN
                    try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
                }
                touch(MotionEvent.ACTION_DOWN)
                try {
                    SystemClock.sleep(250)
                    val held = capture()
                    File(output, "$dark-$tag-held.png").outputStream().use { held.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    assertTrue("Press painted a highlight for $tag", before.sameAs(held))
                    held.recycle()
                } finally { touch(MotionEvent.ACTION_UP); before.recycle() }
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { assertEquals(1, it.selectedTab); assertEquals(1, it.addRequests) }
        }
    }
}
