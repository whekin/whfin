package dev.whekin.whfin.ui.setup

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.whekin.whfin.R
import java.io.File
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class PersonalSetupJourneyTest {
    @Test fun englishLight() = journey("en", false, 1f)
    @Test fun englishDark() = journey("en", true, 1f)
    @Test fun russianLarge() = journey("ru", true, 1.5f)

    private fun journey(language: String, dark: Boolean, scale: Float) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
        SetupQaActivity.language = language; SetupQaActivity.dark = dark; SetupQaActivity.fontScale = scale
        val resources = context.createConfigurationContext(android.content.res.Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }).resources
        val output = File(context.getExternalFilesDir(null), "setup-qa").apply { mkdirs() }
        fun text(id: Int) = resources.getString(id)
        fun click(label: String) {
            var node = device.wait(Until.findObject(By.text(label)), 8000)
            if (node == null) {
                device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
                    device.displayWidth / 2, device.displayHeight / 3, 20)
                node = device.wait(Until.findObject(By.text(label)), 3000)
            }
            assertNotNull(label, node); node.click()
        }
        fun capture(name: String) {
            device.waitForIdle(1000)
            device.takeScreenshot(File(output, "$language-$dark-$scale-$name.png"))
            device.dumpWindowHierarchy(File(output, "$language-$dark-$scale-$name.xml"))
        }
        val previousIme = device.executeShellCommand("settings get secure show_ime_with_hard_keyboard").trim()
        device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1")
        try { ActivityScenario.launch<SetupQaActivity>(Intent(context, SetupQaActivity::class.java)).use { scenario ->
            assertTrue(device.wait(Until.hasObject(By.text(text(R.string.setup_banks_title))), 10000))
            capture("banks")
            click("TBC")
            assertNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 8000))
            capture("tbc")
            device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.text(text(R.string.setup_banks_title))), 5000))
            click(text(R.string.setup_next))
            click(text(R.string.tab_accounts))
            assertNotNull(device.wait(Until.findObject(By.desc(text(R.string.accounts_add))), 8000))
            capture("accounts")
            device.pressBack()
            click(text(R.string.setup_next))
            capture("categories")
            click(text(R.string.categories_title))
            assertTrue(device.wait(Until.hasObject(By.text(text(R.string.categories_expense))), 8000))
            device.pressBack()
            click(text(R.string.setup_next))
            click(text(R.string.income_sources_title))
            assertTrue(device.wait(Until.hasObject(By.text(text(R.string.income_sources_add))), 8000) ||
                device.hasObject(By.desc(text(R.string.income_sources_add))))
            capture("income")
            val addIncome = device.findObject(By.desc(text(R.string.income_sources_add)))
            if (addIncome != null) addIncome.click() else click(text(R.string.income_sources_add))
            val field = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 8000)
            assertNotNull(field)
            field.click()
            val keyboard = device.executeShellCommand("settings get secure default_input_method").trim().substringBefore('/')
            assertTrue(device.wait(Until.hasObject(By.pkg(keyboard)), 10000))
            val save = device.wait(Until.findObject(By.text(text(R.string.action_save))), 5000)
            assertNotNull(save)
            assertTrue("Save label must remain readable above the keyboard",
                save.visibleBounds.height() >= (14 * scale * resources.displayMetrics.density).toInt())
            capture("income-editor")
            device.pressBack()
            // If Back hid the keyboard, the editor still needs dismissing.
            if (device.hasObject(By.clazz("android.widget.EditText"))) device.pressBack()
            device.pressBack()
            click(text(R.string.setup_next))
            capture("plans")
            click(text(R.string.debts_title))
            assertTrue(device.wait(Until.hasObject(By.text(text(R.string.debts_title))), 8000))
            capture("debts")
            device.pressBack()
            click(text(R.string.setup_next))
            click(text(R.string.settings_application))
            assertTrue(device.wait(Until.hasObject(By.text(text(R.string.settings_theme_system))), 8000))
            capture("preferences")
            // Return directly to the setup stage that opened appearance.
            device.pressBack()
            click(text(R.string.setup_next))
            assertTrue(device.wait(Until.hasObject(By.text(text(R.string.setup_finish_title))), 5000))
            capture("ready")
            scenario.recreate()
            assertTrue(device.wait(Until.hasObject(By.text(text(R.string.setup_finish_title))), 8000))
            click(text(R.string.personal_setup_continue_action))
            assertTrue(device.wait(Until.hasObject(By.text("Setup finished")), 5000))
        } } finally {
            if (previousIme == "null") device.executeShellCommand("settings delete secure show_ime_with_hard_keyboard")
            else device.executeShellCommand("settings put secure show_ime_with_hard_keyboard $previousIme")
        }
    }
}
