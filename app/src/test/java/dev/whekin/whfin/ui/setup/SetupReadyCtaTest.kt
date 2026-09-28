package dev.whekin.whfin.ui.setup

import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import org.junit.Assert.assertEquals
import org.junit.Test

class SetupReadyCtaTest {
    private val account = AccountEntity(id = 1, name = "Current", type = AccountType.BANK, currency = "GEL")
    private fun ready(difference: Long?): SetupOverviewState.Ready {
        val review = SetupAccountReview(account, "Credo", 0, difference?.let { 0 },
            difference?.let { 1 }, difference, null, 0, "evidence")
        return SetupOverviewState.Ready(SetupOverview(listOf(review), emptyMap(), emptySet(),
            0, 0, 0, 0, 0, 0))
    }

    @Test fun loadingEvidenceCannotBeSkippedByAnImmediateStartTap() {
        assertEquals(SetupReadyCta.WAIT_FOR_DATA,
            setupReadyCta(SetupOverviewState.Loading, false, false))
        assertEquals(SetupReadyCta.START_WITHOUT_REVIEW,
            setupReadyCta(SetupOverviewState.Failed, false, false))
    }

    @Test fun bankMatchNeedsNoOwnerTapButUnprovedBalanceStaysVisible() {
        assertEquals(SetupReadyCta.START, setupReadyCta(ready(0), false, false))
        assertEquals(SetupReadyCta.START_WITH_BALANCES_TO_REVIEW,
            setupReadyCta(ready(null), false, false))
        val reviewedDifference = SetupOverviewState.Ready(ready(50).value.copy(
            accounts = ready(50).value.accounts.map { it.copy(checked = true) }))
        assertEquals(SetupReadyCta.START_WITH_KNOWN_DIFFERENCE,
            setupReadyCta(reviewedDifference, false, false))
        assertEquals(SetupReadyCta.START_WITH_BANK_ACTION,
            setupReadyCta(ready(0), true, false))
    }

    @Test fun noAccountsOrActiveBankAreNamedBeforeStarting() {
        val empty = SetupOverviewState.Ready(SetupOverview(emptyList(), emptyMap(), emptySet(),
            0, 0, 0, 0, 0, 0))
        assertEquals(SetupReadyCta.START_WITHOUT_ACCOUNTS, setupReadyCta(empty, false, false))
        assertEquals(SetupReadyCta.START_DURING_BANK_SYNC, setupReadyCta(empty, false, true))
    }
}
