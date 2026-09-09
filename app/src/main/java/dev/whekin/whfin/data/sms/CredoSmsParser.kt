package dev.whekin.whfin.data.sms

import dev.whekin.whfin.data.sms.BankSmsMessage.IgnoreReason
import dev.whekin.whfin.data.sms.BankSmsMessage.Classification
import dev.whekin.whfin.data.sms.BankSmsMessage.Sms
import dev.whekin.whfin.data.sms.BankSmsMessage.CardPayment
import dev.whekin.whfin.data.sms.BankSmsMessage.OutgoingTransfer
import dev.whekin.whfin.data.sms.BankSmsMessage.IncomingTransfer
import dev.whekin.whfin.data.sms.BankSmsMessage.DepositTopUp
import dev.whekin.whfin.data.sms.BankSmsMessage.OwnTransfer
import dev.whekin.whfin.data.sms.BankSmsMessage.BillPayment
import dev.whekin.whfin.data.sms.BankSmsMessage.CashDeposit
import dev.whekin.whfin.data.sms.BankSmsMessage.InterestAccrual
import dev.whekin.whfin.data.sms.BankSmsMessage.CurrencyExchange
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Парсер SMS банка Credo (Грузия). Проверен на приватных и синтетических fixtures.
 *
 * ВАЖНО: в Credo два разных формата даты:
 *  - Payment / Rejected: `dd/MM/yyyy HH:mm:ss` (24 часа, день первым)
 *  - Переводы / конвертация: `M/d/yyyy h:mm:ss AM/PM` (месяц первым!)
 * Перепутать = сдвинуть транзакцию на месяцы.
 *
 * FX-платежи: сумма в валюте покупки (USD/EUR/GBP), Balance — в валюте счёта (GEL).
 */
object CredoSmsParser {
    /** Increment when accepted Credo message structures materially change. */
    const val SCHEMA_VERSION = 3

    private val paymentDate = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss", Locale.US).withResolverStyle(java.time.format.ResolverStyle.STRICT)
    private val transferDate = DateTimeFormatter.ofPattern("M/d/uuuu h:mm:ss a", Locale.US).withResolverStyle(java.time.format.ResolverStyle.STRICT)

    private val amountRegex = Regex("""(?<![\d.,])(-?[\d,]+(?:\.\d{1,2})?)\s*([A-Z]{3})(?![A-Z])""")
    private val cardRegex = Regex("""(?i)card N \*+\s*(\d{4})""")
    private val depositNumberRegex = Regex("""(?i)\bon your\s+([A-Za-z0-9./-]{4,32})\s+deposit\b""")
    private val paymentDateRegex = Regex("""(\d{2}/\d{2}/\d{4} \d{2}:\d{2}:\d{2})""")
    private val transferDateRegex =
        Regex("""Date:\s*(\d{1,2}/\d{1,2}/\d{4} \d{1,2}:\d{2}:\d{2} [AP]M)""")
    private val ibanRegex = Regex("""(GE\d{2}[A-Z]{2}\d{16})""")

