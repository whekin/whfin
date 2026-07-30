package dev.whekin.whfin.data.sms

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
    const val SCHEMA_VERSION = 2

    enum class IgnoreReason { OTP, REJECTED, UNRELATED }

    sealed interface Classification {
        data class Parsed(val sms: Sms) : Classification

        /**
         * The bank reversed a card payment that a previous message already reported.
         * It is not an operation of its own: it retracts the draft the payment created.
         */
        data class Canceled(val payment: CardPayment) : Classification
        data class Ignored(val reason: IgnoreReason, val credoCandidate: Boolean) : Classification
        data object Unrecognized : Classification
    }

    sealed interface Sms {
        val amountMinor: Long
        val currency: String
        val balanceMinor: Long?
        val balanceCurrency: String?

        /**
         * Null when the message carries no usable date of its own.
         *
         * Credo ships a broken template for utility payments — the date field arrives as an
         * unresolved placeholder — and states only an ambiguous day for interest. Inventing a date
         * would silently move money between months, so the caller substitutes the delivery time.
         */
        val timestamp: LocalDateTime?
    }

    data class CardPayment(
        override val amountMinor: Long,
        override val currency: String,
        val cardLast4: String,
        /** Сырая строка мерчанта до '>' (нормализация — отдельный шаг). */
        val merchantRaw: String,
        /** Хвост после '>': город/локация + код страны. */
        val locationRaw: String?,
        override val balanceMinor: Long?,
        override val balanceCurrency: String?,
        override val timestamp: LocalDateTime?,
    ) : Sms

    data class OutgoingTransfer(
        override val amountMinor: Long,
        override val currency: String,
        override val balanceMinor: Long?,
        override val balanceCurrency: String?,
        override val timestamp: LocalDateTime?,
    ) : Sms

    data class IncomingTransfer(
        override val amountMinor: Long,
        override val currency: String,
        val senderName: String?,
        override val balanceMinor: Long?,
        override val balanceCurrency: String?,
        override val timestamp: LocalDateTime?,
    ) : Sms

    /** Пополнение депозита; Credo не присылает IBAN, но присылает новый доступный остаток. */
    data class DepositTopUp(
        override val amountMinor: Long,
        override val currency: String,
        override val balanceMinor: Long?,
        override val balanceCurrency: String?,
        override val timestamp: LocalDateTime?,
    ) : Sms

    /** Перевод между своими счетами: есть From/To IBAN. */
    data class OwnTransfer(
        override val amountMinor: Long,
        override val currency: String,
        val fromIban: String,
        val toIban: String,
        override val balanceMinor: Long?,
        override val balanceCurrency: String?,
        override val timestamp: LocalDateTime?,
    ) : Sms

    /** Utility or service bill paid from the account; no card is named. */
    data class BillPayment(
        override val amountMinor: Long,
        override val currency: String,
        /** Service provider as printed, before normalization. */
        val serviceRaw: String?,
        override val balanceMinor: Long?,
        override val balanceCurrency: String?,
        override val timestamp: LocalDateTime?,
    ) : Sms

    /** Cash paid into the account at a machine or a desk. */
    data class CashDeposit(
        override val amountMinor: Long,
        override val currency: String,
        override val balanceMinor: Long?,
        override val balanceCurrency: String?,
        override val timestamp: LocalDateTime?,
    ) : Sms

    /** Interest the bank paid on a deposit. */
    data class InterestAccrual(
        override val amountMinor: Long,
        override val currency: String,
        override val balanceMinor: Long?,
        override val balanceCurrency: String?,
        override val timestamp: LocalDateTime?,
    ) : Sms

    data class CurrencyExchange(
        /** Продано (списано). */
        override val amountMinor: Long,
        override val currency: String,
        /** Получено. */
        val receivedAmountMinor: Long,
        val receivedCurrency: String,
        override val balanceMinor: Long?,
        override val balanceCurrency: String?,
        override val timestamp: LocalDateTime?,
    ) : Sms

    private val paymentDate = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.US)
    private val transferDate = DateTimeFormatter.ofPattern("M/d/yyyy h:mm:ss a", Locale.US)

    private val amountRegex = Regex("""([\d,]+\.\d{1,2})\s*([A-Z]{3})""")
    private val cardRegex = Regex("""Card N \*+(\d{4})""")
    private val paymentDateRegex = Regex("""(\d{2}/\d{2}/\d{4} \d{2}:\d{2}:\d{2})""")
    private val transferDateRegex =
        Regex("""Date:\s*(\d{1,2}/\d{1,2}/\d{4} \d{1,2}:\d{2}:\d{2} [AP]M)""")
    private val ibanRegex = Regex("""(GE\d{2}[A-Z]{2}\d{16})""")

    fun classify(body: String): Classification {
        val text = body
            .substringBefore("want to split the bill?")
            .trim()
        if (text.isEmpty()) return Classification.Ignored(IgnoreReason.UNRELATED, credoCandidate = false)

        return when {
            text.startsWith("Rejected payment") ->
                Classification.Ignored(IgnoreReason.REJECTED, credoCandidate = true)
            text.startsWith("Canceled operation") ->
                parseCardPayment(text.substringAfter("\n"))
                    ?.let(Classification::Canceled)
                    ?: Classification.Ignored(IgnoreReason.REJECTED, credoCandidate = true)
            isOneTimeCode(text) -> Classification.Ignored(IgnoreReason.OTP, credoCandidate = true)
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
            else -> Classification.Ignored(IgnoreReason.UNRELATED, credoCandidate = false)
        }
    }

    /** Credo uses more than one code template, and none of them is an operation. */
    private fun isOneTimeCode(text: String): Boolean =
        (text.startsWith("CODE:") && text.contains("confirms card", ignoreCase = true)) ||
            text.startsWith("# SMS Code:") ||
            Regex("""\bOTP:\s*\d""").containsMatchIn(text)

    /** Money moved only if the message states an amount next to a ledger label. */
    private fun looksFinancial(text: String): Boolean =
        amountRegex.containsMatchIn(text) &&
            Regex("""(?i)\b(amount|balance)\b|Card N \*""").containsMatchIn(text)

    /** Backwards-compatible parser for callers that only need recognized transactions. */
    fun parse(body: String): Sms? = (classify(body) as? Classification.Parsed)?.sms

    fun isCredoCandidate(body: String): Boolean = when (val result = classify(body)) {
        is Classification.Parsed, is Classification.Canceled, Classification.Unrecognized -> true
        is Classification.Ignored -> result.credoCandidate
    }

    private inline fun parsedOrUnrecognized(block: () -> Sms?): Classification =
        block()?.let(Classification::Parsed) ?: Classification.Unrecognized

    private fun money(match: MatchResult): Pair<Long, String> {
        // "12.3" is twelve lari thirty, not one hundred twenty-three: pad before dropping the dot.
        val (units, fraction) = match.groupValues[1].replace(",", "").split(".")
        val minor = units.toLong() * 100 + fraction.padEnd(2, '0').toLong()
        return minor to match.groupValues[2]
    }

    private fun firstAmountAfter(text: String, label: String): Pair<Long, String>? {
        val idx = text.indexOf(label)
        if (idx < 0) return null
        return amountRegex.find(text, idx)?.let(::money)
    }

    private fun parseCardPayment(text: String): CardPayment? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val (amount, currency) = firstAmountAfter(text, "Payment:") ?: return null
        val card = cardRegex.find(text)?.groupValues?.get(1) ?: return null
        val timestamp = paymentDateRegex.find(text)
            ?.let { runCatching { LocalDateTime.parse(it.groupValues[1], paymentDate) }.getOrNull() }
            ?: return null

        // Строка мерчанта — следующая после "Card N ..."
        val cardLineIdx = lines.indexOfFirst { cardRegex.containsMatchIn(it) }
        val merchantLine = lines.getOrNull(cardLineIdx + 1) ?: return null
        val merchantRaw = merchantLine.substringBefore('>').trim()
        val locationRaw = merchantLine.substringAfter('>', "").trim().takeIf { it.isNotEmpty() }

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
        val (amount, currency) = firstAmountAfter(text, "Amount:") ?: return null
        val balance = firstAmountAfter(text, "Balance:")
        return OutgoingTransfer(
            amountMinor = amount,
            currency = currency,
            balanceMinor = balance?.first,
            balanceCurrency = balance?.second,
            timestamp = transferTimestamp(text) ?: return null,
        )
    }

    private fun parseIncomingTransfer(text: String): IncomingTransfer? {
        val (amount, currency) = firstAmountAfter(text, "Amount:") ?: return null
        val sender = Regex("""From sender:\s*([^;\n]+)""").find(text)
            ?.groupValues?.get(1)?.trim()
        val balance = firstAmountAfter(text, "Balance:")
        return IncomingTransfer(
            amountMinor = amount,
            currency = currency,
            senderName = sender,
            balanceMinor = balance?.first,
            balanceCurrency = balance?.second,
            timestamp = transferTimestamp(text) ?: return null,
        )
    }

    private fun parseDepositTopUp(text: String): DepositTopUp? {
        val (amount, currency) = firstAmountAfter(text, "Amount:") ?: return null
        val balance = firstAmountAfter(text, "Available Balance on Deposit")
        return DepositTopUp(
            amountMinor = amount,
            currency = currency,
            balanceMinor = balance?.first,
            balanceCurrency = balance?.second,
            timestamp = transferTimestamp(text) ?: return null,
        )
    }

    private fun parseOwnTransfer(text: String): OwnTransfer? {
        val (amount, currency) = firstAmountAfter(text, "Amount:") ?: return null
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
            timestamp = transferTimestamp(text) ?: return null,
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
            timestamp = looseTransferTimestamp(text),
        )
    }

    private fun parseInterest(text: String): InterestAccrual? {
        val (amount, currency) = firstAmountAfter(text, "amount") ?: return null
        val balance = firstAmountAfter(text, "Available Balance")
        return InterestAccrual(
            amountMinor = amount,
            currency = currency,
            balanceMinor = balance?.first,
            balanceCurrency = balance?.second,
            // Only a bare day is printed, and its order is ambiguous; the delivery time is honest.
            timestamp = null,
        )
    }

    private fun parseCurrencyExchange(text: String): CurrencyExchange? {
        val (amount, currency) = firstAmountAfter(text, "Amount:") ?: return null
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
            timestamp = transferTimestamp(text) ?: return null,
        )
    }
}
