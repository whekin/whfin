package dev.whekin.whfin.data.importer

import dev.whekin.whfin.data.statement.BankStatement
import dev.whekin.whfin.data.statement.StatementRow
import java.time.LocalDate

/**
 * What makes a statement row the same row when the same statement is imported twice.
 *
 * Deduplication used to be a string assembled inside the import loop, which meant the one rule that
 * decides whether money is counted once or twice was invisible and untestable. It lives here instead.
 *
 * Currency is part of the identity, not decoration: one IBAN can hold a GEL and a USD ledger, and the
 * same amount on the same day in each is two different movements. The ordinal disambiguates rows a
 * bank prints identically — same date, same amount, same running balance — and is assigned in file
 * order, so re-importing the same file lands on the same keys.
 */
class StatementIdentity(private val iban: String, private val currency: String) {

    private val ordinals = mutableMapOf<String, Int>()

    /** Stable key of one row within this ledger. Call once per row, in file order. */
    fun rowKey(row: StatementRow): String {
        val base = listOf(
            iban,
            currency,
            row.postedDate,
            row.amountMinor,
            row.balanceAfterMinor ?: "",
        ).joinToString(SEPARATOR)
        val ordinal = ordinals.merge(base, 1, Int::plus)!!
        return "$ROW_PREFIX$SEPARATOR$base$SEPARATOR$ordinal"
    }

    /**
     * Key of the ledger's single opening anchor.
     *
     * A fiat balance is the sum of its rows, so exactly one anchor may describe the earliest imported
     * period; importing older history moves this key's row rather than stacking a second opening
     * balance on top of the existing ledger.
     */
    fun openingKey(earliestPeriodFrom: LocalDate): String =
        listOf(OPENING_PREFIX, iban, currency, earliestPeriodFrom).joinToString(SEPARATOR)

    companion object {
        private const val SEPARATOR = "|"
        const val ROW_PREFIX = "stmt"
        const val OPENING_PREFIX = "opening"

        fun of(statement: BankStatement): StatementIdentity =
            StatementIdentity(statement.accountIban, statement.currency)

        /** True for keys this class owns, so other provenance is never mistaken for statement truth. */
        fun isStatementRowKey(externalKey: String?): Boolean =
            externalKey?.startsWith("$ROW_PREFIX$SEPARATOR") == true
    }
}