    fun classify(body: String): Classification {
        val text = normalizeTemplate(body.substringBefore("want to split the bill?").trim())
        if (text.isEmpty()) return Classification.Ignored(IgnoreReason.UNRELATED, bankCandidate = false)

        return when {
            text.startsWith("Rejected payment") ->
                Classification.Ignored(IgnoreReason.REJECTED, bankCandidate = true)
            text.startsWith("Canceled operation") ->
                parseCardPayment(text.substringAfter("Canceled operation").trim())
                    ?.let(Classification::Canceled)
                    ?: Classification.Unrecognized
            isOneTimeCode(text) -> Classification.Ignored(IgnoreReason.OTP, bankCandidate = true)
            text.startsWith("Deposit:") -> parsedOrUnrecognized { parseIncomingTransfer(text.replaceFirst("Deposit:", "Incoming transfer")) }
            text.startsWith("Payment:") -> parsedOrUnrecognized { parseCardPayment(text) }
            text.startsWith("Transfer between accounts") -> parsedOrUnrecognized { parseOwnTransfer(text) }
            text.startsWith("Currency exchange") -> parsedOrUnrecognized { parseCurrencyExchange(text) }
            text.startsWith("Outgoing transfer") -> parsedOrUnrecognized { parseOutgoingTransfer(text) }
            text.startsWith("Incoming transfer") -> parsedOrUnrecognized { parseIncomingTransfer(text) }
            text.startsWith("Deposit top-up") -> parsedOrUnrecognized { parseDepositTopUp(text) }
            text.startsWith("Service/utility payment") -> parsedOrUnrecognized { parseBillPayment(text) }
            text.startsWith("Depositing funds to the account") -> parsedOrUnrecognized { parseCashDeposit(text) }
            text.startsWith("Accrued interest") -> parsedOrUnrecognized { parseInterest(text) }
            // A bank also sends offers and notices. Merely naming the bank never made a message an
            // operation, and treating those as parser failures buried the real ones in noise.
            looksFinancial(text) -> Classification.Unrecognized
            else -> Classification.Ignored(IgnoreReason.UNRELATED, bankCandidate = false)
        }
    }

    private fun normalizeTemplate(raw: String): String {
        var text = raw.removePrefix("Credo Bank:").trim()
        text = text.replace(Regex("^Gadaxda:?\\s+"), "Payment: ")
            .replace(Regex("(?i)Baratis N"), "Card N")
            .replace(Regex("(?i)\\bNashti:"), "Balance:")
        return text
    }

    /** AM/PM is explicit month-first evidence; unlabelled 24-hour legacy dates are day-first. */
    private fun anyTimestamp(text: String): LocalDateTime? {
        looseTransferTimestamp(text)?.let { return it }
        val value = Regex("""\d{2}/\d{2}/\d{4} \d{2}:\d{2}(?::\d{2})?""").find(text)?.value ?: return null
        return runCatching { LocalDateTime.parse(value, DateTimeFormatter.ofPattern(
            if (value.length == 16) "dd/MM/uuuu HH:mm" else "dd/MM/uuuu HH:mm:ss",
        ).withResolverStyle(java.time.format.ResolverStyle.STRICT)) }.getOrNull()
    }

    /** Credo uses more than one code template, and none of them is an operation. */
    private fun isOneTimeCode(text: String): Boolean =
        (text.startsWith("CODE:") && text.contains("confirms card", ignoreCase = true)) ||
            text.startsWith("# SMS Code:") ||
            text.startsWith("Ertjeradi kodi", ignoreCase = true) ||
            Regex("""\bOTP:\s*\d""").containsMatchIn(text)

    /** Money moved only if the message states an amount next to a ledger label. */
    private fun looksFinancial(text: String): Boolean =
        amountRegex.containsMatchIn(text) &&
            Regex("""(?i)\b(amount|balance|tanxa|nashti|baratis|angarishi|charicxva|cashback)\b|Card N \*""").containsMatchIn(text)

    /** Backwards-compatible parser for callers that only need recognized transactions. */
    fun parse(body: String): Sms? = (classify(body) as? Classification.Parsed)?.sms

    fun isCredoCandidate(body: String): Boolean = when (val result = classify(body)) {
        is Classification.Parsed, is Classification.Canceled, Classification.Unrecognized -> true
        is Classification.Ignored -> result.bankCandidate
    }

    private inline fun parsedOrUnrecognized(block: () -> Sms?): Classification =
        runCatching { block() }.getOrNull()?.takeIf { it.amountMinor > 0 }?.let(Classification::Parsed) ?: Classification.Unrecognized

    private fun money(match: MatchResult): Pair<Long, String> =
        java.math.BigDecimal(match.groupValues[1].replace(",", "")).movePointRight(2).longValueExact() to match.groupValues[2]

