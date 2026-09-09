package dev.whekin.whfin.data.statement.tbc

import dev.whekin.whfin.data.statement.*
import dev.whekin.whfin.data.importer.StatementValidator
import dev.whekin.whfin.data.importer.StatementIdentity
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import java.io.File
import java.time.LocalDate

class TbcStatementParserTest {
    private fun parse(bytes: ByteArray = SyntheticTbcWorkbook.build()) =
        StatementParsers.parse(StatementFile("statement.xlsx", bytes))

    @Test fun `summary balances and all rows survive the registered parser`() {
        val s = parse()
        StatementValidator.validate(s)
        assertEquals("TBC", s.bank.provider)
        assertEquals(SyntheticTbcWorkbook.IBAN, s.accountIban)
        assertEquals(LocalDate.of(2025, 9, 9), s.periodFrom)
        assertEquals(20100L, s.closingBalanceMinor)
        assertEquals(listOf(25000L, -700L, -4000L, -200L), s.rows.map { it.amountMinor })
        assertEquals(StatementOperation.FEE, s.rows[1].operation)
        assertEquals("Example Processor", s.rows[0].beneficiaryName)
    }
    @Test fun `POS wallet and POS are purchases on the purchase day not transfers`() {
        val rows = parse().rows.drop(2)
        assertEquals(listOf("EXAMPLE DENTIST", "EXAMPLE BUS"), rows.map { it.merchantRaw })
        rows.forEach {
            assertEquals(StatementOperation.CARD_PAYMENT, it.operation)
            assertEquals(LocalDate.of(2026, 9, 8), it.purchaseDate)
            assertEquals(LocalDate.of(2026, 9, 9), it.postedDate)
        }
    }
    @Test fun `bank ID keeps identity through a settlement correction`() {
        val row = parse().rows[2]
        val keys = StatementIdentity.of(parse())
        assertEquals(keys.rowKey(row), keys.rowKey(row.copy(amountMinor = -4200, balanceAfterMinor = 19000)))
        assertNotEquals(keys.rowKey(row), keys.rowKey(row.copy(bankTransactionId = "999")))
    }
    @Test fun `duplicate IDs invalid amounts dates and broken POS stop the whole file`() {
        for ((col, value) in listOf(9 to "101", 2 to "4.001", 0 to "46274.5", 1 to "POS - unreadable")) {
            val rows = SyntheticTbcWorkbook.defaultRows.map { it.toMutableList() }
            rows[2][col] = value
            assertThrows(MalformedStatementException::class.java) { parse(SyntheticTbcWorkbook.build(rows)) }
        }
    }
    @Test fun `turnover mismatch cannot silently drop a financial row`() {
        assertThrows(MalformedStatementException::class.java) {
            parse(SyntheticTbcWorkbook.build(SyntheticTbcWorkbook.defaultRows.dropLast(1)))
        }
    }
    @Test fun `unknown operation stays visible and never becomes an own transfer`() {
        val rows = SyntheticTbcWorkbook.defaultRows.map { it.toMutableList() }
        rows[0][5] = "Future bank type"
        assertEquals(setOf("Future bank type"), parse(SyntheticTbcWorkbook.build(rows)).unmappedOperationNames)
    }
    @Test fun `empty statement retains its declared coverage`() {
        val s = parse(SyntheticTbcWorkbook.build(emptyList(), "0", "0", "0"))
        StatementValidator.validate(s)
        assertTrue(s.rows.isEmpty())
        assertNotNull(s.periodFrom)
    }
    @Test fun `optional private statement validates without publishing its contents`() {
        val path = System.getenv("WHFIN_REAL_STATEMENT")
        assumeTrue(path != null)
        val s = parse(File(requireNotNull(path)).readBytes())
        assumeTrue(s.bank.provider == "TBC")
        StatementValidator.validate(s)
        assertEquals(s.rows.size, s.rows.map { it.bankTransactionId }.distinct().size)
    }
    @Test fun `origin note evidence belongs only to TBC and must name a unique account`() {
        val note = "Example Owner, ა/ნ: ${SyntheticTbcWorkbook.CREDO_IBAN}"
        assertEquals(SyntheticTbcWorkbook.CREDO_IBAN, StatementParsers.originAccountFromNote(SyntheticTbcWorkbook.IBAN, note))
        assertNull(StatementParsers.originAccountFromNote(SyntheticTbcWorkbook.CREDO_IBAN, note))
        assertNull(StatementParsers.originAccountFromNote(SyntheticTbcWorkbook.IBAN, "$note ა/ნ: GE00CD0000000000000002"))
    }
}
