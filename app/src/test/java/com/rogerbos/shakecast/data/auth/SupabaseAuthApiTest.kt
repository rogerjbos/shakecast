package com.rogerbos.shakecast.data.auth

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SupabaseAuthApiTest {

    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var api: SupabaseAuthApi
    private lateinit var url: String

    private val sessionBody = """
        {"access_token":"jwt-1","token_type":"bearer","expires_in":3600,"expires_at":1800000000,
         "refresh_token":"refresh-1","user":{"id":"6f1d2a3b-0000-4000-8000-000000000001","email":"roger@example.com"}}
    """.trimIndent()

    @Before
    fun setUp() {
        server.start()
        api = SupabaseAuthApi(OkHttpClient(), json)
        url = server.url("/").toString()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun signInPostsPasswordGrantAndParsesSession() {
        server.enqueue(MockResponse().setBody(sessionBody))

        val session = api.signIn(url, "anon-key", "roger@example.com", "hunter22").getOrThrow()

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/auth/v1/token?grant_type=password", request.path)
        assertEquals("anon-key", request.getHeader("apikey"))
        assertNull(request.getHeader("Authorization"))
        assertTrue(request.body.readUtf8().contains("\"password\":\"hunter22\""))
        assertEquals("6f1d2a3b-0000-4000-8000-000000000001", session.userId)
        assertEquals("roger@example.com", session.email)
        assertEquals("jwt-1", session.accessToken)
        assertEquals("refresh-1", session.refreshToken)
        assertEquals(1800000000_000L, session.expiresAtMs)
    }

    @Test
    fun signUpWithoutSessionAsksForEmailConfirmation() {
        server.enqueue(MockResponse().setBody("""{"id":"u1","email":"roger@example.com","confirmation_sent_at":"2026-11-02T10:00:00Z"}"""))

        val result = api.signUp(url, "anon-key", "roger@example.com", "hunter22").getOrThrow()

        assertEquals("/auth/v1/signup", server.takeRequest().path)
        assertEquals(SignUpResult.ConfirmEmail, result)
    }

    @Test
    fun signUpWithSessionIsSignedIn() {
        server.enqueue(MockResponse().setBody(sessionBody))
        val result = api.signUp(url, "anon-key", "roger@example.com", "hunter22").getOrThrow()
        assertTrue(result is SignUpResult.SignedIn)
    }

    @Test
    fun refreshSendsRefreshTokenAndKeepsEmail() {
        server.enqueue(MockResponse().setBody(sessionBody.replace("jwt-1", "jwt-2").replace("refresh-1", "refresh-2")))
        val old = AuthSession("u", "roger@example.com", "jwt-1", "refresh-1", 0L)

        val fresh = api.refresh(url, "anon-key", old).getOrThrow()

        val request = server.takeRequest()
        assertEquals("/auth/v1/token?grant_type=refresh_token", request.path)
        assertEquals("""{"refresh_token":"refresh-1"}""", request.body.readUtf8())
        assertEquals("jwt-2", fresh.accessToken)
        assertEquals("refresh-2", fresh.refreshToken)
    }

    @Test
    fun signOutSendsBearerToken() {
        server.enqueue(MockResponse().setResponseCode(204))
        api.signOut(url, "anon-key", AuthSession("u", "e", "jwt-1", "refresh-1", 0L)).getOrThrow()
        val request = server.takeRequest()
        assertEquals("/auth/v1/logout", request.path)
        assertEquals("Bearer jwt-1", request.getHeader("Authorization"))
    }

    @Test
    fun serverMessageIsSurfacedWithStatus() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"code":400,"error_code":"invalid_credentials","msg":"Invalid login credentials"}"""))

        val failure = api.signIn(url, "anon-key", "roger@example.com", "nope").exceptionOrNull() as AuthException

        assertEquals(400, failure.code)
        assertEquals("Invalid login credentials", failure.message)
    }

    @Test
    fun legacyErrorShapeIsAlsoParsed() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"invalid_grant","error_description":"Token expired"}"""))
        val failure = api.refresh(url, "anon-key", AuthSession("u", "e", "a", "r", 0L)).exceptionOrNull()
        assertEquals("Token expired", failure?.message)
    }
}
