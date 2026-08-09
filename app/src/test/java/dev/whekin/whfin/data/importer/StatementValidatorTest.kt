package dev.whekin.whfin.data.importer

import dev.whekin.whfin.data.statement.BankProfile
import dev.whekin.whfin.data.statement.BankStatement
import dev.whekin.whfin.data.statement.StatementOperation
import dev.whekin.whfin.data.statement.StatementRow
import java.time.LocalDate
import org.junit.Assert.assertThrows
import org.junit.Test

class StatementValidatorTest {
    @Test
    fun `accepts a complete balance chain`() {
        StatementValidator.validate(statement())
    }

    @Test
    fun `rejects a missing row balance`() {
        val statement = statement().let { value ->
            value.copy(rows = value.rows.mapIndexed { index, row ->
                if (index == 1) row.copy(balanceAfterMinor = null) else row
            })
        }

        assertThrows(InvalidStatementException::class.java) {
            StatementValidator.validate(statement)
        }
    }

    @Test
    fun `rejects a broken intermediate balance even when closing matches`() {
        val statement = statement().let { value ->
            value.copy(rows = value.rows.mapIndexed { index, row ->
                if (index == 0) row.copy(balanceAfterMinor = 9_001) else row
            })
        }

        assertThrows(InvalidStatementException::class.java) {
            StatementValidator.validate(statement)
        }
    }

    @Test
    fun `rejects rows outside the declared period`() {
        val statement = statement().let { value ->
            value.copy(rows = value.rows.mapIndexed { index, row ->
                if (index == 0) row.copy(postedDate = LocalDate.of(2025, 12, 31)) else row
            })
        }

        assertThrows(InvalidStatementException::class.java) {
            StatementValidator.validate(statement)
        }
    }

    private fun statement(): BankStatement = BankStatement(
        bank = BankProfile("Credo", "Credo"),
        accountIban = "GE00WH0000000000000000",
        currency = "GEL",
        periodFrom = LocalDate.of(2026, 1, 1),
        periodTo = LocalDate.of(2026, 1, 31),
        openingBalanceMinor = 10_000,
        closingBalanceMinor = 9_500,
        rows = listOf(
            row(LocalDate.of(2026, 1, 5), -1_000, 9_000),
            row(LocalDate.of(2026, 1, 8), 500, 9_500),
        ),
    )

    private fun row(date: LocalDate, amount: Long, balance: Long) = StatementRow(
        postedDate = date,
        operation = StatementOperation.OTHER,
        operationRaw = "test",
        amountMinor = amount,
        balanceAfterMinor = balance,
        description = "",
        beneficiaryName = null,
        beneficiaryAccount = null,
    )
}
