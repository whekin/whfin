package dev.whekin.whfin.ui.feed

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsDecisionVisualTest {
    @Test fun englishLight() = render("en", false, 1f)
    @Test fun russianDarkLarge() = render("ru", true, 1.5f)

    private fun render(language: String, dark: Boolean, scale: Float) {
        SmsDecisionQaActivity.language = language
        SmsDecisionQaActivity.dark = dark
        SmsDecisionQaActivity.scale = scale
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        ActivityScenario.launch<SmsDecisionQaActivity>(Intent(context, SmsDecisionQaActivity::class.java)).use {
            assertNotNull(device.wait(Until.findObject(By.text(if (language == "ru")
                "Ждёт сверку с банком" else "Awaiting bank match")), 8000))
            assertNotNull(device.findObject(By.text(if (language == "ru") "Выбрать счёт" else "Choose account")))
            device.waitForIdle(500)
            val out = File(context.getExternalFilesDir(null), "sms-decision-qa").apply { mkdirs() }
            assertTrue(device.takeScreenshot(File(out, "$language.png")))
        }
    }
}
