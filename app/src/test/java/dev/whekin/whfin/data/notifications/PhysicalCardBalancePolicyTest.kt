package dev.whekin.whfin.data.notifications

import dev.whekin.whfin.data.db.PaymentInstrumentEntity
import dev.whekin.whfin.data.db.PaymentInstrumentType
import org.junit.Assert.assertEquals
import org.junit.Test

class PhysicalCardBalancePolicyTest {
    @Test
    fun primaryCardOwnsFocusAndVirtualPrimarySilencesPhysicalWarning() {
        val physical = PaymentInstrumentEntity(
            id = 1,
            groupId = 1,
            type = PaymentInstrumentType.PHYSICAL_CARD,
            last4 = "0001",
        )
        val virtualPrimary = PaymentInstrumentEntity(
            id = 2,
            groupId = 1,
            type = PaymentInstrumentType.VIRTUAL_CARD,
            last4 = "0002",
            isPrimary = true,
        )

        assertEquals(emptyList<PaymentInstrumentEntity>(), focusedPhysicalCards(listOf(physical, virtualPrimary), setOf(1, 2)))
        assertEquals(listOf(physical), focusedPhysicalCards(listOf(physical), setOf(1)))
    }

    @Test
    fun thresholdsUseStrictBelowSemantics() {
        assertEquals(PhysicalCardBalanceStatus.Enough, physicalCardBalanceStatus(150_00))
        assertEquals(PhysicalCardBalanceStatus.Low, physicalCardBalanceStatus(149_99))
        assertEquals(PhysicalCardBalanceStatus.Low, physicalCardBalanceStatus(100_00))
        assertEquals(PhysicalCardBalanceStatus.Critical, physicalCardBalanceStatus(99_99))
    }

    @Test
    fun criticalBalanceNotifiesOnceUntilRecovery() {
        val first = lowBalanceNotificationTransition(emptySet(), mapOf(7L to 99_00))
        assertEquals(setOf(7L), first.accountIdsToNotify)

        val stillLow = lowBalanceNotificationTransition(first.alertedAccountIds, mapOf(7L to 50_00))
        assertEquals(emptySet<Long>(), stillLow.accountIdsToNotify)

        val recovered = lowBalanceNotificationTransition(stillLow.alertedAccountIds, mapOf(7L to 100_00))
        assertEquals(emptySet<Long>(), recovered.alertedAccountIds)

        val lowAgain = lowBalanceNotificationTransition(recovered.alertedAccountIds, mapOf(7L to 80_00))
        assertEquals(setOf(7L), lowAgain.accountIdsToNotify)
    }
}
