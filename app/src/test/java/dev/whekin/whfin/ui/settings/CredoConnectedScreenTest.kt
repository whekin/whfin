package dev.whekin.whfin.ui.settings

import android.content.Context
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.R
import dev.whekin.whfin.data.credo.CredoRemoteAccount
import dev.whekin.whfin.data.importer.StatementImporter
import dev.whekin.whfin.ui.theme.WhfinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The connected screen: what a finished run says, and the separate way to reach past it.
 *
 * Everything here sits behind a real bank login, so it is the only place this composition can be
 * exercised without one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CredoConnectedScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val accounts = listOf(
        CredoRemoteAccount("GE00WH0000000000000000", "GEL", 1, null, null),
        CredoRemoteAccount("GE00WH0000000000000000", "USD", 2, null, null),
    )

    private fun show(
        state: CredoSyncUiState,
        onLoadHistory: () -> Unit = {},
        onSaveOriginalStatement: (String, String) -> Unit = { _, _ -> },
    ) {
        compose.setContent {
            WhfinTheme {
                CredoSyncScreen(
                    state = state,
                    appLockEnabled = true,
                    onOpenAppLock = {},
                    onConnect = { _, _, _ -> },
                    onSubmitOtp = {},
                    onResendOtp = {},
                    onSync = {},
                    onLoadHistory = onLoadHistory,
                    onDisconnect = {},
                    onDismissError = {},
                    onSaveOriginalStatement = onSaveOriginalStatement,
                )
            }
        }
    }

    @Test
    fun quietAccountsAreSaidOnceInsteadOfOncePerAccount() {
        show(
            CredoSyncUiState(
                stage = CredoSyncStage.Connected,
                accounts = accounts,
                results = listOf(CredoSyncFileResult("Card •0000 · GEL", inserted = 4, duplicates = 11)),
                unchanged = 2,
            ),
        )

        compose.onNodeWithText(
            context.getString(R.string.credo_sync_result_success, 4, 11, 0),
        ).performScrollTo().assertExists()
        compose.onNodeWithText(
            context.resources.getQuantityString(R.plurals.credo_sync_unchanged, 2, 2),
        ).performScrollTo().assertExists()
    }

    @Test
    fun aRunWhereNothingChangedStillReportsItself() {
        show(
            CredoSyncUiState(
                stage = CredoSyncStage.Connected,
                accounts = accounts,
                results = emptyList(),
                unchanged = 2,
            ),
        )

        // No result rows at all, so the section has to appear for the count alone.
        compose.onNodeWithText(context.getString(R.string.credo_sync_result_section))
            .performScrollTo().assertExists()
    }

    @Test
    fun aRefusedStatementNamesTheRuleAndThePeriodItWasAskedFor() {
        show(
            CredoSyncUiState(
                stage = CredoSyncStage.Connected,
                accounts = accounts,
                results = listOf(
                    CredoSyncFileResult(
                        "Card •0000 · GEL",
                        errorCode = "STATEMENT_REJECTED",
                        detail = "Statement balance chain breaks at row 12.",
                        askedFrom = "2025-08-11",
                        askedTo = "2026-08-11",
                    ),
                ),
            ),
        )

        // Without these two a genuine fault reads the same as one asked for the wrong window.
        compose.onNodeWithText("Statement balance chain breaks at row 12.", substring = true)
            .performScrollTo().assertExists()
        compose.onNodeWithText(
            context.getString(R.string.credo_sync_error_window, "2025-08-11", "2026-08-11"),
            substring = true,
        ).performScrollTo().assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.credo_sync_original_save))
            .assertDoesNotExist()
    }

    @Test
    fun aRejectedDownloadedStatementOffersItsExactOriginalForSaving() {
        var requested: Pair<String, String>? = null
        show(
            state = CredoSyncUiState(
                stage = CredoSyncStage.Connected,
                accounts = accounts,
                results = listOf(
                    CredoSyncFileResult(
                        accountLabel = "Card •0000 · GEL",
                        errorCode = "STATEMENT_REJECTED",
                        originalStatementToken = "memory-token",
                        originalStatementFileName = "mycredo_gel_0000.xlsx",
                    ),
                ),
            ),
            onSaveOriginalStatement = { token, fileName -> requested = token to fileName },
        )

        compose.onNodeWithContentDescription(context.getString(R.string.credo_sync_original_save))
            .performScrollTo()
            .performClick()

        assertEquals("memory-token" to "mycredo_gel_0000.xlsx", requested)
    }

    @Test
    fun aHistoryWalkShowsImportedRowsAndTheLaterRejectedWindow() {
        show(
            CredoSyncUiState(
                stage = CredoSyncStage.Connected,
                accounts = accounts,
                results = listOf(
                    CredoSyncFileResult(
                        accountLabel = "Card •0000 · GEL",
                        inserted = 42,
                        detail = "Statement balance summary is incomplete.",
                        askedFrom = "2022-08-11",
                        askedTo = "2023-08-10",
                        originalStatementToken = "memory-token",
                        originalStatementFileName = "mycredo_gel_0000.xlsx",
                    ),
                ),
            ),
        )

        compose.onNodeWithText(
            context.getString(R.string.credo_sync_result_success, 42, 0, 0),
            substring = true,
        ).performScrollTo().assertExists()
        compose.onNodeWithText("Statement balance summary is incomplete.", substring = true)
            .performScrollTo().assertExists()
        compose.onNodeWithText(
            context.getString(R.string.credo_sync_error_window, "2022-08-11", "2023-08-10"),
            substring = true,
        ).performScrollTo().assertExists()
    }

    @Test
    fun loadingHistoryIsItsOwnAction() {
        var requested = 0
        show(
            CredoSyncUiState(stage = CredoSyncStage.Connected, accounts = accounts),
            onLoadHistory = { requested += 1 },
        )

        compose.onNodeWithText(context.getString(R.string.credo_sync_history_action))
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        assertEquals(1, requested)
    }

    @Test
    fun aRunInFlightNamesTheYearItIsFetchingAndBlocksBothActions() {
        show(
            CredoSyncUiState(
                stage = CredoSyncStage.Syncing,
                accounts = accounts,
                currentAccount = 1,
                currentChunk = 3,
                currentPhase = StatementImporter.Phase.READING,
            ),
        )

        compose.onNodeWithText(context.getString(R.string.credo_sync_history_progress, 3))
            .performScrollTo().assertExists()
        compose.onNodeWithText(context.getString(R.string.credo_sync_history_action))
            .performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.credo_sync_syncing))
            .performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun pricingWhatTheWalkBroughtInIsShownRatherThanLookingStuck() {
        show(
            CredoSyncUiState(
                stage = CredoSyncStage.Syncing,
                accounts = accounts,
                currentAccount = 2,
                currentChunk = 4,
                valuedDays = 120,
            ),
        )

        // Rates come after the last statement, so this replaces the year counter rather than
        // leaving it frozen on the chunk that already finished.
        compose.onNodeWithText(context.getString(R.string.credo_sync_valuing, 120))
            .performScrollTo().assertExists()
        compose.onNodeWithText(context.getString(R.string.credo_sync_history_action))
            .performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.credo_sync_syncing))
            .performScrollTo().assertIsNotEnabled()
    }
}
