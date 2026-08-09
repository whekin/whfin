package dev.whekin.whfin.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.whekin.whfin.MainActivity
import dev.whekin.whfin.R
import org.junit.Assert.assertFalse
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
            passFirstRunGate(device, instrumentation.targetContext)
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

    /**
     * A clean install opens on the Welcome choice, an upgraded one goes straight to the shell.
     * The journey has to survive both, so wait for whichever surface appears first instead of
     * assuming one of them.
     */
    private fun passFirstRunGate(device: UiDevice, context: android.content.Context) {
        dismissForeignAnrDialog(device, context)
        val welcomeAction = By.text(context.getString(R.string.welcome_personal_action))
        val shellTab = By.text(context.getString(R.string.tab_accounts))
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            if (device.hasObject(welcomeAction) || device.hasObject(shellTab)) break
            device.waitForIdle(250)
        }
        val welcome = device.findObject(welcomeAction) ?: return
        welcome.click()
        val skip = device.wait(
            Until.findObject(By.text(context.getString(R.string.personal_setup_skip_action))),
            10_000,
        )
        assertNotNull("personal setup should offer an explicit skip", skip)
        skip.click()
    }

    /**
     * Clears an "isn't responding" dialog raised by *another* process.
     *
     * An emulator whose SystemUI stalls keeps that dialog on top across installs, and every journey
     * run afterwards fails while looking for a screen that is simply covered. Our own app is never
     * dismissed this way: an ANR in WHFIN has to fail the test loudly rather than be clicked away.
     */
    private fun dismissForeignAnrDialog(device: UiDevice, context: android.content.Context) {
        val wait = device.findObject(By.res("android", "aerr_wait")) ?: return
        val ownLabel = context.getString(R.string.app_name)
        val mentionsUs = device.hasObject(By.textContains(ownLabel)) ||
            device.hasObject(By.textContains(context.packageName))
        assertFalse("WHFIN itself stopped responding", mentionsUs)
        wait.click()
        device.waitForIdle(2_000)
    }
}
