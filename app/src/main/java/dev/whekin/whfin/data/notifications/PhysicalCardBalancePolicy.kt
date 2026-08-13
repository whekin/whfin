package dev.whekin.whfin.data.notifications

import dev.whekin.whfin.data.db.PaymentInstrumentEntity
import dev.whekin.whfin.data.db.PaymentInstrumentType

const val PHYSICAL_CARD_WARNING_MINOR = 150_00L
const val PHYSICAL_CARD_CRITICAL_MINOR = 100_00L

enum class PhysicalCardBalanceStatus { Enough, Low, Critical }

fun physicalCardBalanceStatus(balanceMinor: Long): PhysicalCardBalanceStatus = when {
    balanceMinor < PHYSICAL_CARD_CRITICAL_MINOR -> PhysicalCardBalanceStatus.Critical
    balanceMinor < PHYSICAL_CARD_WARNING_MINOR -> PhysicalCardBalanceStatus.Low
    else -> PhysicalCardBalanceStatus.Enough
}

/** A chosen Primary card owns the focus, even when it is virtual; without one, keep legacy fallback. */
internal fun focusedPhysicalCards(
    instruments: List<PaymentInstrumentEntity>,
    linkedInstrumentIds: Set<Long>,
): List<PaymentInstrumentEntity> {
    val linked = instruments.filter { it.id in linkedInstrumentIds && !it.isArchived }
    val focused = linked.filter(PaymentInstrumentEntity::isPrimary).ifEmpty { linked }
    return focused.filter { it.type == PaymentInstrumentType.PHYSICAL_CARD }
}

internal data class LowBalanceNotificationTransition(
    val accountIdsToNotify: Set<Long>,
    val alertedAccountIds: Set<Long>,
)

/** A recovered account re-arms; an account that stays below 100 GEL remains quiet. */
internal fun lowBalanceNotificationTransition(
    previouslyAlertedAccountIds: Set<Long>,
    balancesByAccountId: Map<Long, Long>,
): LowBalanceNotificationTransition {
    val criticalIds = balancesByAccountId
        .filterValues { physicalCardBalanceStatus(it) == PhysicalCardBalanceStatus.Critical }
        .keys
    return LowBalanceNotificationTransition(
        accountIdsToNotify = criticalIds - previouslyAlertedAccountIds,
        alertedAccountIds = criticalIds,
    )
}
