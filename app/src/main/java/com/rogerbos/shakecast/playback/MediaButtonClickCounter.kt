package com.rogerbos.shakecast.playback

/**
 * Counts headset button clicks inside a window and resolves them to one
 * action: 1 = play/pause, 2 = skip forward, 3+ = skip back. The scheduler is
 * injected so the class stays pure.
 */
class MediaButtonClickCounter(
    private val windowMs: Long = 450,
    private val schedule: (delayMs: Long, action: () -> Unit) -> Cancellable,
    private val onResolved: (clicks: Int) -> Unit
) {
    fun interface Cancellable {
        fun cancel()
    }

    private var clicks = 0
    private var pending: Cancellable? = null

    fun onClick() {
        clicks++
        pending?.cancel()
        pending = schedule(windowMs) { resolve() }
    }

    fun flush() {
        pending?.cancel()
        if (clicks > 0) resolve()
    }

    private fun resolve() {
        val count = clicks
        clicks = 0
        pending = null
        onResolved(count)
    }
}
