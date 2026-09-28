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
data class TbcLedgerAccount(val id: String, val iban: String, val currency: String, val name: String, val coreId: String? = null, val balanceMinor: Long? = null, val cardSuffixes: Map<String, String> = emptyMap()) {
    val key: String get() = "$iban|$currency"
    val label: String get() = "$currency · •${iban.takeLast(4)}"
}
/** A bank-provided IBAN ↔ card suffix; physical/virtual and primary remain the owner's choice. */
data class TbcCardCandidate(val iban: String, val last4: String)
data class TbcHistoryReadStats(val pages: Int = 0, val parsed: Int = 0, val blocked: Int = 0,
    val firstPageEmpty: Boolean = false)
data class TbcHistoryRow(val movementId: String, val transactionId: String, val row: StatementRow)
data class TbcHistoryPage(val rows: List<TbcHistoryRow>, val nextCursor: Long?, val empty: Boolean, val blockedCursor: Long? = null, val blockedCount: Int = 0, val holds: List<TbcHold> = emptyList())

/** A bank authorization, distinct from a booked movement. Its fingerprint does not include mutable money. */
data class TbcHold(val key: String, val iban: String, val currency: String, val amountMinor: Long,
    val occurredAt: Long, val merchant: String, val cardLast4: String?)

internal object TbcHistoryParser {
    private val epochDay = DateTimeFormatter.ofPattern("MMM d uuuu h:mma", Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT)
    private val pos = Regex("""^POS(?: wallet)? - (.+), ([\d,.]+) ([A-Z]{3}), ([A-Za-z]{3}\s+\d{1,2}\s+\d{4}\s+\d{1,2}:\d{2}[AP]M),.*$""")
    fun purchaseTime(description: String): Long? = runCatching {
        val match = pos.matchEntire(description.substringBefore('\n').trim()) ?: return null
        LocalDateTime.parse(match.groupValues[4].replace(Regex("\\s+"), " "), epochDay)
            .atZone(LedgerCalendar.zone).toInstant().toEpochMilli()
    }.getOrNull()
    fun page(json: JSONArray, account: TbcLedgerAccount): TbcHistoryPage {
        val rows = mutableListOf<TbcHistoryRow>()
        val holds = mutableListOf<TbcHold>()
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
                    // The API repeats holds in responses for other currencies of the same product.
                    // Route by the hold's own IBAN/currency, never by the request that returned it.
                    val iban = tx.optString("blockedMovementIban")
                    val currency = tx.optString("currency")
                    if (iban == account.iban && currency == account.currency) {
                        val cardId = tx.optLong("blockedMovementCardId").takeIf { it > 0 }?.toString()
                            ?: throw TbcException("HISTORY_HOLD")
                        val amount = BigDecimal(tx.get("amount").toString()).movePointRight(2).longValueExact()
                        val title = tx.optString("title").trim()
                        if (amount >= 0 || amount == Long.MIN_VALUE || title.isBlank() || tx.optString("transactionStatus") != "Green" ||
                            !tx.optString("subTitle").equals("Blocked amount", true)) throw TbcException("HISTORY_HOLD")
                        val time = requireNotNull(blockedCursor)
                        val key = "hold|tbc|" + dev.whekin.whfin.data.push.TbcPush.hash("$iban|$currency|$cardId|$time")
                        holds += TbcHold(key, iban, currency, amount, time, title, account.cardSuffixes[cardId])
                    } else if (!iban.matches(Regex("GE[0-9]{2}TB[0-9]{16}")) || !currency.matches(Regex("[A-Z]{3}"))) {
                        throw TbcException("HISTORY_HOLD")
                    }
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
                // The subtitle is the bank saying whose accounts the money moved between, and it
                // says it in Georgian: funding a deposit is titled by its contract number and only
                // this line marks it as the owner's own money moving. Read as a title-only English
                // phrase it was an ordinary payment, so a deposit top-up counted as spending on one
                // side and income on the other.
                val ownAccounts = subtitle.equals("internal transfer", true) ||
                    subtitle == "საკუთარ ანგარიშებს შორის გადარიცხვა"
                val operation = when {
                    card != null -> StatementOperation.CARD_PAYMENT
                    title == "კონვერტაცია" && ownAccounts -> StatementOperation.CURRENCY_EXCHANGE
                    ownAccounts -> StatementOperation.OWN_TRANSFER
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
        return TbcHistoryPage(rows, cursor, json.length() == 0, blockedCursor, blockedCount, holds)
    }
}

/** Both source IDs live on the same ledger row, so ordinary backup/restore preserves the link. */
object TbcRowIdentity : ApiRowIdentity("mobile:", "|mobile|")
