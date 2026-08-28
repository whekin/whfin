package dev.whekin.whfin.ui.feed

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

/** Stateless UI only: no Room writes, restore, permission changes or bank actions. */
class HomeRunwayVisualTest {
    @Test fun lightEnglish() = render("en-light", false, "en", 1f)
    @Test fun darkEnglish() = render("en-dark", true, "en", 1f)
    @Test fun russianLargeFontCompact() = render("ru-dark-large-compact", true, "ru", 1.5f)
    @Test fun enoughUntilPayday() = render("en-enough", false, "en", 1f, enough = true)

    private fun render(name: String, dark: Boolean, language: String, fontScale: Float, enough: Boolean = false) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        check(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish") { "Disposable emulator only" }
        val previousLocale = Locale.getDefault()
        val previousSize = Regex("Override size: (\\d+x\\d+)")
            .find(device.executeShellCommand("wm size"))?.groupValues?.get(1)
        Locale.setDefault(Locale.forLanguageTag(language))
        if (fontScale > 1f) device.executeShellCommand("wm size 1200x1920")
        val intent = Intent(context, CashRunwayQaActivity::class.java).apply {
            putExtra("dark", dark)
            putExtra("language", language)
            putExtra("fontScale", fontScale)
            putExtra("enough", enough)
        }
        try {
          ActivityScenario.launch<CashRunwayQaActivity>(intent).use {
            val title = if (enough) "Should last until payday" else if (language == "ru") "Может не хватить" else "May be"
            assertNotNull(device.wait(Until.findObject(By.textContains(title)), 10_000))
            save(device, "$name-collapsed")
            val details = if (language == "ru") "Как рассчитано" else "Calculation details"
            device.findObject(By.desc(details)).click()
            val bill = device.wait(Until.findObject(By.text("Northwind Hosting")), 3_000)
            assertNotNull(bill)
            save(device, "$name-expanded")
            val accounts = if (language == "ru") "Счета" else "Accounts"
            repeat(5) {
                if (!device.hasObject(By.text(accounts))) {
                    device.swipe(device.displayWidth / 2, device.displayHeight * 4 / 5,
                        device.displayWidth / 2, device.displayHeight / 4, 40)
                }
            }
            assertNotNull(device.findObject(By.text(accounts)))
            save(device, "$name-scrolled")
            device.findObject(By.text(accounts)).click()
            assertNotNull(device.wait(Until.findObject(By.text("Accounts callback received")), 3_000))
          }
        } finally {
            Locale.setDefault(previousLocale)
            if (fontScale > 1f) device.executeShellCommand("wm size ${previousSize ?: "reset"}")
        }
    }

    private fun save(device: UiDevice, name: String) {
        device.waitForIdle(2_000)
        // Accessibility can publish the new nodes just before their first rendered frame.
        android.os.SystemClock.sleep(400)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "runway-qa").apply { mkdirs() }
        assertTrue(device.takeScreenshot(File(directory, "$name.png")))
        device.dumpWindowHierarchy(File(directory, "$name.xml"))
    }
}
