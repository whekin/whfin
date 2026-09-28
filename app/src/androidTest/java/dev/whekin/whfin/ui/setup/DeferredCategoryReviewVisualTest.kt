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

class DeferredCategoryReviewVisualTest {
    @Test fun homeEnglishLight() = render("en", false, 1f, "deferredHome", R.string.home_category_review_title)
    @Test fun homeRussianLargeCompact() = render("ru", true, 1.5f,
        "deferredHome", R.string.home_category_review_title, compact = true)
    @Test fun readyRussianLargeCompact() = render("ru", true, 1.5f,
        "deferredReady", R.string.setup_category_new_review, compact = true)

    private fun render(language: String, dark: Boolean, font: Float, extra: String,
        expected: Int, compact: Boolean = false) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val resources = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }).resources
        val oldSize = Regex("Override size: (\\d+x\\d+)").find(device.executeShellCommand("wm size"))?.groupValues?.get(1)
        if (compact) device.executeShellCommand("wm size 1200x1920")
        SetupQaActivity.language = language
        SetupQaActivity.dark = dark
        SetupQaActivity.fontScale = font
        try {
            ActivityScenario.launch<SetupQaActivity>(Intent(context, SetupQaActivity::class.java)
                .putExtra(extra, true)).use {
                assertNotNull(device.wait(Until.findObject(By.text(resources.getString(expected))), 10_000))
                val out = File(context.getExternalFilesDir(null), "deferred-category-qa").apply { mkdirs() }
                assertTrue(device.takeScreenshot(File(out, "$language-$extra.png")))
            }
        } finally {
            if (compact) device.executeShellCommand("wm size ${oldSize ?: "reset"}")
        }
    }
}
