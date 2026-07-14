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

    sealed interface Sms {
        val amountMinor: Long
        val currency: String
        val balanceMinor: Long?
        val balanceCurrency: String?
        val timestamp: LocalDateTime
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
        override val timestamp: LocalDateTime,
    ) : Sms

    data class OutgoingTransfer(
        override val amountMinor: Long,
        override val currency: String,
        override val balanceMinor: Long?,
        override val balanceCurrency: String?,
        override val timestamp: LocalDateTime,
    ) : Sms

    data class IncomingTransfer(
        override val amountMinor: Long,
        override val currency: String,
        val senderName: String?,
        override val balanceMinor: Long?,
        override val balanceCurrency: String?,
        override val timestamp: LocalDateTime,
    ) : Sms

    /** Перевод между своими счетами: есть From/To IBAN. */
    data class OwnTransfer(
        override val amountMinor: Long,
        override val currency: String,
        val fromIban: String,
        val toIban: String,
        override val balanceMinor: Long?,
        override val balanceCurrency: String?,
        override val timestamp: LocalDateTime,
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
        override val timestamp: LocalDateTime,
    ) : Sms

    private val paymentDate = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.US)
    private val transferDate = DateTimeFormatter.ofPattern("M/d/yyyy h:mm:ss a", Locale.US)

    private val amountRegex = Regex("""([\d,]+\.\d{2})\s*([A-Z]{3})""")
    private val cardRegex = Regex("""Card N \*+(\d{4})""")
    private val paymentDateRegex = Regex("""(\d{2}/\d{2}/\d{4} \d{2}:\d{2}:\d{2})""")
    private val transferDateRegex =
        Regex("""Date:\s*(\d{1,2}/\d{1,2}/\d{4} \d{1,2}:\d{2}:\d{2} [AP]M)""")
    private val ibanRegex = Regex("""(GE\d{2}[A-Z]{2}\d{16})""")

    /**
     * @return распознанная транзакция или null (не транзакционная SMS Credo:
     * OTP-коды, rejected payment, реклама и чужие сообщения).
     */
    fun parse(body: String): Sms? {
        val text = body
            .substringBefore("want to split the bill?")
            .trim()
        if (text.isEmpty()) return null

        return when {
            text.startsWith("Rejected payment") -> null
            text.startsWith("CODE:") -> null
            text.startsWith("Payment:") -> parseCardPayment(text)
            text.startsWith("Transfer between accounts") -> parseOwnTransfer(text)
            text.startsWith("Currency exchange") -> parseCurrencyExchange(text)
            text.startsWith("Outgoing transfer") -> parseOutgoingTransfer(text)
            text.startsWith("Incoming transfer") -> parseIncomingTransfer(text)
            else -> null
        }
    }

    private fun money(match: MatchResult): Pair<Long, String> {
        val minor = match.groupValues[1].replace(",", "").replace(".", "").toLong()
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
