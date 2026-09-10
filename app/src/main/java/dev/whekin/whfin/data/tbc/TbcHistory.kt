package dev.whekin.whfin.data.tbc

import dev.whekin.whfin.data.LedgerCalendar
import dev.whekin.whfin.data.statement.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** Currency ledger IDs used by the mobile history endpoint, distinct from dashboard/card IDs. */
data class TbcLedgerAccount(val id: String, val iban: String, val currency: String, val name: String, val coreId: String? = null, val balanceMinor: Long? = null) {
    val key: String get() = "$iban|$currency"
    val label: String get() = "$currency · •${iban.takeLast(4)}"
}
data class TbcHistoryReadStats(val pages: Int = 0, val parsed: Int = 0, val blocked: Int = 0,
    val firstPageEmpty: Boolean = false)
data class TbcHistoryRow(val movementId: String, val transactionId: String, val row: StatementRow)
data class TbcHistoryPage(val rows: List<TbcHistoryRow>, val nextCursor: Long?, val empty: Boolean, val blockedCursor: Long? = null, val blockedCount: Int = 0)

internal object TbcHistoryParser {
    private val epochDay = DateTimeFormatter.ofPattern("MMM d uuuu h:mma", Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT)
    private val pos = Regex("""^POS(?: wallet)? - (.+), ([\d,.]+) ([A-Z]{3}), ([A-Za-z]{3}\s+\d{1,2}\s+\d{4}\s+\d{1,2}:\d{2}[AP]M),.*$""")
    fun page(json: JSONArray, account: TbcLedgerAccount): TbcHistoryPage {
        val rows = mutableListOf<TbcHistoryRow>()
        var cursor: Long? = null
        var blockedCursor: Long? = null
        var blockedCount = 0
        for (i in 0 until json.length()) {
            val group = json.getJSONObject(i)
            val timestamp = group.getLong("date")
            if (timestamp <= 0) throw TbcException("HISTORY_FORMAT")
            val date = Instant.ofEpochMilli(timestamp).atZone(LedgerCalendar.zone).toLocalDate()
            val transactions = group.getJSONArray("transactions")
            for (j in 0 until transactions.length()) {
                val tx = transactions.getJSONObject(j)
                val id = tx.getLong("transactionId")
                if (id > 0) cursor = id
                if (tx.optString("entryType") == "BlockedTransaction") {
                    blockedCount++
                    blockedCursor = tx.optLong("blockedMovementDate").takeIf { it > 0 } ?: throw TbcException("HISTORY_PAGE")
                    continue
                }
                if (tx.optString("entryType") != "StandardMovement" || id <= 0) throw TbcException("HISTORY_FORMAT")
                val movement = tx.optString("movementId").takeIf { it.isNotBlank() && it != "null" && it.length <= 160 }
                    ?: throw TbcException("HISTORY_FORMAT")
                if (tx.getString("currency") != account.currency) throw TbcException("HISTORY_ACCOUNT")
                if (account.id.toLongOrNull() != null && tx.has("accountId") && !tx.isNull("accountId") &&
                    tx.get("accountId").toString() !in setOfNotNull(account.id, account.coreId)) throw TbcException("HISTORY_ACCOUNT")
                if (tx.optString("transactionStatus") != "Green") throw TbcException("HISTORY_FORMAT")
                val title = tx.getString("title").trim().takeIf(String::isNotEmpty) ?: throw TbcException("HISTORY_FORMAT")
                val subtitle = tx.optString("subTitle").takeUnless { it == "null" }.orEmpty().trim()
                val amount = BigDecimal(tx.get("amount").toString()).movePointRight(2).longValueExact()
                if (amount == 0L) throw TbcException("HISTORY_FORMAT")
                val card = if (title.startsWith("POS - ") || title.startsWith("POS wallet - ")) {
                    pos.matchEntire(title) ?: throw TbcException("HISTORY_FORMAT")
                } else null
                val category = tx.optString("categoryCode")
                val operation = when {
                    card != null -> StatementOperation.CARD_PAYMENT
                    title == "კონვერტაცია" && subtitle.equals("internal transfer", true) -> StatementOperation.CURRENCY_EXCHANGE
                    title.equals("Transfer between your accounts", true) && subtitle.equals("internal transfer", true) -> StatementOperation.OWN_TRANSFER
                    category == "BANK_INSURE_TAX" && title.contains("საკომისიო") -> StatementOperation.FEE
                    title.startsWith("ნაკრების საკომისიო") -> StatementOperation.FEE
                    category == "UTIL_PAY" -> StatementOperation.BILL_PAYMENT
                    amount > 0 -> StatementOperation.TRANSFER_IN
                    else -> StatementOperation.OTHER
                }
                val purchaseDate = card?.groupValues?.get(4)?.let {
                    LocalDateTime.parse(it.replace(Regex("\\s+"), " "), epochDay).toLocalDate()
                }
                val description = listOf(title, subtitle.takeIf { it.isNotEmpty() }).filterNotNull().joinToString("\n")
                val counterparty = Regex("""GE[0-9]{2}[A-Z]{2}[0-9]{16}""").findAll(description)
                    .map { it.value }.filter { it != account.iban }.distinct().toList().singleOrNull()
                rows += TbcHistoryRow(movement, id.toString(), StatementRow(date, operation, category.ifBlank { "Other" },
                    amount, null, description, if (card == null) title else null, counterparty,
                    card?.groupValues?.get(1)?.trim(), purchaseDate,
                    bankTransactionId = TbcRowIdentity.mobileId(movement)))
            }
        }
        return TbcHistoryPage(rows, cursor, json.length() == 0, blockedCursor, blockedCount)
    }
}

/** Both source IDs live on the same ledger row, so ordinary backup/restore preserves the link. */
object TbcRowIdentity : ApiRowIdentity("mobile:", "|mobile|")
