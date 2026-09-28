package dev.whekin.whfin.ui.savings

import android.content.Intent
import android.content.res.Configuration
import dev.whekin.whfin.R
import java.util.Locale
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavingsMotionVisualTest {
    @Test fun light() = render("en", false, false, 1f)
    @Test fun materialYouDarkLarge() = render("ru", true, true, 1.5f)
    @Test fun materialYouLight() = render("en", false, true, 1f)
    @Test fun brandDarkLarge() = render("ru", true, false, 1.5f)
    private fun render(language: String, dark: Boolean, dynamic: Boolean, scale: Float) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        SavingsMotionQaActivity.language = language
        SavingsMotionQaActivity.dark = dark
        SavingsMotionQaActivity.dynamic = dynamic
        SavingsMotionQaActivity.textScale = scale
        val out = File(context.getExternalFilesDir(null), "savings-motion-qa").apply { mkdirs() }
        val resources = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }).resources
        val prefix = "$language-${if (dynamic) "dynamic" else "brand"}"
        val oldSize = Regex("Override size: (\\d+x\\d+)").find(device.executeShellCommand("wm size"))?.groupValues?.get(1)
        if (scale > 1f) device.executeShellCommand("wm size 1200x1920")
        fun capture(name: String) {
            device.waitForIdle(1000)
            android.os.SystemClock.sleep(800)
            assertTrue(device.takeScreenshot(File(out, "$prefix-$name.png")))
            device.dumpWindowHierarchy(File(out, "$prefix-$name.xml"))
        }
        try { ActivityScenario.launch<SavingsMotionQaActivity>(Intent(context, SavingsMotionQaActivity::class.java)).use {
            assertNotNull(device.wait(Until.findObject(By.text(if (language == "ru") "Изменить план" else "Edit plan")), 8000))
            capture("forecast")
            // Use the chart's independent accessibility label, never infer a coordinate from a screenshot.
            val chart = requireNotNull(device.wait(Until.findObject(By.descContains(resources.getString(R.string.savings_projection_title))), 5000))
            val bounds = chart.visibleBounds
            device.click(bounds.left + bounds.width() / 2, bounds.top + bounds.height() / 3)
            capture("selected")
            device.swipe(bounds.left + bounds.width() / 3, bounds.top + bounds.height() / 3,
                bounds.right - 12, bounds.top + bounds.height() / 3, 30)
            capture("dragged")
            device.findObject(By.text(if (language == "ru") "Изменить план" else "Edit plan")).click()
            val monthly = requireNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5000))
            monthly.click()
            monthly.text = "1500"
            capture("editor-keyboard")
            val ime = device.executeShellCommand("settings get secure default_input_method").trim().substringBefore('/')
            if (device.hasObject(By.pkg(ime))) device.pressBack()
            capture("editor")
            // Scroll the form to the live projection with the keyboard dismissed.
            repeat(3) {
                if (device.findObject(By.descContains(resources.getString(R.string.savings_projection_title))) == null)
                    device.swipe(device.displayWidth / 2, device.displayHeight * 2 / 3,
                        device.displayWidth / 2, device.displayHeight / 3, 20)
            }
            capture("edited-projection")
        } } finally {
            if (scale > 1f) device.executeShellCommand("wm size ${oldSize ?: "reset"}")
            SavingsMotionQaActivity.language = "en"; SavingsMotionQaActivity.textScale = 1f
            SavingsMotionQaActivity.dark = false; SavingsMotionQaActivity.dynamic = false
        }
    }
}