    private fun firstAmountAfter(text: String, label: String): Pair<Long, String>? {
        val idx = text.indexOf(label, ignoreCase = true)
        if (idx < 0) return null
        val value = text.substring(idx + label.length).trimStart().removePrefix(":").trimStart()
        return runCatching { amountRegex.find(value)?.takeIf { it.range.first == 0 }?.let(::money) }.getOrNull()
    }

    private fun parseCardPayment(text: String): CardPayment? {
        val (amount, currency) = firstAmountAfter(text, "Payment:") ?: return null
        val card = cardRegex.find(text)?.groupValues?.get(1) ?: return null
        val timestamp = paymentDateRegex.find(text)
            ?.let { runCatching { LocalDateTime.parse(it.groupValues[1], paymentDate) }.getOrNull() }
            ?: return null

        val cardMatch = cardRegex.find(text) ?: return null
        val tail = text.substring(cardMatch.range.last + 1)
        val end = listOfNotNull(
            paymentDateRegex.find(tail)?.range?.first,
            Regex("(?i)Balance:|Details:|Check your balance").find(tail)?.range?.first,
        ).minOrNull() ?: return null
        val merchantLine = tail.substring(0, end).trim()
        val merchantRaw = merchantLine.substringBefore('>').trim().takeIf(String::isNotBlank) ?: return null
        val locationRaw = merchantLine.substringAfter('>', "").trim().takeIf(String::isNotEmpty)

        val balance = firstAmountAfter(text, "Balance:")
        return CardPayment(
            amountMinor = amount,
            currency = currency,
            cardLast4 = card,
            merchantRaw = merchantRaw,
            locationRaw = locationRaw,
            balanceMinor = balance?.first,
            balanceCurrency = balance?.second,
            timestamp = timestamp,
        )
    }

    private val looseTransferDateRegex =
        Regex("""(\d{1,2}/\d{1,2}/\d{4} \d{1,2}:\d{2}:\d{2} [AP]M)""")

    private fun looseTransferTimestamp(text: String): LocalDateTime? =
        looseTransferDateRegex.find(text)
            ?.let { runCatching { LocalDateTime.parse(it.groupValues[1], transferDate) }.getOrNull() }

    private fun transferTimestamp(text: String): LocalDateTime? =
        transferDateRegex.find(text)
            ?.let { runCatching { LocalDateTime.parse(it.groupValues[1], transferDate) }.getOrNull() }

    private fun parseOutgoingTransfer(text: String): OutgoingTransfer? {
        val (amount, currency) = firstAmountAfter(text, "Amount")
            ?: firstAmountAfter(text, "Outgoing transfer") ?: return null
        val balance = firstAmountAfter(text, "Balance")
        return OutgoingTransfer(
            amountMinor = amount,
            currency = currency,
            balanceMinor = balance?.first,
            balanceCurrency = balance?.second,
            timestamp = anyTimestamp(text) ?: return null,
        )
    }

    private fun parseIncomingTransfer(text: String): IncomingTransfer? {
        // Two templates share the opening: a transfer states `Amount:` and a date, a card refund
        // states the money on the first line, names the card, and carries no date at all.
        val (amount, currency) = firstAmountAfter(text, "Amount")
            ?: firstAmountAfter(text, "Incoming transfer")
            ?: return null
        val card = cardRegex.find(text)?.groupValues?.get(1)
        val sender = Regex("""(?:From sender|Sender):\s*([^;\n]+)""").find(text)
            ?.groupValues?.get(1)?.trim()
            ?: card?.let { refundSource(text) }
        val balance = firstAmountAfter(text, "Balance:")
        val timestamp = anyTimestamp(text)
        // A stated date that will not parse is a message we do not understand; an absent one is
        // simply absent, and the delivery time is then the honest booking moment.
        if (timestamp == null && (text.contains("Date:") || Regex("""\d{1,2}/\d{1,2}/\d{4}""").containsMatchIn(text))) return null
        return IncomingTransfer(
            amountMinor = amount,
            currency = currency,
            senderName = sender,
            cardLast4 = card,
            balanceMinor = balance?.first,
            balanceCurrency = balance?.second,
            timestamp = timestamp,
        )
    }

