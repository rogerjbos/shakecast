package com.rogerbos.shakecast.data.remote.sync

import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

/** Connection details for one Supabase project; `syncKey` scopes rows to one user. */
data class SupabaseConfig(val url: String, val apiKey: String, val syncKey: String) {
    val isComplete: Boolean get() = url.isNotBlank() && apiKey.isNotBlank() && syncKey.isNotBlank()
}

/**
 * Minimal PostgREST client for the two sync tables. Upserts use
 * `resolution=merge-duplicates`; a database trigger keeps the newer row.
 */
class SupabaseSyncApi @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json
) {
    fun pushSubscriptions(config: SupabaseConfig, rows: List<SubscriptionRow>): Result<Unit> =
        upsert(config, SUBSCRIPTIONS, json.encodeToString(rows))

    fun pushEpisodeStates(config: SupabaseConfig, rows: List<EpisodeStateRow>): Result<Unit> =
        upsert(config, EPISODE_STATES, json.encodeToString(rows))

    fun pullSubscriptions(config: SupabaseConfig): Result<List<SubscriptionRow>> =
        pullAll(config, SUBSCRIPTIONS) { json.decodeFromString<List<SubscriptionRow>>(it) }

    fun pullEpisodeStates(config: SupabaseConfig): Result<List<EpisodeStateRow>> =
        pullAll(config, EPISODE_STATES) { json.decodeFromString<List<EpisodeStateRow>>(it) }

    fun pushManualSkips(config: SupabaseConfig, rows: List<ManualSkipRow>): Result<Unit> =
        upsert(config, MANUAL_SKIPS, json.encodeToString(rows))

    fun pullManualSkips(config: SupabaseConfig): Result<List<ManualSkipRow>> =
        pullAll(config, MANUAL_SKIPS) { json.decodeFromString<List<ManualSkipRow>>(it) }

    fun pushSettings(config: SupabaseConfig, row: SettingsRow): Result<Unit> =
        upsert(config, SETTINGS, json.encodeToString(listOf(row)))

    fun pullSettings(config: SupabaseConfig): Result<SettingsRow?> =
        pullAll(config, SETTINGS) { json.decodeFromString<List<SettingsRow>>(it) }.map { it.firstOrNull() }

    /** Reads one row to confirm the URL, key, tables and policies are all in place. */
    fun ping(config: SupabaseConfig): Result<String> = runCatching {
        val url = tableUrl(config, SUBSCRIPTIONS).newBuilder()
            .addQueryParameter("select", "sync_key")
            .addQueryParameter("limit", "1")
            .build()
        client.newCall(request(config, url).get().build()).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException(describe(response.code, response.body?.string()))
            "OK"
        }
    }

    private fun upsert(config: SupabaseConfig, table: String, body: String): Result<Unit> = runCatching {
        if (body == "[]") return@runCatching
        val request = request(config, tableUrl(config, table))
            .header("Prefer", "resolution=merge-duplicates,return=minimal")
            .post(body.toRequestBody(JSON))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException(describe(response.code, response.body?.string()))
        }
    }

    private fun <T> pullAll(config: SupabaseConfig, table: String, decode: (String) -> List<T>): Result<List<T>> = runCatching {
        val all = mutableListOf<T>()
        var offset = 0
        while (true) {
            val url = tableUrl(config, table).newBuilder()
                .addQueryParameter("select", "*")
                .addQueryParameter("sync_key", "eq.${config.syncKey}")
                .addQueryParameter("order", "updated_at_ms.asc")
                .addQueryParameter("limit", PAGE.toString())
                .addQueryParameter("offset", offset.toString())
                .build()
            val page = client.newCall(request(config, url).get().build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException(describe(response.code, text))
                decode(text)
            }
            all += page
            if (page.size < PAGE) return@runCatching all
            offset += PAGE
        }
        @Suppress("UNREACHABLE_CODE")
        all
    }

    private fun tableUrl(config: SupabaseConfig, table: String): HttpUrl {
        val base = config.url.trim().trimEnd('/').toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid Supabase URL: ${config.url}")
        return base.newBuilder().addPathSegments("rest/v1/$table").build()
    }

    private fun request(config: SupabaseConfig, url: HttpUrl): Request.Builder {
        val key = config.apiKey.trim()
        val builder = Request.Builder().url(url)
            .header("apikey", key)
            .header("x-sync-key", config.syncKey.trim())
            .header("Content-Type", "application/json")
        if (key.startsWith("eyJ")) builder.header("Authorization", "Bearer $key")
        return builder
    }

    private fun describe(code: Int, body: String?): String {
        val detail = body?.trim()?.take(200).orEmpty()
        return if (detail.isEmpty()) "HTTP $code" else "HTTP $code: $detail"
    }

    private companion object {
        const val SUBSCRIPTIONS = "subscriptions"
        const val EPISODE_STATES = "episode_states"
        const val MANUAL_SKIPS = "manual_skips"
        const val SETTINGS = "settings"
        const val PAGE = 1000
        val JSON = "application/json".toMediaType()
    }
}
