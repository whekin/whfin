package dev.whekin.whfin.ui.analytics

import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import java.io.File
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class AnalyticsMonthMotionTest {
    @Test fun lightMonthsStayInPlace() = journey(false, "en", 1f, false)
    @Test fun darkMonthsStayInPlace() = journey(true, "en", 1f, false)
    @Test fun russianLargeMonthsStayInPlace() = journey(true, "ru", 1.5f, false)
    @Test fun expenseMonthsStayInPlace() = journey(true, "en", 1f, true)

    private fun journey(dark: Boolean, language: String, font: Float, expenses: Boolean) {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val i = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(i)
        val context = i.targetContext
        val oldLocale = Locale.getDefault()
        val out = File(context.getExternalFilesDir(null), "analytics-motion").apply { mkdirs() }
        val intent = Intent(context, AnalyticsQaActivity::class.java).putExtra("interactive", true)
            .putExtra("running", true).putExtra("dark", dark).putExtra("language", language)
            .putExtra("fontScale", font).putExtra("expenses", expenses)
        try { ActivityScenario.launch<AnalyticsQaActivity>(intent).use { scenario ->
            val list = if (expenses) "expense-analysis-list" else "analytics-list"
            assertNotNull(device.wait(Until.findObject(By.res(list)), 10000))
            assertNotNull(device.wait(Until.findObject(By.res("analytics-timeline")), 10000))
            fun capture(suffix: String) {
                device.takeScreenshot(File(out, "$language-$dark-$font-$expenses-$suffix.png"))
                device.dumpWindowHierarchy(File(out, "$language-$dark-$font-$expenses-$suffix.xml"))
            }
            device.waitForIdle(1000); SystemClock.sleep(250)
            capture("before")
            fun chartOffset(): Int {
                var offset = 0
                scenario.onActivity { activity ->
                    offset = activity.listState.layoutInfo.visibleItemsInfo.first { it.key == "analytics-period" }.offset
                }
                return offset
            }
            for (month in listOf(7, 8, 5, 1, 8)) {
                val before = chartOffset()
                val bar = device.findObject(By.res("whfin-monthly-bar-${month - 1}"))
                assertNotNull(bar); bar.click()
                assertTrue(device.wait(Until.hasObject(By.res("whfin-monthly-bar-${month - 1}").checked(true)), 5000))
                device.waitForIdle(1000); SystemClock.sleep(250)
                val after = chartOffset()
                capture("month-$month")
                if (kotlin.math.abs(before - after) > 3) scenario.onActivity {
                    if (after < before) assertFalse("Chart moved up away from the start boundary in month $month", it.listState.canScrollBackward)
                    else assertFalse("Chart moved down away from the end boundary in month $month", it.listState.canScrollForward)
                }
            }
        } } finally { Locale.setDefault(oldLocale) }
    }
}
