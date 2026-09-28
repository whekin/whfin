package dev.whekin.whfin.ui.setup

import android.content.Intent
import android.content.res.Configuration
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.whekin.whfin.R
import java.io.File
import java.util.Locale
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IllustrationVisualTest {
    @Test fun brandLight() = journey("en", false, false, 1f)
    @Test fun brandDarkLargeCompact() = journey("ru", true, false, 1.5f, compact = true)
    @Test fun materialYouLight() = journey("en", false, true, 1f)
    @Test fun materialYouDarkLargeCompact() = journey("ru", true, true, 1.5f, compact = true)
    @Test fun animationsDisabled() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val previous = device.executeShellCommand("settings get global animator_duration_scale").trim()
        try {
            device.executeShellCommand("settings put global animator_duration_scale 0")
            journey("en", false, true, 1f)
        } finally {
            if (previous == "null") device.executeShellCommand("settings delete global animator_duration_scale")
            else device.executeShellCommand("settings put global animator_duration_scale $previous")
        }
    }

    private fun journey(language: String, dark: Boolean, dynamic: Boolean, font: Float, compact: Boolean = false) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
        val out = File(context.getExternalFilesDir(null), "illustrations-qa").apply { mkdirs() }
        IllustrationQaActivity.language = language
        IllustrationQaActivity.dark = dark
        IllustrationQaActivity.dynamic = dynamic
        IllustrationQaActivity.textScale = font
        val resources = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }).resources
        val oldSize = Regex("Override size: (\\d+x\\d+)").find(device.executeShellCommand("wm size"))?.groupValues?.get(1)
        if (compact) device.executeShellCommand("wm size 1200x1920")
        val prefix = "$language-${if (dynamic) "dynamic" else "brand"}-${if (dark) "dark" else "light"}"
        fun capture(page: String) {
            // Accessibility can publish the next scene before SurfaceFlinger presents it.
            android.os.SystemClock.sleep(1000)
            device.waitForIdle()
            assertTrue(device.takeScreenshot(File(out, "$prefix-$page.png")))
            device.dumpWindowHierarchy(File(out, "$prefix-$page.xml"))
        }
        fun action(id: Int) = requireNotNull(device.wait(Until.findObject(By.text(resources.getString(id))), 5000))
        try {
            ActivityScenario.launch<IllustrationQaActivity>(Intent(context, IllustrationQaActivity::class.java)).use {
                assertNotNull(device.wait(Until.findObject(By.text(resources.getString(R.string.welcome_title))), 8000))
                capture("welcome")
                action(R.string.welcome_personal_action).click()
                action(R.string.category_setup_title)
                capture("categories")
                // The main action remains reachable without scrolling even at 1.5x on a short phone.
                val continueButton = action(R.string.category_setup_continue)
                assertTrue(continueButton.visibleBounds.bottom < device.displayHeight)
                continueButton.click()
                action(R.string.setup_finish_title)
                capture("ready")
                action(R.string.personal_setup_continue_action).click()
            }
        } finally {
            if (compact) device.executeShellCommand("wm size ${oldSize ?: "reset"}")
            IllustrationQaActivity.language = "en"
            IllustrationQaActivity.textScale = 1f
            IllustrationQaActivity.dark = false
            IllustrationQaActivity.dynamic = false
        }
    }
}
