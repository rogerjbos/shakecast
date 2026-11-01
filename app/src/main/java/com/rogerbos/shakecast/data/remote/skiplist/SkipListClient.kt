package com.rogerbos.shakecast.data.remote.skiplist

import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

/** Fetches "<key>_skiplist.json" from the Mac that runs the podcast3r pipeline. */
class SkipListClient @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json
) {
    /** Returns null when the server has no skip-list for this episode. */
    fun fetch(baseUrl: String, fileName: String): Result<SkipListDto?> = runCatching {
        val base = baseUrl.trim().trimEnd('/').toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid skip-list base URL: $baseUrl")
        val url = base.newBuilder().addPathSegment(fileName).build()
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (response.code == 404) return@use null
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code} for $url")
            val body = response.body?.string() ?: return@use null
            json.decodeFromString<SkipListDto>(body)
        }
    }

    fun ping(baseUrl: String): Result<Int> = runCatching {
        val base = baseUrl.trim().toHttpUrlOrNull() ?: throw IllegalArgumentException("Invalid URL")
        client.newCall(Request.Builder().url(base).head().build()).execute().use { it.code }
    }
}
