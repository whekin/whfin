package dev.whekin.whfin.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.test.performScrollTo
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
import org.junit.Assert.assertFalse
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

        val historyAction = context.getString(R.string.sms_history_title)
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

        compose.onNodeWithText(context.getString(R.string.sms_card_required_title)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.sms_last_four_label)).performTextInput("25")
        compose.onNodeWithText(context.getString(R.string.sms_save_card_action)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(context.getString(R.string.sms_last_four_label)).performTextInput("33")
        compose.onNodeWithText(context.getString(R.string.sms_save_card_action)).performClick()

        assertEquals(Triple(11L, "2533", PaymentInstrumentType.PHYSICAL_CARD), saved)
    }

    @Test
    fun cardMapping_withoutAnyAccount_createsTheFirstCredoLedgerInPlace() {
        var created: List<Any>? = null
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            WhfinTheme {
                SmsDiagnosticsScreen(
                    loadState = SmsDiagnosticsLoadState.Content(SmsDiagnosticsData()),
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
                    onAddCardMapping = { _, _, _ -> error("There is no account to link to yet") },
                    onCreateAccountAndAddCardMapping = { name, currency, last4, type ->
                        created = listOf(name, currency, last4, type)
                    },
                    onViewMessage = {},
                    onDismissMessage = {},
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.sms_card_required_title)).performClick()
        val primary = context.getString(R.string.sms_create_account_and_card_action)
        compose.onNodeWithText(primary).assertIsNotEnabled()
        compose.onNodeWithContentDescription(context.getString(R.string.sms_last_four_label))
            .performTextInput("2533")
        compose.onNodeWithText(primary).performClick()

        compose.runOnIdle {
            assertEquals(
                listOf("Credo", "GEL", "2533", PaymentInstrumentType.PHYSICAL_CARD),
                created,
            )
        }
    }

    @Test
    fun attentionOutcome_withoutMatchingAccount_createsOneWithoutLeavingTheSheet() {
        var created: List<Any>? = null
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val diagnostic = SmsDiagnosticEntity(
            id = 21,
            externalKey = "sms|no-account",
            kind = SmsDiagnosticKind.CARD_PAYMENT,
            outcome = SmsDiagnosticOutcome.CHOOSE_ACCOUNT,
            reason = SmsDiagnosticReason.NO_ACCOUNT,
            receivedAt = 1_000,
            amountMinor = 2_500,
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
                    onResolve = { _, _, _ -> error("There is no matching account to link to") },
                    onAddCardMapping = { _, _, _ -> },
                    onCreateAccountAndResolve = { diagnosticId, name, currency, type ->
                        created = listOf(diagnosticId, name, currency, type)
                    },
                    onViewMessage = {},
                    onDismissMessage = {},
                )
            }
        }

        compose.onNodeWithTag("sms-diagnostics-list")
            .performScrollToNode(hasContentDescription(context.getString(R.string.sms_link_action)))
        compose.onNodeWithContentDescription(context.getString(R.string.sms_link_action)).performClick()
        compose.onNodeWithText(context.getString(R.string.accounts_add)).performClick()
        compose.onNodeWithText(context.getString(R.string.sms_create_and_link_action)).performClick()

        compose.runOnIdle {
            assertEquals(
                listOf(21L, "Credo", "GEL", PaymentInstrumentType.PHYSICAL_CARD),
                created,
            )
        }
    }

    @Test
    fun monitoringOff_hasOneDirectEnableAction() {
        var enabled = false
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            WhfinTheme {
                SmsDiagnosticsScreen(
                    loadState = SmsDiagnosticsLoadState.Content(SmsDiagnosticsData()),
                    scanState = SmsScanState.Idle,
                    messageState = SmsMessageState.Hidden,
                    smsImportEnabled = false,
                    hasReceivePermission = false,
                    hasHistoryPermission = false,
                    canRequestHistoryPermission = true,
                    onEnableMonitoring = { enabled = true },
                    onScanHistory = {},
                    onConfirmHistoryImport = {},
                    onCancelHistoryImport = {},
                    onResolve = { _, _, _ -> },
                    onAddCardMapping = { _, _, _ -> },
                    onViewMessage = {},
                    onDismissMessage = {},
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.sms_monitoring_enable_action)).performClick()
        compose.runOnIdle { assertTrue(enabled) }
    }

    @Test
    fun groupedAttention_routesBackToFeedInsteadOfOneAccountSheet() {
        var openedFeed = false
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val diagnostic = SmsDiagnosticEntity(
            id = 18,
            externalKey = "sms|grouped",
            kind = SmsDiagnosticKind.CURRENCY_EXCHANGE,
            outcome = SmsDiagnosticOutcome.CHOOSE_ACCOUNT,
            reason = SmsDiagnosticReason.NO_ACCOUNT,
            receivedAt = 1_000,
            occurredAt = 1_000,
            amountMinor = 5_000,
            currency = "GEL",
            secondaryAmountMinor = 1_800,
            secondaryCurrency = "USD",
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
                    onOpenFeed = { openedFeed = true },
                    onScanHistory = {},
                    onConfirmHistoryImport = {},
                    onCancelHistoryImport = {},
                    onResolve = { _, _, _ -> error("Grouped SMS cannot use one-account routing") },
                    onAddCardMapping = { _, _, _ -> },
                    onViewMessage = {},
                    onDismissMessage = {},
                )
            }
        }

        val action = context.getString(R.string.sms_resolve_in_feed_action)
        compose.onNodeWithTag("sms-diagnostics-list")
            .performScrollToNode(hasContentDescription(action))
        compose.onNodeWithContentDescription(action).performClick()
        compose.runOnIdle { assertTrue(openedFeed) }
    }

    @Test
    fun unrecognizedMessage_opensRedactedEditorBeforeRequestingOriginal() {
        var originalRequestedFor: Long? = null
        var sharedPayload: String? = null
        var rawState by mutableStateOf<SmsShareMessageState>(SmsShareMessageState.Hidden)
        val rawBody = "Credo notice: changed private format"
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val diagnostic = SmsDiagnosticEntity(
            id = 15,
            externalKey = "sms|private",
            kind = SmsDiagnosticKind.UNRECOGNIZED,
            outcome = SmsDiagnosticOutcome.UNRECOGNIZED,
            reason = SmsDiagnosticReason.PARSE_FAILURE,
            receivedAt = 1_000,
            counterparty = "Private Person",
            cardLast4 = "0001",
            updatedAt = 1_000,
        )
        compose.setContent {
            WhfinTheme {
                SmsDiagnosticsScreen(
                    appVersion = "0.1.0 (1)",
                    loadState = SmsDiagnosticsLoadState.Content(
                        SmsDiagnosticsData(diagnostics = listOf(diagnostic)),
                    ),
                    scanState = SmsScanState.Idle,
                    messageState = SmsMessageState.Hidden,
                    shareMessageState = rawState,
                    smsImportEnabled = true,
                    hasReceivePermission = true,
                    hasHistoryPermission = true,
                    canRequestHistoryPermission = true,
                    onScanHistory = {},
                    onConfirmHistoryImport = {},
                    onCancelHistoryImport = {},
                    onResolve = { _, _, _ -> },
                    onAddCardMapping = { _, _, _ -> },
                    onViewMessage = {},
                    onDismissMessage = {},
                    onLoadShareMessage = {
                        originalRequestedFor = it.id
                        rawState = SmsShareMessageState.Content(rawBody)
                    },
                    onDismissShareMessage = { rawState = SmsShareMessageState.Hidden },
                    onSharePayload = { sharedPayload = it },
                )
            }
        }

        val shareAction = context.getString(R.string.sms_share_problem_action)
        compose.onNodeWithTag("sms-diagnostics-list")
            .performScrollToNode(hasContentDescription(shareAction))
        compose.onNodeWithContentDescription(shareAction).performClick()

        compose.onNodeWithText(context.getString(R.string.sms_share_redacted_title)).assertIsDisplayed()
        compose.onNodeWithTag("sms-include-original").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(15L, originalRequestedFor) }
        compose.onNodeWithText(
            context.getString(R.string.sms_share_raw_confirm_title),
        ).assertIsDisplayed()
        compose.onNodeWithText(rawBody, substring = true).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.sms_share_include_original_action)).performClick()
        compose.onNodeWithText(context.getString(R.string.sms_share_action)).performClick()
        compose.runOnIdle {
            assertFalse(requireNotNull(sharedPayload).contains("Private Person"))
            assertFalse(requireNotNull(sharedPayload).contains("0001"))
            assertTrue(requireNotNull(sharedPayload).contains(rawBody))
        }
    }
}
