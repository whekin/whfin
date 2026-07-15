package dev.whekin.whfin.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WhfinEncryptedBackupEnvelopeTest {

    private val payload = """{"format":"whfin-backup","tables":{}}""".toByteArray(Charsets.UTF_8)

    private fun encrypt(passphrase: String, data: ByteArray = payload): ByteArray {
        val output = ByteArrayOutputStream()
        WhfinEncryptedBackupEnvelope.encryptingStream(output, passphrase.toCharArray()).use { it.write(data) }
        return output.toByteArray()
    }

    @Test
    fun `roundtrip decrypts to original payload`() {
        val envelope = encrypt("correct horse")
        val plain = WhfinEncryptedBackupEnvelope
            .decrypt(ByteArrayInputStream(envelope), "correct horse".toCharArray())
            .readBytes()
        assertArrayEquals(payload, plain)
    }

    @Test
    fun `ciphertext does not contain plaintext`() {
        val envelope = encrypt("correct horse")
        assertFalse(String(envelope, Charsets.ISO_8859_1).contains("whfin-backup"))
    }

    @Test
    fun `wrong passphrase fails with passphrase error`() {
        val envelope = encrypt("correct horse")
        assertThrows(WhfinBackupPassphraseException::class.java) {
            WhfinEncryptedBackupEnvelope.decrypt(ByteArrayInputStream(envelope), "battery staple".toCharArray())
        }
    }

    @Test
    fun `truncated envelope fails as invalid backup`() {
        val envelope = encrypt("correct horse")
        assertThrows(WhfinBackupException::class.java) {
            WhfinEncryptedBackupEnvelope.decrypt(
                ByteArrayInputStream(envelope.copyOf(20)),
                "correct horse".toCharArray(),
            )
        }
    }

    @Test
    fun `tampered ciphertext fails even with correct passphrase`() {
        val envelope = encrypt("correct horse")
        envelope[envelope.size - 1] = (envelope.last().toInt() xor 0x01).toByte()
        assertThrows(WhfinBackupPassphraseException::class.java) {
            WhfinEncryptedBackupEnvelope.decrypt(ByteArrayInputStream(envelope), "correct horse".toCharArray())
        }
    }

    @Test
    fun `detect recognizes envelope and passes stream through intact`() {
        val envelope = encrypt("correct horse")
        val (stream, encrypted) = WhfinEncryptedBackupEnvelope.detect(ByteArrayInputStream(envelope))
        assertTrue(encrypted)
        assertArrayEquals(envelope, stream.readBytes())
    }

    @Test
    fun `detect leaves plain json untouched`() {
        val json = """{"format":"whfin-backup"}""".toByteArray()
        val (stream, encrypted) = WhfinEncryptedBackupEnvelope.detect(ByteArrayInputStream(json))
        assertFalse(encrypted)
        assertArrayEquals(json, stream.readBytes())
    }

    @Test
    fun `detect handles stream shorter than magic`() {
        val tiny = byteArrayOf(0x7b)
        val (stream, encrypted) = WhfinEncryptedBackupEnvelope.detect(ByteArrayInputStream(tiny))
        assertFalse(encrypted)
        assertArrayEquals(tiny, stream.readBytes())
    }

    @Test
    fun `short passphrase is rejected`() {
        assertThrows(WhfinBackupPassphraseException::class.java) {
            WhfinEncryptedBackupEnvelope.encryptingStream(ByteArrayOutputStream(), "12345".toCharArray())
        }
    }

    @Test
    fun `two exports use distinct salt and iv`() {
        val first = encrypt("correct horse")
        val second = encrypt("correct horse")
        // magic+version+iterations = 14 bytes, дальше соль — не должна совпадать.
        assertFalse(first.copyOfRange(14, 31).contentEquals(second.copyOfRange(14, 31)))
        assertEquals(first.size, second.size)
    }
}
