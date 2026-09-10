package dev.whekin.whfin.data.push

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Test
import org.junit.Assert.*

/** Run only with the explicitly installed, temporary synthetic TBC-package sender on an emulator. */
class PushListenerDeliveryTest {
    @Test fun androidDeliversOriginalFieldsUpdatesAndUnknownNotifications() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("externalPush") == "true")
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageManager.getPackageInfo(TbcPush.PACKAGE, 0).versionName == "synthetic-test")
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val component = ComponentName(context, TbcPushListener::class.java)
        val manager = context.getSystemService(NotificationManager::class.java)
        val previousAccess = manager.isNotificationListenerAccessGranted(component)
        val settings = PushSettings(context)
        val previousEnabled = settings.enabled
        val journal = PushJournal(context)
        fun waitFor(condition: () -> Boolean) {
            val deadline = System.currentTimeMillis() + 30000
            while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(100)
            assertTrue("outcomes=${journal.entries().map { it.outcome }} receiverError=${PushRuntime.error.value}", condition())
        }
        fun send(mode: String, id: Int) {
            context.startActivity(Intent().setComponent(ComponentName(TbcPush.PACKAGE, TbcPush.PACKAGE + ".MainActivity"))
                .putExtra("mode", mode).putExtra("id", id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        try {
            journal.clear(); settings.enabled = true
            device.executeShellCommand("cmd notification allow_listener ${component.flattenToString()}")
            waitFor { PushRuntime.connected.value }
            send("payment", 1001)
            waitFor { journal.entries().any { it.diagnosticId != null } }
            val first = journal.entries().first { it.diagnosticId != null }
            assertTrue(first.push.bigText.contains("EXAMPLE BUS"))
            assertEquals("synthetic", first.push.channel)
            send("update", 1001)
            waitFor { journal.entries().any { it.push.bigText.contains("Balance:") } }
            assertEquals(1, journal.entries().mapNotNull { it.diagnosticId }.distinct().size)
            send("unknown", 1002)
            waitFor { journal.entries().any { it.outcome == "UNRECOGNIZED" } }
            assertTrue(journal.entries().any { it.push.text.contains("future format") })
            send("otp", 1003)
            Thread.sleep(1000)
            assertFalse(journal.entries().any { entry -> entry.push.fields().any { it.contains("0000") } })
        } finally {
            settings.enabled = previousEnabled
            if (!previousAccess) device.executeShellCommand("cmd notification disallow_listener ${component.flattenToString()}")
            device.executeShellCommand("am force-stop ${TbcPush.PACKAGE}")
            journal.clear()
        }
    }
}
