package dev.whekin.whfin.ui.feed

import android.app.ActivityManager
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import dev.whekin.whfin.WhfinApp
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class BankSyncBackgroundTest {
    @Test fun english() = render("en", false, 1f)
    @Test fun russianLarge() = render("ru", true, 1.5f)
    @Test fun russianCompact() = render("ru", true, 1.5f, compact = true)
    private fun render(language: String, dark: Boolean, font: Float, compact: Boolean = false) {
        check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val previousFont = device.executeShellCommand("settings get system font_scale").trim().toFloatOrNull() ?: 1f
        val previousSize = Regex("Override size: (\\d+x\\d+)").find(device.executeShellCommand("wm size"))?.groupValues?.get(1)
        device.executeShellCommand("settings put system font_scale $font")
        if (compact) device.executeShellCommand("wm size 1080x1600")
        device.wakeUp(); device.executeShellCommand("wm dismiss-keyguard")
        try { ActivityScenario.launch<BankSyncQaActivity>(Intent(context, BankSyncQaActivity::class.java)
            .putExtra("language", language).putExtra("dark", dark).putExtra("fontScale", font)).use {
            val label = if (language == "ru") "Идёт синхронизация банков" else "Bank sync in progress"
            val button = device.wait(Until.findObject(By.desc(label)), 15000)
            assertNotNull(button)
            val out = File(context.getExternalFilesDir(null), "bank-background").apply { mkdirs() }
            val name = if (compact) "$language-compact" else language
            device.takeScreenshot(File(out, "$name-home.png"))
            button.click()
            val phase = if (language == "ru") "Получаем операции…" else "Getting transactions…"
            assertNotNull(device.wait(Until.findObject(By.textContains(phase)), 10000))
            device.takeScreenshot(File(out, "$name-status.png"))
            device.dumpWindowHierarchy(File(out, "$name-status.xml"))
            if (compact) {
                UiScrollable(UiSelector().scrollable(true)).scrollToEnd(5)
                device.takeScreenshot(File(out, "$name-status-bottom.png"))
                device.findObject(By.textContains("Требуется внимание")).click()
                assertNotNull(device.wait(Until.findObject(By.text("Opened TBC")), 10000))
                device.findObject(By.desc(label)).click()
                assertNotNull(device.wait(Until.findObject(By.textContains(phase)), 10000))
            }
            device.findObject(By.textContains(phase)).click()
            assertNotNull(device.wait(Until.findObject(By.text("Opened Credo")), 10000))
        } } finally {
            device.executeShellCommand("settings put system font_scale $previousFont")
            if (compact) device.executeShellCommand("wm size ${previousSize ?: "reset"}")
        }
    }

    @Test fun workCompletesAfterHomeAndActivityDestruction() {
        check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val app = context.applicationContext as WhfinApp
        val marker = File(context.cacheDir, "bank-sync-probe").apply { delete() }
        val release = File(context.cacheDir, "bank-sync-release").apply { delete() }
        val device = UiDevice.getInstance(instrumentation)
        device.wakeUp(); device.executeShellCommand("wm dismiss-keyguard")
        val scenario = ActivityScenario.launch<BankSyncQaActivity>(Intent(context, BankSyncQaActivity::class.java).putExtra("probe", true))
        try {
            device.wait(Until.findObject(By.text("Start synthetic sync")), 15000).click()
            val manager = context.getSystemService(ActivityManager::class.java)
            var foreground = false
            repeat(50) {
                if (manager.getRunningServices(100).any { it.service.className.endsWith("BankSyncService") && it.foreground }) foreground = true
                if (!foreground) SystemClock.sleep(100)
            }
            assertTrue("The OS must see a foreground sync service", foreground)
            device.pressHome()
            scenario.close()
            assertFalse(marker.exists())
            release.writeText("go")
            repeat(100) { if (!marker.exists() || app.bankSync.hasActiveWork()) SystemClock.sleep(100) }
            assertEquals("complete", marker.readText())
            assertFalse(app.bankSync.hasActiveWork())
        } finally { scenario.close(); app.bankSync.cancel("Credo"); marker.delete(); release.delete() }
    }
}
