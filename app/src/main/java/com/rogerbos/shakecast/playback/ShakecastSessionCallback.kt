package com.rogerbos.shakecast.playback

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.core.content.IntentCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ConnectionResult
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.rogerbos.shakecast.data.repository.EpisodeRepository
import com.rogerbos.shakecast.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.guava.future
import timber.log.Timber

@UnstableApi
class ShakecastSessionCallback(
    private val scope: CoroutineScope,
    private val episodeRepository: EpisodeRepository,
    private val settingsRepository: SettingsRepository,
    private val mediaItemFactory: MediaItemFactory,
    private val clickCounter: MediaButtonClickCounter,
    private val skipForward: (source: String) -> Unit,
    private val skipBack: (source: String) -> Unit,
    private val toggleAutoSkip: () -> Unit
) : MediaSession.Callback {

    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): ConnectionResult {
        val sessionCommands = ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
            .apply { PlaybackCommands.all.forEach { add(it) } }
            .build()
        return ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(sessionCommands)
            .build()
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        when (customCommand.customAction) {
            PlaybackCommands.ACTION_SKIP_FORWARD -> skipForward("custom-command")
            PlaybackCommands.ACTION_SKIP_BACK -> skipBack("custom-command")
            PlaybackCommands.ACTION_TOGGLE_AUTO_SKIP -> toggleAutoSkip()
            else -> return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        }
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    override fun onMediaButtonEvent(session: MediaSession, controllerInfo: MediaSession.ControllerInfo, intent: Intent): Boolean {
        val keyEvent = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java) ?: return false
        val isDown = keyEvent.action == KeyEvent.ACTION_DOWN && keyEvent.repeatCount == 0
        return when (keyEvent.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> {
                if (isDown) clickCounter.onClick()
                true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                if (isDown) skipForward("media-button:${keyEvent.keyCode}")
                true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (isDown) skipBack("media-button:${keyEvent.keyCode}")
                true
            }
            else -> false
        }
    }

    /** Controllers send items by mediaId only; rebuild the full item with its URI here. */
    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>
    ): ListenableFuture<List<MediaItem>> = scope.future {
        mediaItems.mapNotNull { item ->
            if (item.localConfiguration != null) return@mapNotNull item
            val id = mediaItemFactory.episodeId(item.mediaId) ?: return@mapNotNull null
            episodeRepository.get(id)?.let(mediaItemFactory::create)
        }
    }

    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo
    ): ListenableFuture<MediaItemsWithStartPosition> = scope.future {
        val episode = episodeRepository.lastPlayed()
            ?: throw UnsupportedOperationException("Nothing to resume")
        Timber.i("Resuming %s at %d ms", episode.title, episode.positionMs)
        MediaItemsWithStartPosition(listOf(mediaItemFactory.create(episode)), 0, episode.positionMs)
    }

    fun resolveClicks(player: Player, clicks: Int) {
        when (clicks) {
            1 -> Util.handlePlayPauseButtonAction(player)
            2 -> skipForward("headset-double")
            else -> skipBack("headset-triple")
        }
    }
}
