package com.rogerbos.shakecast.playback

/**
 * Detects the volume-key chord: UP then DOWN inside [windowMs] skips forward,
 * DOWN then UP skips back. The two presses cancel each other so the stream
 * volume ends where it started.
 */
class VolumeChordDetector(
    private val windowMs: Long = 500,
    private val onSkipForward: () -> Unit,
    private val onSkipBack: () -> Unit
) {
    enum class Key { UP, DOWN }

    private var lastKey: Key? = null
    private var lastAtMs = Long.MIN_VALUE

    /** Returns true when the press completed a chord. */
    fun onPress(key: Key, nowMs: Long): Boolean {
        val previous = lastKey
        val completed = previous != null && previous != key && nowMs - lastAtMs <= windowMs
        if (completed) {
            lastKey = null
            lastAtMs = Long.MIN_VALUE
            if (previous == Key.UP) onSkipForward() else onSkipBack()
            return true
        }
        lastKey = key
        lastAtMs = nowMs
        return false
    }

    fun reset() {
        lastKey = null
        lastAtMs = Long.MIN_VALUE
    }
}
