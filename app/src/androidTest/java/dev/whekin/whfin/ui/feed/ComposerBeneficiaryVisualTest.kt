package dev.whekin.whfin.ui.feed

import android.content.Intent
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class ComposerBeneficiaryVisualTest {
    @Test fun quickExpense() = quickJourney(false)
    @Test fun quickExpenseLarge() = quickJourney(true)
    private fun quickJourney(large: Boolean) {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val i = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(i)
        val previousFont = device.executeShellCommand("settings get system font_scale").trim()
        device.executeShellCommand("settings put system font_scale ${if (large) 1.5 else 1.0}")
        try { ActivityScenario.launch<ComposerQaActivity>(Intent(i.targetContext, ComposerQaActivity::class.java)
            .putExtra("quick", true).putExtra("dark", large)).use { activity ->
            assertNotNull(device.wait(Until.findObject(By.text("GEL")), 10000))
            device.findObject(By.text("5")).click()
            val label = i.targetContext.getString(dev.whekin.whfin.R.string.expense_beneficiary)
            if (!device.hasObject(By.textStartsWith(label))) UiScrollable(UiSelector().scrollable(true)).scrollTextIntoView(label)
            device.findObject(By.textStartsWith(label)).click()
            assertNotNull(device.wait(Until.findObject(By.text("Mira")), 5000))
            device.findObject(By.text("Mira")).click()
            assertNotNull(device.wait(Until.findObject(By.text(i.targetContext.getString(dev.whekin.whfin.R.string.expense_beneficiary_full_hint))), 5000))
            device.findObject(By.text(i.targetContext.getString(dev.whekin.whfin.R.string.action_done))).click()
            val save = i.targetContext.getString(dev.whekin.whfin.R.string.action_save)
            assertNotNull(device.wait(Until.findObject(By.text("5")), 5000))
            if (!device.hasObject(By.text(save))) UiScrollable(UiSelector().scrollable(true)).scrollTextIntoView(save)
            device.waitForIdle(1000); android.os.SystemClock.sleep(400)
            device.takeScreenshot(File(i.targetContext.getExternalFilesDir(null), "quick-beneficiary-$large.png"))
            device.dumpWindowHierarchy(File(i.targetContext.getExternalFilesDir(null), "quick-beneficiary-$large.xml"))
            activity.onActivity { assertNull(it.result) }
            device.findObject(By.text(save)).click()
            assertNotNull(device.wait(Until.findObject(By.text("Saved expense")), 5000))
            activity.onActivity { assertEquals(-500L, it.result?.amountMinor); assertEquals(1L, it.result?.beneficiary?.personId) }
        } } finally { device.executeShellCommand("settings put system font_scale ${previousFont.takeUnless { it == "null" } ?: "1.0"}") }
    }
    @Test fun composer() = journey(false)
    @Test fun largeDarkComposer() = journey(true)
    private fun journey(large: Boolean) {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val i = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(i)
        val previousFont = device.executeShellCommand("settings get system font_scale").trim()
        val previousIme = device.executeShellCommand("settings get secure show_ime_with_hard_keyboard").trim()
        device.executeShellCommand("settings put system font_scale ${if (large) 1.5f else 1f}")
        device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1")
        try { ActivityScenario.launch<ComposerQaActivity>(Intent(i.targetContext, ComposerQaActivity::class.java).putExtra("dark", large)).use { activity ->
            assertNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 10000))
            val ime = device.executeShellCommand("settings get secure default_input_method").trim().substringBefore('/')
            if (device.wait(Until.hasObject(By.pkg(ime)), 3000)) device.pressBack()
            device.waitForIdle(1000)
            val out = File(i.targetContext.getExternalFilesDir(null), "composer-qa").apply { mkdirs() }
            device.takeScreenshot(File(out, "composer.png"))
            device.dumpWindowHierarchy(File(out, "composer.xml"))
            val amount = device.findObjects(By.clazz("android.widget.EditText")).first()
            amount.text = "12.50"
            if (device.hasObject(By.pkg(ime))) device.pressBack()
            val label = i.targetContext.getString(dev.whekin.whfin.R.string.expense_beneficiary)
            if (!device.hasObject(By.text(label))) UiScrollable(UiSelector().scrollable(true)).scrollTextIntoView(label)
            device.findObject(By.text(label)).click()
            assertNotNull(device.wait(Until.findObject(By.text("Mira")), 5000))
            device.findObject(By.text("Mira")).click()
            assertNotNull(device.wait(Until.findObject(By.text(i.targetContext.getString(dev.whekin.whfin.R.string.expense_beneficiary_full_hint))), 5000))
            device.waitForIdle(1000)
            android.os.SystemClock.sleep(300)
            device.takeScreenshot(File(out, "recipient-$large.png"))
            device.findObject(By.text(i.targetContext.getString(dev.whekin.whfin.R.string.action_done))).click()
            activity.onActivity { assertNull(it.result) }
            val save = i.targetContext.getString(dev.whekin.whfin.R.string.action_save)
            assertNotNull(device.wait(Until.findObject(By.text(save)), 5000))
            device.waitForIdle(1000)
            device.takeScreenshot(File(out, "expense-$large.png"))
            device.findObject(By.text(save)).click()
            assertNotNull(device.wait(Until.findObject(By.text("Saved expense")), 5000))
            activity.onActivity {
                assertEquals(1L, it.result?.accountId)
                assertEquals(-1250L, it.result?.amountMinor)
                assertEquals(1L, it.result?.beneficiary?.personId)
            }
        } } finally {
            device.executeShellCommand("settings put system font_scale ${previousFont.takeUnless { it == "null" } ?: "1.0"}")
            if (previousIme == "null") device.executeShellCommand("settings delete secure show_ime_with_hard_keyboard")
            else device.executeShellCommand("settings put secure show_ime_with_hard_keyboard $previousIme")
        }
    }
}
