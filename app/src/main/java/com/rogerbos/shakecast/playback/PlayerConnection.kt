package com.rogerbos.shakecast.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.rogerbos.shakecast.domain.model.Episode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class PlayerSnapshot(
    val connected: Boolean,
    val episodeId: Long?,
    val title: String,
    val podcastTitle: String,
    val artworkUri: String?,
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val speed: Float,
    val sleepEndAtMs: Long?,
    val sleepEndOfEpisode: Boolean
) {
    companion object {
        val EMPTY = PlayerSnapshot(false, null, "", "", null, false, false, 0, 0, 1f, null, false)
    }
}

/** App-side MediaController holder shared by all screens. */
@Singleton
class PlayerConnection @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaItemFactory: MediaItemFactory
) {
    private val handler = Handler(Looper.getMainLooper())
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var pending: ((MediaController) -> Unit)? = null

    private val _state = MutableStateFlow(PlayerSnapshot.EMPTY)
    val state: StateFlow<PlayerSnapshot> = _state

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    private val controllerListener = object : MediaController.Listener {
        override fun onExtrasChanged(controller: MediaController, extras: Bundle) = refresh()
    }

    private val poll = object : Runnable {
        override fun run() {
            refresh()
            if (controller?.isPlaying == true) handler.postDelayed(this, 500)
        }
    }

    fun connect() {
        if (controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).setListener(controllerListener).buildAsync()
        controllerFuture = future
        future.addListener({
            runCatching { future.get() }
                .onSuccess { onConnected(it) }
                .onFailure { Timber.w(it, "MediaController connection failed"); controllerFuture = null }
        }, MoreExecutors.directExecutor())
    }

    fun release() {
        handler.removeCallbacks(poll)
        controller?.removeListener(listener)
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        _state.update { it.copy(connected = false) }
    }

    private fun onConnected(c: MediaController) {
        controller = c
        c.addListener(listener)
        refresh()
        pending?.invoke(c)
        pending = null
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else { pending = block; connect() }
    }

    fun play(episode: Episode) = withController { c ->
        if (c.currentMediaItem?.mediaId == episode.id.toString()) {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
            return@withController
        }
        val item: MediaItem = mediaItemFactory.create(episode)
        c.setMediaItem(item, episode.positionMs)
        c.prepare()
        c.play()
    }

    fun togglePlayPause() = withController { c ->
        if (c.isPlaying) c.pause() else { if (c.playbackState == Player.STATE_IDLE) c.prepare(); c.play() }
    }

    fun skipForward() = withController { it.seekForward() }

    fun skipBack() = withController { it.seekBack() }

    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs) }

    fun setSpeed(speed: Float) = withController { it.setPlaybackSpeed(speed) }

    /** Minutes until pause, 0 to cancel, or [PlaybackCommands.SLEEP_END_OF_EPISODE]. */
    fun setSleepTimer(minutes: Int) = withController { c ->
        val args = Bundle().apply { putInt(PlaybackCommands.ARG_SLEEP_MINUTES, minutes) }
        c.sendCustomCommand(PlaybackCommands.setSleepTimer, args)
    }

    private fun refresh() {
        val c = controller ?: run { _state.value = PlayerSnapshot.EMPTY; return }
        val item = c.currentMediaItem
        _state.value = PlayerSnapshot(
            connected = true,
            episodeId = mediaItemFactory.episodeId(item?.mediaId),
            title = item?.mediaMetadata?.title?.toString().orEmpty(),
            podcastTitle = item?.mediaMetadata?.artist?.toString().orEmpty(),
            artworkUri = item?.mediaMetadata?.artworkUri?.toString(),
            isPlaying = c.isPlaying,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0) ?: 0,
            speed = c.playbackParameters.speed,
            sleepEndAtMs = c.sessionExtras.getLong(PlaybackCommands.EXTRA_SLEEP_END_AT_MS, 0L).takeIf { it > 0 },
            sleepEndOfEpisode = c.sessionExtras.getBoolean(PlaybackCommands.EXTRA_SLEEP_END_OF_EPISODE, false)
        )
        if (c.isPlaying) {
            handler.removeCallbacks(poll)
            handler.postDelayed(poll, 500)
        }
    }
}
