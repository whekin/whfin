package dev.whekin.whfin.data.credo

import dev.whekin.whfin.data.LedgerCalendar
import dev.whekin.whfin.data.statement.*
import dev.whekin.whfin.data.statement.credo.CredoStatementParser
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

object CredoRowIdentity : ApiRowIdentity("credoapi:", "|credoapi|")

/** Strict booked-row adapter. Unknown classifications retain the bank's label for review. */
internal object CredoHistoryParser {
    private val receipt = Regex("""^(?:გადახდა|Payment) - (.+?)\s+[\d,]+\.\d{2} [A-Z]{3} (\d{2}\.\d{2}\.\d{4})$""")
    fun text(json: JSONObject, name: String): String? = if (json.isNull(name)) null else
        json.optString(name).trim().takeIf { it.isNotEmpty() && it != "null" }
    fun amount(json: JSONObject): Long {
        fun money(key: String) = if (json.isNull(key)) 0L else
            BigDecimal(json.get(key).toString()).movePointRight(2).longValueExact()
        val credit = money("credit"); val debit = money("debit")
        if (credit < 0 || debit < 0 || (credit == 0L) == (debit == 0L)) fail()
        return Math.subtractExact(credit, debit)
    }
    fun postedDate(json: JSONObject): java.time.LocalDate {
        val raw = text(json, "operationDateTime") ?: fail()
        return runCatching { OffsetDateTime.parse(raw).atZoneSameInstant(LedgerCalendar.zone).toLocalDate() }
            .getOrElse { LocalDateTime.parse(raw.replace(' ', 'T')).toLocalDate() }
    }
    fun row(list: JSONObject, detail: JSONObject, account: CredoRemoteAccount): StatementRow {
        val id = text(list, "stmtEntryId")?.takeIf { it.length <= 160 } ?: fail()
        if (list.getBoolean("isCardBlock") || detail.getBoolean("isCardBlock")) fail()
        if (list.getString("currency") != account.currency || detail.getString("currency") != account.currency ||
            detail.getString("accountNumber") != account.accountNumber || amount(list) != amount(detail) ||
            text(list, "transactionId") != text(detail, "transactionId")) fail()
        val date = text(list, "operationDateTime") ?: fail()
        fun day(raw: String) = runCatching {
            OffsetDateTime.parse(raw).atZoneSameInstant(LedgerCalendar.zone).toLocalDate()
        }.getOrElse { LocalDateTime.parse(raw.replace(' ', 'T')).toLocalDate() }
        if (day(date) != day(text(detail, "operationDateTime") ?: fail())) fail()
        val description = text(detail, "description") ?: text(list, "description").orEmpty()
        val raw = text(list, "operationType") ?: text(detail, "operationType") ?: "Other"
        val type = text(list, "transactionType")
        val typeName = text(list, "transactionTypeName")
        val card = receipt.matchEntire(description)
        val operation = CredoStatementParser.operationFor(raw) ?: when {
            typeName == "Transfer_to_own_account" || type == "Transferbetweenownaccounts" || raw == "Transfer between own accounts" -> StatementOperation.OWN_TRANSFER
            typeName == "Currency_exchange" || type == "CurrencyExchange" || raw in setOf("Currency conversion", "Currency exchange", "Безналичная конвертация") -> StatementOperation.CURRENCY_EXCHANGE
            card != null || raw == "Card transaction" -> StatementOperation.CARD_PAYMENT
            raw in setOf("Different fees", "Transfer fee", "Instant transfer fee") -> StatementOperation.FEE
            raw == "Accrued interest payment" -> StatementOperation.INTEREST
            else -> StatementOperation.OTHER
        }
        val details = detail.optJSONObject("details")
        val amount = amount(list)
        val peerField = if (amount < 0) "creditAccount" else "debitAccount"
        val peer = text(detail, "contragentAccount") ?: details?.let { text(it, peerField) }
        val name = text(detail, "contragentFullName") ?: details?.let {
            text(it, if (amount < 0) "creditFullName" else "debitFullName")
        }
        return StatementRow(day(date), operation, raw, amount, null, description,
            name, peer?.takeIf { it != account.accountNumber && Regex("GE[0-9]{2}[A-Z]{2}[0-9]{16}").matches(it) },
            merchantRaw = card?.groupValues?.get(1),
            purchaseDate = card?.groupValues?.get(2)?.let { java.time.LocalDate.parse(it, DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT)) },
            bankTransactionId = CredoRowIdentity.mobileId(id))
    }
    private fun fail(): Nothing = throw CredoApiException("HISTORY_FORMAT")
}
