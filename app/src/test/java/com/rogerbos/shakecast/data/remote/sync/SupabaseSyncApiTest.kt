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

    @Before
    fun setUp() {
        server.start()
        api = SupabaseSyncApi(OkHttpClient(), json)
        config = SupabaseConfig(url = server.url("/").toString(), apiKey = "sb_publishable_abc", syncKey = "shakecast-1234")
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun upsertSendsMergeDuplicatesAndScopingHeaders() {
        server.enqueue(MockResponse().setResponseCode(201))

        val row = EpisodeStateRow("shakecast-1234", "https://feed", "guid-1", 1234, false, null, 99)
        api.pushEpisodeStates(config, listOf(row)).getOrThrow()

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/rest/v1/episode_states", request.path)
        assertEquals("sb_publishable_abc", request.getHeader("apikey"))
        assertEquals("shakecast-1234", request.getHeader("x-sync-key"))
        assertEquals("resolution=merge-duplicates,return=minimal", request.getHeader("Prefer"))
        assertNull(request.getHeader("Authorization"))
        val sent = json.decodeFromString<List<EpisodeStateRow>>(request.body.readUtf8())
        assertEquals(listOf(row), sent)
    }

    @Test
    fun legacyJwtKeyIsAlsoSentAsBearer() {
        server.enqueue(MockResponse().setResponseCode(201))
        val jwtConfig = config.copy(apiKey = "eyJhbGciOiJIUzI1NiJ9.x.y")

        api.pushSubscriptions(jwtConfig, listOf(SubscriptionRow(syncKey = "k", feedUrl = "f", subscribed = false, updatedAtMs = 1))).getOrThrow()

        assertEquals("Bearer eyJhbGciOiJIUzI1NiJ9.x.y", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun emptyPushMakesNoRequest() {
        api.pushSubscriptions(config, emptyList()).getOrThrow()
        assertEquals(0, server.requestCount)
    }

    @Test
    fun pullFiltersBySyncKeyAndPagesUntilShortPage() {
        val page = (0 until 1000).map { SubscriptionRow("shakecast-1234", "https://feed/$it", subscribed = true, updatedAtMs = it.toLong()) }
        val tail = listOf(SubscriptionRow("shakecast-1234", "https://feed/last", subscribed = true, updatedAtMs = 5000))
        server.enqueue(MockResponse().setBody(json.encodeToString(page)))
        server.enqueue(MockResponse().setBody(json.encodeToString(tail)))

        val rows = api.pullSubscriptions(config).getOrThrow()

        assertEquals(1001, rows.size)
        val first = server.takeRequest()
        assertEquals("GET", first.method)
        assertEquals("eq.shakecast-1234", first.requestUrl?.queryParameter("sync_key"))
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
        assertEquals("/rest/v1/subscriptions?select=sync_key&limit=1", server.takeRequest().path)
    }
}
