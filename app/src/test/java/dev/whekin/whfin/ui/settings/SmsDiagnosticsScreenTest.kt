package dev.whekin.whfin.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.R
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.PaymentInstrumentType
import dev.whekin.whfin.data.db.SmsDiagnosticEntity
import dev.whekin.whfin.data.db.SmsDiagnosticKind
import dev.whekin.whfin.data.db.SmsDiagnosticOutcome
import dev.whekin.whfin.data.db.SmsDiagnosticReason
import dev.whekin.whfin.ui.theme.WhfinTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SmsDiagnosticsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun historyPermission_isExplicitAndDoesNotImportBeforePreview() {
        var requested = false
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            WhfinTheme {
                SmsDiagnosticsScreen(
                    loadState = SmsDiagnosticsLoadState.Content(SmsDiagnosticsData()),
                    scanState = SmsScanState.Idle,
                    smsImportEnabled = true,
                    hasReceivePermission = true,
                    hasHistoryPermission = false,
                    canRequestHistoryPermission = true,
                    onScanHistory = { requested = true },
                    onConfirmHistoryImport = { error("Import must not start before preview") },
                    onCancelHistoryImport = {},
                    onResolve = { _, _, _ -> },
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.sms_read_permission)).performClick()
        assertTrue(requested)
        compose.onNodeWithText(context.getString(R.string.sms_diagnostics_empty_title)).assertIsDisplayed()
    }

    @Test
    fun attentionOutcome_canBeLinkedToTheOnlyMatchingAccount() {
        var resolved: Triple<Long, Long, PaymentInstrumentType>? = null
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val diagnostic = SmsDiagnosticEntity(
            id = 7,
            externalKey = "sms|test",
            kind = SmsDiagnosticKind.CARD_PAYMENT,
            outcome = SmsDiagnosticOutcome.NEEDS_CARD_MAPPING,
            reason = SmsDiagnosticReason.NO_CARD_MAPPING,
            receivedAt = 1_000,
            amountMinor = 1234,
            currency = "GEL",
            balanceCurrency = "GEL",
            cardLast4 = "0001",
            counterparty = "Example",
            updatedAt = 1_000,
        )
        compose.setContent {
            WhfinTheme {
                SmsDiagnosticsScreen(
                    loadState = SmsDiagnosticsLoadState.Content(
                        SmsDiagnosticsData(
                            diagnostics = listOf(diagnostic),
                            accounts = listOf(
                                SmsAccountOption(
                                    AccountEntity(
                                        id = 11,
                                        name = "Main",
                                        type = AccountType.BANK,
                                        groupId = 1,
                                        currency = "GEL",
                                    ),
                                    groupName = "Credo",
                                ),
                            ),
                        ),
                    ),
                    scanState = SmsScanState.Idle,
                    smsImportEnabled = true,
                    hasReceivePermission = true,
                    hasHistoryPermission = true,
                    canRequestHistoryPermission = true,
                    onScanHistory = {},
                    onConfirmHistoryImport = {},
                    onCancelHistoryImport = {},
                    onResolve = { diagnosticId, accountId, cardType ->
                        resolved = Triple(diagnosticId, accountId, cardType)
                    },
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.sms_outcome_mapping)).performClick()
        compose.onNodeWithText(context.getString(R.string.sms_link_action)).performClick()
        assertEquals(Triple(7L, 11L, PaymentInstrumentType.PHYSICAL_CARD), resolved)
    }
}
