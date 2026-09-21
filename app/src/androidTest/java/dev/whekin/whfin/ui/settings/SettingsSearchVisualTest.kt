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
    @Test fun englishDark() = render("en-dark", "en", true, 1f)
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
            assertTrue(device.hasObject(By.desc(hint)))
            assertFalse(device.hasObject(By.clazz("android.widget.EditText")))
            device.swipe(device.displayWidth / 2, device.displayHeight / 3, device.displayWidth / 2, device.displayHeight * 2 / 3, 35)
            assertNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5000))
            assertFalse(device.findObject(By.clazz("android.widget.EditText")).isFocused)
            device.takeScreenshot(File(dir, "$name-pulled.png"))
            device.swipe(device.displayWidth / 2, device.displayHeight * 2 / 3, device.displayWidth / 2, device.displayHeight / 3, 35)
            assertTrue(device.wait(Until.gone(By.clazz("android.widget.EditText")), 5000))
            assertTrue(device.hasObject(By.desc(hint)))
            device.takeScreenshot(File(dir, "$name-collapsed.png"))
            device.findObject(By.text(if (language == "ru") "Приложение" else "Application")).click()
            assertNotNull(device.wait(Until.findObject(By.desc(hint)), 5000))
            device.waitForIdle(1000)
            assertTrue(device.takeScreenshot(File(dir, "$name-application.png")))
            assertEquals(height, device.findObject(By.res("secondary-topbar")).visibleBounds.height())
            listOf(if (language == "ru") "Тёмная" else "Dark", if (language == "ru") "Светлая" else "Light", if (language == "ru") "Авто" else "System").forEachIndexed { index, label ->
                val choiceIndex = listOf(2, 1, 0)[index]
                val target = By.res("theme-choice-$choiceIndex")
                device.findObject(target).click()
                assertNotNull(device.wait(Until.findObject(target.checked(true)), 5000))
                device.waitForIdle(1000)
                android.os.SystemClock.sleep(300)
                device.takeScreenshot(File(dir, "$name-theme-$index.png"))
            }
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
