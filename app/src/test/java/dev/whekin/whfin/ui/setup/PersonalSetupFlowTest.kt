package dev.whekin.whfin.ui.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersonalSetupFlowTest {
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
            PersonalSetupPage.Home,
            personalSetupResolutionPage(
                PersonalSetupState(unresolvedSmsCount = 0, statementReviewCount = 0),
            ),
        )
    }
}
