package dev.whekin.whfin.data.sms

import dev.whekin.whfin.data.sms.BankSmsMessage.Classification
import dev.whekin.whfin.data.sms.BankSmsMessage.IgnoreReason
import dev.whekin.whfin.data.sms.BankSmsMessage.Sms
import dev.whekin.whfin.data.sms.BankSmsMessage.IncomingTransfer
import dev.whekin.whfin.data.sms.BankSmsMessage.CashDeposit
import dev.whekin.whfin.data.sms.BankSmsMessage.OutgoingTransfer
import dev.whekin.whfin.data.sms.BankSmsMessage.BillPayment
import dev.whekin.whfin.data.sms.BankSmsMessage.CardPayment
import java.math.BigDecimal
import java.time.LocalDateTime

/** TBC Georgia notification text. Sender verification belongs to BankSmsBank, not this parser. */
object TbcSmsParser {
    private val money = Regex("""(?<![\d/.:])(-?\d[\d,.]*(?:[ \u00A0]\d{3}(?!\d)[\d,.]*)*)\s*([A-Z]{3})(?![A-Z])""")
    private val card = Regex("""\(\*+(\d{4})\)""")
    private val date = Regex("""\b(\d{2})[./-](\d{2})[./-](\d{4}|\d{2})(?:\s+(\d{2}):(\d{2})(?::(\d{2}))?)?\b""")
    private val balanceLabel = Regex("(?i)\\b(?:Balance|Nashti|Bal|Avilable amount|Available amount|Available balance)\\s*:|ხელმისაწვდომი თანხა\\s*:")
    fun classify(body: String): Classification {
        val text = body.trim().replace(Regex("\\s+"), " ")
        if (Regex("(?i)(?:\\bcode\\s*:|\\bPIN\\s+code\\b|\\bSMS\\s+code\\b)").containsMatchIn(text)) {
            return Classification.Ignored(IgnoreReason.OTP, bankCandidate = false)
        }
        // This is a different country's deposit message, present in the upstream Georgia directory.
        if (text.contains("было переведено")) return Classification.Ignored(IgnoreReason.UNRELATED, false)
        val values = money.findAll(text).toList()
        if (values.isEmpty()) return Classification.Ignored(IgnoreReason.UNRELATED, false)
        val financial = Regex("(?i)^(?:\\d|Reversal|Ukugatareba|Cash Deposit|Deposit|Money Transfer|Card transaction|Sabarate operacia|Payment|Gadaxda|Transfer between|Conversion|Your money transfer)|^(?:საბარათე|ჩარიცხვა|კონვერტაცია)").containsMatchIn(text)
        if (!financial) return Classification.Ignored(IgnoreReason.UNRELATED, false)
        return try {
            val first = values.first()
            val amount = minor(first.groupValues[1])
            if (amount <= 0) return Classification.Unrecognized
            val currency = first.groupValues[2]
            val dateMatch = date.find(text) ?: return Classification.Unrecognized
            val d = dateMatch.groupValues
            val year = d[3].toInt().let { if (d[3].length == 2) 2000 + it else it }
            val timestamp = LocalDateTime.of(year, d[2].toInt(), d[1].toInt(), d[4].toIntOrNull() ?: 0,
                d[5].toIntOrNull() ?: 0, d[6].toIntOrNull() ?: 0)
            val digits = card.find(text)
            val balanceAt = balanceLabel.find(text)
            val balanceCurrency = balanceAt?.let { money.find(text.substring(it.range.last + 1).trimStart())?.takeIf { it.range.first == 0 }?.groupValues?.get(2) }
            // TBC prints available funds (including card holds). Never use that number as a booked
            // balance anchor or to guess which account owns a payment. Currency is still useful for FX.
            val balanceMinor: Long? = null
            val merchantEnd = listOfNotNull(dateMatch.range.first, balanceAt?.range?.first).min()
            val merchant = digits?.let { text.substring(it.range.last + 1, merchantEnd.coerceAtLeast(it.range.last + 1)).trim() }
            val result: Sms = when {
                text.startsWith("Reversal", true) || text.startsWith("Ukugatareba", true) ->
                    IncomingTransfer(amount, currency, merchant?.takeIf(String::isNotBlank), digits?.groupValues?.get(1), balanceMinor, balanceCurrency, timestamp)
                text.startsWith("Cash Deposit", true) -> CashDeposit(amount, currency, null, currency, timestamp)
                text.startsWith("Deposit", true) || text.startsWith("ჩარიცხვა") ->
                    IncomingTransfer(amount, currency, merchant?.takeIf(String::isNotBlank), digits?.groupValues?.get(1), null, balanceCurrency ?: currency, timestamp)
                text.startsWith("Money Transfer", true) -> OutgoingTransfer(amount, currency, null, currency, timestamp)
                text.startsWith("Payment", true) || text.startsWith("Gadaxda", true) -> {
                    val tail = text.substring(first.range.last + 1).trim()
                    val service = tail.substringBefore("ID:").substringBefore("Creation date").trim()
                    BillPayment(amount, currency, service.takeIf(String::isNotBlank), null, currency, timestamp)
                }
                digits != null && !merchant.isNullOrBlank() && (text.first().isDigit() || text.startsWith("საბარათე") || text.startsWith("Card transaction", true) || text.startsWith("Sabarate operacia", true)) ->
                    CardPayment(amount, currency, digits.groupValues[1], merchant, null, null, balanceCurrency, timestamp)
                // Names such as MC GOLD are not account identifiers. Do not turn a one-sided own
                // transfer or an informational conversion into an ordinary expense.
                else -> return Classification.Unrecognized
            }
            Classification.Parsed(result)
        } catch (_: Exception) { Classification.Unrecognized }
    }
    private fun minor(raw: String): Long {
        val cleaned = raw.filterNot(Char::isWhitespace)
        val decimal = if (cleaned.contains('.') && cleaned.contains(',')) cleaned.replace(",", "") else cleaned.replace(',', '.')
        return BigDecimal(decimal).movePointRight(2).longValueExact()
    }
}
