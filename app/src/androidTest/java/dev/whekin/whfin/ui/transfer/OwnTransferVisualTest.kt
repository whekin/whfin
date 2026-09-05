package dev.whekin.whfin.ui.transfer

import android.content.Intent
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sheet has to read at both ends: a long Russian sentence at font 1.5, and an empty offer where
 * the only way forward is writing the other side down.
 */
class OwnTransferVisualTest {
    @Test fun englishLight() = render("own-transfer-en", large = false, empty = false)
    @Test fun russianLargeDark() = render("own-transfer-ru-large", large = true, empty = false)
    @Test fun englishNothingOffered() = render("own-transfer-en-empty", large = false, empty = true)

    private fun render(name: String, large: Boolean, empty: Boolean) {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        OwnTransferQaActivity.language = if (large) "ru" else "en"
        OwnTransferQaActivity.fontScale = if (large) 1.5f else 1f
        OwnTransferQaActivity.dark = large
        OwnTransferQaActivity.empty = empty
        val link = if (large) "Это одно движение" else "One movement"
        val record = if (large) "Записать вручную" else "Write it down"
        ActivityScenario.launch<OwnTransferQaActivity>(
            Intent(context, OwnTransferQaActivity::class.java),
        ).use {
            assertNotNull(device.wait(Until.findObject(By.text(link)), 10_000))
            device.waitForIdle(2000)
            val dir = File(context.getExternalFilesDir(null), "own-transfer-qa").apply { mkdirs() }
            assertTrue(device.takeScreenshot(File(dir, "$name.png")))
            device.dumpWindowHierarchy(File(dir, "$name.xml"))
            if (empty) {
                // With nothing to offer, the form is already the whole sheet: no mode rail at all.
                assertNotNull(device.findObject(By.textContains(if (large) "Пришло на" else "Arrived on")))
            } else {
                device.findObject(By.text(record)).click()
                assertNotNull(
                    device.wait(Until.findObject(By.textContains(if (large) "Пришло на" else "Arrived on")), 5_000),
                )
                device.waitForIdle(2000)
                assertTrue(device.takeScreenshot(File(dir, "$name-record.png")))
                device.findObject(By.desc(if (large) "Сумма, GEL" else "Amount, GEL")).click()
                assertTrue(device.wait(Until.hasObject(By.pkg("com.google.android.inputmethod.latin")), 5_000))
                device.waitForIdle(2000)
                assertTrue(device.takeScreenshot(File(dir, "$name-ime.png")))
            }
        }
    }
}
