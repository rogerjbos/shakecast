package com.rogerbos.shakecast.playback

import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player

/** Saves the playback position every few seconds and marks episodes played at the end. */
class PositionPersister(
    private val player: Player,
    private val mediaItemFactory: MediaItemFactory,
    private val onPosition: (episodeId: Long, positionMs: Long) -> Unit,
    private val onEnded: (episodeId: Long) -> Unit,
    private val handler: Handler = Handler(Looper.getMainLooper()),
    private val intervalMs: Long = 5_000
) : Player.Listener {

    private var lastMediaId: String? = null
    private var running = false

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            save()
            handler.postDelayed(this, intervalMs)
        }
    }

    fun attach() {
        player.addListener(this)
        lastMediaId = player.currentMediaItem?.mediaId
    }

    fun detach() {
        save()
        player.removeListener(this)
        stop()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) start() else { stop(); save() }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        lastMediaId = mediaItem?.mediaId
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) {
            mediaItemFactory.episodeId(player.currentMediaItem?.mediaId)?.let(onEnded)
        }
    }

    fun save() {
        val id = mediaItemFactory.episodeId(player.currentMediaItem?.mediaId) ?: return
        if (player.playbackState == Player.STATE_ENDED) return
        val pos = player.currentPosition
        if (pos > 0) onPosition(id, pos)
    }

    private fun start() {
        if (running) return
        running = true
        handler.postDelayed(tick, intervalMs)
    }

    private fun stop() {
        running = false
        handler.removeCallbacks(tick)
    }
}
