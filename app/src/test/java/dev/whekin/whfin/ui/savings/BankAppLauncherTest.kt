package dev.whekin.whfin.ui.savings

import dev.whekin.whfin.data.db.*
import org.junit.Assert.assertEquals
import org.junit.Test

class BankAppLauncherTest {
    @Test fun `catalog recognizes only official supported providers`() {
        assertEquals(SupportedBankApp.CREDO, supportedBankApp("Credo Bank"))
        assertEquals(SupportedBankApp.TBC, supportedBankApp("TBC Bank"))
        assertEquals(null, supportedBankApp("Credo Business"))
        assertEquals(null, supportedBankApp("Unknown"))
    }

    @Test fun `only installed apps belonging to current reserve currency are offered`() {
        val groups = listOf(
            FinancialGroupEntity(id = 1, name = "Credo", type = FinancialGroupType.BANK, provider = "Credo"),
            FinancialGroupEntity(id = 2, name = "TBC", type = FinancialGroupType.BANK, provider = "TBC Bank"),
        )
        val accounts = listOf(
            account(1, 1, "GEL", FundRole.RESERVE),
            account(2, 2, "USD", FundRole.RESERVE),
            account(3, 2, "GEL", FundRole.AVAILABLE),
        )
        assertEquals(listOf(SupportedBankApp.CREDO), bankAppsForReserve(accounts, groups, "GEL") { true })
        assertEquals(emptyList<SupportedBankApp>(), bankAppsForReserve(accounts, groups, "GEL") { false })
        assertEquals(listOf(SupportedBankApp.TBC), bankAppsForReserve(accounts, groups, "USD") { true })
    }

    private fun account(id: Long, groupId: Long, currency: String, role: FundRole) = AccountEntity(
        id = id, name = "Ledger", type = AccountType.BANK, groupId = groupId,
        currency = currency, fundRole = role,
    )
}
