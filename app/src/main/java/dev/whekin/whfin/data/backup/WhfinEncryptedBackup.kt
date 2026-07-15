package dev.whekin.whfin.data.backup

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class WhfinBackupPassphraseException(message: String) : Exception(message)

/**
 * Бинарный конверт для шифрованного бэкапа. Ключ выводится из passphrase, не из Keystore:
 * копия обязана восстанавливаться на новом устройстве, где Keystore-ключей уже нет.
 *
 * Формат: magic(9) + version(1) + iterations(int32 BE) + saltLen(1)+salt + ivLen(1)+iv + AES-GCM ciphertext.
 * Внутри ciphertext — обычный whfin-backup JSON без изменений.
 */
object WhfinEncryptedBackupEnvelope {
    private val MAGIC = "WHFIN-ENC".toByteArray(Charsets.US_ASCII)
    private const val VERSION: Int = 1
    private const val KDF_ITERATIONS = 600_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val MIN_PASSPHRASE_LENGTH = 6
    /** Расшифровка буферизуется целиком, чтобы GCM-тег проверялся до парсинга. */
    private const val MAX_PLAINTEXT_BYTES = 256 * 1024 * 1024

    const val FILE_EXTENSION = "whfinbackup"

    fun validatePassphrase(passphrase: CharArray) {
        if (passphrase.size < MIN_PASSPHRASE_LENGTH) {
            throw WhfinBackupPassphraseException(
                "Passphrase must be at least $MIN_PASSPHRASE_LENGTH characters.",
            )
        }
    }

    /** Оборачивает выход так, что записанный в него plain JSON шифруется на лету. */
    fun encryptingStream(output: OutputStream, passphrase: CharArray): OutputStream {
        validatePassphrase(passphrase)
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt, KDF_ITERATIONS), GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        output.write(MAGIC)
        output.write(VERSION)
        output.write(byteArrayOf(
            (KDF_ITERATIONS ushr 24).toByte(),
            (KDF_ITERATIONS ushr 16).toByte(),
            (KDF_ITERATIONS ushr 8).toByte(),
            KDF_ITERATIONS.toByte(),
        ))
        output.write(salt.size)
        output.write(salt)
        output.write(iv.size)
        output.write(iv)
        return CipherOutputStream(output, cipher)
    }

    /**
     * Смотрит начало потока без потребления: если это шифрованный конверт, возвращает
     * поток с уже возвращёнными назад байтами и признак encrypted.
     */
    fun detect(input: InputStream): Pair<InputStream, Boolean> {
        val pushback = PushbackInputStream(input, MAGIC.size)
        val head = ByteArray(MAGIC.size)
        var read = 0
        while (read < head.size) {
            val count = pushback.read(head, read, head.size - read)
            if (count < 0) break
            read += count
        }
        pushback.unread(head, 0, read)
        return pushback to (read == MAGIC.size && head.contentEquals(MAGIC))
    }

    /**
     * Полностью расшифровывает конверт в память: AEAD-тег проверяется до того, как
     * хоть один байт уйдёт в JSON-парсер. Неверный passphrase → WhfinBackupPassphraseException.
     */
    fun decrypt(input: InputStream, passphrase: CharArray): InputStream {
        val data = DataInputStream(input)
        try {
            val magic = ByteArray(MAGIC.size).also(data::readFully)
            if (!magic.contentEquals(MAGIC)) {
                throw WhfinBackupException("This is not an encrypted WHFIN backup.")
            }
            val version = data.readUnsignedByte()
            if (version != VERSION) {
                throw WhfinBackupException("Unsupported encrypted backup version: $version.")
            }
            val iterations = data.readInt()
            if (iterations !in 10_000..10_000_000) {
                throw WhfinBackupException("Invalid encrypted backup key parameters.")
            }
            val salt = ByteArray(readBoundedLength(data, max = 64)).also(data::readFully)
            val iv = ByteArray(readBoundedLength(data, max = 32)).also(data::readFully)
            val ciphertext = readAllBounded(data)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt, iterations), GCMParameterSpec(GCM_TAG_BITS, iv))
            }
            val plain = try {
                cipher.doFinal(ciphertext)
            } catch (error: AEADBadTagException) {
                throw WhfinBackupPassphraseException("Wrong passphrase or corrupted backup file.")
            }
            return ByteArrayInputStream(plain)
        } catch (error: EOFException) {
            throw WhfinBackupException("Encrypted backup file is truncated.", error)
        }
    }

    private fun readAllBounded(input: InputStream): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(chunk)
            if (count < 0) break
            buffer.write(chunk, 0, count)
            if (buffer.size() > MAX_PLAINTEXT_BYTES) {
                throw WhfinBackupException("Encrypted backup is too large.")
            }
        }
        return buffer.toByteArray()
    }

    private fun readBoundedLength(data: DataInputStream, max: Int): Int {
        val length = data.readUnsignedByte()
        if (length !in 1..max) throw WhfinBackupException("Invalid encrypted backup structure.")
        return length
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        try {
            val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
            return SecretKeySpec(key.encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
