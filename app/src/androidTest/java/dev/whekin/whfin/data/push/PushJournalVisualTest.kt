package dev.whekin.whfin.data.push

import android.content.Intent
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import dev.whekin.whfin.R
import dev.whekin.whfin.ui.settings.PushJournalQaActivity
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class PushJournalVisualTest {
    @Test fun logAndOriginalExample() = render(false)
    @Test fun largeDarkLogAndOriginalExample() = render(true)
    @Test fun bankDiagnosticsOnly() = render(false, true)
    private fun render(large: Boolean, diagnostics: Boolean = false) {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val i = InstrumentationRegistry.getInstrumentation()
        val context = i.targetContext
        val device = UiDevice.getInstance(i)
        val previousFont = device.executeShellCommand("settings get system font_scale").trim()
        device.executeShellCommand("settings put system font_scale ${if (large) 1.5 else 1.0}")
        val journal = PushJournal(context)
        journal.clear()
        journal.record(BankPush(TbcPush.PACKAGE, "synthetic-example", System.currentTimeMillis(), "TBC", "Synthetic future payment format"), "UNRECOGNIZED")
        try { ActivityScenario.launch<PushJournalQaActivity>(Intent(context, PushJournalQaActivity::class.java).putExtra("dark", large).putExtra("diagnostics", diagnostics)).use {
            val outcome = context.getString(R.string.push_unknown)
            assertNotNull(device.wait(Until.findObject(By.text(context.getString(R.string.push_title))), 10000))
            if (!device.hasObject(By.text(outcome))) UiScrollable(UiSelector().scrollable(true)).scrollTextIntoView(outcome)
            assertNotNull(device.findObject(By.text(outcome)))
            if (diagnostics) assertFalse(device.hasObject(By.text(context.getString(R.string.push_enable))))
            device.waitForIdle(1000)
            assertFalse("A normal resume must not show an error", device.hasObject(By.text(context.getString(R.string.push_error))))
            device.takeScreenshot(File(context.getExternalFilesDir(null), "push-journal-$large.png"))
            device.dumpWindowHierarchy(File(context.getExternalFilesDir(null), "push-journal-$large.xml"))
            device.findObject(By.text(outcome)).click()
            val export = context.getString(R.string.push_export)
            assertNotNull(device.wait(Until.findObject(By.text(export)), 5000))
            device.waitForIdle(1000)
            device.takeScreenshot(File(context.getExternalFilesDir(null), "push-journal-entry-$large.png"))
            assertTrue(device.hasObject(By.textContains("Synthetic future payment format")))
        } } finally { journal.clear(); device.executeShellCommand("settings put system font_scale ${previousFont.takeUnless { it == "null" } ?: "1.0"}") }
    }
}
