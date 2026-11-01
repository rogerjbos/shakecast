package com.rogerbos.shakecast.data.remote.skiplist

/**
 * Reproduces podcast3r/transcribe.py safe_filename("<podcast>_<episode>") so
 * the phone can fetch "<key>_skiplist.json" from the Mac without renaming.
 */
object SkipListKey {
    private const val MAX_LEN = 80
    private val disallowed = Regex("[^\\p{L}\\p{N}_\\s-]")
    private val whitespace = Regex("\\s+")

    fun forEpisode(podcastTitle: String, episodeTitle: String): String =
        safeFilename("${podcastTitle}_$episodeTitle")

    fun safeFilename(s: String): String {
        val stripped = disallowed.replace(s, "")
        val underscored = whitespace.replace(stripped.trim(), "_")
        return underscored.take(MAX_LEN)
    }

    fun fileName(podcastTitle: String, episodeTitle: String): String =
        forEpisode(podcastTitle, episodeTitle) + "_skiplist.json"
}