    /** Who returned the money: printed between the card and the balance, without a label. */
    private fun refundSource(text: String): String? {
        val cardLine = cardRegex.find(text)?.value ?: return null
        return text.substringAfter(cardLine)
            .substringBefore("Balance")
            .trim()
            .takeIf(String::isNotEmpty)
    }

    private fun parseDepositTopUp(text: String): DepositTopUp? {
        val (amount, currency) = firstAmountAfter(text, "Amount") ?: return null
        val balance = firstAmountAfter(text, "Available Balance on Deposit")
        return DepositTopUp(
            amountMinor = amount,
            currency = currency,
            balanceMinor = balance?.first,
            balanceCurrency = balance?.second,
            timestamp = anyTimestamp(text) ?: return null,
        )
    }

    private fun parseOwnTransfer(text: String): OwnTransfer? {
        val (amount, currency) = firstAmountAfter(text, "Amount") ?: return null
        val fromIban = Regex("""From:\s*(\S+)""").find(text)?.groupValues?.get(1) ?: return null
        val toIban = Regex("""To:\s*(\S+)""").find(text)?.groupValues?.get(1) ?: return null
        if (!ibanRegex.matches(fromIban) || !ibanRegex.matches(toIban)) return null
        val balance = firstAmountAfter(text, "Balance:")
        return OwnTransfer(
            amountMinor = amount,
            currency = currency,
            fromIban = fromIban,
            toIban = toIban,
            balanceMinor = balance?.first,
            balanceCurrency = balance?.second,
            timestamp = anyTimestamp(text) ?: return null,
        )
    }

    private fun parseBillPayment(text: String): BillPayment? {
        val (amount, currency) = firstAmountAfter(text, "Amount") ?: return null
        val service = Regex("""Service:\s*([^,;\n]+)""").find(text)?.groupValues?.get(1)?.trim()
        val balance = firstAmountAfter(text, "Balance")
        return BillPayment(
            amountMinor = amount,
            currency = currency,
            serviceRaw = service?.takeIf { it.isNotEmpty() },
            balanceMinor = balance?.first,
            balanceCurrency = balance?.second,
            // The template ships an unresolved date placeholder, so there is nothing to read.
            timestamp = transferTimestamp(text),
        )
    }

    private fun parseCashDeposit(text: String): CashDeposit? {
        val (amount, currency) = firstAmountAfter(text, "Amount") ?: return null
        val balance = firstAmountAfter(text, "Available Balance")
        return CashDeposit(
            amountMinor = amount,
            currency = currency,
            balanceMinor = balance?.first,
            balanceCurrency = balance?.second,
            timestamp = anyTimestamp(text),
        )
    }

    private fun parseInterest(text: String): InterestAccrual? {
        val (amount, currency) = firstAmountAfter(text, "amount") ?: return null
        val balance = firstAmountAfter(text, "Available Balance")
        return InterestAccrual(
            amountMinor = amount,
            currency = currency,
            depositNumber = depositNumberRegex.find(text)
                ?.groupValues
                ?.get(1)
                // A token with no digit in it is a word the template moved, not an account.
                ?.takeIf { value -> value.any(Char::isDigit) },
            balanceMinor = balance?.first,
            balanceCurrency = balance?.second,
            // Only a bare day is printed, and its order is ambiguous; the delivery time is honest.
            timestamp = null,
        )
    }

    private fun parseCurrencyExchange(text: String): CurrencyExchange? {
        val (amount, currency) = firstAmountAfter(text, "Amount") ?: return null
        val (received, receivedCurrency) =
            firstAmountAfter(text, "Received amount:") ?: return null
        val balance = firstAmountAfter(text, "Balance:")
        return CurrencyExchange(
            amountMinor = amount,
            currency = currency,
            receivedAmountMinor = received,
            receivedCurrency = receivedCurrency,
            balanceMinor = balance?.first,
            balanceCurrency = balance?.second,
            timestamp = anyTimestamp(text) ?: return null,
        )
    }
}
