package com.rogerbos.shakecast.data.remote.sync

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

class SupabaseSyncApiTest {

    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var api: SupabaseSyncApi
    private lateinit var config: SupabaseConfig
    private val USER = "6f1d2a3b-0000-4000-8000-000000000001"

    @Before
    fun setUp() {
        server.start()
        api = SupabaseSyncApi(OkHttpClient(), json)
        config = SupabaseConfig(url = server.url("/").toString(), apiKey = "sb_publishable_abc", accessToken = "user-jwt", userId = USER)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun upsertSendsMergeDuplicatesAndScopingHeaders() {
        server.enqueue(MockResponse().setResponseCode(201))

        val row = EpisodeStateRow(USER, "https://feed", "guid-1", 1234, false, null, 99)
        api.pushEpisodeStates(config, listOf(row)).getOrThrow()

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/rest/v1/episode_states", request.path)
        assertEquals("sb_publishable_abc", request.getHeader("apikey"))
        assertEquals("Bearer user-jwt", request.getHeader("Authorization"))
        assertNull(request.getHeader("x-sync-key"))
        assertEquals("resolution=merge-duplicates,return=minimal", request.getHeader("Prefer"))
        val sent = json.decodeFromString<List<EpisodeStateRow>>(request.body.readUtf8())
        assertEquals(listOf(row), sent)
    }

    @Test
    fun anonKeyIsNeverUsedAsBearer() {
        server.enqueue(MockResponse().setResponseCode(201))
        val jwtConfig = config.copy(apiKey = "eyJhbGciOiJIUzI1NiJ9.x.y")

        api.pushSubscriptions(jwtConfig, listOf(SubscriptionRow(userId = USER, feedUrl = "f", subscribed = false, updatedAtMs = 1))).getOrThrow()

        val request = server.takeRequest()
        assertEquals("eyJhbGciOiJIUzI1NiJ9.x.y", request.getHeader("apikey"))
        assertEquals("Bearer user-jwt", request.getHeader("Authorization"))
    }

    @Test
    fun emptyPushMakesNoRequest() {
        api.pushSubscriptions(config, emptyList()).getOrThrow()
        assertEquals(0, server.requestCount)
    }

    @Test
    fun pullFiltersByUserAndPagesUntilShortPage() {
        val page = (0 until 1000).map { SubscriptionRow(USER, "https://feed/$it", subscribed = true, updatedAtMs = it.toLong()) }
        val tail = listOf(SubscriptionRow(USER, "https://feed/last", subscribed = true, updatedAtMs = 5000))
        server.enqueue(MockResponse().setBody(json.encodeToString(page)))
        server.enqueue(MockResponse().setBody(json.encodeToString(tail)))

        val rows = api.pullSubscriptions(config).getOrThrow()

        assertEquals(1001, rows.size)
        val first = server.takeRequest()
        assertEquals("GET", first.method)
        assertEquals("eq.$USER", first.requestUrl?.queryParameter("user_id"))
        assertEquals("0", first.requestUrl?.queryParameter("offset"))
        assertEquals("1000", server.takeRequest().requestUrl?.queryParameter("offset"))
    }

    @Test
    fun serverErrorsSurfaceStatusAndBody() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"Invalid API key"}"""))

        val failure = api.pullEpisodeStates(config).exceptionOrNull()

        assertTrue(failure?.message?.startsWith("HTTP 401") == true)
        assertTrue(failure?.message?.contains("Invalid API key") == true)
    }

    @Test
    fun pingReportsOkWhenTableIsReadable() {
        server.enqueue(MockResponse().setBody("[]"))
        assertEquals("OK", api.ping(config).getOrThrow())
        assertEquals("/rest/v1/subscriptions?select=user_id&limit=1", server.takeRequest().path)
    }
}
