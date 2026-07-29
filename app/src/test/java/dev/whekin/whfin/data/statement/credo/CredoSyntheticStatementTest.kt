package dev.whekin.whfin.data.statement.credo

import dev.whekin.whfin.data.statement.StatementFile
import dev.whekin.whfin.data.statement.StatementOperation
import dev.whekin.whfin.data.statement.StatementParsers
import dev.whekin.whfin.data.statement.SyntheticCredoWorkbook
import dev.whekin.whfin.data.statement.SyntheticCredoWorkbook.Row
import dev.whekin.whfin.data.statement.UnsupportedStatementException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Golden coverage of the Credo adapter on generated data, so the mapping onto the bank-neutral model
 * is verified without any private statement present.
 */
class CredoSyntheticStatementTest {

    private val cardPayment = Row(
        date = LocalDate.of(2026, 1, 12),
        operation = "საბარათე ოპერაცია",
        debit = "7.14",
        balance = "92.86",
        description = "გადახდა - NIKORA 7.14 GEL 09.01.2026",
    )
    private val ownTransfer = Row(
        date = LocalDate.of(2026, 1, 13),
        operation = "საკუთარ ანგარიშებს შორის გადარიცხვა",
        debit = "20.00",
        balance = "72.86",
        description = "Own accounts",
        beneficiaryName = "Account owner",
        beneficiaryAccount = "GE00WH0000000000000001",
    )
    private val conversion = Row(
        date = LocalDate.of(2026, 1, 14),
        operation = "უნაღდო კონვერტაცია",
        debit = "5.00",
        balance = "67.86",
        description = "currency exchange",
    )
    private val fee = Row(
        date = LocalDate.of(2026, 1, 15),
        operation = "ლარის გადარიცხვის საკომისიო",
        debit = "1.00",
        balance = "66.86",
        description = "Transfer fee",
    )
    private val incoming = Row(
        date = LocalDate.of(2026, 1, 16),
        operation = "სხვა ბანკიდან ჩარიცხვა",
        credit = "3.00",
        balance = "69.86",
        description = "Incoming",
        beneficiaryName = "Synthetic Sender",
    )
    private val unknown = Row(
        date = LocalDate.of(2026, 1, 17),
        operation = "სრულიად უცნობი ოპერაცია",
        debit = "6.00",
        balance = "63.86",
        description = "Unknown kind",
    )

    private fun parse(vararg rows: Row) = CredoStatementParser.parse(
        StatementFile(
            "statement.xlsx",
            SyntheticCredoWorkbook.build(rows = rows.toList()),
        ),
    )

    @Test
    fun `account metadata and period are read from the details sheet`() {
        val statement = parse(cardPayment)

        assertEquals("Credo", statement.bank.provider)
        assertEquals(SyntheticCredoWorkbook.IBAN, statement.accountIban)
        assertEquals("GEL", statement.currency)
        assertEquals(LocalDate.of(2026, 1, 1), statement.periodFrom)
        assertEquals(LocalDate.of(2026, 1, 31), statement.periodTo)
        assertEquals(10000L, statement.openingBalanceMinor)
        assertEquals(6386L, statement.closingBalanceMinor)
    }

    @Test
    fun `a card payment keeps merchant, purchase date and a negative amount`() {
        val row = parse(cardPayment).rows.single()

        assertEquals(StatementOperation.CARD_PAYMENT, row.operation)
        assertEquals("NIKORA", row.merchantRaw)
        assertEquals(LocalDate.of(2026, 1, 9), row.purchaseDate)
        assertEquals(LocalDate.of(2026, 1, 12), row.postedDate)
        assertEquals(-714L, row.amountMinor)
        assertEquals(9286L, row.balanceAfterMinor)
    }

    @Test
    fun `credit rows stay positive and keep the counterparty`() {
        val row = parse(incoming).rows.single()

        assertEquals(StatementOperation.TRANSFER_IN, row.operation)
        assertEquals(300L, row.amountMinor)
        assertEquals("Synthetic Sender", row.beneficiaryName)
        assertNull(row.merchantRaw)
    }

    @Test
    fun `own transfers and conversions are marked as own movement, fees are not`() {
        val rows = parse(ownTransfer, conversion, fee).rows

        assertEquals(
            listOf(true, true, false),
            rows.map { it.operation.isOwnMovement },
        )
        assertEquals(StatementOperation.FEE, rows[2].operation)
        assertEquals("GE00WH0000000000000001", rows[0].beneficiaryAccount)
    }

    @Test
    fun `an unmapped operation degrades to OTHER and keeps its raw name`() {
        val row = parse(unknown).rows.single()

        assertEquals(StatementOperation.OTHER, row.operation)
        assertEquals("სრულიად უცნობი ოპერაცია", row.operationRaw)
    }

    @Test
    fun `the balance chain of a generated statement stays consistent`() {
        val statement = parse(cardPayment, ownTransfer, conversion, fee, incoming, unknown)

        assertEquals(
            statement.closingBalanceMinor!! - statement.openingBalanceMinor!!,
            statement.rows.sumOf { it.amountMinor },
        )
        var previous = statement.openingBalanceMinor!!
        statement.rows.forEach { row ->
            assertEquals(previous + row.amountMinor, row.balanceAfterMinor)
            previous = row.balanceAfterMinor!!
        }
    }

    @Test
    fun `the registry routes a Credo workbook to the Credo adapter`() {
        val file = StatementFile("statement.xlsx", SyntheticCredoWorkbook.build(rows = listOf(cardPayment)))

        assertEquals("Credo", StatementParsers.parse(file).bank.provider)
    }

    @Test
    fun `a workbook without the details sheet is not claimed by Credo`() {
        val file = StatementFile(
            "other-bank.xlsx",
            SyntheticCredoWorkbook.build(rows = listOf(cardPayment), includeDetailsSheet = false),
        )

        assertTrue(!CredoStatementParser.canParse(file))
        assertThrows(UnsupportedStatementException::class.java) { StatementParsers.parse(file) }
    }
}
