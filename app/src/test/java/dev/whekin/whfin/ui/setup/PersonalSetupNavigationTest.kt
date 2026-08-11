package dev.whekin.whfin.ui.setup

import org.junit.Assert.assertEquals
import org.junit.Test

class PersonalSetupNavigationTest {
    @Test
    fun `finishing App Lock returns to the in-progress Credo setup`() {
        assertEquals(PersonalSetupPage.CredoSync, personalSetupPageAfterAppLock())
    }
}
