package dev.whekin.whfin.ui.accounts

import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountOverviewTest {
    @Test
    fun `overview keeps currencies native and separates assets from liabilities`() {
        val accounts = listOf(
            account(1, "Credo", AccountType.BANK, "GEL", 10_000, group = "Credo"),
            account(2, "Overdraft", AccountType.BANK, "GEL", -2_000, group = "Credo"),
            account(3, "Reserve", AccountType.SAVINGS, "GEL", 3_000),
            account(4, "Credo USD", AccountType.BANK, "USD", 500, group = "Credo"),
        )

        val overview = accountOverviewData(accounts)

        assertEquals(11_000, overview.netWorthMinor)
        assertEquals(13_000, overview.assetsMinor)
        assertEquals(2_000, overview.liabilitiesMinor)
        assertEquals(8_000, overview.availableMinor)
        assertEquals(3_000, overview.reserveMinor)
        assertEquals(
            listOf(AccountSourceShare("Credo", 10_000), AccountSourceShare("Reserve", 3_000)),
            overview.sources,
        )
        assertEquals(listOf(NativeCurrencyBalance("USD", 500)), overview.otherCurrencies)
    }

    private fun account(
        id: Long,
        name: String,
        type: AccountType,
        currency: String,
        balanceMinor: Long,
        group: String? = null,
    ) = AccountWithBalance(
        account = AccountEntity(id = id, name = name, type = type, currency = currency),
        balanceMinor = balanceMinor,
        cardMasks = emptyList(),
        groupName = group,
    )
}
