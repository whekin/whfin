package dev.whekin.whfin.ui.feed

import android.content.Intent
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class BankConnectionVisualTest {
    @Test fun syncEnglishLight() = render("sync-en")
    @Test fun syncRussianDarkLarge() = render("sync-ru-dark-large", "ru", true, 1.5f)
    @Test fun syncRussianCompact() = render("sync-ru-compact", "ru", font = 1.5f, compact = true)
    @Test fun accountEnglishLight() = render("account-en", account = true)
    @Test fun accountRussianDarkLarge() = render("account-ru-dark-large", "ru", true, 1.5f, true)
    private fun render(name: String, language: String = "en", dark: Boolean = false, font: Float = 1f, account: Boolean = false, compact: Boolean = false) {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val previousSize = device.executeShellCommand("wm size").lineSequence().firstOrNull { it.startsWith("Override size:") }
            ?.substringAfter(":")?.trim()?.takeIf { it.matches(Regex("[0-9]+x[0-9]+")) }
        if (compact) device.executeShellCommand("wm size 1280x1500")
        val previousFont = device.executeShellCommand("settings get system font_scale").trim()
        device.executeShellCommand("settings put system font_scale $font")
        android.os.SystemClock.sleep(700)
        try {
        ActivityScenario.launch<BankConnectionQaActivity>(Intent(context, BankConnectionQaActivity::class.java).apply {
            putExtra("language", language); putExtra("dark", dark); putExtra("fontScale", font); putExtra("account", account)
        }).use {
            if (compact) UiScrollable(UiSelector().scrollable(true)).scrollIntoView(UiSelector().description("Credo"))
            val credo = device.wait(Until.findObject(if (account) By.text("Credo") else By.desc("Credo")), 10000)
            assertNotNull(credo)
            if (account) {
                credo.click()
                assertTrue(device.wait(Until.hasObject(By.text(if (language == "ru") "Подключить банк" else "Connect bank")), 5000))
                assertTrue(device.wait(Until.gone(By.clazz("android.widget.EditText")), 5000))
            }
            device.waitForIdle(1500)
            val dir = File(context.getExternalFilesDir(null), "bank-connection-qa").apply { mkdirs() }
            assertTrue(device.takeScreenshot(File(dir, "$name.png")))
            if (account) {
                device.findObject(By.text(if (language == "ru") "Без подключения — создать вручную" else "Skip connection, create manually")).click()
                assertTrue(device.wait(Until.hasObject(By.clazz("android.widget.EditText")), 5000))
                device.findObject(By.text("TBC")).click()
                assertTrue(device.wait(Until.gone(By.clazz("android.widget.EditText")), 5000))
                device.findObject(By.text(if (language == "ru") "Подключить банк" else "Connect bank")).click()
                assertTrue(device.wait(Until.hasObject(By.text("Connected: TBC")), 5000))
            } else if (compact) {
                UiScrollable(UiSelector().scrollable(true)).scrollIntoView(UiSelector().text("TBC"))
                assertTrue(device.takeScreenshot(File(dir, "$name-scrolled.png")))
                device.findObject(By.text("TBC")).click()
                assertTrue(device.wait(Until.hasObject(By.text("Connected: TBC")), 5000))
            } else {
                credo.click()
                assertTrue(device.wait(Until.hasObject(By.text("Connected: Credo")), 5000))
            }
        }
        } finally {
            device.executeShellCommand("settings put system font_scale ${previousFont.toFloatOrNull() ?: 1f}")
            if (compact) device.executeShellCommand("wm size ${previousSize ?: "reset"}")
        }
    }
}
