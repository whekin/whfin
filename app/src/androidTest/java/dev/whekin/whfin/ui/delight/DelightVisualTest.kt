package dev.whekin.whfin.ui.delight

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

class DelightVisualTest {
    @Test fun brandLight() = render("en", false, false, 1f)
    @Test fun materialYouDarkCompact() = render("ru", true, true, 1.5f)
    @Test fun aboutDarkSystemBars() = render("ru", true, true, 1.5f, listOf("about"))
    private fun render(language: String, dark: Boolean, dynamic: Boolean, font: Float,
        pages: List<String> = listOf("about", "history", "debts", "savings", "category", "transfer", "sync")) {
        val inst = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(inst)
        val context = inst.targetContext
        val resources = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }).resources
        val out = File(context.getExternalFilesDir(null), "delight-qa").apply { mkdirs() }
        DelightQaActivity.language = language; DelightQaActivity.dark = dark
        DelightQaActivity.dynamic = dynamic; DelightQaActivity.textScale = font
        val oldSize = Regex("Override size: (\\d+x\\d+)").find(device.executeShellCommand("wm size"))?.groupValues?.get(1)
        if (font > 1f) device.executeShellCommand("wm size 1200x1920")
        fun capture(page: String) {
            device.waitForIdle(500); android.os.SystemClock.sleep(400)
            assertTrue(device.takeScreenshot(File(out, "$language-$page.png")))
            device.dumpWindowHierarchy(File(out, "$language-$page.xml"))
        }
        try {
            for (page in pages) {
                ActivityScenario.launch<DelightQaActivity>(Intent(context, DelightQaActivity::class.java).putExtra("page", page)).use {
                    device.waitForIdle(); android.os.SystemClock.sleep(800)
                    capture(page)
                    if (page == "about") {
                        val cat = requireNotNull(device.wait(Until.findObject(By.desc(resources.getString(R.string.about_cat_description))), 5000))
                        cat.longClick()
                        android.os.SystemClock.sleep(500)
                        assertTrue(device.takeScreenshot(File(out, "$language-cat-playing.png")))
                    }
                    if (page == "transfer") {
                        val swap = device.findObject(By.desc(resources.getString(R.string.transfer_swap_accounts)))
                        assertNotNull(swap)
                        swap.click()
                        capture("transfer-swapped")
                    }
                    if (page == "sync") {
                        device.findObject(By.text("Complete")).click()
                        val done = requireNotNull(device.wait(Until.findObject(By.desc(resources.getString(R.string.bank_sync_complete))), 5000))
                        capture("sync-complete")
                        requireNotNull(device.findObject(By.desc(resources.getString(R.string.bank_sync_complete)))).click()
                        assertNotNull(device.wait(Until.findObject(By.text(resources.getString(R.string.bank_sync_status_title))), 5000))
                        assertNotNull(device.findObject(By.text(resources.getString(R.string.bank_sync_complete))))
                        capture("sync-result")
                        device.pressBack()
                        device.findObject(By.text("Attention")).click()
                        assertNotNull(device.wait(Until.findObject(By.desc(resources.getString(R.string.bank_sync_attention))), 5000))
                        capture("sync-attention")
                    }
                }
            }
        } finally {
            if (font > 1f) device.executeShellCommand("wm size ${oldSize ?: "reset"}")
            DelightQaActivity.language = "en"; DelightQaActivity.dark = false
            DelightQaActivity.dynamic = false; DelightQaActivity.textScale = 1f
        }
    }
}
