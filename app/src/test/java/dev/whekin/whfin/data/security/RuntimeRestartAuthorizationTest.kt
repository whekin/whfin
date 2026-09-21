package dev.whekin.whfin.data.security

import org.junit.Assert.*
import org.junit.Test

class RuntimeRestartAuthorizationTest {
    @Test fun `only an issued token works and it works once`() {
        val authorization = RuntimeRestartAuthorization()
        assertFalse(authorization.consume(null))
        assertFalse(authorization.consume("true"))
        val token = authorization.issue()
        assertFalse(authorization.consume("forged"))
        assertTrue(authorization.consume(token))
        assertFalse(authorization.consume(token))
    }

    @Test fun `expired permits and permits from an earlier process are rejected`() {
        var now = 10L
        val authorization = RuntimeRestartAuthorization { now }
        val token = authorization.issue()
        assertFalse(RuntimeRestartAuthorization { now }.consume(token))
        now += 30_001
        assertFalse(authorization.consume(token))
    }
}
