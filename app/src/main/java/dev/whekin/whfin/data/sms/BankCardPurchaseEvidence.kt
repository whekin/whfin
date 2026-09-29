package dev.whekin.whfin.data.sms

import dev.whekin.whfin.data.db.SmsDiagnosticEntity
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.importer.MerchantNormalizer
import java.math.BigDecimal
import kotlin.math.abs

/** Purchase money printed by Credo, independent of the currency actually debited. */
internal object BankCardPurchaseEvidence {
    private val description = Regex("""^გადახდა - (.+?)\s+([\d,]+\.\d{2}) ([A-Z]{3}) \d{2}\.\d{2}\.\d{4}$""")
    private data class Money(val amount: Long, val currency: String)
    private fun original(transaction: TransactionEntity): Money? {
        if (transaction.origAmountMinor != null && transaction.origCurrency != null)
            return Money(abs(transaction.origAmountMinor), transaction.origCurrency)
        val match = description.matchEntire(transaction.note.orEmpty().trim()) ?: return null
        val amount = runCatching { BigDecimal(match.groupValues[2].replace(",", "")).movePointRight(2).longValueExact() }.getOrNull() ?: return null
        return Money(amount, match.groupValues[3])
    }
    fun exactMoney(transaction: TransactionEntity, message: SmsDiagnosticEntity): Boolean {
        val amount = message.amountMinor ?: return false
        val printed = original(transaction)
        // A printed purchase in this currency is stronger than the converted debit amount.
        if (printed != null && printed.currency == message.currency) return printed.amount == abs(amount)
        return transaction.currency == message.currency && abs(transaction.amountMinor) == abs(amount)
    }
    fun contradictsMoney(transaction: TransactionEntity, message: SmsDiagnosticEntity): Boolean =
        original(transaction)?.let { it.currency == message.currency && it.amount != message.amountMinor?.let(::abs) } == true

    /** Descriptor families are scoped to card evidence; merchant/category memory is untouched. */
    fun merchantMatches(transaction: TransactionEntity, message: SmsDiagnosticEntity): Boolean {
        if (MerchantNormalizer.equivalent(transaction.rawCounterparty, message.counterparty)) return true
        if (!exactMoney(transaction, message)) return false
        val first = amazonFamily(transaction.rawCounterparty) ?: return false
        return first == amazonFamily(message.counterparty)
    }
    private fun amazonFamily(raw: String?): String? {
        val name = raw?.let(MerchantNormalizer::normalize) ?: return null
        return when {
            name == "amazon mktplace pmts" || Regex("""^amazon mktpl\*\S.*$""").matches(name) -> "marketplace"
            Regex("""^amazon(?:\.co\.uk)?(?:\*\s*\S.*)?$""").matches(name) -> "retail"
            else -> null
        }
    }
}
