package dev.whekin.whfin.data.credo

import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The drawer exists so a failure can still be explained later. Every rule here is about that: the
 * bytes outlive the run and the process, they are reachable without repeating the sync, and they
 * disappear once they describe nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FailedStatementStoreTest {

    private var now = 1_000_000L

    private fun store() = FailedStatementStore(ApplicationProvider.getApplicationContext()) { now }

    private fun save(
        label: String = "Credo · GEL",
        bytes: ByteArray = byteArrayOf(1, 2, 3),
        detail: String? = "Credo statement period is unreadable.",
    ) = store().save(
        accountLabel = label,
        fileName = "mycredo_gel.xlsx",
        errorCode = null,
        detail = detail,
        askedFrom = "2026-08-01",
        askedTo = "2026-08-12",
        bytes = bytes,
    )

    @Before
    fun clean() {
        store().clear()
    }

    @Test
    fun `a kept failure is readable by a store built after the process died`() {
        val entry = requireNotNull(save(bytes = byteArrayOf(7, 8, 9)))

        val output = ByteArrayOutputStream()
        assertTrue(FailedStatementStore(ApplicationProvider.getApplicationContext()).copyTo(entry.token, output))
        assertArrayEquals(byteArrayOf(7, 8, 9), output.toByteArray())
    }

    @Test
    fun `metadata survives the round trip and a missing code stays missing`() {
        save()

        val entry = store().entries().single()

        assertEquals("Credo · GEL", entry.accountLabel)
        assertEquals("mycredo_gel.xlsx", entry.fileName)
        assertEquals("2026-08-01", entry.askedFrom)
        // `optString` would have turned the absent code into the text "null".
        assertNull(entry.errorCode)
    }

    @Test
    fun `a repeated failure of one account replaces its own evidence`() {
        save(bytes = byteArrayOf(1))
        now += 1_000
        save(bytes = byteArrayOf(2))

        val entries = store().entries()

        assertEquals(1, entries.size)
        val output = ByteArrayOutputStream()
        store().copyTo(entries.single().token, output)
        assertArrayEquals(byteArrayOf(2), output.toByteArray())
    }

    @Test
    fun `an account that imports again stops offering the old file`() {
        val entry = requireNotNull(save())

        store().forget("Credo · GEL")

        assertTrue(store().entries().isEmpty())
        assertFalse(store().copyTo(entry.token, ByteArrayOutputStream()))
    }

    @Test
    fun `evidence expires rather than accumulating forever`() {
        val entry = requireNotNull(save())

        now += 15L * 24 * 60 * 60 * 1000

        assertTrue(store().entries().isEmpty())
        assertFalse(store().copyTo(entry.token, ByteArrayOutputStream()))
    }

    @Test
    fun `the drawer keeps a bounded number of accounts`() {
        repeat(12) { index ->
            now += 1_000
            save(label = "Credo · ledger $index")
        }

        assertEquals(8, store().entries().size)
        // Newest first, so the ones dropped are the ones least likely to still be investigated.
        assertEquals("Credo · ledger 11", store().entries().first().accountLabel)
    }
}
