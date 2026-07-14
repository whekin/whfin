package dev.whekin.whfin.data.security

import dev.whekin.whfin.data.preferences.AppLockTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockSessionTest {
    private var now = 1_000L
    private val session = AppLockSession { now }

    @Test
    fun disabledColdStart_isImmediatelyAvailable() {
        session.configure(AppLockTimeout.Disabled)

        assertFalse(session.locked)
    }

    @Test
    fun missingWhfinCode_neverLocksAfterRestoredPreference() {
        session.configure(AppLockTimeout.Immediate, hasPin = false)

        assertFalse(session.locked)
        assertFalse(session.timeout.enabled)
    }

    @Test
    fun enabledColdStart_staysLockedUntilAuthentication() {
        session.configure(AppLockTimeout.OneMinute)

        assertTrue(session.locked)
        session.unlock()
        assertFalse(session.locked)
    }

    @Test
    fun foreground_locksOnlyAfterConfiguredTimeout() {
        session.configure(AppLockTimeout.OneMinute)
        session.unlock()
        session.background()
        now += 59_999L
        session.foreground()
        assertFalse(session.locked)

        session.background()
        now += 60_000L
        session.foreground()
        assertTrue(session.locked)
    }

    @Test
    fun immediate_locksOnFirstForegroundReturn() {
        session.configure(AppLockTimeout.Immediate)
        session.unlock()
        session.background()
        session.foreground()

        assertTrue(session.locked)
    }

    @Test
    fun disablingLock_clearsLockedStateAndTimer() {
        session.configure(AppLockTimeout.FiveMinutes)
        session.background()
        session.configure(AppLockTimeout.Disabled)
        now += 1_000_000L
        session.foreground()

        assertFalse(session.locked)
    }
}
