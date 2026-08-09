package dev.whekin.whfin.data.importer

import dev.whekin.whfin.data.statement.BankStatement

class InvalidStatementException(message: String) : Exception(message)

/**
 * Rejects a statement before the importer mutates the ledger.
 *
 * An adapter may understand the workbook shape while still returning incomplete financial data after
 * a bank-side format change. The balance chain is the bank-neutral proof that every money row survived
 * parsing, so it is checked at this seam instead of being left to individual adapters or UI callers.
 */
object StatementValidator {
    fun validate(statement: BankStatement) {
        if (statement.accountIban.isBlank()) invalid("Statement account is missing.")
        if (!statement.currency.matches(Regex("[A-Z0-9]{2,12}"))) {
            invalid("Statement currency is invalid.")
        }
        val from = statement.periodFrom
        val to = statement.periodTo
        if ((from == null) != (to == null)) invalid("Statement period is incomplete.")
        if (from != null && to != null && from > to) invalid("Statement period is reversed.")
        if (from != null && to != null && statement.rows.any { it.postedDate !in from..to }) {
            invalid("Statement contains a row outside its declared period.")
        }

        val opening = statement.openingBalanceMinor
        val closing = statement.closingBalanceMinor
        if ((opening == null) != (closing == null)) invalid("Statement balance summary is incomplete.")
        if (opening == null || closing == null) return

        var expected: Long = opening
        statement.rows.forEachIndexed { index, row ->
            expected = addExact(expected, row.amountMinor, index)
            val actual = row.balanceAfterMinor
                ?: invalid("Statement row ${index + 1} has no balance.")
            if (actual != expected) {
                invalid("Statement balance chain breaks at row ${index + 1}.")
            }
        }
        if (expected != closing) invalid("Statement closing balance does not match its rows.")
    }

    private fun addExact(balance: Long, amount: Long, rowIndex: Int): Long = try {
        Math.addExact(balance, amount)
    } catch (error: ArithmeticException) {
        throw InvalidStatementException("Statement amount overflows at row ${rowIndex + 1}.")
    }

    private fun invalid(message: String): Nothing = throw InvalidStatementException(message)
}
