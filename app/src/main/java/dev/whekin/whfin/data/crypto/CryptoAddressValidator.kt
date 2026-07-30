package dev.whekin.whfin.data.crypto

import java.math.BigInteger
import java.security.MessageDigest

/**
 * Network-specific address validation.
 *
 * A watch-only address is the only thing standing between a typo and a permanently wrong balance, so
 * the network is an explicit user choice and the address is checked against that network only.
 */
object CryptoAddressValidator {

    sealed interface Result {
        /** Storable form: lowercase for EVM, exact base58 for Tron. */
        data class Valid(val address: String) : Result
        data class Invalid(val problem: Problem) : Result
    }

    enum class Problem {
        EMPTY,

        /** Wrong prefix, length, or alphabet for the selected network. */
        FORMAT,

        /** Shape is right but the built-in checksum does not match, i.e. a typo. */
        CHECKSUM,
    }

    private val evmPattern = Regex("^0x[0-9a-fA-F]{40}$")
    private const val TRON_VERSION = 0x41.toByte()
    private const val BASE58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

    fun check(network: CryptoNetwork, raw: String): Result {
        val address = raw.trim().filterNot(Char::isWhitespace)
        if (address.isEmpty()) return Result.Invalid(Problem.EMPTY)
        return when (network) {
            CryptoNetwork.ETHEREUM -> checkEvm(address)
            CryptoNetwork.TRON -> checkTron(address)
        }
    }

    fun isValid(network: CryptoNetwork, raw: String): Boolean = check(network, raw) is Result.Valid

    /**
     * EIP-55 mixed-case checksums need Keccak-256, which the platform does not provide, so casing is
     * accepted as typed and normalized away. Shape is still enforced.
     */
    private fun checkEvm(address: String): Result =
        if (evmPattern.matches(address)) {
            Result.Valid(address.lowercase())
        } else {
            Result.Invalid(Problem.FORMAT)
        }

    private fun checkTron(address: String): Result {
        if (address.length != 34 || !address.startsWith("T")) return Result.Invalid(Problem.FORMAT)
        val decoded = decodeBase58(address) ?: return Result.Invalid(Problem.FORMAT)
        if (decoded.size != 25 || decoded[0] != TRON_VERSION) return Result.Invalid(Problem.FORMAT)
        val payload = decoded.copyOfRange(0, 21)
        val checksum = decoded.copyOfRange(21, 25)
        val expected = sha256(sha256(payload)).copyOfRange(0, 4)
        return if (checksum.contentEquals(expected)) {
            Result.Valid(address)
        } else {
            Result.Invalid(Problem.CHECKSUM)
        }
    }

    /**
     * The 20 raw address bytes as hex, without the `0x41` Tron version prefix.
     * Contract calls take this form, so the conversion stays next to the base58 decoder.
     */
    fun tronAddressHex(address: String): String? {
        val decoded = decodeBase58(address.trim()) ?: return null
        if (decoded.size != 25 || decoded[0] != TRON_VERSION) return null
        return decoded.copyOfRange(1, 21).joinToString("") { "%02x".format(it) }
    }

    private fun decodeBase58(input: String): ByteArray? {
        var value = BigInteger.ZERO
        input.forEach { char ->
            val digit = BASE58.indexOf(char)
            if (digit < 0) return null
            value = value.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(digit.toLong()))
        }
        val body = value.toByteArray().let { bytes ->
            // BigInteger keeps a leading sign byte; base58 payloads are unsigned.
            if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
        }
        val leadingZeros = input.takeWhile { it == BASE58[0] }.length
        return ByteArray(leadingZeros) + body
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)
}
