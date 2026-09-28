package dev.whekin.whfin.ui.settings

import android.content.Intent
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import dev.whekin.whfin.R
import dev.whekin.whfin.data.sms.BankSmsBank
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.util.Locale

class SettingsHierarchyVisualTest {
    @Test fun englishHierarchy() = run("en", false, 1f)
    @Test fun russianLargeHierarchy() = run("ru", true, 1.5f)
    private fun run(language: String, dark: Boolean, font: Float) {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val i = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(i)
        val context = i.targetContext
        val resources = context.createConfigurationContext(android.content.res.Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }).resources
        val oldFont = device.executeShellCommand("settings get system font_scale").trim()
        device.executeShellCommand("settings put system font_scale $font")
        try { ActivityScenario.launch<SettingsQaActivity>(Intent(context, SettingsQaActivity::class.java).putExtra("language", language).putExtra("dark", dark)).use { activity ->
            assertNotNull(device.wait(Until.findObject(By.desc("settings-qa-ready")), 10000))
            val out = File(context.getExternalFilesDir(null), "settings-hierarchy").apply { mkdirs() }
            fun capture(name: String) { device.waitForIdle(1000); Thread.sleep(300); device.takeScreenshot(File(out, "$language-$name.png")); device.dumpWindowHierarchy(File(out, "$language-$name.xml")) }
            capture("root")
            listOf(R.string.settings_connections, R.string.settings_accounting, R.string.settings_application, R.string.settings_data_security, R.string.about_title).forEach {
                assertNotNull(device.findObject(By.text(resources.getString(it))))
            }
            assertFalse(device.hasObject(By.desc(resources.getString(R.string.settings_bank_sms))))
            device.findObject(By.text(resources.getString(R.string.settings_connections))).click()
            assertNotNull(device.wait(Until.findObject(By.res("connection-TBC")), 5000))
            assertNotNull(device.findObject(By.desc(resources.getString(R.string.settings_all_bank_sms_action))))
            capture("connections")
            device.findObject(By.res("connection-TBC")).click()
            assertNotNull(device.wait(Until.findObject(By.desc(resources.getString(R.string.settings_bank_push))), 5000))
            val update = resources.getString(R.string.settings_update_operations)
            device.findObject(By.text(update)).click()
            activity.onActivity { assertEquals(BankSmsBank.TBC, it.syncedBank) }
            if (!device.hasObject(By.res("connection-account-2"))) UiScrollable(UiSelector().scrollable(true)).scrollTextIntoView("USD")
            capture("tbc")
            device.findObject(By.res("connection-account-2")).click()
            activity.onActivity { assertEquals(2L, it.openedAccount) }
            device.findObject(By.desc(resources.getString(R.string.action_back))).click()
            assertNotNull(device.wait(Until.findObject(By.res("connection-CREDO")), 5000))
            device.findObject(By.res("connection-CREDO")).click()
            assertNotNull(device.wait(Until.findObject(By.desc(resources.getString(R.string.settings_bank_sms))), 5000))
            assertFalse(device.hasObject(By.desc(resources.getString(R.string.settings_bank_push))))
            capture("credo")
            device.findObject(By.desc(resources.getString(R.string.settings_search_hint))).click()
            val search = device.wait(Until.findObject(By.clazz("android.widget.EditText").focused(true)), 5000)
            assertNotNull(search); search.text = "TBC push"
            val result = device.wait(Until.findObject(By.res("settings-row-tbc-push")), 5000)
            capture("search")
            assertNotNull(result); result.click()
            assertNotNull(device.wait(Until.findObject(By.desc(resources.getString(R.string.settings_bank_push))), 5000))
            device.pressBack()
            assertNotNull(device.wait(Until.findObject(By.text("TBC push")), 5000))
        } } finally { device.executeShellCommand("settings put system font_scale ${oldFont.takeUnless { it == "null" } ?: "1.0"}") }
    }
}
