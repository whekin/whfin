package dev.whekin.whfin.ui.settings

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockKeypadVisualTest {
    @Test fun englishLight() = verify("en", 1f, false)
    @Test fun russianDarkLarge() = verify("ru", 1.5f, true)
    @Test fun russianCompactLarge() = verify("ru", 1.5f, true, compact = true)
    @Test fun wrongCodeKeepsUnlockKeypadStill() {
        AppLockQaActivity.language = "ru"
        AppLockQaActivity.scale = 1.5f
        AppLockQaActivity.dark = true
        AppLockQaActivity.gate = true
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val out = File(context.getExternalFilesDir(null), "pin-qa").apply { mkdirs() }
        try {
            ActivityScenario.launch<AppLockQaActivity>(Intent(context, AppLockQaActivity::class.java)).use {
                assertNotNull(device.wait(Until.findObject(By.text("WHFIN заблокирован")), 8000))
                val before = requireNotNull(device.findObject(By.text("1"))).visibleBounds.top
                assertTrue(device.takeScreenshot(File(out, "ru-unlock.png")))
                "1234".forEach { digit -> requireNotNull(device.findObject(By.text(digit.toString()))).click() }
                val error = device.wait(Until.findObject(By.textContains("Неверный код")), 8000)
                assertNotNull(error)
                val after = requireNotNull(device.findObject(By.text("1"))).visibleBounds.top
                assertTrue(device.takeScreenshot(File(out, "ru-unlock-error.png")))
                assertEquals(before, after)
                assertTrue("Error text must end above the keypad", error.visibleBounds.bottom < after)
            }
        } finally { AppLockQaActivity.gate = false }
    }

    private fun verify(language: String, scale: Float, dark: Boolean, compact: Boolean = false) {
        AppLockQaActivity.language = language
        AppLockQaActivity.scale = scale
        AppLockQaActivity.dark = dark
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val out = File(context.getExternalFilesDir(null), "pin-qa").apply { mkdirs() }
        val oldSize = Regex("Override size: (\\d+x\\d+)").find(device.executeShellCommand("wm size"))?.groupValues?.get(1)
        if (compact) device.executeShellCommand("wm size 1200x1920")
        try {
        ActivityScenario.launch<AppLockQaActivity>(Intent(context, AppLockQaActivity::class.java)).use {
            val first = if (language == "ru") "Создай код WHFIN" else "Create a WHFIN code"
            val repeat = if (language == "ru") "Повтори код" else "Repeat the code"
            assertNotNull(device.wait(Until.findObject(By.text(first)), 8000))
            device.waitForIdle(500)
            val before = requireNotNull(device.findObject(By.text("1"))).visibleBounds.top
            val prefix = if (compact) "$language-compact" else language
            assertTrue(device.takeScreenshot(File(out, "$prefix-create.png")))
            "1234".forEach { digit -> requireNotNull(device.findObject(By.text(digit.toString()))).click() }
            assertNotNull(device.wait(Until.findObject(By.text(repeat)), 8000))
            device.waitForIdle(500)
            val after = requireNotNull(device.findObject(By.text("1"))).visibleBounds.top
            assertTrue(device.takeScreenshot(File(out, "$prefix-repeat.png")))
            assertEquals(before, after)
            "4321".forEach { digit -> requireNotNull(device.findObject(By.text(digit.toString()))).click() }
            assertNotNull(device.wait(Until.findObject(By.text(first)), 8000))
            val mismatchTop = requireNotNull(device.findObject(By.text("1"))).visibleBounds.top
            assertTrue(device.takeScreenshot(File(out, "$prefix-mismatch.png")))
            assertEquals(before, mismatchTop)
        }
        } finally {
            if (compact) device.executeShellCommand("wm size ${oldSize ?: "reset"}")
        }
    }
}
