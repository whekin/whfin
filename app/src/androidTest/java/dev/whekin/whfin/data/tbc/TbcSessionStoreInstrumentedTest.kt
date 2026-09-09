package dev.whekin.whfin.data.tbc

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import dev.whekin.whfin.data.security.EncryptedBankSessionStore
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TbcSessionStoreInstrumentedTest {
    @Test fun keystoreRoundTripTamperingAndForget() {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = EncryptedBankSessionStore(context, "tbctest")
        val value = "synthetic-session-value"
        val file = File(context.noBackupFilesDir, "whfin_tbctest_session_v1.bin")
        try {
            store.clear()
            assertFalse(store.hasSaved())
            store.save(value)
            assertEquals(value, EncryptedBankSessionStore(context, "tbctest").load())
            assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains(value))
            val bytes = file.readBytes()
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
            file.writeBytes(bytes)
            assertNull(store.load())
            assertFalse(store.hasSaved())
            store.save(value)
            store.clear()
            assertFalse(file.exists())
            assertNull(store.load())
        } finally { store.clear() }
    }
}
