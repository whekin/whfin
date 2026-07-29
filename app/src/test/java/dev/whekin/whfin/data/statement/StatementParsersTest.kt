package dev.whekin.whfin.data.statement

import dev.whekin.whfin.data.statement.credo.CredoStatementParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The bank-neutral boundary: routing, failure, and what stays shared between banks. */
class StatementParsersTest {

    private fun file(name: String? = "statement.xlsx") = StatementFile(name, byteArrayOf(1, 2, 3))

    private fun fakeStatement(provider: String) = BankStatement(
        bank = BankProfile(provider, provider),
        accountIban = "GE00WH0000000000000000",
        currency = "GEL",
        periodFrom = LocalDate.of(2026, 1, 1),
        periodTo = LocalDate.of(2026, 1, 31),
        openingBalanceMinor = 0,
        closingBalanceMinor = 0,
        rows = emptyList(),
    )

    private inner class FakeParser(
        provider: String,
        private val accepts: Boolean,
        override val conversionNoteMarkers: List<String> = emptyList(),
        private val probeThrows: Boolean = false,
    ) : StatementParser {
        override val bank = BankProfile(provider, provider)
        var parsed = false
            private set

        override fun canParse(file: StatementFile): Boolean {
            if (probeThrows) throw IllegalStateException("broken probe")
            return accepts
        }

        override fun parse(file: StatementFile): BankStatement {
            parsed = true
            return fakeStatement(bank.provider)
        }
    }

    @Test
    fun `the first accepting adapter parses the file`() {
        val declining = FakeParser("A", accepts = false)
        val accepting = FakeParser("B", accepts = true)

        val statement = StatementParsers.parse(file(), listOf(declining, accepting))

        assertEquals("B", statement.bank.provider)
        assertEquals(false, declining.parsed)
        assertEquals(true, accepting.parsed)
    }

    @Test
    fun `an unknown format fails loudly instead of importing nothing`() {
        val error = assertThrows(UnsupportedStatementException::class.java) {
            StatementParsers.parse(file("mystery.csv"), listOf(FakeParser("A", accepts = false)))
        }
        assertEquals("mystery.csv", error.fileName)
    }

    @Test
    fun `a broken probe never blocks the next adapter`() {
        val broken = FakeParser("A", accepts = true, probeThrows = true)
        val healthy = FakeParser("B", accepts = true)

        val statement = StatementParsers.parse(file(), listOf(broken, healthy))

        assertEquals("B", statement.bank.provider)
    }

    @Test
    fun `bytes stay readable for every probe and for the parse that follows`() {
        val source = StatementFile("statement.xlsx", "credo bytes".toByteArray())
        val first = source.open().readBytes()
        val second = source.open().readBytes()

        assertEquals(String(first), String(second))
        assertEquals("credo bytes", String(second))
    }

    @Test
    fun `conversion vocabulary is collected from the registered adapters`() {
        val markers = StatementParsers.conversionNoteMarkers

        assertTrue(markers.containsAll(CredoStatementParser.conversionNoteMarkers))
        assertEquals(markers.distinct(), markers)
    }

    @Test
    fun `Credo is registered as an adapter, not as the pipeline itself`() {
        assertSame(
            CredoStatementParser,
            StatementParsers.all.single { it.bank.provider == "Credo" },
        )
    }

    @Test
    fun `own movement never counts as income or expense`() {
        val ownMovement = StatementOperation.entries.filter { it.isOwnMovement }.toSet()

        assertEquals(
            setOf(
                StatementOperation.OWN_TRANSFER,
                StatementOperation.CURRENCY_EXCHANGE,
                StatementOperation.SAVINGS_TOPUP,
            ),
            ownMovement,
        )
    }
}
