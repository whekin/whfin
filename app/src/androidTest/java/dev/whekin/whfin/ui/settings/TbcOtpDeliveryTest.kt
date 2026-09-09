package dev.whekin.whfin.ui.settings

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import dev.whekin.whfin.R

/** Host injects a synthetic SMS through `adb emu sms send` while the real login route waits. */
class TbcOtpDeliveryTest {
    @Test fun incomingSmsFillsOtpWithoutSubmittingIt() {
        org.junit.Assume.assumeTrue("Run with the host SMS injector", InstrumentationRegistry.getArguments().getString("externalSms") == "true")
        check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        ActivityScenario.launch<TbcOtpQaActivity>(Intent(context, TbcOtpQaActivity::class.java)).use { activity ->
            assertNotNull(device.wait(Until.findObject(By.text("TBC OTP QA")), 10000))
            val fields = device.findObjects(By.clazz("android.widget.EditText"))
            assertEquals(2, fields.size)
            fields[0].click(); fields[0].text = "example-user"
            fields[1].click(); fields[1].text = "example-credential"
            device.waitForIdle(1000)
            val ime = device.executeShellCommand("settings get secure default_input_method").trim().substringBefore('/')
            if (device.hasObject(By.pkg(ime))) device.pressBack()
            val login = context.getString(R.string.tbc_login)
            if (!device.hasObject(By.text(login))) UiScrollable(UiSelector().scrollable(true)).scrollTextIntoView(login)
            device.findObject(By.text(login)).click()
            assertNotNull(device.wait(Until.findObject(By.text(context.getString(R.string.tbc_code))), 10000))
            val label = context.getString(R.string.tbc_confirm)
            assertTrue(device.hasObject(By.desc(context.getString(R.string.tbc_otp_progress, 0))))
            instrumentation.sendStatus(2, android.os.Bundle().apply { putString("stream", "TBC_OTP_QA_READY\n") })
            assertTrue(device.wait(Until.hasObject(By.desc(context.getString(R.string.tbc_otp_progress, 6))), 30000))
            activity.onActivity { assertEquals(0, it.confirmationCalls) }
            assertTrue(device.takeScreenshot(File(context.getExternalFilesDir(null), "tbc-otp-delivery.png")))
            device.findObject(By.text(label)).click()
            assertNotNull(device.wait(Until.findObject(By.text(context.getString(R.string.tbc_connected))), 10000))
            activity.onActivity { assertEquals(1, it.confirmationCalls) }
        }
    }
}
