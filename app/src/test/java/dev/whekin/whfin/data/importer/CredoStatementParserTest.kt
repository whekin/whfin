package dev.whekin.whfin.data.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * Optional integration checks against private statement fixtures.
 *
 * Fixtures must live outside the repository and be supplied explicitly through
 * WHFIN_REAL_STATEMENT or WHFIN_REAL_STATEMENTS_DIR. The assertions intentionally
 * cover structural invariants only, so no personal metadata needs to be committed.
 */
class CredoStatementParserTest {

    private fun privateStatements(): List<File> {
        val single = System.getenv("WHFIN_REAL_STATEMENT")
            ?.let(::File)
            ?.takeIf(File::isFile)
        val directory = System.getenv("WHFIN_REAL_STATEMENTS_DIR")
            ?.let(::File)
            ?.takeIf(File::isDirectory)
            ?.listFiles { file -> file.extension.equals("xlsx", ignoreCase = true) }
            .orEmpty()
        return listOfNotNull(single) + directory
    }

    @Test
    fun `private statements parse and preserve their balance chain`() {
        val files = privateStatements()
        assumeTrue("private statements not configured, skipping", files.isNotEmpty())

        files.forEach { file ->
            val statement = file.inputStream().use(CredoStatementParser::parse)
            assertTrue("statement IBAN", statement.accountIban.matches(Regex("[A-Z]{2}[0-9]{2}[A-Z0-9]{18}")))
            assertTrue("statement currency", statement.currency.matches(Regex("[A-Z]{3}")))
            assertTrue("statement rows", statement.rows.isNotEmpty())
            val openingBalance = requireNotNull(statement.openingBalanceMinor)
            val closingBalance = requireNotNull(statement.closingBalanceMinor)
            assertEquals(
                "closing balance",
                closingBalance - openingBalance,
                statement.rows.sumOf { it.amountMinor },
            )

            var previous = openingBalance
            statement.rows.forEach { row ->
                row.balanceAfterMinor?.let { balance ->
                    assertEquals("balance chain", previous + row.amountMinor, balance)
                    previous = balance
                }
            }

            val unmapped = statement.rows
                .filter { it.operation == CredoStatementParser.OperationType.OTHER }
                .map { it.operationRaw }
                .distinct()
            assertEquals("unmapped operations: $unmapped", emptyList<String>(), unmapped)
        }
    }

    @Test
    fun `money parsing handles thousands separators`() {
        assertEquals(108320L, CredoStatementParser.moneyToMinor("1,083.20"))
        assertEquals(714L, CredoStatementParser.moneyToMinor("7.14"))
        assertEquals(6836060L, CredoStatementParser.moneyToMinor("68,360.60"))
        assertEquals(null, CredoStatementParser.moneyToMinor(""))
    }

    @Test
    fun `card description regex extracts merchant and purchase date`() {
        val statement = syntheticRow("გადახდა - Vip Pay*YANDEX.GO 25.20 GEL 14.07.2025")
        assertNotNull(statement)
        assertEquals("Vip Pay*YANDEX.GO", statement!!.merchantRaw)
        assertEquals(LocalDate.of(2025, 7, 14), statement.purchaseDate)
    }

    private fun syntheticRow(description: String): CredoStatementParser.Row? {
        val regex = Regex("""^გადახდა - (.+?)\s+[\d,]+\.\d{2} [A-Z]{3} (\d{2}\.\d{2}\.\d{4})$""")
        val match = regex.find(description) ?: return null
        return CredoStatementParser.Row(
            postedDate = LocalDate.now(),
            operation = CredoStatementParser.OperationType.CARD_PAYMENT,
            operationRaw = "საბარათე ოპერაცია",
            amountMinor = -2520,
            balanceAfterMinor = null,
            description = description,
            beneficiaryName = null,
            beneficiaryAccount = null,
            merchantRaw = match.groupValues[1].trim(),
            purchaseDate = LocalDate.parse(
                match.groupValues[2],
                java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            ),
        )
    }
}
