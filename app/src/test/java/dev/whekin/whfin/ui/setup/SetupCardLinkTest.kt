package dev.whekin.whfin.ui.setup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupCardLinkTest {
    @Test fun aLateBankImportStartsOneCardScanEvenAfterTheSmsPageWasPassed() {
        assertFalse(shouldCheckCards(true, true, 7, -1, 0, -1))
        assertTrue(shouldCheckCards(true, false, 7, -1, 0, -1))
        assertFalse(shouldCheckCards(true, false, 7, 7, 0, 0))
        assertTrue(shouldCheckCards(true, false, 8, 7, 0, 0))
        assertTrue(shouldCheckCards(true, false, 7, 7, 1, 0))
        assertFalse(shouldCheckCards(false, false, 8, 7, 0, 0))
    }
}
