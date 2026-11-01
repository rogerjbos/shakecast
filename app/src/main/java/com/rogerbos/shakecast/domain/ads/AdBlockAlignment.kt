package com.rogerbos.shakecast.domain.ads

import kotlin.math.abs

/**
 * Skip-lists and transcripts describe one rendition of an episode. If the
 * local file's duration differs, dynamically inserted ads shifted the
 * timestamps and the blocks must not be trusted.
 */
object AdBlockAlignment {
    /** Skip-lists come from Whisper over the whole file, so their total matches the file closely. */
    const val SKIPLIST_TOLERANCE_SEC = 2.0

    /** Publisher transcripts end at the last spoken word, so trailing music leaves a gap. */
    const val TRANSCRIPT_TRAILING_TOLERANCE_SEC = 45.0
    const val TRANSCRIPT_OVERRUN_TOLERANCE_SEC = 2.0

    fun isAligned(expectedDuration: Double, actualDuration: Double, toleranceSec: Double = SKIPLIST_TOLERANCE_SEC): Boolean =
        expectedDuration > 0 && actualDuration > 0 && abs(expectedDuration - actualDuration) <= toleranceSec

    /**
     * A transcript is trusted when it does not run past the audio and ends no
     * more than [TRANSCRIPT_TRAILING_TOLERANCE_SEC] before it. A dynamically
     * inserted pre-roll longer than that pushes the file beyond the window.
     */
    fun isTranscriptAligned(transcriptEnd: Double, actualDuration: Double): Boolean {
        if (transcriptEnd <= 0 || actualDuration <= 0) return false
        val gap = actualDuration - transcriptEnd
        return gap >= -TRANSCRIPT_OVERRUN_TOLERANCE_SEC && gap <= TRANSCRIPT_TRAILING_TOLERANCE_SEC
    }
}
