package com.rogerbos.shakecast.data.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

/** Outcome of a sign-up: either a usable session or a request to confirm the address first. */
sealed interface SignUpResult {
    data class SignedIn(val session: AuthSession) : SignUpResult
    data object ConfirmEmail : SignUpResult
}

/** Thrown for auth responses the server rejected; [code] is the HTTP status. */
class AuthException(val code: Int, message: String) : Exception(message)

/**
 * Minimal client for the Supabase Auth (GoTrue) REST endpoints the app needs:
 * email + password sign-up and sign-in, token refresh, sign-out and account deletion.
 */
class SupabaseAuthApi @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json
) {
    fun signUp(url: String, apiKey: String, email: String, password: String): Result<SignUpResult> = runCatching {
        val body = json.encodeToString(Credentials(email, password))
        val text = call(request(url, apiKey, "auth/v1/signup").post(body.toRequestBody(JSON)).build())
        val payload = json.decodeFromString<SessionDto>(text)
        if (payload.accessToken.isNullOrBlank()) SignUpResult.ConfirmEmail else SignUpResult.SignedIn(payload.toSession(email))
    }

    fun signIn(url: String, apiKey: String, email: String, password: String): Result<AuthSession> = runCatching {
        val body = json.encodeToString(Credentials(email, password))
        val text = call(request(url, apiKey, "auth/v1/token", "grant_type" to "password").post(body.toRequestBody(JSON)).build())
        json.decodeFromString<SessionDto>(text).toSession(email)
    }

    fun refresh(url: String, apiKey: String, session: AuthSession): Result<AuthSession> = runCatching {
        val body = json.encodeToString(RefreshRequest(session.refreshToken))
        val text = call(request(url, apiKey, "auth/v1/token", "grant_type" to "refresh_token").post(body.toRequestBody(JSON)).build())
        json.decodeFromString<SessionDto>(text).toSession(session.email)
    }

    /** Revokes the refresh token server-side; a failure here is not worth surfacing. */
    fun signOut(url: String, apiKey: String, session: AuthSession): Result<Unit> = runCatching {
        val request = request(url, apiKey, "auth/v1/logout")
            .header("Authorization", "Bearer ${session.accessToken}")
            .post(ByteArray(0).toRequestBody(JSON))
            .build()
        call(request)
        Unit
    }

    /** Calls the `delete_account` database function, which removes the user and, by cascade, every row they own. */
    fun deleteAccount(url: String, apiKey: String, session: AuthSession): Result<Unit> = runCatching {
        val request = request(url, apiKey, "rest/v1/rpc/delete_account")
            .header("Authorization", "Bearer ${session.accessToken}")
            .post("{}".toRequestBody(JSON))
            .build()
        call(request)
        Unit
    }

    private fun call(request: Request): String = client.newCall(request).execute().use { response ->
        val text = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw AuthException(response.code, describe(response.code, text))
        text
    }

    private fun request(url: String, apiKey: String, path: String, vararg query: Pair<String, String>): Request.Builder {
        val base = url.trim().trimEnd('/').toHttpUrlOrNull() ?: throw IllegalArgumentException("Invalid Supabase URL: $url")
        val built: HttpUrl = base.newBuilder().addPathSegments(path)
            .apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }
            .build()
        return Request.Builder().url(built)
            .header("apikey", apiKey.trim())
            .header("Content-Type", "application/json")
    }

    private fun describe(code: Int, body: String): String {
        val message = runCatching {
            val obj = json.parseToJsonElement(body) as JsonObject
            listOf("msg", "error_description", "message", "error").firstNotNullOfOrNull { obj[it]?.jsonPrimitive?.content }
        }.getOrNull()
        return when {
            !message.isNullOrBlank() -> message
            body.isBlank() -> "HTTP $code"
            else -> "HTTP $code: ${body.trim().take(200)}"
        }
    }

    @Serializable
    private data class Credentials(val email: String, val password: String)

    @Serializable
    private data class RefreshRequest(@SerialName("refresh_token") val refreshToken: String)

    @Serializable
    private data class UserDto(val id: String, val email: String? = null)

    @Serializable
    private data class SessionDto(
        @SerialName("access_token") val accessToken: String? = null,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresIn: Long? = null,
        @SerialName("expires_at") val expiresAt: Long? = null,
        val user: UserDto? = null,
        val id: String? = null,
        val email: String? = null
    ) {
        fun toSession(fallbackEmail: String): AuthSession {
            val user = user ?: throw IllegalStateException("Auth response has no user")
            val expiresAtMs = when {
                expiresAt != null -> expiresAt * 1000
                expiresIn != null -> System.currentTimeMillis() + expiresIn * 1000
                else -> System.currentTimeMillis() + 3600_000
            }
            return AuthSession(
                userId = user.id,
                email = user.email ?: fallbackEmail,
                accessToken = accessToken ?: throw IllegalStateException("Auth response has no access token"),
                refreshToken = refreshToken ?: throw IllegalStateException("Auth response has no refresh token"),
                expiresAtMs = expiresAtMs
            )
        }
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
