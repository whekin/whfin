package dev.whekin.whfin.ui.accounts

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import dev.whekin.whfin.R
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Locale

class TransactionPolishVisualTest {
    @Test fun englishLight() = journey("en", false, 1f)
    @Test fun russianDarkLarge() = journey("ru", true, 1.5f)
    private fun journey(language: String, dark: Boolean, font: Float) {
        check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val oldFont = device.executeShellCommand("settings get system font_scale").trim()
        val oldLocale = Locale.getDefault()
        val oldIme = device.executeShellCommand("settings get secure show_ime_with_hard_keyboard").trim()
        device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1")
        val res = context.createConfigurationContext(android.content.res.Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }).resources
        val out = File(context.getExternalFilesDir(null), "transaction-polish").apply { mkdirs() }
        fun capture(name: String) { device.waitForIdle(1000); Thread.sleep(300)
            device.takeScreenshot(File(out, "$language-$name.png")); device.dumpWindowHierarchy(File(out, "$language-$name.xml")) }
        fun intent(mode: String) = Intent(context, AccountsQaActivity::class.java).putExtra(mode, true)
            .putExtra("language", language).putExtra("dark", dark).putExtra("fontScale", font)
        device.executeShellCommand("settings put system font_scale $font")
        try {
            ActivityScenario.launch<AccountsQaActivity>(intent("activity")).use { activity ->
                val balance = device.wait(Until.findObject(By.res("account-balance")), 10000)
                assertNotNull(balance); balance.click()
                assertNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5000))
                capture("balance-edit")
                device.pressBack()
                activity.onActivity { assertFalse(it.balanceAdjusted) }
                assertNotNull(device.wait(Until.findObject(By.res("account-balance")), 5000))
                device.findObject(By.res("account-balance")).click()
                val field = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5000)
                field.click(); field.text = "0"
                val save = device.wait(Until.findObject(By.text(res.getString(R.string.action_save)).enabled(true)), 5000)
                assertNotNull(save)
                capture("balance-zero-form")
                save.click()
                assertTrue(device.wait(Until.gone(By.clazz("android.widget.EditText")), 5000))
                activity.onActivity { assertTrue(it.balanceAdjusted) }
                capture("balance-zero")
            }
            ActivityScenario.launch<AccountsQaActivity>(intent("transaction")).use { activity ->
                val assign = res.getString(R.string.category_assign_hint)
                assertNotNull(device.wait(Until.findObject(By.text(assign)), 10000))
                capture("uncategorized")
                device.findObject(By.text(assign)).click()
                val search = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5000)
                assertNotNull(search); search.click(); search.text = if (language == "ru") "Каф" else "Cof"
                val ime = device.executeShellCommand("settings get secure default_input_method").trim().substringBefore('/')
                assertTrue(device.wait(Until.hasObject(By.pkg(ime)), 10000))
                capture("category-search")
                val category = if (language == "ru") "Кафе" else "Coffee"
                if (!device.hasObject(By.text(category))) UiScrollable(UiSelector().scrollable(true)).scrollTextIntoView(category)
                device.findObject(By.text(category)).click()
                assertNotNull(device.wait(Until.findObject(By.text(category)), 5000))
                activity.onActivity { assertTrue(it.categorySelected) }
                capture("categorized")
                if (!device.hasObject(By.text(category))) UiScrollable(UiSelector().scrollable(true)).scrollTextIntoView(category)
                device.findObject(By.text(category)).click()
                assertNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5000))
                device.pressBack()
                assertNotNull(device.wait(Until.findObject(By.text(category)), 5000))
            }
        } finally {
            Locale.setDefault(oldLocale)
            device.executeShellCommand("settings put system font_scale ${oldFont.toFloatOrNull() ?: 1f}")
            if (oldIme == "null") device.executeShellCommand("settings delete secure show_ime_with_hard_keyboard")
            else device.executeShellCommand("settings put secure show_ime_with_hard_keyboard $oldIme")
        }
    }
}
