package dev.whekin.whfin.data.demo

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The first-run gate is decided by whether a ledger already exists, never by install timestamps:
 * those survive a data wipe and made the welcome screen unreachable once a build had been updated.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RuntimeModeStoreTest {

    private fun store() = RuntimeModeStore(ApplicationProvider.getApplicationContext())

    @Test
    fun anEmptyInstallationStillOwesTheWelcomeChoice() {
        val store = store()

        store.adoptExistingInstallation(hasExistingUserData = false)

        assertFalse(store.hasWelcomeChoice)
        assertFalse(store.welcomeCompleted)
    }

    @Test
    fun anInstallationThatAlreadyHasDataIsNeverInterrupted() {
        val store = store()

        store.adoptExistingInstallation(hasExistingUserData = true)

        assertTrue(store.welcomeCompleted)
        assertFalse("adoption must not send an existing user into setup", store.personalSetupPending)
    }

    @Test
    fun anExplicitChoiceIsNeverOverwrittenByAdoption() {
        val store = store()
        store.completeWelcomeChoice(personalSetupPending = true)

        store.adoptExistingInstallation(hasExistingUserData = true)

        assertTrue("the pending setup of a real choice must survive", store.personalSetupPending)
    }
}
