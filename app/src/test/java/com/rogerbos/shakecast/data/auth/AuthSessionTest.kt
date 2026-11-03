package com.rogerbos.shakecast.data.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthSessionTest {
    private val session = AuthSession("u", "e", "a", "r", expiresAtMs = 1_000_000L)

    @Test
    fun freshTokenNeedsNoRefresh() = assertFalse(session.needsRefresh(nowMs = 1_000_000L - 120_000L))

    @Test
    fun tokenInsideTheMarginIsRefreshed() = assertTrue(session.needsRefresh(nowMs = 1_000_000L - 30_000L))

    @Test
    fun expiredTokenIsRefreshed() = assertTrue(session.needsRefresh(nowMs = 2_000_000L))
}
