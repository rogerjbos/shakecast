package com.rogerbos.shakecast.data.remote.chapters

import com.rogerbos.shakecast.domain.model.Chapter
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

class ChaptersFetcher @Inject constructor(
    private val client: OkHttpClient
) {
    fun fetch(url: String): Result<List<Chapter>> = runCatching {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code} for $url")
            ChaptersParser.parse(response.body?.string().orEmpty())
        }
    }
}
