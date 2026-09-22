package dev.whekin.whfin.ui.settings

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BankSmsVisualTest {
    @Test fun longJournalEnglishLight() = render("en", false, 1f)
    @Test fun longJournalRussianLarge() = render("ru", true, 1.5f)

    private fun render(language: String, dark: Boolean, font: Float) {
        check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val title = if (language == "ru") "SMS банка" else "Bank SMS"
        val output = File(context.getExternalFilesDir(null), "bank-sms-qa").apply { mkdirs() }
        ActivityScenario.launch<BankSmsQaActivity>(Intent(context, BankSmsQaActivity::class.java)
            .putExtra("language", language).putExtra("dark", dark).putExtra("fontScale", font)).use {
            assertNotNull(device.wait(Until.findObject(By.text(title)), 10_000))
            device.waitForIdle(1_000)
            if (language == "en") assertNotNull(device.findObject(By.text("CARDS & ACCOUNTS")))
            assertTrue(device.takeScreenshot(File(output, "$language-top.png")))
            device.dumpWindowHierarchy(File(output, "$language-top.xml"))
            device.swipe(device.displayWidth / 2, device.displayHeight * 4 / 5,
                device.displayWidth / 2, device.displayHeight / 4, 55)
            device.waitForIdle(1_000)
            assertTrue(device.takeScreenshot(File(output, "$language-scrolled.png")))
            device.dumpWindowHierarchy(File(output, "$language-scrolled.xml"))
        }
    }
}
