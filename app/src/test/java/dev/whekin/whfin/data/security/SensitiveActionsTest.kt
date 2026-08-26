package dev.whekin.whfin.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveActionSessionTest {
    private var now = 1_000L
    private val session = SensitiveActionSession({ now })

    @Test
    fun coldSession_grantsNothing() {
        assertFalse(session.isGranted())
    }

    @Test
    fun freshGrant_coversTheRestOfOneFlow() {
        session.grant()
        now += SensitiveActionSession.GRACE_MILLIS - 1

        assertTrue(session.isGranted())
    }

    @Test
    fun expiredGrant_isNotInherited() {
        session.grant()
        now += SensitiveActionSession.GRACE_MILLIS

        assertFalse(session.isGranted())
    }

    @Test
    fun leavingTheApp_endsTheGrace() {
        session.grant()
        session.clear()

        assertFalse(session.isGranted())
    }

    /** A clock that walks backwards must fail closed, not hand out an unbounded grace. */
    @Test
    fun clockGoingBackwards_revokesTheGrant() {
        session.grant()
        now -= 1

        assertFalse(session.isGranted())
    }
}

class SensitiveActionControllerTest {
    private var now = 1_000L
    private var hasPin = true
    private val controller = SensitiveActionController(hasPin = { hasPin }, elapsedRealtime = { now })

    @Test
    fun withoutACode_thereIsNothingToVerifyAgainst() {
        hasPin = false
        var ran = 0
        controller.require(SensitiveAction.BackupExport) { ran++ }

        assertEquals(1, ran)
        assertNull(controller.pending)
    }

    @Test
    fun protectedAction_waitsForAnAnswer() {
        var ran = 0
        controller.require(SensitiveAction.BackupExport) { ran++ }

        assertEquals(0, ran)
        assertEquals(SensitiveAction.BackupExport, controller.pending)
    }

    @Test
    fun allow_runsTheActionExactlyOnce() {
        var ran = 0
        controller.require(SensitiveAction.BackupRestore) { ran++ }
        controller.allow()
        controller.allow()

        assertEquals(1, ran)
        assertNull(controller.pending)
    }

    @Test
    fun cancel_isAnOrdinaryOutcome() {
        var ran = 0
        controller.require(SensitiveAction.BankCredential) { ran++ }
        controller.cancel()

        assertEquals(0, ran)
        assertNull(controller.pending)
        assertNull(controller.problem)
    }

    @Test
    fun cancelledAction_cannotBeRunByALaterGrant() {
        var ran = 0
        controller.require(SensitiveAction.BankCredential) { ran++ }
        controller.cancel()
        controller.allow()

        assertEquals(0, ran)
    }

    @Test
    fun withinGrace_oneFlowIsNotAskedTwice() {
        controller.require(SensitiveAction.BackupExport) {}
        controller.allow()

        var ran = 0
        controller.require(SensitiveAction.BackupExport) { ran++ }

        assertEquals(1, ran)
        assertNull(controller.pending)
    }

    @Test
    fun afterTheGraceExpires_theNextActionAsksAgain() {
        controller.require(SensitiveAction.BackupExport) {}
        controller.allow()
        now += SensitiveActionSession.GRACE_MILLIS

        var ran = 0
        controller.require(SensitiveAction.BackupExport) { ran++ }

        assertEquals(0, ran)
        assertEquals(SensitiveAction.BackupExport, controller.pending)
    }

    @Test
    fun leavingTheApp_endsTheGraceForTheNextAction() {
        controller.require(SensitiveAction.BackupExport) {}
        controller.allow()
        controller.endGrace()

        var ran = 0
        controller.require(SensitiveAction.BackupRestore) { ran++ }

        assertEquals(0, ran)
        assertEquals(SensitiveAction.BackupRestore, controller.pending)
    }

    @Test
    fun aDismissedPrompt_leavesTheRequestStanding() {
        var ran = 0
        controller.require(SensitiveAction.BackupExport) { ran++ }
        controller.report(AppLockProblem.Cancelled)

        assertEquals(AppLockProblem.Cancelled, controller.problem)
        assertEquals(SensitiveAction.BackupExport, controller.pending)

        controller.allow()
        assertEquals(1, ran)
    }
}
