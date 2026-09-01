package dev.whekin.whfin.ui.sms

import android.content.Intent
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import java.util.Locale
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Renders the routing question against the shape real data produces and the demo fixture does not:
 * several deposits whose names the bank wrote itself as "<Bank> <CUR> •<last4>".
 *
 * Stateless UI only: no Room writes, restore, permission changes or bank actions.
 */
class SmsRoutingVisualTest {
    @Test fun interestQuestionEnglish() = render("sms-routing-interest-en")
    @Test fun interestQuestionRussianLarge() = render("sms-routing-interest-ru-large", large = true)

    private fun render(name: String, large: Boolean = false) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        check(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish") { "Disposable emulator only" }
        val previousLocale = Locale.getDefault()
        SmsRoutingQaActivity.language = if (large) "ru" else "en"
        SmsRoutingQaActivity.fontScale = if (large) 1.5f else 1f
        val intent = Intent(context, SmsRoutingQaActivity::class.java)
        try {
            ActivityScenario.launch<SmsRoutingQaActivity>(intent).use {
                assertNotNull(device.wait(Until.findObject(By.textContains("0002")), 10_000))
                device.waitForIdle(2_000)
                android.os.SystemClock.sleep(400)
                val directory = File(context.getExternalFilesDir(null), "sms-routing-qa").apply { mkdirs() }
                assertTrue(device.takeScreenshot(File(directory, "$name.png")))
                device.dumpWindowHierarchy(File(directory, "$name.xml"))

                // The deposit the bank named is stated, because that is what the owner recognises.
                assertTrue(device.hasObject(By.textContains("10000002")))
                // Each deposit is offered; the current account is not an answer to interest.
                listOf("0001", "0002", "0003").forEach { tail ->
                    assertNotNull(tail, device.findObject(By.textContains(tail)))
                }
                assertFalse("a current account is not an answer", device.hasObject(By.textContains("0004")))
                // Nothing says the bank, the number or the currency twice, and the number is never
                // dressed as a card mask.
                assertFalse(device.hasObject(By.textContains("Credo · Credo")))
                assertFalse(device.hasObject(By.textContains("••")))
                // A name its owner chose still survives the removal.
                assertTrue(device.hasObject(By.textContains("Travel")))
            }
        } finally {
            Locale.setDefault(previousLocale)
        }
    }
}
