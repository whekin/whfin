package dev.whekin.whfin.widget

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetLoadFallbackTest {
    @Test
    fun returnsLoadedValue() = runTest {
        val value = withWidgetFallback(fallback = { "fallback" }) { "loaded" }

        assertEquals("loaded", value)
    }

    @Test
    fun fallsBackAfterTimeout() = runTest {
        val value = withWidgetFallback(timeoutMillis = 10, fallback = { "fallback" }) {
            delay(1_000)
            "loaded"
        }

        assertEquals("fallback", value)
    }

    @Test
    fun fallsBackAfterFailure() = runTest {
        val value = withWidgetFallback(fallback = { "fallback" }) {
            error("database unavailable")
        }

        assertEquals("fallback", value)
    }

    @Test(expected = CancellationException::class)
    fun preservesParentCancellation() = runTest {
        withWidgetFallback(fallback = { "fallback" }) {
            throw CancellationException("cancelled")
        }
    }
}
