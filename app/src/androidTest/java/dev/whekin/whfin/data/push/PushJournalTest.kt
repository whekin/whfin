package dev.whekin.whfin.data.push

import android.app.Notification
import android.os.Build
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import java.io.File

class PushJournalTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun before() { check(Build.HARDWARE in setOf("ranchu", "goldfish")); PushJournal(context).clear() }
    @After fun after() { PushJournal(context).clear() }
    private fun entry(i: Int = 0) = BankPush(TbcPush.PACKAGE, "synthetic-$i", 1000, title = "TBC", text = "Synthetic unrecognized notification $i")
    @Test fun encryptedJournalSurvivesReconstructionAndKeepsUnknownFields() {
        PushJournal(context).record(entry(), "UNRECOGNIZED")
        val restored = PushJournal(context).entries().single()
        assertEquals(entry(), restored.push)
        val stored = File(context.noBackupFilesDir, "tbc-push-journal.bin").readBytes().toString(Charsets.UTF_8)
        assertFalse(stored.contains("Synthetic")); assertFalse(stored.contains("UNRECOGNIZED"))
        assertTrue(File(context.noBackupFilesDir, "tbc-push-journal.bin").exists())
    }
    @Test fun retentionCapacityAndAuthenticationExclusion() {
        var now = 1000L
        val journal = PushJournal(context, { now }, 3)
        repeat(4) { journal.record(entry(it), "UNRECOGNIZED"); now++ }
        assertEquals(3, journal.entries().size)
        assertFalse(journal.entries().any { it.push.notificationKey == "synthetic-0" })
        assertTrue(runCatching { journal.record(entry().copy(text = "OTP code: 0000"), "UNRECOGNIZED") }.isFailure)
        now += PushJournal.RETENTION + 1
        assertTrue(journal.entries().isEmpty())
    }
    @Test fun notificationFieldsAreCapturedBeforeParsing() {
        val body = "2.00 GEL\n(*0001)\nEXAMPLE BUS 10/09/26 14:07"
        val notification = Notification.Builder(context, "synthetic-channel").setContentTitle("TBC")
            .setContentText("2.00 GEL").setSubText("Synthetic").setStyle(Notification.BigTextStyle().bigText(body)).build()
        val sbn = StatusBarNotification(TbcPush.PACKAGE, TbcPush.PACKAGE, 1, null, android.os.Process.myUid(), 0,
            0, notification, UserHandle.getUserHandleForUid(android.os.Process.myUid()), 1000)
        val captured = TbcPushListener.capture(sbn)
        assertEquals(body, captured.bigText)
        assertEquals("2.00 GEL", captured.text)
        assertEquals("synthetic-channel", captured.channel)
        assertTrue(TbcPush.classify(captured) is dev.whekin.whfin.data.sms.BankSmsMessage.Classification.Parsed)
    }
}
