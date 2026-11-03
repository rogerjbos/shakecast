package com.rogerbos.shakecast.playback

import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import timber.log.Timber

/** Pauses playback after a delay or at the end of the episode, fading out first. */
class SleepTimer(
    private val player: Player,
    private val onChanged: (endAtMs: Long?, endOfEpisode: Boolean) -> Unit,
    private val handler: Handler = Handler(Looper.getMainLooper()),
    private val fadeMs: Long = 5_000
) {
    var endAtMs: Long? = null
        private set
    var endOfEpisode: Boolean = false
        private set

    private val expire = Runnable { fadeThenPause() }
    private var fading = false

    fun setMinutes(minutes: Int) {
        cancel(notify = false)
        endAtMs = System.currentTimeMillis() + minutes * 60_000L
        handler.postDelayed(expire, maxOf(0L, minutes * 60_000L - fadeMs))
        Timber.i("Sleep timer set for %d min", minutes)
        onChanged(endAtMs, false)
    }

    fun setEndOfEpisode() {
        cancel(notify = false)
        endOfEpisode = true
        Timber.i("Sleep timer set to end of episode")
        onChanged(null, true)
    }

    fun cancel(notify: Boolean = true) {
        handler.removeCallbacks(expire)
        endAtMs = null
        endOfEpisode = false
        if (fading) { fading = false; player.volume = 1f }
        if (notify) onChanged(null, false)
    }

    /** Called when the current episode finishes; returns true when playback should stop here. */
    fun consumeEndOfEpisode(): Boolean {
        if (!endOfEpisode) return false
        cancel()
        return true
    }

    private fun fadeThenPause() {
        fading = true
        val steps = 10
        val stepMs = fadeMs / steps
        var step = 0
        val fade = object : Runnable {
            override fun run() {
                if (!fading) return
                step++
                if (step < steps) {
                    player.volume = 1f - step.toFloat() / steps
                    handler.postDelayed(this, stepMs)
                } else {
                    player.pause()
                    Timber.i("Sleep timer paused playback")
                    cancel()
                }
            }
        }
        handler.post(fade)
    }
}
