package dev.whekin.whfin.ui.settings

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SettingsSearchVisualTest {
    @Test fun englishLight() = render("en-light", "en", false, 1f)
    @Test fun russianDarkLarge() = render("ru-dark-large", "ru", true, 1.5f)
    private fun render(name: String, language: String, dark: Boolean, font: Float) {
        check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val title = if (language == "ru") "Настройки" else "Settings"
        val hint = if (language == "ru") "Найти настройку" else "Find a setting"
        val previousFont = device.executeShellCommand("settings get system font_scale").trim()
        device.executeShellCommand("settings put system font_scale $font")
        android.os.SystemClock.sleep(700)
        val previous = device.executeShellCommand("settings get secure show_ime_with_hard_keyboard").trim()
        device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1")
        try {
        ActivityScenario.launch<SettingsQaActivity>(Intent(context, SettingsQaActivity::class.java).apply {
            putExtra("language", language); putExtra("dark", dark); putExtra("font", font)
        }).use {
            assertNotNull(device.wait(Until.findObject(By.desc("settings-qa-ready")), 10000))
            device.waitForIdle(1500)
            val heading = device.wait(Until.findObject(By.text(title)), 10000)
            assertNotNull(heading)
            val dir = File(context.getExternalFilesDir(null), "settings-search-qa").apply { mkdirs() }
            assertTrue(device.takeScreenshot(File(dir, "$name-expanded.png")))
            // Text accessibility ink bounds can change when a sibling appears. The layout owner
            // is the invariant that matters: its height must never move the catalogue.
            val height = device.findObject(By.res("secondary-topbar")).visibleBounds.height()
            assertFalse(device.hasObject(By.desc(hint)))
            val scroller = device.findObject(By.scrollable(true))
            scroller.scroll(Direction.DOWN, 0.8f)
            assertNotNull(device.wait(Until.findObject(By.desc(hint)), 5000))
            device.waitForIdle(1000)
            assertTrue(device.takeScreenshot(File(dir, "$name-collapsed.png")))
            assertEquals(height, device.findObject(By.res("secondary-topbar")).visibleBounds.height())
            assertTrue(device.takeScreenshot(File(dir, "$name-collapsed.png")))
            scroller.scroll(Direction.UP, 0.05f)
            assertTrue(device.hasObject(By.desc(hint)))
            device.findObject(By.desc(hint)).click()
            val field = device.wait(Until.findObject(By.clazz("android.widget.EditText").focused(true)), 10000)
            assertNotNull(field)
            field.text = "Credo"
            val ime = device.executeShellCommand("settings get secure default_input_method").trim().substringBefore('/')
            assertTrue(device.wait(Until.hasObject(By.pkg(ime)), 10000))
            assertFalse(device.hasObject(By.desc(hint)))
            assertEquals(height, device.findObject(By.res("secondary-topbar")).visibleBounds.height())
            device.waitForIdle(1000)
            assertTrue(device.takeScreenshot(File(dir, "$name-searching.png")))
        }
        } finally {
            device.executeShellCommand("settings put system font_scale ${previousFont.toFloatOrNull() ?: 1f}")
            if (previous == "null") device.executeShellCommand("settings delete secure show_ime_with_hard_keyboard")
            else device.executeShellCommand("settings put secure show_ime_with_hard_keyboard $previous")
        }
    }
}
