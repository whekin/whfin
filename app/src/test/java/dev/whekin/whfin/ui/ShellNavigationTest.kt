package dev.whekin.whfin.ui

import dev.whekin.whfin.ui.analytics.AnalyticsTransactionsRequest
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellNavigationTest {

    private val request = AnalyticsTransactionsRequest(
        month = YearMonth.of(2026, 7),
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
}
