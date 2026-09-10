package dev.whekin.whfin.ui.accounts

import android.content.Intent
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import java.util.Locale
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Renders the account shapes the demo fixture does not contain: three currencies in one strip, a
 * five-figure balance inside a narrow cell, and the fall back to stacked rows at a large font scale.
 *
 * Stateless UI only: no Room writes, restore, permission changes or bank actions.
 */
class AccountCardsVisualTest {
    @Test fun threeCurrenciesLight() = render("accounts-3cur-light")
    @Test fun threeCurrenciesDark() = render("accounts-3cur-dark", dark = true)
    @Test fun largeRussianFallsBackToRows() = render("accounts-3cur-ru-large", dark = true, large = true)

    @Test fun activityAndBalanceSheet() = activityJourney("en", false, 1f)
    @Test fun activityRussianLarge() = activityJourney("ru", true, 1.5f)

    private fun activityJourney(language: String, dark: Boolean, font: Float) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val res = context.createConfigurationContext(android.content.res.Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }).resources
        val previousLocale = Locale.getDefault()
        val previousFont = device.executeShellCommand("settings get system font_scale").trim()
        val previousIme = device.executeShellCommand("settings get secure show_ime_with_hard_keyboard").trim()
        device.executeShellCommand("settings put system font_scale $font")
        device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1")
        val intent = Intent(context, AccountsQaActivity::class.java).putExtra("activity", true)
            .putExtra("language", language).putExtra("dark", dark).putExtra("fontScale", font)
        try { ActivityScenario.launch<AccountsQaActivity>(intent).use {
            assertNotNull(device.wait(Until.findObject(By.textContains("0001")), 10000))
            device.waitForIdle(1000)
            org.junit.Assert.assertEquals(1, device.findObjects(By.textContains("0001")).size)
            val out = File(context.getExternalFilesDir(null), "activity-qa").apply { mkdirs() }
            fun capture(name: String) {
                device.waitForIdle(1000); android.os.SystemClock.sleep(300)
                device.takeScreenshot(File(out, "$language-$name.png"))
                device.dumpWindowHierarchy(File(out, "$language-$name.xml"))
            }
            capture("activity")
            device.findObject(By.descContains(res.getString(dev.whekin.whfin.R.string.account_edit))).click()
            assertNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5000))
            capture("edit")
            device.pressBack()
            device.findObject(By.descContains(res.getString(dev.whekin.whfin.R.string.account_actions))).click()
            val correct = res.getString(dev.whekin.whfin.R.string.opening_correct_action)
            assertNotNull(device.wait(Until.findObject(By.text(correct)), 5000))
            capture("menu")
            device.findObject(By.text(correct)).click()
            val field = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5000)
            assertNotNull(field); field.click(); field.text = "0"
            val ime = device.executeShellCommand("settings get secure default_input_method").trim().substringBefore('/')
            assertTrue(device.wait(Until.hasObject(By.pkg(ime)), 10000))
            android.os.SystemClock.sleep(700)
            capture("correction-ime")
            if (device.hasObject(By.pkg(ime))) device.pressBack()
            capture("correction")
            device.findObject(By.text(res.getString(dev.whekin.whfin.R.string.action_save))).click()
            assertTrue(device.wait(Until.gone(By.clazz("android.widget.EditText")), 5000))
            assertNotNull(device.wait(Until.findObject(By.textContains(if (language == "ru") "0,00" else "0.00")), 5000))
            capture("corrected")
        } } finally {
            Locale.setDefault(previousLocale)
            device.executeShellCommand("settings put system font_scale ${previousFont.takeUnless { it == "null" } ?: "1.0"}")
            if (previousIme == "null") device.executeShellCommand("settings delete secure show_ime_with_hard_keyboard")
            else device.executeShellCommand("settings put secure show_ime_with_hard_keyboard $previousIme")
        }
    }

    private fun render(name: String, dark: Boolean = false, large: Boolean = false) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        check(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish") { "Disposable emulator only" }
        val previousLocale = Locale.getDefault()
        val intent = Intent(context, AccountsQaActivity::class.java).apply {
            putExtra("dark", dark)
            putExtra("language", if (large) "ru" else "en")
            putExtra("fontScale", if (large) 1.5f else 1f)
        }
        try {
            ActivityScenario.launch<AccountsQaActivity>(intent).use {
                // The imported name carries nothing of its own, so the card is named by its number.
                assertNotNull(device.wait(Until.findObject(By.textContains("0001")), 10_000))
                device.waitForIdle(2_000)
                android.os.SystemClock.sleep(400)
                val directory = File(context.getExternalFilesDir(null), "accounts-qa").apply { mkdirs() }
                assertTrue(device.takeScreenshot(File(directory, "$name.png")))
                device.dumpWindowHierarchy(File(directory, "$name.xml"))
                // Every currency of the strip is present, including the empty one.
                listOf("GEL", "EUR", "USD").forEach { currency ->
                    assertNotNull(currency, device.findObject(By.text(currency)))
                }
                // The widest balance a cell has to hold is printed, not dropped for want of room.
                assertNotNull(device.findObject(By.textContains("488")))
                // Colour never carries the warning alone: a cell says it in its description, a row
                // in its supporting line.
                val status = if (large) "Очень мало" else "Very low"
                assertTrue(
                    status,
                    device.hasObject(By.descContains(status)) || device.hasObject(By.textContains(status)),
                )
            }
        } finally {
            Locale.setDefault(previousLocale)
        }
    }
}
