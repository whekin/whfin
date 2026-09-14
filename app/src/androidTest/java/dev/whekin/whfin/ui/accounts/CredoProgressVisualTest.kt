package dev.whekin.whfin.ui.accounts

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CredoProgressVisualTest {
    @Test fun englishCoverage() = show("en", "history", "Checking how far the bank history goes…")
    @Test fun russianCardsLarge() = show("ru", "cards", "Сопоставляем карты с банковскими сообщениями…")
    private fun show(language: String, phase: String, expected: String) {
        check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.wakeUp(); device.executeShellCommand("wm dismiss-keyguard")
        ActivityScenario.launch<AccountsQaActivity>(Intent(context, AccountsQaActivity::class.java)
            .putExtra("credoCoverage", true).putExtra("credoProgress", phase).putExtra("language", language)
            .putExtra("dark", language == "ru").putExtra("fontScale", if (language == "ru") 1.5f else 1f)).use {
            assertNotNull(device.wait(Until.findObject(By.text(expected)), 10000))
            assertFalse(device.hasObject(By.text("Matching SMS and manual entries…")))
            device.waitForIdle(1000)
            val out = File(context.getExternalFilesDir(null), "credo-progress").apply { mkdirs() }
            assertTrue(device.takeScreenshot(File(out,"$language.png")))
            device.dumpWindowHierarchy(File(out,"$language.xml"))
        }
    }
}
