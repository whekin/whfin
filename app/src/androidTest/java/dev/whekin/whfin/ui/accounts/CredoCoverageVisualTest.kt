package dev.whekin.whfin.ui.accounts

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CredoCoverageVisualTest {
    @Test fun english() = show("en", false, 1f)
    @Test fun russianLarge() = show("ru", true, 1.5f)
    private fun show(language: String, dark: Boolean, font: Float) {
        check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        ActivityScenario.launch<AccountsQaActivity>(Intent(context, AccountsQaActivity::class.java)
            .putExtra("credoCoverage", true).putExtra("language", language).putExtra("dark", dark).putExtra("fontScale", font)).use {
            val error = if (language == "ru") "Некоторые банковские операции" else "Some bank operations"
            val scroll = UiScrollable(UiSelector().scrollable(true))
            if (!device.wait(Until.hasObject(By.textStartsWith(error)), 10000)) scroll.scrollTextIntoView(if (language == "ru")
                "Некоторые банковские операции соответствуют нескольким записям. Этот счёт оставлен без изменений."
                else "Some bank operations match more than one existing record. This account was left unchanged.")
            assertNotNull(device.wait(Until.findObject(By.textStartsWith(error)), 10000))
            assertFalse(device.hasObject(By.text("Fetch older history")))
            device.waitForIdle(1000)
            val out = File(context.getExternalFilesDir(null), "credo-coverage").apply { mkdirs() }
            device.takeScreenshot(File(out,"$language.png")); device.dumpWindowHierarchy(File(out,"$language.xml"))
        }
    }
}
