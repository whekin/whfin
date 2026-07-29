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

    @Test
    fun packageUpdateIsDistinctFromFreshInstall() {
        assertFalse(isUpgradeInstallation(firstInstallTime = 10_000, lastUpdateTime = 10_000))
        assertTrue(isUpgradeInstallation(firstInstallTime = 10_000, lastUpdateTime = 10_500))
        assertTrue(isUpgradeInstallation(firstInstallTime = 10_000, lastUpdateTime = 20_000))
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
