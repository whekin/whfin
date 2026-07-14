package dev.whekin.whfin.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.whekin.whfin.MainActivity
import dev.whekin.whfin.R
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainJourneyTest {
    @Test
    fun launchAndOpenAccounts() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val device = UiDevice.getInstance(instrumentation)
            val accounts = instrumentation.targetContext.getString(R.string.tab_accounts)
            // A fresh API 36 emulator may spend its first seconds compiling Compose and opening
            // Room. Keep this a journey assertion, not an accidental cold-start benchmark.
            val node = device.wait(Until.findObject(By.text(accounts)), 30_000)
            assertNotNull(node)
            node.click()
            val summary = device.wait(
                Until.findObject(By.text(instrumentation.targetContext.getString(R.string.accounts_net_worth))),
                10_000,
            )
            assertNotNull(summary)
        }
    }
}
