package dev.whekin.whfin

import dev.whekin.whfin.data.preferences.AppLockTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityStartupTest {
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
}
