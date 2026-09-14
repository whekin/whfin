package dev.whekin.whfin.ui.accounts

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CredoBalanceAuditVisualTest {
    @Test fun english() = show("en", false, 1f)
    @Test fun russianLarge() = show("ru", true, 1.5f)
    private fun show(language: String, dark: Boolean, font: Float) {
        check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        ActivityScenario.launch<AccountsQaActivity>(Intent(context, AccountsQaActivity::class.java)
            .putExtra("credoCoverage", true).putExtra("balanceReview", true)
            .putExtra("language", language).putExtra("dark", dark).putExtra("fontScale", font)).use {
            val text = if (language == "ru") "История загружена. Остаток на конец выписки" else "History loaded. The recorded balance"
            val scroll = UiScrollable(UiSelector().scrollable(true))
            if (!device.wait(Until.hasObject(By.textContains(text)), 10000)) scroll.scrollIntoView(UiSelector().textContains(text))
            assertNotNull(device.wait(Until.findObject(By.textContains(text)), 10000))
            device.waitForIdle(1000)
            val out = File(context.getExternalFilesDir(null), "credo-balance-audit").apply { mkdirs() }
            assertTrue(device.takeScreenshot(File(out, "$language.png")))
            device.dumpWindowHierarchy(File(out, "$language.xml"))
        }
    }
}
