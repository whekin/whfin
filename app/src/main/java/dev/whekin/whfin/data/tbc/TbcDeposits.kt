package dev.whekin.whfin.data.tbc

import dev.whekin.whfin.data.LedgerCalendar
import dev.whekin.whfin.data.statement.StatementOperation
import dev.whekin.whfin.data.statement.StatementRow
import java.math.BigDecimal
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

/**
 * A savings/deposit product, which TBC keeps outside the cards-and-accounts dashboard.
 *
 * "My Safe" and term deposits are neither a card product nor a dashboard saving row, so account
 * discovery that reads only those two lists cannot see them at all. They have their own listing and
 * their own movement endpoint, whose rows are deposit/withdrawal/interest amounts rather than the
 * PFM movements the checking accounts return.
 */
data class TbcDepositAccount(
    val id: String,
    val accountNo: String,
    val currency: String,
    val name: String,
    /** The figure the bank shows for the product; used only to notice a gap, never as an anchor. */
    val balanceMinor: Long?,
    /** The bank's own statement about whether the product accepts further top-ups. */
    val acceptsTopUp: Boolean?,
) {
    val key: String get() = "$accountNo|$currency"
    val label: String
        get() = listOfNotNull(name.takeIf { it.isNotBlank() }, currency, "•${accountNo.takeLast(4)}")
            .joinToString(" · ")
}

/**
 * Movements of one deposit together with the balance they start and end at.
 *
 * The bank prints a running balance on every row, so the opening is its own arithmetic rather than
 * an owner estimate: the balance before the earliest row is what the bank itself says it was. A
 * truncated list stays safe for the same reason — the anchor describes the imported period only.
 */
data class TbcDepositStatement(val rows: List<StatementRow>, val openingMinor: Long, val closingMinor: Long)

internal object TbcDepositParser {

    private data class Raw(
        val date: Long,
        val amountMinor: Long,
        val balanceMinor: Long,
        val operation: StatementOperation,
        val description: String,
    )

    fun accounts(json: JSONObject): List<TbcDepositAccount> {
        // A second page would silently hide deposits, which is exactly the failure being fixed here.
        if (json.opt("nextPageId")?.takeUnless { it == JSONObject.NULL } != null) throw TbcException("DEPOSIT_FORMAT")
        val items = json.optJSONArray("items") ?: throw TbcException("DEPOSIT_FORMAT")
        return (0 until items.length()).map { index ->
            val item = items.getJSONObject(index)
            val id = item.optLong("id").takeIf { it > 0 } ?: throw TbcException("DEPOSIT_FORMAT")
            val currency = item.optString("currency").takeIf { it.matches(Regex("[A-Z]{3}")) }
                ?: throw TbcException("DEPOSIT_FORMAT")
            val accountNo = item.optString("accountNo").takeIf { it.isNotBlank() && it != "null" }
                ?: throw TbcException("DEPOSIT_FORMAT")
            TbcDepositAccount(
                id = id.toString(),
                accountNo = accountNo,
                currency = currency,
                name = item.optString("friendlyName").takeUnless { it == "null" }.orEmpty(),
                balanceMinor = minor(item.opt("currentAmount")),
                acceptsTopUp = item.opt("addAmountPossibility")
                    ?.takeUnless { it == JSONObject.NULL }?.let { item.optBoolean("addAmountPossibility") },
            )
        }
    }

    /**
     * Reads one deposit's movements.
     *
     * Two things the response does not state are settled by the balance chain instead of by
     * assumption: which end of the list is the oldest row, and whether a withdrawal is printed as a
     * positive amount to subtract or as an already signed one. The documented convention is tried
     * first, and the alternative only when at least two rows can actually prove it. A list that
     * proves neither is refused rather than imported at a guessed sign.
     */
    fun statement(json: JSONArray): TbcDepositStatement {
        val documented = rows(json, signedWithdrawal = false)
        if (documented.isEmpty()) return TbcDepositStatement(emptyList(), 0L, 0L)
        val candidates = buildList {
            add(documented); add(documented.reversed())
            if (documented.size >= 2) {
                val signed = rows(json, signedWithdrawal = true)
                add(signed); add(signed.reversed())
            }
        }
        val ordered = candidates.firstOrNull(::chained) ?: throw TbcException("DEPOSIT_CHAIN")
        val opening = Math.subtractExact(ordered.first().balanceMinor, ordered.first().amountMinor)
        return TbcDepositStatement(
            // A zero row moves no money and no balance; keeping it would put an empty line in the feed.
            rows = ordered.filter { it.amountMinor != 0L }.map { row ->
                StatementRow(
                    postedDate = Instant.ofEpochMilli(row.date).atZone(LedgerCalendar.zone).toLocalDate(),
                    operation = row.operation,
                    operationRaw = row.description,
                    amountMinor = row.amountMinor,
                    balanceAfterMinor = row.balanceMinor,
                    description = row.description,
                    beneficiaryName = null,
                    beneficiaryAccount = null,
                )
            },
            openingMinor = opening,
            closingMinor = ordered.last().balanceMinor,
        )
    }

    private fun rows(json: JSONArray, signedWithdrawal: Boolean): List<Raw> = (0 until json.length()).map { index ->
        val item = json.getJSONObject(index)
        val date = item.optLong("movementDate").takeIf { it > 0 } ?: throw TbcException("DEPOSIT_FORMAT")
        val balance = minor(item.opt("balance")) ?: throw TbcException("DEPOSIT_FORMAT")
        val deposited = minor(item.opt("depositAmount")) ?: 0L
        val interest = minor(item.opt("interestedAmount")) ?: 0L
        val withdrawn = minor(item.opt("withdrawnDepositAmount")) ?: 0L
        val amount = Math.addExact(
            Math.addExact(deposited, interest),
            if (signedWithdrawal) withdrawn else Math.negateExact(withdrawn),
        )
        val (operation, description) = when {
            interest != 0L && deposited == 0L && withdrawn == 0L -> StatementOperation.INTEREST to "Deposit interest"
            deposited != 0L && interest == 0L && withdrawn == 0L -> StatementOperation.SAVINGS_TOPUP to "Deposit top-up"
            withdrawn != 0L && interest == 0L && deposited == 0L -> StatementOperation.SAVINGS_TOPUP to "Deposit withdrawal"
            // Still balance-proven money; it stays visible as an unclassified movement.
            else -> StatementOperation.OTHER to "Deposit movement"
        }
        Raw(date, amount, balance, operation, description)
    }

    /** Oldest first, every balance reached by its own row. Either property failing rejects the order. */
    private fun chained(rows: List<Raw>): Boolean = (1 until rows.size).all { index ->
        rows[index].date >= rows[index - 1].date &&
            Math.subtractExact(rows[index].balanceMinor, rows[index].amountMinor) == rows[index - 1].balanceMinor
    }

    private fun minor(value: Any?): Long? = value?.takeUnless { it == JSONObject.NULL }
        ?.let { BigDecimal(it.toString()).movePointRight(2).longValueExact() }
}
