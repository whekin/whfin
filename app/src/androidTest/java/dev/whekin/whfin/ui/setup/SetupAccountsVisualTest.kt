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

class SetupAccountsVisualTest {
    @Test fun bankInventoryEnglish() = render("en", false, 1f, "accountSetup", R.string.setup_accounts_title)
    @Test fun bankInventoryRussianCompact() = render("ru", true, 1.5f,
        "accountSetup", R.string.setup_accounts_title, compact = true)
    @Test fun unknownCardRussianLarge() = render("ru", true, 1.5f,
        "unknownCard", R.string.account_card_type_needed)

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
        SetupQaActivity.language = language; SetupQaActivity.dark = dark; SetupQaActivity.fontScale = font
        try {
            ActivityScenario.launch<SetupQaActivity>(Intent(context, SetupQaActivity::class.java)
                .putExtra(extra, true)).use {
                val label = resources.getString(expected)
                var node = device.wait(Until.findObject(By.text(label)), 10_000)
                if (node == null && extra == "unknownCard") for (attempt in 0 until 8) {
                    device.swipe(device.displayWidth / 2, device.displayHeight * 2 / 3,
                        device.displayWidth / 2, device.displayHeight / 3, 25)
                    node = device.wait(Until.findObject(By.text(label)), 700)
                    if (node != null) break
                }
                assertNotNull(node)
                val out = File(context.getExternalFilesDir(null), "setup-accounts-qa").apply { mkdirs() }
                assertTrue(device.takeScreenshot(File(out, "$language-$extra.png")))
            }
        } finally {
            if (compact) device.executeShellCommand("wm size ${oldSize ?: "reset"}")
        }
    }
}
