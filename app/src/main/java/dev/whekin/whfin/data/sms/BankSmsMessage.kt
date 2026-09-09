package dev.whekin.whfin.data.sms

import java.time.LocalDateTime

/** Bank-neutral message meaning; identity of the sending bank is carried by BankSmsBank. */
object BankSmsMessage {
    enum class IgnoreReason { OTP, REJECTED, UNRELATED }

    sealed interface Classification {
        data class Parsed(val sms: Sms) : Classification

        /**
         * The bank reversed a card payment that a previous message already reported.
         * It is not an operation of its own: it retracts the draft the payment created.
         */
        data class Canceled(val payment: CardPayment) : Classification
        data class Ignored(val reason: IgnoreReason, val bankCandidate: Boolean) : Classification
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
        /**
         * Set when the money came back to a card rather than to an account.
         *
         * A refund names the card and no account at all, so without this the message has nothing to
         * route by: it landed in the ledger only after the user picked an account by hand, or not at
         * all. The card already knows which ledger it belongs to.
         */
        val cardLast4: String? = null,
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
        /**
         * The deposit the bank names, as printed.
         *
         * The only identity this message carries, and the one that does not depend on the ledger
         * being complete: the stated balance has to be reached by adding up everything recorded since
         * the bank last declared one, so a deposit money is constantly moved out of is exactly where
         * that arithmetic misses. The number is printed every time and means the same thing every
         * time.
         */
        val depositNumber: String?,
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

}
