package dev.whekin.whfin.ui.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersonalSetupFlowTest {
    @Test
    fun `bank connection waits for SMS consent before opening MyCredo`() {
        assertNull(
            personalSetupPageAfterBankConsent(
                PersonalSetupState(hasCredoImport = false),
            ),
        )
        assertEquals(
            PersonalSetupPage.CredoSync,
            personalSetupPageAfterBankConsent(
                PersonalSetupState(
                    hasCredoImport = false,
                    smsMonitoringEnabled = true,
                    hasSmsPermission = true,
                ),
            ),
        )
    }

    @Test
    fun `existing Credo history continues to resolution after SMS consent`() {
        assertEquals(
            PersonalSetupPage.Categories,
            personalSetupPageAfterBankConsent(
                PersonalSetupState(
                    hasCredoImport = true,
                    smsMonitoringEnabled = true,
                    hasSmsPermission = true,
                    unresolvedSmsCount = 0,
                    statementReviewCount = 0,
                ),
            ),
        )
    }

    @Test
    fun `guided resolution waits until both queues are loaded`() {
        assertNull(
            personalSetupResolutionPage(
                PersonalSetupState(unresolvedSmsCount = 0, statementReviewCount = null),
            ),
        )
    }

    @Test
    fun `guided resolution clears SMS before statement review`() {
        assertEquals(
            PersonalSetupPage.BankSms,
            personalSetupResolutionPage(
                PersonalSetupState(unresolvedSmsCount = 2, statementReviewCount = 1),
            ),
        )
        assertEquals(
            PersonalSetupPage.Statements,
            personalSetupResolutionPage(
                PersonalSetupState(unresolvedSmsCount = 0, statementReviewCount = 1),
            ),
        )
        assertEquals(
            PersonalSetupPage.Categories,
            personalSetupResolutionPage(
                PersonalSetupState(unresolvedSmsCount = 0, statementReviewCount = 0),
            ),
        )
    }

    /**
     * The one step built from the user's own history, so it cannot come before the history does.
     */
    @Test
    fun `categories are proposed only once every import queue is clear`() {
        assertEquals(
            PersonalSetupPage.BankSms,
            personalSetupResolutionPage(
                PersonalSetupState(unresolvedSmsCount = 1, statementReviewCount = 0),
            ),
        )
        assertEquals(
            PersonalSetupPage.Categories,
            personalSetupResolutionPage(
                PersonalSetupState(unresolvedSmsCount = 0, statementReviewCount = 0),
            ),
        )
    }
}
