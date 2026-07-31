package dev.whekin.whfin.ui.feed

import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultManualAccountTest {

    private fun account(id: Long, type: AccountType, currency: String, name: String = "Ledger") =
        AccountEntity(id = id, name = name, type = type, groupId = null, currency = currency)

    @Test
    fun `cash wins over whatever the database returns first`() {
        val accounts = listOf(
            account(1, AccountType.CRYPTO, "TRX", "Wallet"),
            account(2, AccountType.BANK, "GEL", "Credo"),
            account(3, AccountType.CASH, "GEL", "Cash"),
        )

        assertEquals(3L, defaultManualAccount(accounts)?.id)
    }

    @Test
    fun `the main currency wins inside the preferred type`() {
        val accounts = listOf(
            account(1, AccountType.CASH, "USD"),
            account(2, AccountType.CASH, "GEL"),
        )

        assertEquals(2L, defaultManualAccount(accounts)?.id)
    }

    @Test
    fun `without cash the form starts on a bank ledger, not a wallet`() {
        val accounts = listOf(
            account(1, AccountType.CRYPTO, "USDT"),
            account(2, AccountType.SAVINGS, "GEL"),
            account(3, AccountType.BANK, "EUR"),
        )

        assertEquals(3L, defaultManualAccount(accounts)?.id)
    }

    @Test
    fun `a watch-only wallet is the last resort, never a silent default`() {
        val onlyCrypto = listOf(account(1, AccountType.CRYPTO, "ETH"))

        assertEquals(1L, defaultManualAccount(onlyCrypto)?.id)
        assertNull(defaultManualAccount(emptyList()))
    }
}
