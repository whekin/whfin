package dev.whekin.whfin.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
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
                    messageState = SmsMessageState.Hidden,
                    smsImportEnabled = true,
                    hasReceivePermission = true,
                    hasHistoryPermission = false,
                    canRequestHistoryPermission = true,
                    onScanHistory = { requested = true },
                    onConfirmHistoryImport = { error("Import must not start before preview") },
                    onCancelHistoryImport = {},
                    onResolve = { _, _, _ -> },
                    onAddCardMapping = { _, _, _ -> },
                    onViewMessage = { _ -> },
                    onDismissMessage = {},
                )
            }
        }

        val historyAction = context.getString(R.string.sms_read_permission)
        compose.onNodeWithTag("sms-diagnostics-list").performScrollToNode(hasText(historyAction))
        compose.onNodeWithText(historyAction).performClick()
        assertTrue(requested)
        val emptyTitle = context.getString(R.string.sms_diagnostics_empty_title)
        compose.onNodeWithTag("sms-diagnostics-list").performScrollToNode(hasText(emptyTitle))
        compose.onNodeWithText(emptyTitle).assertIsDisplayed()
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
                            cardFamilies = listOf(
                                SmsCardFamily(
                                    primaryAccountId = 11,
                                    groupName = "Credo",
                                    iban = null,
                                    accounts = listOf(
                                        AccountEntity(11, "Main", AccountType.BANK, 1, "GEL"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                    scanState = SmsScanState.Idle,
                    messageState = SmsMessageState.Hidden,
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
                    onAddCardMapping = { _, _, _ -> },
                    onViewMessage = { _ -> },
                    onDismissMessage = {},
                )
            }
        }

        val mappingTitle = context.getString(R.string.sms_outcome_mapping)
        compose.onNodeWithTag("sms-diagnostics-list").performScrollToNode(hasText(mappingTitle))
        compose.onNodeWithContentDescription(context.getString(R.string.sms_link_action)).performClick()
        compose.onNodeWithText(context.getString(R.string.sms_link_action)).performClick()
        assertEquals(Triple(7L, 11L, PaymentInstrumentType.PHYSICAL_CARD), resolved)
    }

    @Test
    fun diagnostic_exposesOriginalMessageWithoutPersistingItInUiState() {
        var viewedId: Long? = null
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val diagnostic = SmsDiagnosticEntity(
            id = 9,
            externalKey = "sms|source",
            kind = SmsDiagnosticKind.CARD_PAYMENT,
            outcome = SmsDiagnosticOutcome.IMPORTED,
            receivedAt = 1_000,
            amountMinor = 1234,
            currency = "GEL",
            cardLast4 = "2533",
            counterparty = "Example",
            updatedAt = 1_000,
        )
        compose.setContent {
            WhfinTheme {
                SmsDiagnosticsScreen(
                    loadState = SmsDiagnosticsLoadState.Content(
                        SmsDiagnosticsData(diagnostics = listOf(diagnostic)),
                    ),
                    scanState = SmsScanState.Idle,
                    messageState = SmsMessageState.Hidden,
                    smsImportEnabled = true,
                    hasReceivePermission = true,
                    hasHistoryPermission = true,
                    canRequestHistoryPermission = true,
                    onScanHistory = {},
                    onConfirmHistoryImport = {},
                    onCancelHistoryImport = {},
                    onResolve = { _, _, _ -> },
                    onAddCardMapping = { _, _, _ -> },
                    onViewMessage = { viewedId = it.id },
                    onDismissMessage = {},
                )
            }
        }

        val action = context.getString(R.string.sms_view_message_action)
        compose.onNodeWithTag("sms-diagnostics-list").performScrollToNode(hasContentDescription(action))
        compose.onNodeWithContentDescription(action).performClick()
        assertEquals(9L, viewedId)
    }

    @Test
    fun proactiveCardMapping_requiresExactlyFourDigits() {
        var saved: Triple<Long, String, PaymentInstrumentType>? = null
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            WhfinTheme {
                SmsDiagnosticsScreen(
                    loadState = SmsDiagnosticsLoadState.Content(
                        SmsDiagnosticsData(
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
                            cardFamilies = listOf(
                                SmsCardFamily(
                                    primaryAccountId = 11,
                                    groupName = "Credo",
                                    iban = null,
                                    accounts = listOf(
                                        AccountEntity(11, "Main", AccountType.BANK, 1, "GEL"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                    scanState = SmsScanState.Idle,
                    messageState = SmsMessageState.Hidden,
                    smsImportEnabled = false,
                    hasReceivePermission = true,
                    hasHistoryPermission = true,
                    canRequestHistoryPermission = true,
                    onScanHistory = {},
                    onConfirmHistoryImport = {},
                    onCancelHistoryImport = {},
                    onResolve = { _, _, _ -> },
                    onAddCardMapping = { accountId, last4, type ->
                        saved = Triple(accountId, last4, type)
                    },
                    onViewMessage = { _ -> },
                    onDismissMessage = {},
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.sms_add_card_action)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.sms_last_four_label)).performTextInput("25")
        compose.onNodeWithText(context.getString(R.string.sms_save_card_action)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(context.getString(R.string.sms_last_four_label)).performTextInput("33")
        compose.onNodeWithText(context.getString(R.string.sms_save_card_action)).performClick()

        assertEquals(Triple(11L, "2533", PaymentInstrumentType.PHYSICAL_CARD), saved)
    }
}
