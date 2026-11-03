package com.rogerbos.shakecast.playback

import com.rogerbos.shakecast.domain.model.Chapter

/** Pure chapter arithmetic shared by the service and tests. */
object ChapterNavigator {
    /** Start of the first chapter that begins after the current position, or null at the last chapter. */
    fun nextStartMs(chapters: List<Chapter>, positionMs: Long, minAheadMs: Long = 1_000): Long? =
        chapters.map { (it.start * 1000).toLong() }.sorted().firstOrNull { it > positionMs + minAheadMs }

    /**
     * Start of the current chapter, or of the previous one when the current
     * chapter only just began (like a track-back button).
     */
    fun previousStartMs(chapters: List<Chapter>, positionMs: Long, restartWithinMs: Long = 3_000): Long {
        val starts = chapters.map { (it.start * 1000).toLong() }.sorted()
        val current = starts.lastOrNull { it <= positionMs } ?: return 0L
        return if (positionMs - current > restartWithinMs) current else starts.lastOrNull { it < current } ?: 0L
    }

    fun current(chapters: List<Chapter>, positionMs: Long): Chapter? =
        chapters.sortedBy { it.start }.lastOrNull { (it.start * 1000).toLong() <= positionMs }
}
