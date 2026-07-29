package dev.whekin.whfin.data.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Addresses here are synthetic: the Tron ones are the base58check encodings of all-zero and
 * all-`0xDE` payloads, and the EVM ones are repeated nibbles. No personal wallet is involved.
 */
class CryptoAddressValidatorTest {

    private val tron = "T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwb"
    private val evm = "0x00112233445566778899aabbccddeeff00112233"

    private fun problem(network: CryptoNetwork, raw: String) =
        (CryptoAddressValidator.check(network, raw) as? CryptoAddressValidator.Result.Invalid)?.problem

    private fun valid(network: CryptoNetwork, raw: String) =
        (CryptoAddressValidator.check(network, raw) as? CryptoAddressValidator.Result.Valid)?.address

    @Test
    fun `an EVM address is accepted and stored lowercase`() {
        assertEquals(evm, valid(CryptoNetwork.ETHEREUM, evm.uppercase().replace("0X", "0x")))
        assertEquals(evm, valid(CryptoNetwork.ETHEREUM, " $evm "))
    }

    @Test
    fun `EVM rejects a wrong prefix or length`() {
        assertEquals(CryptoAddressValidator.Problem.FORMAT, problem(CryptoNetwork.ETHEREUM, evm.drop(2)))
        assertEquals(CryptoAddressValidator.Problem.FORMAT, problem(CryptoNetwork.ETHEREUM, evm + "aa"))
        assertEquals(CryptoAddressValidator.Problem.FORMAT, problem(CryptoNetwork.ETHEREUM, evm.dropLast(1)))
        assertEquals(
            CryptoAddressValidator.Problem.FORMAT,
            problem(CryptoNetwork.ETHEREUM, evm.dropLast(1) + "z"),
        )
    }

    @Test
    fun `a Tron address is accepted with its exact base58 casing`() {
        assertEquals(tron, valid(CryptoNetwork.TRON, tron))
        assertEquals(tron, valid(CryptoNetwork.TRON, " $tron "))
    }

    @Test
    fun `a second Tron payload also validates`() {
        assertEquals(
            "TWHdzxwX6144Btjk1hvrRcVdiagwvUHika",
            valid(CryptoNetwork.TRON, "TWHdzxwX6144Btjk1hvrRcVdiagwvUHika"),
        )
    }

    @Test
    fun `a mistyped Tron address fails on its checksum instead of being accepted`() {
        val mistyped = tron.dropLast(1) + if (tron.last() == 'b') 'c' else 'b'

        assertEquals(CryptoAddressValidator.Problem.CHECKSUM, problem(CryptoNetwork.TRON, mistyped))
    }

    @Test
    fun `Tron rejects wrong length, wrong prefix and non-base58 characters`() {
        assertEquals(CryptoAddressValidator.Problem.FORMAT, problem(CryptoNetwork.TRON, tron.drop(1)))
        assertEquals(CryptoAddressValidator.Problem.FORMAT, problem(CryptoNetwork.TRON, "A" + tron.drop(1)))
        assertEquals(CryptoAddressValidator.Problem.FORMAT, problem(CryptoNetwork.TRON, tron.dropLast(1) + "0"))
    }

    @Test
    fun `an address of one network is never valid on the other`() {
        assertEquals(CryptoAddressValidator.Problem.FORMAT, problem(CryptoNetwork.ETHEREUM, tron))
        assertEquals(CryptoAddressValidator.Problem.FORMAT, problem(CryptoNetwork.TRON, evm))
    }

    @Test
    fun `an empty address is reported as empty, not malformed`() {
        assertEquals(CryptoAddressValidator.Problem.EMPTY, problem(CryptoNetwork.TRON, "   "))
        assertEquals(CryptoAddressValidator.Problem.EMPTY, problem(CryptoNetwork.ETHEREUM, ""))
    }

    @Test
    fun `each network exposes only assets it can actually track`() {
        assertEquals(listOf("ETH", "USDT"), CryptoNetwork.ETHEREUM.assets.map { it.symbol })
        assertEquals(listOf("TRX", "USDT"), CryptoNetwork.TRON.assets.map { it.symbol })
        assertEquals(listOf("ETH", "USDT", "TRX"), CryptoNetwork.supportedSymbols)
        assertNull(CryptoNetwork.ETHEREUM.asset("BTC"))
        assertNull(CryptoNetwork.TRON.asset("TON"))
    }

    @Test
    fun `USDT is a different asset on each chain`() {
        val ethereum = CryptoNetwork.ETHEREUM.asset("usdt")!!
        val tronUsdt = CryptoNetwork.TRON.asset("USDT")!!

        assertTrue(ethereum.contractAddress != tronUsdt.contractAddress)
        assertEquals(6, ethereum.decimals)
        assertEquals(6, tronUsdt.decimals)
        assertNull(CryptoNetwork.ETHEREUM.asset("ETH")!!.contractAddress)
        assertEquals(18, CryptoNetwork.ETHEREUM.asset("ETH")!!.decimals)
    }

    @Test
    fun `chain ids round-trip`() {
        CryptoNetwork.entries.forEach { network ->
            assertEquals(network, CryptoNetwork.byChainId(network.chainId))
        }
        assertNull(CryptoNetwork.byChainId("bip122:bitcoin"))
    }
}
