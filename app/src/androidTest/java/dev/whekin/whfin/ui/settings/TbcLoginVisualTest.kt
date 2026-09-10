package dev.whekin.whfin.ui.settings

import android.content.Intent
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TbcLoginVisualTest {
    @Test fun englishLight() = render("en-light")
    @Test fun russianDarkLarge() = render("ru-dark-large", "ru", true, 1.5f)
    @Test fun historyReadDetailsRussianLarge() = render("read-details-ru", "ru", true, 1.5f, stage = "Connected")
    @Test fun codeRussian() = render("code-ru", "ru", stage = "Code")
    @Test fun codeEnglishDarkLarge() = render("code-en-dark-large", dark = true, font = 1.5f, stage = "Code")
    @Test fun connectedEnglishDark() = render("connected-en", dark = true, stage = "Connected")
    @Test fun initialStatementRussianLarge() = render("initial-ru-large", "ru", font = 1.5f, stage = "Connected", initial = true)
    @Test fun manualBalanceEnglish() = render("manual-balance-en", stage = "Connected", initial = true)
    @Test fun errorRussian() = render("error-ru", "ru", error = "LOGIN")
    @Test fun savedEnglishLight() = render("saved-en", saved = true)
    @Test fun savedRussianDarkLarge() = render("saved-ru-dark-large", "ru", true, 1.5f, saved = true)
    @Test fun expiredRussian() = render("expired-ru", "ru", error = "SESSION")
    @Test fun keyboardAndSyntheticLoginJourney() = render("ime-journey", journey = true)
    private fun render(name: String, language: String = "en", dark: Boolean = false, font: Float = 1f,
        stage: String = "Login", error: String? = null, journey: Boolean = false, initial: Boolean = false, saved: Boolean = false) {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val intent = Intent(context, TbcLoginQaActivity::class.java).apply {
            putExtra("language", language); putExtra("dark", dark); putExtra("fontScale", font)
            putExtra("stage", stage); putExtra("error", error); putExtra("initial", initial); putExtra("saved", saved)
        }
        val previousIme = device.executeShellCommand("settings get secure show_ime_with_hard_keyboard").trim()
        if (journey || initial) device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1")
        try {
        ActivityScenario.launch<TbcLoginQaActivity>(intent).use {
            assertNotNull(device.wait(Until.findObject(By.text(if (language == "ru") "Подключение TBC" else "TBC connection")), 10000))
            device.waitForIdle(1500)
            val out = File(context.getExternalFilesDir(null), "tbc-login-qa").apply { mkdirs() }
            if (stage == "Connected" && !initial) {
                val details = if (language == "ru") "Подробности загрузки" else "Read details"
                assertNotNull(device.wait(Until.findObject(By.text(details)), 5000))
                device.findObject(By.text(details)).click()
                assertNotNull(device.wait(Until.findObject(By.textContains(if (language == "ru") "пустую первую страницу" else "empty first page")), 5000))
            }
            if (saved) assertFalse(device.hasObject(By.clazz("android.widget.EditText")))
            if (journey) {
                val fields = device.findObjects(By.clazz("android.widget.EditText"))
                assertEquals(2, fields.size)
                fields[0].text = "example-user"
                fields[1].click(); fields[1].text = "example-credential"
                val imePackage = device.executeShellCommand("settings get secure default_input_method").trim().substringBefore('/')
                assertTrue("Software keyboard must be visible", device.wait(Until.hasObject(By.pkg(imePackage)), 10000))
                device.waitForIdle(1000)
                android.os.SystemClock.sleep(500)
                assertTrue(device.takeScreenshot(File(out, "$name-keyboard.png")))
                device.pressBack()
                val button = device.wait(Until.findObject(By.text("Sign in to TBC")), 5000)
                assertNotNull(button); button.click()
                assertNotNull(device.wait(Until.findObject(By.text("One-time code")), 5000))
                assertFalse(device.hasObject(By.clazz("android.widget.EditText")))
                repeat(4) { device.findObject(By.text("0")).click() }
                device.findObject(By.text("Confirm sign-in")).click()
                assertNotNull(device.wait(Until.findObject(By.text("TBC sign-in confirmed")), 5000))
            }
            android.os.SystemClock.sleep(400)
            assertTrue(device.takeScreenshot(File(out, "$name.png")))
            if (stage == "Code") {
                assertFalse(device.hasObject(By.clazz("android.widget.EditText")))
                if (!device.hasObject(By.text("0"))) {
                    androidx.test.uiautomator.UiScrollable(androidx.test.uiautomator.UiSelector().scrollable(true)).scrollTextIntoView("0")
                }
                repeat(4) { device.findObject(By.text("0")).click() }
                val confirm = if (language == "ru") "Подтвердить вход" else "Confirm sign-in"
                if (!device.hasObject(By.text(confirm))) {
                    androidx.test.uiautomator.UiScrollable(androidx.test.uiautomator.UiSelector().scrollable(true)).scrollTextIntoView(confirm)
                }
                device.waitForIdle(1000)
                android.os.SystemClock.sleep(300)
                assertTrue(device.takeScreenshot(File(out, "$name-filled.png")))
                device.findObject(By.text(confirm)).click()
                assertNotNull(device.wait(Until.findObject(By.text(if (language == "ru") "Вход в TBC подтверждён" else "TBC sign-in confirmed")), 5000))
            }
            if (saved) {
                device.findObject(By.text(if (language == "ru") "Продолжить с TBC" else "Continue to TBC")).click()
                assertNotNull(device.wait(Until.findObject(By.text(if (language == "ru") "Вход в TBC подтверждён" else "TBC sign-in confirmed")), 5000))
                assertFalse(device.hasObject(By.clazz("android.widget.EditText")))
            }
            if (initial && language == "en") {
                val field = device.findObject(By.clazz("android.widget.EditText"))
                assertNotNull(field)
                field.click(); field.text = "0"
                val ime = device.executeShellCommand("settings get secure default_input_method").trim().substringBefore('/')
                assertTrue(device.wait(Until.hasObject(By.pkg(ime)), 10000))
                device.waitForIdle(1000)
                assertTrue(device.takeScreenshot(File(out, "$name-keyboard.png")))
                device.pressBack()
                if (!device.hasObject(By.text("Confirm balance and load transactions"))) {
                    androidx.test.uiautomator.UiScrollable(androidx.test.uiautomator.UiSelector().scrollable(true))
                        .scrollTextIntoView("Confirm balance and load transactions")
                }
                device.findObject(By.text("Confirm balance and load transactions")).click()
                assertTrue(device.wait(Until.gone(By.clazz("android.widget.EditText")), 5000))
            }
        }
        } finally {
            if (journey || initial) {
                if (previousIme == "null") device.executeShellCommand("settings delete secure show_ime_with_hard_keyboard")
                else device.executeShellCommand("settings put secure show_ime_with_hard_keyboard $previousIme")
            }
        }
    }
}
