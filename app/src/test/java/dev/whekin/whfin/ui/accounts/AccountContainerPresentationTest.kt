package dev.whekin.whfin.ui.accounts

import dev.whekin.whfin.ui.ledgerOwnName
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.BankProduct
import dev.whekin.whfin.data.rates.ExchangeRate
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountContainerPresentationTest {
    @Test
    fun cardBackedEverydayAccountLeadsRegardlessOfIbanOrder() {
        val deposit = row(
            id = 1,
            name = "Deposit",
            currency = "GEL",
            iban = "GE00CD0000000000000001",
            bankProduct = BankProduct.DEMAND_DEPOSIT,
        )
        val everyday = row(
            id = 2,
            name = "Everyday",
            currency = "GEL",
            iban = "GE00CD0000000000009999",
            bankProduct = BankProduct.CURRENT_ACCOUNT,
            cards = listOf("3241"),
        )

        val ordered = orderedAccountContainers(listOf(deposit, everyday))

        assertEquals("Everyday", ordered.first().first().account.name)
        assertEquals(listOf("3241"), ordered.first().first().cardMasks)
    }

    /**
     * A statement names what it creates after the bank, the currency and the account number, all
     * three of which the screen has already printed above the ledger. Nothing of that name is the
     * account's own, so the card must not repeat it once per currency.
     */
    @Test
    fun anImportedLedgerNameCarriesNothingTheScreenHasNotSaid() {
        val imported = row(1, "Credo GEL •0001", "GEL", "GE00CD0000000000000001")

        assertEquals(null, ledgerOwnName(imported.account, "Credo"))
    }

    @Test
    fun aNameThePersonChoseSurvivesTheSameRemoval() {
        val named = row(1, "Travel", "EUR", "GE00CD0000000000000001")
        val seeded = row(2, "Cash", "GEL", iban = "")

        assertEquals("Travel", ledgerOwnName(named.account, "Credo"))
        // WHFIN wrote this one itself, in whatever language was current; it names nothing.
        assertEquals(null, ledgerOwnName(seeded.account.copy(iban = null), "Cash"))
    }

    @Test
    fun eachIbanGetsOneConvertedTotalAcrossItsCurrencyLedgers() {
        val rows = listOf(
            row(1, "Everyday", "GEL", "GE00CD1", balance = 100_00),
            row(2, "Everyday", "USD", "GE00CD1", balance = 10_00),
            row(3, "Reserve", "GEL", "GE00CD2", balance = 50_00),
        )
        val rates = mapOf(
            "USD" to ExchangeRate("USD", BigDecimal("2.70"), observedAt = 1),
        )

        val totals = buildAccountContainerTotals(rows, rates, "GEL")

        assertEquals(BigDecimal("127.00"), totals[accountContainerKey(rows[0].account)]?.amount)
        assertEquals(BigDecimal("50.00"), totals[accountContainerKey(rows[2].account)]?.amount)
        assertTrue(totals.values.all { it.isComplete })
    }

    private fun row(
        id: Long,
        name: String,
        currency: String,
        iban: String,
        bankProduct: BankProduct? = null,
        cards: List<String> = emptyList(),
        balance: Long = 0,
    ) = AccountWithBalance(
        account = AccountEntity(
            id = id,
            name = name,
            type = AccountType.BANK,
            groupId = 1,
            currency = currency,
            iban = iban,
            bankProduct = bankProduct,
        ),
        balanceMinor = balance,
        cardMasks = cards,
        groupName = "Credo",
    )
}
