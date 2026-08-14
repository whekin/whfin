package dev.whekin.whfin.ui

import dev.whekin.whfin.ui.analytics.AnalyticsPeriod
import dev.whekin.whfin.ui.analytics.AnalyticsTransactionsRequest
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellNavigationTest {

    private val request = AnalyticsTransactionsRequest(
        period = AnalyticsPeriod.month(YearMonth.of(2026, 7)),
        categoryFilterEnabled = true,
        categoryId = 4,
        filterName = "Eating out",
        expectedExpenseMinor = -12_700,
    )

    @Test
    fun `a scene carries the arguments it was opened with`() {
        val target = shellTargetFor(
            secondaryDestination = SecondaryDestination.AccountTransactions,
            accountTransactionsId = 42,
            analyticsTransactions = null,
        )

        assertEquals(ShellScene.AccountTransactions, target.scene)
        assertEquals(42L, target.accountId)
    }

    @Test
    fun `monthly transactions are a scene of their own instead of a nested layer`() {
        val target = shellTargetFor(
            secondaryDestination = SecondaryDestination.Analytics,
            accountTransactionsId = null,
            analyticsTransactions = request,
        )

        assertEquals(ShellScene.AnalyticsTransactions, target.scene)
        assertEquals(request, target.analytics)
        assertTrue(
            "opening the month must read as going deeper",
            shellTransitionIsForward(shellTargetFor(SecondaryDestination.Analytics, null, null), target),
        )
    }

    @Test
    fun `returning from the month lands back on statistics, not the feed`() {
        val analytics = shellTargetFor(SecondaryDestination.Analytics, null, null)
        val month = shellTargetFor(SecondaryDestination.Analytics, null, request)

        assertFalse(shellTransitionIsForward(month, analytics))
        assertEquals(ShellScene.Analytics, analytics.scene)
    }

    @Test
    fun `transaction history is one level below home`() {
        val home = shellTargetFor(null, null, null)
        val history = shellTargetFor(SecondaryDestination.TransactionHistory, null, null)

        assertEquals(ShellScene.TransactionHistory, history.scene)
        assertTrue(shellTransitionIsForward(home, history))
        assertFalse(shellTransitionIsForward(history, home))
    }

    @Test
    fun `spending analysis is a child of statistics`() {
        val analytics = shellTargetFor(SecondaryDestination.Analytics, null, null)
        val spending = shellTargetFor(SecondaryDestination.AnalyticsExpenses, null, null)

        assertEquals(ShellScene.AnalyticsExpenses, spending.scene)
        assertTrue(shellTransitionIsForward(analytics, spending))
        assertFalse(shellTransitionIsForward(spending, analytics))
    }

    @Test
    fun `only a shallower destination animates as a return`() {
        val primary = shellTargetFor(null, null, null)
        val settings = shellTargetFor(SecondaryDestination.Settings, null, null)
        val about = shellTargetFor(SecondaryDestination.About, null, null)

        assertTrue(shellTransitionIsForward(primary, settings))
        assertTrue(shellTransitionIsForward(settings, about))
        assertFalse(shellTransitionIsForward(about, settings))
        assertFalse(shellTransitionIsForward(settings, primary))
        // Peer replacing peer: Bank SMS and Backup both live under Settings.
        assertTrue(
            shellTransitionIsForward(
                shellTargetFor(SecondaryDestination.SmsDiagnostics, null, null),
                shellTargetFor(SecondaryDestination.Backup, null, null),
            ),
        )
    }

    @Test
    fun `app lock setup returns to the Credo caller`() {
        assertEquals(
            SecondaryDestination.CredoSync,
            appLockReturnDestination(SecondaryDestination.CredoSync),
        )
        assertEquals(
            SecondaryDestination.Settings,
            appLockReturnDestination(null),
        )
    }

    @Test
    fun `opening Credo setup enables SMS monitoring before navigation`() {
        val events = mutableListOf<String>()

        openCredoSetup(
            enableSmsMonitoring = { events += "sms" },
            openCredo = { events += "credo" },
        )

        assertEquals(listOf("sms", "credo"), events)
    }

    @Test
    fun `Credo Back returns to the screen that opened it`() {
        assertEquals(null, credoBackDestination(null))
        assertEquals(
            SecondaryDestination.Settings,
            credoBackDestination(SecondaryDestination.Settings),
        )
    }

    @Test
    fun `secondary Back returns through the actual callers`() {
        var current: SecondaryDestination? = null
        var stack = emptyList<SecondaryDestination>()

        fun open(destination: SecondaryDestination) {
            stack = pushSecondaryDestination(current, stack, destination)
            current = destination
        }

        open(SecondaryDestination.Settings)
        open(SecondaryDestination.DataHealth)
        open(SecondaryDestination.Backup)

        popSecondaryDestination(stack).also { back ->
            assertEquals(SecondaryDestination.DataHealth, back.destination)
            current = back.destination
            stack = back.remaining
        }
        popSecondaryDestination(stack).also { back ->
            assertEquals(SecondaryDestination.Settings, back.destination)
            current = back.destination
            stack = back.remaining
        }
        popSecondaryDestination(stack).also { back ->
            assertEquals(null, back.destination)
            assertTrue(back.remaining.isEmpty())
        }
    }

    @Test
    fun `Back from a screen opened at the shell root returns to the shell root`() {
        val stack = pushSecondaryDestination(
            current = null,
            backStack = emptyList(),
            destination = SecondaryDestination.Statements,
        )

        val back = popSecondaryDestination(stack)

        assertEquals(null, back.destination)
        assertTrue(back.remaining.isEmpty())
    }

    @Test
    fun `Back from Accounts opened by the low balance warning returns to Home`() {
        assertEquals(0, primaryTabAfterBack(currentTab = 1))
        assertEquals(null, primaryTabAfterBack(currentTab = 0))
    }
}
