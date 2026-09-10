package dev.whekin.whfin.ui

import dev.whekin.whfin.ui.analytics.AnalyticsPeriod
import dev.whekin.whfin.ui.analytics.AnalyticsTransactionsRequest
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellNavigationTest {

    private val homeRoot = shellTargetFor(null, null, null, RootDestination.Home)
    private val analyticsRoot = shellTargetFor(null, null, null, RootDestination.Analytics)

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
            secondaryDestination = null,
            accountTransactionsId = null,
            analyticsTransactions = request,
            root = RootDestination.Analytics,
        )

        assertEquals(ShellScene.AnalyticsTransactions, target.scene)
        assertEquals(request, target.analytics)
        assertTrue(
            "opening the month must read as going deeper",
            shellTransitionIsForward(analyticsRoot, target),
        )
    }

    @Test
    fun `returning from the month lands back on analytics, not the feed`() {
        val month = shellTargetFor(null, null, request, RootDestination.Analytics)

        assertFalse(shellTransitionIsForward(month, analyticsRoot))
        assertEquals(ShellScene.Analytics, analyticsRoot.scene)
    }

    @Test
    fun `the four destinations are peers, and Back from any of them is Home`() {
        // The record and analytics used to be rooms behind an icon beside a balance. As
        // destinations they are neither above nor below the others, and Back is one step out
        // rather than a replay of the order they happened to be visited in.
        RootDestination.entries.forEach { destination ->
            val target = shellTargetFor(null, null, null, destination)
            assertEquals(0, target.scene.depth)
            assertEquals(destination.ordinal, rootOrder(target.scene))
        }
        assertTrue(shellTransitionIsBetweenRoots(homeRoot, analyticsRoot))
        assertFalse(shellTransitionIsBetweenRoots(homeRoot, homeRoot))
        assertFalse(
            shellTransitionIsBetweenRoots(
                homeRoot,
                shellTargetFor(SecondaryDestination.Settings, null, null),
            ),
        )
        assertEquals(RootDestination.Home, rootAfterBack(RootDestination.Analytics))
        assertEquals(RootDestination.Home, rootAfterBack(RootDestination.Transactions))
        assertEquals(RootDestination.Home, rootAfterBack(RootDestination.Accounts))
        assertEquals(null, rootAfterBack(RootDestination.Home))
    }

    @Test
    fun `savings is a first-level reading opened from accounts`() {
        val accounts = shellTargetFor(null, null, null)
        val savings = shellTargetFor(SecondaryDestination.Savings, null, null)

        assertEquals(ShellScene.Savings, savings.scene)
        assertTrue(shellTransitionIsForward(accounts, savings))
        assertFalse(shellTransitionIsForward(savings, accounts))
    }

    @Test
    fun `spending analysis is a step inside analytics`() {
        val spending = shellTargetFor(SecondaryDestination.AnalyticsExpenses, null, null)

        assertEquals(ShellScene.AnalyticsExpenses, spending.scene)
        assertTrue(shellTransitionIsForward(analyticsRoot, spending))
        assertFalse(shellTransitionIsForward(spending, analyticsRoot))
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
        assertEquals(RootDestination.Home, rootAfterBack(RootDestination.Accounts))
        assertEquals(null, rootAfterBack(RootDestination.Home))
    }
}
