package dev.whekin.whfin.data.credo

import dev.whekin.whfin.data.db.BankProduct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CredoRemoteAccountProductTest {
    @Test
    fun bankProductUsesBankMetadataWithoutChoosingTheOwnersFundRole() {
        assertEquals(
            BankProduct.CURRENT_ACCOUNT,
            account(category = "მიმდინარე", type = "ACCOUNT").bankProduct,
        )
        assertEquals(
            BankProduct.DEMAND_DEPOSIT,
            account(category = "მოთხოვნამდე 2", type = "DEPOSIT").bankProduct,
        )
        assertEquals(
            BankProduct.TERM_DEPOSIT,
            account(category = "Term deposit", type = "DEPOSIT").bankProduct,
        )
        assertNull(account(category = "Saving deposit", type = "DEPOSIT").bankProduct)
    }

    private fun account(category: String, type: String) = CredoRemoteAccount(
        accountNumber = "GE00CD1",
        currency = "GEL",
        accountId = 1,
        category = category,
        type = type,
    )
}
