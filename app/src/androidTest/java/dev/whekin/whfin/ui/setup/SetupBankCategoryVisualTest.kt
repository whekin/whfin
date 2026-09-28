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

class SetupBankCategoryVisualTest {
    @Test fun bankStatusEnglish() = renderBanks("en", false, 1f)
    @Test fun bankStatusRussianLarge() = renderBanks("ru", true, 1.5f)
    @Test fun categoryPacksEnglish() = renderPacks("en", false, 1f)
    @Test fun categoryPacksRussianCompact() = renderPacks("ru", true, 1.5f, compact = true)

    private fun renderBanks(language: String, dark: Boolean, font: Float) {
        val (context, device, out, resources) = prepare(language, dark, font)
        ActivityScenario.launch<SetupQaActivity>(Intent(context, SetupQaActivity::class.java)
            .putExtra("bankStatus", true)).use {
            assertNotNull(device.wait(Until.findObject(By.text(resources.getString(R.string.setup_bank_loading_progress, 2, 5))), 8000))
            assertNotNull(device.findObject(By.text(resources.getString(R.string.setup_bank_review_balances, 5))))
            assertTrue(device.takeScreenshot(File(out, "$language-banks.png")))
        }
    }

    private fun renderPacks(language: String, dark: Boolean, font: Float, compact: Boolean = false) {
        val (context, device, out, resources) = prepare(language, dark, font)
        val oldSize = Regex("Override size: (\\d+x\\d+)").find(device.executeShellCommand("wm size"))?.groupValues?.get(1)
        if (compact) device.executeShellCommand("wm size 1200x1920")
        val name = "$language-packs" + if (compact) "-compact" else ""
        try {
            ActivityScenario.launch<SetupQaActivity>(Intent(context, SetupQaActivity::class.java)
                .putExtra("categoryPacks", true)).use {
                assertNotNull(device.wait(Until.findObject(By.text(resources.getString(R.string.category_setup_title))), 8000))
                assertTrue(device.takeScreenshot(File(out, "$name-initial.png")))
                val chosenCount = if (compact) {
                    val label = "Аутдор и спорт"
                    scrollTo(device, label)
                    device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 5,
                        device.displayWidth / 2, device.displayHeight / 3, 20)
                    device.waitForIdle(500)
                    requireNotNull(device.findObject(By.text(label))).click()
                    1
                } else {
                    scrollTo(device, resources.getString(R.string.category_packs_select_all)).click()
                    3
                }
                device.waitForIdle(500)
                assertTrue(device.takeScreenshot(File(out, "$name-after-select.png")))
                assertNotNull(device.wait(Until.findObject(By.text(resources.getString(R.string.category_packs_add_selected, chosenCount))), 5000))
                assertTrue(device.takeScreenshot(File(out, "$name-selected.png")))
                device.findObject(By.text(resources.getString(R.string.category_packs_add_selected, chosenCount))).click()
                assertTrue(device.takeScreenshot(File(out, "$name-added.png")))
            }
        } finally {
            if (compact) device.executeShellCommand("wm size ${oldSize ?: "reset"}")
        }
    }

    private fun scrollTo(device: UiDevice, label: String): androidx.test.uiautomator.UiObject2 {
        repeat(10) {
            device.findObject(By.text(label))?.let { return it }
            // The footer is pinned; dragging there does not move the category list.
            device.swipe(device.displayWidth / 2, device.displayHeight * 2 / 3,
                device.displayWidth / 2, device.displayHeight / 4, 25)
            device.waitForIdle(300)
        }
        return requireNotNull(device.findObject(By.text(label)))
    }

    private data class Harness(val context: android.content.Context, val device: UiDevice,
        val out: File, val resources: android.content.res.Resources)

    private fun prepare(language: String, dark: Boolean, font: Float): Harness {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        SetupQaActivity.language = language
        SetupQaActivity.dark = dark
        SetupQaActivity.fontScale = font
        val resources = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }).resources
        return Harness(context, device, File(context.getExternalFilesDir(null), "setup-bank-category-qa").apply { mkdirs() }, resources)
    }
}
