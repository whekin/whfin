package dev.whekin.whfin.ui.setup

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.whekin.whfin.R
import dev.whekin.whfin.data.sync.BankSyncStatus
import dev.whekin.whfin.data.sync.SyncPhase

internal enum class SetupBankProgressKind {
    NOT_CONNECTED, CONNECTED, CONNECTING, LOADING, REVIEW_BALANCES,
    NEEDS_ATTENTION, INTERRUPTED, HISTORY_AVAILABLE, HISTORY_LOADED,
}

internal data class SetupBankProgress(
    val kind: SetupBankProgressKind,
    val count: Int = 0,
    val current: Int = 0,
    val total: Int = 0,
) {
    val isNotable: Boolean get() = kind in setOf(
        SetupBankProgressKind.CONNECTED,
        SetupBankProgressKind.CONNECTING,
        SetupBankProgressKind.LOADING,
        SetupBankProgressKind.REVIEW_BALANCES,
        SetupBankProgressKind.NEEDS_ATTENTION,
        SetupBankProgressKind.INTERRUPTED,
    )
    val needsAction: Boolean get() = kind in setOf(
        SetupBankProgressKind.CONNECTED,
        SetupBankProgressKind.REVIEW_BALANCES,
        SetupBankProgressKind.NEEDS_ATTENTION,
        SetupBankProgressKind.INTERRUPTED,
    )
    val historyPending: Boolean get() = kind in setOf(
        SetupBankProgressKind.CONNECTED,
        SetupBankProgressKind.CONNECTING,
        SetupBankProgressKind.LOADING,
        SetupBankProgressKind.REVIEW_BALANCES,
        SetupBankProgressKind.NEEDS_ATTENTION,
        SetupBankProgressKind.INTERRUPTED,
    )
}

/** Both banks use the same setup vocabulary; their actual import evidence remains bank-specific. */
internal fun setupBankProgress(
    status: BankSyncStatus?,
    accountCount: Int,
    hasImportedHistory: Boolean,
    balancesToReview: Int = 0,
): SetupBankProgress = when {
    status?.active == true && status.canReturnHome -> SetupBankProgress(
        SetupBankProgressKind.LOADING, current = status.current, total = status.total)
    status?.active == true -> SetupBankProgress(SetupBankProgressKind.CONNECTING)
    balancesToReview > 0 -> SetupBankProgress(SetupBankProgressKind.REVIEW_BALANCES, count = balancesToReview)
    status?.phase == SyncPhase.INTERRUPTED -> SetupBankProgress(SetupBankProgressKind.INTERRUPTED)
    status?.phase == SyncPhase.ATTENTION -> SetupBankProgress(SetupBankProgressKind.NEEDS_ATTENTION)
    status?.phase == SyncPhase.COMPLETE && status.canReturnHome ->
        SetupBankProgress(SetupBankProgressKind.HISTORY_LOADED)
    hasImportedHistory -> SetupBankProgress(SetupBankProgressKind.HISTORY_AVAILABLE)
    accountCount > 0 -> SetupBankProgress(SetupBankProgressKind.CONNECTED, count = accountCount)
    else -> SetupBankProgress(SetupBankProgressKind.NOT_CONNECTED)
}

/** An old idle run must never send a newly opened bank page forward. */
internal fun shouldAdvanceAfterBankSignIn(
    armedPage: SetupPage?,
    visiblePage: SetupPage?,
    entryRunId: Long,
    status: BankSyncStatus?,
): Boolean = armedPage != null && armedPage == visiblePage &&
    status?.runId != null && status.runId != entryRunId && status.canReturnHome

internal data class SetupBankReturn(val stack: List<String>, val stage: SetupStage)

internal fun returnFromBankPage(
    stack: List<String>, stage: SetupStage, page: SetupPage, firstConnection: Boolean,
): SetupBankReturn {
    if (SetupPage.fromSaved(stack.lastOrNull()) != page) return SetupBankReturn(stack, stage)
    return SetupBankReturn(stack.dropLast(1),
        if (firstConnection && stage == SetupStage.Banks) SetupStage.Sms else stage)
}

@Composable
internal fun SetupBankProgress.label(): String = when (kind) {
    SetupBankProgressKind.NOT_CONNECTED -> stringResource(R.string.setup_not_added)
    SetupBankProgressKind.CONNECTED -> stringResource(R.string.setup_bank_connected, count)
    SetupBankProgressKind.CONNECTING -> stringResource(R.string.setup_bank_connecting)
    SetupBankProgressKind.LOADING -> if (total > 0 && current > 0)
        stringResource(R.string.setup_bank_loading_progress, current, total)
    else stringResource(R.string.setup_bank_loading)
    SetupBankProgressKind.REVIEW_BALANCES -> stringResource(R.string.setup_bank_review_balances, count)
    SetupBankProgressKind.NEEDS_ATTENTION -> stringResource(R.string.setup_bank_attention)
    SetupBankProgressKind.INTERRUPTED -> stringResource(R.string.setup_bank_interrupted)
    SetupBankProgressKind.HISTORY_AVAILABLE -> stringResource(R.string.setup_history_available)
    SetupBankProgressKind.HISTORY_LOADED -> stringResource(R.string.setup_history_loaded)
}
