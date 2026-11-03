package com.rogerbos.shakecast.data.auth

/** Tokens for one signed-in Supabase Auth user. */
data class AuthSession(
    val userId: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMs: Long
) {
    /** True when the access token has expired or will within [marginMs]. */
    fun needsRefresh(nowMs: Long, marginMs: Long = REFRESH_MARGIN_MS): Boolean = nowMs + marginMs >= expiresAtMs

    companion object {
        const val REFRESH_MARGIN_MS = 60_000L
    }
}
