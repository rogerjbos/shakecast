package com.rogerbos.shakecast.playback

/** A contiguous span the user skipped over by hand. */
data class SkipSpan(val episodeId: Long, val startMs: Long, val endMs: Long)

/**
 * Turns individual forward and back skips into spans worth remembering.
 * Consecutive forward skips within [mergeGapMs] extend one span; a back skip
 * shortly after trims it (the user overshot); spans shorter than [minSpanMs]
 * are dropped. Pure Kotlin so it is unit-testable; the caller supplies time.
 */
class ManualSkipRecorder(
    private val minSpanMs: Long = 15_000,
    private val mergeGapMs: Long = 10_000,
    private val continuityToleranceMs: Long = 3_000
) {
    private var pending: Pending? = null

    private class Pending(val episodeId: Long, val startMs: Long, var endMs: Long, var lastEventMs: Long)

    /** Returns a span that was finalized because this skip started a new one. */
    fun onSkipForward(episodeId: Long, fromMs: Long, toMs: Long, nowMs: Long): SkipSpan? {
        val current = pending
        if (current != null && current.episodeId == episodeId && nowMs - current.lastEventMs <= mergeGapMs &&
            kotlin.math.abs(fromMs - current.endMs) <= continuityToleranceMs
        ) {
            current.endMs = maxOf(current.endMs, toMs)
            current.lastEventMs = nowMs
            return null
        }
        val finalized = flush(nowMs, force = true)
        pending = Pending(episodeId, fromMs, toMs, nowMs)
        return finalized
    }

    fun onSkipBack(episodeId: Long, fromMs: Long, toMs: Long, nowMs: Long) {
        val current = pending ?: return
        if (current.episodeId != episodeId || nowMs - current.lastEventMs > mergeGapMs) return
        if (kotlin.math.abs(fromMs - current.endMs) > continuityToleranceMs) return
        current.endMs = toMs
        current.lastEventMs = nowMs
        if (current.endMs <= current.startMs) pending = null
    }

    /** Finalizes the pending span once it has been quiet for [mergeGapMs], or immediately when forced. */
    fun flush(nowMs: Long, force: Boolean = false): SkipSpan? {
        val current = pending ?: return null
        if (!force && nowMs - current.lastEventMs < mergeGapMs) return null
        pending = null
        if (current.endMs - current.startMs < minSpanMs) return null
        return SkipSpan(current.episodeId, current.startMs, current.endMs)
    }

    val hasPending: Boolean get() = pending != null
}
