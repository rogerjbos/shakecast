package com.rogerbos.shakecast.data.remote.transcript

import com.rogerbos.shakecast.domain.model.TranscriptSegment
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

class TranscriptFetcher @Inject constructor(
    private val client: OkHttpClient
) {
    fun fetch(url: String, typeHint: String?): Result<List<TranscriptSegment>> = runCatching {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code} for $url")
            val body = response.body?.string() ?: throw IllegalStateException("Empty transcript at $url")
            val contentType = typeHint ?: response.header("Content-Type")
            TranscriptParser.parse(body, contentType, url)
        }
    }
}
