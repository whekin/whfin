package dev.whekin.whfin

import android.content.ComponentName
import android.content.Intent
import dev.whekin.whfin.data.preferences.AppLockTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityStartupTest {
    @Test
    fun freshInstallStartsAtWelcomeChoice() {
        assertEquals(
            AppEntry.Welcome,
            initialAppEntry(
                welcomeCompleted = false,
                personalSetupPending = false,
                demoMode = false,
            ),
        )
    }

    @Test
    fun completedWelcomeNeverReturnsWhilePersonalSetupCanResume() {
        assertEquals(
            AppEntry.PersonalSetup,
            initialAppEntry(
                welcomeCompleted = true,
                personalSetupPending = true,
                demoMode = false,
            ),
        )
        assertEquals(
            AppEntry.Main,
            initialAppEntry(
                welcomeCompleted = true,
                personalSetupPending = false,
                demoMode = false,
            ),
        )
    }

    @Test
    fun activeDemoAlwaysOpensTheWorkspace() {
        assertEquals(
            AppEntry.Main,
            initialAppEntry(
                welcomeCompleted = false,
                personalSetupPending = true,
                demoMode = true,
            ),
        )
    }

    private fun setupInvitation(
        welcomeCompleted: Boolean = true,
        personalSetupPending: Boolean = false,
        demoMode: Boolean = false,
        dismissed: Boolean = false,
        bankLedgerCount: Int? = 0,
        statementImportCount: Int? = 0,
    ) = showSetupInvitation(
        welcomeCompleted = welcomeCompleted,
        personalSetupPending = personalSetupPending,
        demoMode = demoMode,
        dismissed = dismissed,
        bankLedgerCount = bankLedgerCount,
        statementImportCount = statementImportCount,
    )

    @Test
    fun aWorkspaceWithNoBankIsOfferedTheSetupItWalkedPast() {
        assertTrue(setupInvitation())
    }

    /** The offer withdraws itself the moment a bank exists, however it got there. */
    @Test
    fun aConnectedBankWithdrawsTheOffer() {
        assertFalse(setupInvitation(bankLedgerCount = 1))
        assertFalse(setupInvitation(statementImportCount = 1))
    }

    /** A ledger that has not loaded is not an empty one, so the offer waits instead of flashing. */
    @Test
    fun aLoadingLedgerIsNotAnEmptyOne() {
        assertFalse(setupInvitation(bankLedgerCount = null))
        assertFalse(setupInvitation(statementImportCount = null))
    }

    @Test
    fun theOfferCanBeRefusedForGood() {
        assertFalse(setupInvitation(dismissed = true))
    }

    /** Nothing offers setup over somebody else's synthetic data, or over setup already running. */
    @Test
    fun demoAndAnOpenSetupNeverSeeTheOffer() {
        assertFalse(setupInvitation(demoMode = true))
        assertFalse(setupInvitation(personalSetupPending = true))
        assertFalse(setupInvitation(welcomeCompleted = false))
    }

    @Test
    fun loadingPreferencesNeverShowsLockGate() {
        assertEquals(AppStartupContent.Loading, appStartupContent(null, hasPin = true, sessionLocked = true))
    }

    @Test
    fun disabledLockNeverShowsGateEvenIfSessionStartsLocked() {
        assertEquals(
            AppStartupContent.Main,
            appStartupContent(AppLockTimeout.Disabled, hasPin = true, sessionLocked = true),
        )
    }

    @Test
    fun missingPinCannotShowGate() {
        assertEquals(
            AppStartupContent.Main,
            appStartupContent(AppLockTimeout.Immediate, hasPin = false, sessionLocked = true),
        )
    }

    @Test
    fun enabledLockShowsGateOnlyForLockedSession() {
        assertEquals(
            AppStartupContent.LockGate,
            appStartupContent(AppLockTimeout.OneMinute, hasPin = true, sessionLocked = true),
        )
        assertEquals(
            AppStartupContent.Main,
            appStartupContent(AppLockTimeout.OneMinute, hasPin = true, sessionLocked = false),
        )
    }

    @Test
    fun runtimeModeRestartKeepsAnUnlockedForegroundSession() {
        assertEquals(
            AppStartupContent.Main,
            appStartupContent(
                savedTimeout = AppLockTimeout.Immediate,
                hasPin = true,
                sessionLocked = true,
                runtimeModeRestart = true,
            ),
        )
    }

    @Test
    fun runtimeModeRestartClearsTheOldViewModelTask() {
        val component = ComponentName("dev.whekin.whfin", "dev.whekin.whfin.MainActivity")

        val intent = runtimeModeRestartIntent(component)

        assertEquals(component, intent.component)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK != 0)
        assertTrue(intent.getBooleanExtra(EXTRA_RUNTIME_MODE_RESTART, false))
    }
}
