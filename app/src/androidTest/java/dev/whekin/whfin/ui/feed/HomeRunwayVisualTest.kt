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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stateless UI only: no Room writes, restore, permission changes or bank actions. */
class HomeRunwayVisualTest {
    @Test fun cardWarningLight() = renderCard("card-light")
    @Test fun cardWarningDark() = renderCard("card-dark", dark = true)
    @Test fun cardWarningLargeRussian() = renderCard("card-ru-large", dark = true, large = true)
    @Test fun cardWarningDemo() = renderCard("card-demo", demo = true)
    @Test fun cardLaunchFailure() = renderCard("card-launch-failure", failure = true)

    private fun renderCard(name: String, dark: Boolean = false, large: Boolean = false,
                           demo: Boolean = false, failure: Boolean = false) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        check(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        val previousLocale = Locale.getDefault()
        val previousSize = Regex("Override size: (\\d+x\\d+)")
            .find(device.executeShellCommand("wm size"))?.groupValues?.get(1)
        if (large) device.executeShellCommand("wm size 1200x1920")
        val intent = Intent(context, CashRunwayQaActivity::class.java).apply {
            putExtra("cardNotice", true); putExtra("dark", dark); putExtra("demo", demo)
            putExtra("language", if (large) "ru" else "en"); putExtra("fontScale", if (large) 1.5f else 1f)
            putExtra("bankFailure", failure)
            putExtra("alertsEnabled", !large)
        }
        try {
          ActivityScenario.launch<CashRunwayQaActivity>(intent).use {
            assertNotNull(device.wait(Until.findObject(By.textContains("••0001")), 10_000))
            save(device, name)
            val launchLabel = if (large) "Открыть MyCredo" else "Open MyCredo"
            if (demo) assertFalse(device.hasObject(By.text(launchLabel))) else {
                device.findObject(By.text(launchLabel)).click()
                if (failure) {
                    assertNotNull(device.wait(Until.findObject(By.textContains("could not be opened")), 3_000))
                    save(device, "$name-result")
                } else assertNotNull(device.wait(Until.findObject(By.text("Bank callback received")), 3_000))
            }
          }
        } finally {
            Locale.setDefault(previousLocale)
            if (large) device.executeShellCommand("wm size ${previousSize ?: "reset"}")
        }
    }
    @Test fun lightEnglish() = render("en-light", false, "en", 1f)
    @Test fun darkEnglish() = render("en-dark", true, "en", 1f)
    @Test fun russianLargeFontCompact() = render("ru-dark-large-compact", true, "ru", 1.5f)
    @Test fun enoughUntilPayday() = render("en-enough", false, "en", 1f, enough = true)
    @Test fun weekendPaydayUsesNormalAndDeadlineScenarios() =
        render("en-weekend-sample", false, "en", 1f, weekendSample = true)

    private fun render(
        name: String,
        dark: Boolean,
        language: String,
        fontScale: Float,
        enough: Boolean = false,
        weekendSample: Boolean = false,
    ) {
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
            putExtra("weekendSample", weekendSample)
        }
        try {
          ActivityScenario.launch<CashRunwayQaActivity>(intent).use {
            val title = if (enough) {
                "Should last until 7 Sep"
            } else if (language == "ru") {
                "может не хватить"
            } else {
                "May be"
            }
            assertNotNull(device.wait(Until.findObject(By.textContains(title)), 10_000))
            save(device, "$name-collapsed")
            val details = if (language == "ru") "Как рассчитано" else "Calculation details"
            device.findObject(By.desc(details)).click()
            if (weekendSample) {
                assertNotNull(device.wait(Until.findObject(By.textContains("370.00")), 3_000))
            } else {
                assertNotNull(device.wait(Until.findObject(By.text("Northwind Hosting")), 3_000))
            }
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
