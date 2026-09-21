package dev.whekin.whfin.ui

import android.content.Intent
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.whekin.whfin.MainActivity
import dev.whekin.whfin.R
import dev.whekin.whfin.data.preferences.AppLockTimeout
import dev.whekin.whfin.data.preferences.UiPreferences
import dev.whekin.whfin.data.security.AppLockPinStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ExternalRestartLockTest {
    @Test fun aPublicRestartExtraDoesNotUnlockTheActualActivity() = runBlocking {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val preferences = UiPreferences(context)
        val pins = AppLockPinStore(context)
        try {
            pins.setPin("2468".toCharArray())
            preferences.setAppLockTimeout(AppLockTimeout.Immediate)
            preferences.setBiometricUnlockEnabled(false)
            val forged = Intent(context, MainActivity::class.java)
                .putExtra("dev.whekin.whfin.RUNTIME_MODE_RESTART", true)
            ActivityScenario.launch<MainActivity>(forged).use {
                assertNotNull(device.wait(Until.findObject(By.text(context.getString(R.string.app_lock_gate_title))), 20000))
                assertFalse(device.hasObject(By.text(context.getString(R.string.tab_feed))))
            }
        } finally {
            pins.clear()
            preferences.setAppLockTimeout(AppLockTimeout.Disabled)
        }
    }
}
