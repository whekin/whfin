package dev.whekin.whfin.ui.setup

/** Keep the final action honest while account evidence is still being read. */
internal enum class SetupReadyCta {
    WAIT_FOR_DATA,
    START_WITHOUT_REVIEW,
    START_WITH_BANK_ACTION,
    START_DURING_BANK_SYNC,
    START_WITHOUT_ACCOUNTS,
    START_WITH_BALANCES_TO_REVIEW,
    START_WITH_KNOWN_DIFFERENCE,
    START_WITH_ACCOUNT_DETAILS,
    START,
}

internal fun setupReadyCta(
    overview: SetupOverviewState,
    bankNeedsAction: Boolean,
    bankWorkActive: Boolean,
): SetupReadyCta = when (overview) {
    SetupOverviewState.Loading -> SetupReadyCta.WAIT_FOR_DATA
    SetupOverviewState.Failed -> SetupReadyCta.START_WITHOUT_REVIEW
    is SetupOverviewState.Ready -> when {
        bankNeedsAction -> SetupReadyCta.START_WITH_BANK_ACTION
        bankWorkActive -> SetupReadyCta.START_DURING_BANK_SYNC
        overview.value.accounts.isEmpty() -> SetupReadyCta.START_WITHOUT_ACCOUNTS
        !overview.value.allResolved -> SetupReadyCta.START_WITH_BALANCES_TO_REVIEW
        overview.value.hasBankDifference -> SetupReadyCta.START_WITH_KNOWN_DIFFERENCE
        overview.value.bankSetupNeedsReview > 0 -> SetupReadyCta.START_WITH_ACCOUNT_DETAILS
        else -> SetupReadyCta.START
    }
}
