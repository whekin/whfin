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
    /** A result page is longer than the screen; a target below the fold is still a visible target. */
    private fun scrollTo(device: UiDevice, text: String): androidx.test.uiautomator.UiObject2 {
        repeat(12) {
            device.findObject(By.text(text))?.let { return it }
            device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
                device.displayWidth / 2, device.displayHeight / 3, 25)
            device.waitForIdle(400)
        }
        return requireNotNull(device.findObject(By.text(text))) { "Expected \"$text\" somewhere on the page" }
    }

    @Test fun englishLight() = render("en-light")
    @Test fun russianDarkLarge() = render("ru-dark-large", "ru", true, 1.5f)
    @Test fun historyReadDetailsRussianLarge() = render("read-details-ru", "ru", true, 1.5f, stage = "Connected")
    @Test fun codeRussian() = render("code-ru", "ru", stage = "Code")
    @Test fun codeEnglishDarkLarge() = render("code-en-dark-large", dark = true, font = 1.5f, stage = "Code")
    @Test fun connectedEnglishDark() = render("connected-en", dark = true, stage = "Connected")
    @Test fun initialStatementRussianLarge() = render("initial-ru-large", "ru", font = 1.5f, stage = "Connected", initial = true)
    @Test fun initialBalancesRussianDarkCompact() {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val oldSize = Regex("Override size: (\\d+x\\d+)").find(device.executeShellCommand("wm size"))?.groupValues?.get(1)
        device.executeShellCommand("wm size 1200x1920")
        try { render("initial-ru-dark-compact", "ru", true, 1.5f, stage = "Connected", initial = true) }
        finally { device.executeShellCommand("wm size ${oldSize ?: "reset"}") }
    }
    @Test fun manualBalanceEnglish() = render("manual-balance-en", stage = "Connected", initial = true)
    @Test fun syncResultEnglish() = render("result-en", stage = "Connected", rich = true)
    @Test fun syncResultRussianDarkLarge() = render("result-ru-dark-large", "ru", true, 1.5f, stage = "Connected", rich = true)
    @Test fun errorRussian() = render("error-ru", "ru", error = "LOGIN")
    @Test fun savedEnglishLight() = render("saved-en", saved = true)
    @Test fun savedRussianDarkLarge() = render("saved-ru-dark-large", "ru", true, 1.5f, saved = true)
    @Test fun expiredRussian() = render("expired-ru", "ru", error = "SESSION")
    @Test fun keyboardAndSyntheticLoginJourney() = render("ime-journey", journey = true)
    private fun render(name: String, language: String = "en", dark: Boolean = false, font: Float = 1f,
        stage: String = "Login", error: String? = null, journey: Boolean = false, initial: Boolean = false,
        saved: Boolean = false, rich: Boolean = false) {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val intent = Intent(context, TbcLoginQaActivity::class.java).apply {
            putExtra("language", language); putExtra("dark", dark); putExtra("fontScale", font)
            putExtra("stage", stage); putExtra("error", error); putExtra("initial", initial); putExtra("saved", saved)
            putExtra("rich", rich)
        }
        val previousIme = device.executeShellCommand("settings get secure show_ime_with_hard_keyboard").trim()
        if (journey || initial) device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1")
        try {
        ActivityScenario.launch<TbcLoginQaActivity>(intent).use {
            assertNotNull(device.wait(Until.findObject(By.text(if (language == "ru") "Подключение TBC" else "TBC connection")), 10000))
            device.waitForIdle(1500)
            val out = File(context.getExternalFilesDir(null), "tbc-login-qa").apply { mkdirs() }
            if (rich) {
                // What the owner owes an answer to is on screen without scrolling, and the balance
                // it wants is already filled in from the bank's own figure.
                val attention = if (language == "ru") "Требует решения" else "Needs you"
                val accounts = if (language == "ru") "Счета" else "Accounts"
                assertNotNull(device.wait(Until.findObject(By.textContains(if (language == "ru") "Остаток 1 из" else "Balance 1 of")), 5000))
                assertTrue(device.hasObject(By.text("1287.40")))
                assertTrue(device.hasObject(By.textContains(if (language == "ru") "Проведённый остаток" else "Booked balance")))
                scrollTo(device, if (language == "ru") "Другие варианты" else "Other options").click()
                scrollTo(device, if (language == "ru") "Подробности загрузки" else "Read details").click()
                // The deposit listing is WHFIN's own word, so it is read in the reader's language.
                assertNotNull(scrollTo(device, if (language == "ru") "Депозиты" else "Deposits"))
                scrollTo(device, accounts)
                assertTrue(device.takeScreenshot(File(out, "$name-accounts.png")))
            }
            if (stage == "Connected" && !initial) {
                val details = if (language == "ru") "Подробности загрузки" else "Read details"
                scrollTo(device, details).click()
                assertNotNull(device.wait(Until.findObject(By.textContains(if (language == "ru") "пустую первую страницу" else "empty first page")), 5000))
                assertNotNull(scrollTo(device, if (rich) {
                    if (language == "ru") "Продолжить без истории банка" else "Continue without bank history"
                } else if (language == "ru") "Готово" else "Done"))
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
            device.dumpWindowHierarchy(File(out, "$name.xml"))
            if (initial && language == "ru") {
                scrollTo(device, "Проверить следующий остаток")
                assertTrue(device.takeScreenshot(File(out, "$name-actions.png")))
                repeat(4) { step ->
                    scrollTo(device, "Проверить следующий остаток").click()
                    assertNotNull(device.wait(Until.findObject(By.text("Остаток ${step + 2} из 5")), 5000))
                }
                assertNotNull(device.wait(Until.findObject(By.text("Сохранить и загрузить")), 5000))
                device.waitForIdle(700)
                assertTrue(device.takeScreenshot(File(out, "$name-final-balance.png")))
                scrollTo(device, "Другие варианты").click()
                scrollTo(device, "Настроить позже").click()
                assertNotNull(device.wait(Until.findObject(By.text("Оставить эти счета на потом?")), 5000))
                assertTrue(device.takeScreenshot(File(out, "$name-confirm-exit.png")))
            }
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
                assertFalse(device.hasObject(By.text("Done")))
                val field = device.findObject(By.clazz("android.widget.EditText"))
                assertNotNull(field)
                field.click(); field.text = "0"
                val ime = device.executeShellCommand("settings get secure default_input_method").trim().substringBefore('/')
                assertTrue(device.wait(Until.hasObject(By.pkg(ime)), 10000))
                device.waitForIdle(1000)
                val next = device.findObject(By.text("Check next balance"))
                assertNotNull("Next balance must be pinned above the IME", next)
                assertTrue(next.visibleBounds.height() >= 40)
                assertTrue(device.takeScreenshot(File(out, "$name-keyboard.png")))
                device.pressBack()
                repeat(4) { step ->
                    val next = device.wait(Until.findObject(By.text("Check next balance")), 5000)
                    assertNotNull(next); next.click()
                    assertNotNull(device.wait(Until.findObject(By.text("Balance ${step + 2} of 5")), 5000))
                }
                assertNotNull(device.wait(Until.findObject(By.text("Save and load history")), 5000))
                device.waitForIdle(700)
                assertTrue(device.takeScreenshot(File(out, "$name-final-balance.png")))
                device.findObject(By.clazz("android.widget.EditText")).click()
                assertTrue(device.wait(Until.hasObject(By.pkg(ime)), 10000))
                val finalAction = device.wait(Until.findObject(By.text("Save and load history")), 5000)
                assertNotNull("Final save action must be pinned above the IME", finalAction)
                assertTrue(finalAction.visibleBounds.height() >= 40)
                device.waitForIdle(700)
                assertTrue(device.takeScreenshot(File(out, "$name-final-keyboard.png")))
                device.pressBack()
                device.findObject(By.text("Previous")).click()
                assertNotNull(device.wait(Until.findObject(By.text("Check next balance")), 5000))
                device.findObject(By.text("Check next balance")).click()
                val confirmBalances = device.wait(Until.findObject(By.text("Save and load history")), 5000)
                assertNotNull(confirmBalances); confirmBalances.click()
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
