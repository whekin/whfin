package dev.whekin.whfin.ui.analytics

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

/**
 * Renders the analytics screens over a synthetic multi-month history.
 *
 * Stateless UI only: no Room writes, restore, permission changes or bank actions. The states that
 * matter most are the ones a populated demo cannot produce — no recorded history, a baseline that
 * is genuinely zero, a month under the ordinary level, an empty workspace — and a branch that only
 * exists in code has not been seen. The screen size is never reduced: a layout problem hidden by a
 * narrower window is still a layout problem.
 */
class AnalyticsVisualTest {

    @Test fun dearerMonthEnglishDark() = render("01-dearer-en-dark", dark = true)
    @Test fun dearerMonthEnglishLight() = render("02-dearer-en-light")
    @Test fun dearerMonthRussianLargeFont() =
        render("03-dearer-ru-font15", dark = true, language = "ru", fontScale = 1.5f)
    @Test fun dearerMonthExpanded() = render("04-dearer-expanded", dark = true, expand = true)
    @Test fun runningMonth() = render("05-running-en", dark = true, running = true)
    @Test fun cheaperMonth() = render("06-cheaper-en", dark = true, shape = "CHEAPER")
    @Test fun shortHistory() = render("07-short-history-en", dark = true, shape = "SHORT_HISTORY")
    @Test fun noHistory() = render("08-no-history-en", dark = true, shape = "NO_HISTORY")
    @Test fun zeroBaseline() = render("09-zero-baseline-en", dark = true, shape = "ZERO_BASELINE")
    @Test fun emptyWorkspace() = render("10-empty-en", dark = true, shape = "NONE", scrolls = 0)
    @Test fun expensesScreen() = render("11-expenses-en", dark = true, expenses = true)
    @Test fun expensesRussianLargeFont() =
        render("12-expenses-ru-font15", dark = true, language = "ru", fontScale = 1.5f, expenses = true)

    private fun render(
        name: String,
        dark: Boolean = false,
        language: String = "en",
        fontScale: Float = 1f,
        shape: String = "DEARER",
        running: Boolean = false,
        expenses: Boolean = false,
        expand: Boolean = false,
        scrolls: Int = 4,
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        check(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish") { "Disposable emulator only" }
        val previousLocale = Locale.getDefault()
        val intent = Intent(context, AnalyticsQaActivity::class.java).apply {
            putExtra("dark", dark)
            putExtra("language", language)
            putExtra("fontScale", fontScale)
            putExtra("shape", shape)
            putExtra("running", running)
            putExtra("expenses", expenses)
        }
        try {
            ActivityScenario.launch<AnalyticsQaActivity>(intent).use {
                val title = if (language == "ru") "Август 2026" else "August 2026"
                assertNotNull(device.wait(Until.findObject(By.textContains(title)), 10_000))
                save(device, "$name-top")
                if (expand) {
                    repeat(scrolls) { swipeUp(device) }
                    val label = if (language == "ru") "Все изменения" else "All changes"
                    val all = device.findObject(By.textStartsWith(label))
                    assertNotNull(all)
                    all.click()
                    device.waitForIdle(2_000)
                    save(device, "$name-all")
                    return
                }
                repeat(scrolls) { swipeUp(device) }
                if (scrolls > 0) save(device, "$name-scrolled")
            }
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    private fun swipeUp(device: UiDevice) {
        device.swipe(
            device.displayWidth / 2, device.displayHeight * 4 / 5,
            device.displayWidth / 2, device.displayHeight / 4, 30,
        )
        device.waitForIdle(1_500)
    }

    private fun save(device: UiDevice, name: String) {
        device.waitForIdle(2_000)
        // Accessibility can publish the new nodes just before their first rendered frame.
        android.os.SystemClock.sleep(400)
        // The runner's own output directory, because the app's external files go away with the app
        // when the harness uninstalls it at the end of the run.
        val additional = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = additional?.let { File(it) } ?: context.getExternalFilesDir(null)!!
        val target = File(directory, "analytics-qa").apply { mkdirs() }
        assertTrue(device.takeScreenshot(File(target, "$name.png")))
    }
}
