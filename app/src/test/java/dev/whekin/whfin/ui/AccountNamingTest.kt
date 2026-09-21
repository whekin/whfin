package dev.whekin.whfin.ui

import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import org.junit.Assert.*
import org.junit.Test

class AccountNamingTest {
    @Test fun `source names inside a chosen word do not mutilate that word`() {
        val cash = AccountEntity(name = "Cashmere", type = AccountType.CASH, currency = "GEL")
        assertEquals("Cashmere", ledgerOwnName(cash, "Cash"))
        assertEquals("MyTBC", ledgerOwnName(cash.copy(name = "MyTBC"), "TBC"))
    }

    @Test fun `generated bank names still collapse to their surrounding context`() {
        val bank = AccountEntity(name = "Credo GEL •0001", type = AccountType.BANK, currency = "GEL",
            iban = "GE00CD0000000000000001")
        assertNull(ledgerOwnName(bank, "Credo"))
        assertEquals("Travel", ledgerOwnName(bank.copy(name = "Credo Travel"), "Credo"))
    }
}
