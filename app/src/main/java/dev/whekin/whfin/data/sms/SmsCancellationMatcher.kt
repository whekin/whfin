package dev.whekin.whfin.data.sms

import dev.whekin.whfin.data.db.SmsDiagnosticEntity
import dev.whekin.whfin.data.importer.MerchantNormalizer
import kotlin.math.abs

/** Selects a cancellation target only when the original payment is unambiguous. */
internal object SmsCancellationMatcher {
    fun match(
        payment: CredoSmsParser.CardPayment,
        occurredAt: Long,
        candidates: List<SmsDiagnosticEntity>,
    ): SmsDiagnosticEntity? {
        val sameMerchant = candidates.mapNotNull { candidate ->
            val candidateTime = candidate.occurredAt ?: return@mapNotNull null
            candidate
                .takeIf { MerchantNormalizer.equivalent(it.counterparty, payment.merchantRaw) }
                ?.let { it to abs(candidateTime - occurredAt) }
        }
        val closestDistance = sameMerchant.minOfOrNull { it.second } ?: return null
        return sameMerchant.filter { it.second == closestDistance }.singleOrNull()?.first
    }
}
