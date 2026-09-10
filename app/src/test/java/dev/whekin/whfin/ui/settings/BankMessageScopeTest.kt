package dev.whekin.whfin.ui.settings

import dev.whekin.whfin.data.db.*
import dev.whekin.whfin.data.sms.BankSmsBank
import org.junit.Test
import org.junit.Assert.*

class BankMessageScopeTest {
    @Test fun bankPageKeepsOnlyItsMessagesAccountsAndCardMappings() {
        val credo = AccountEntity(id = 1, name = "Everyday", type = AccountType.BANK, currency = "GEL", iban = "GE00CD0000000000000001")
        val tbc = AccountEntity(id = 2, name = "Everyday", type = AccountType.BANK, currency = "GEL", iban = "GE00TB0000000000000002")
        val families = listOf(SmsCardFamily(1, "Credo", credo.iban, listOf(credo)), SmsCardFamily(2, "TBC", tbc.iban, listOf(tbc)))
        fun diagnostic(id: Long, key: String) = SmsDiagnosticEntity(id = id, externalKey = key, kind = SmsDiagnosticKind.UNRECOGNIZED,
            outcome = SmsDiagnosticOutcome.UNRECOGNIZED, receivedAt = 1, updatedAt = 1)
        val data = SmsDiagnosticsData(listOf(diagnostic(1, "sms|example"), diagnostic(2, "sms|tbc|push|example")),
            listOf(SmsAccountOption(credo, "Credo"), SmsAccountOption(tbc, "TBC")), families)
        val filtered = data.forBank(BankSmsBank.TBC)
        assertEquals(listOf(2L), filtered.diagnostics.map { it.id })
        assertEquals(listOf(2L), filtered.accounts.map { it.account.id })
        assertEquals(listOf(2L), filtered.cardFamilies.map { it.primaryAccountId })
    }
}
