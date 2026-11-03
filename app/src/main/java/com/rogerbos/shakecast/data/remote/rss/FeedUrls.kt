package com.rogerbos.shakecast.data.remote.rss

/** Recognises text that is a feed address rather than a search term. */
object FeedUrls {
    private val bareHost = Regex("^[A-Za-z0-9.-]+\\.[A-Za-z]{2,}(:\\d+)?(/\\S*)?$")

    fun looksLikeUrl(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty() || t.any { it.isWhitespace() }) return false
        if (t.startsWith("http://", ignoreCase = true) || t.startsWith("https://", ignoreCase = true)) return t.length > 10
        return t.contains('/') && bareHost.matches(t)
    }

    /** Adds the https scheme when the user typed a bare host and path. */
    fun normalize(text: String): String {
        val t = text.trim()
        return if (t.startsWith("http://", ignoreCase = true) || t.startsWith("https://", ignoreCase = true)) t else "https://$t"
    }
}
