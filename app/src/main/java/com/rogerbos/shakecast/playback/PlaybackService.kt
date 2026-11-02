package com.rogerbos.shakecast.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.rogerbos.shakecast.MainActivity
import com.rogerbos.shakecast.R
import com.rogerbos.shakecast.data.repository.EpisodeRepository
import com.rogerbos.shakecast.data.repository.PodcastRepository
import com.rogerbos.shakecast.data.settings.Settings
import com.rogerbos.shakecast.data.settings.SettingsRepository
import com.rogerbos.shakecast.data.sync.SyncEngine
import com.rogerbos.shakecast.data.sync.SyncScheduler
import com.rogerbos.shakecast.domain.model.AdSource
import com.rogerbos.shakecast.domain.usecase.MarkEpisodePlayed
import com.rogerbos.shakecast.domain.usecase.ResolveAdBlocks
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.abs

@UnstableApi
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var episodeRepository: EpisodeRepository
    @Inject lateinit var podcastRepository: PodcastRepository
    @Inject lateinit var markEpisodePlayed: MarkEpisodePlayed
    @Inject lateinit var resolveAdBlocks: ResolveAdBlocks
    @Inject lateinit var mediaItemFactory: MediaItemFactory
    @Inject lateinit var haptics: Haptics
    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var syncEngine: SyncEngine

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var player: ShakecastPlayer
    private var mediaSession: MediaSession? = null
    private lateinit var shakeDetector: ShakeDetector
    private lateinit var accelerometer: AccelerometerSource
    private lateinit var adSkip: AdSkipController
    private lateinit var positionPersister: PositionPersister
    private lateinit var callback: ShakecastSessionCallback
    private var settings: Settings = Settings.DEFAULT
    private var blocksJob: Job? = null
    private var podcastAutoSkip = true

    override fun onCreate() {
        super.onCreate()
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekForwardIncrementMs(30_000)
            .setSeekBackIncrementMs(15_000)
            .build()

        val chord = VolumeChordDetector(
            onSkipForward = { skipForward("volume-chord") },
            onSkipBack = { skipBack("volume-chord") }
        )
        player = ShakecastPlayer(exoPlayer, audioManager, chord)

        shakeDetector = ShakeDetector(configFor(settings)) { handler.post { skipForward("shake") } }
        accelerometer = AccelerometerSource(this, shakeDetector)

        val clickCounter = MediaButtonClickCounter(
            schedule = { delay, action ->
                val runnable = Runnable { action() }
                handler.postDelayed(runnable, delay)
                MediaButtonClickCounter.Cancellable { handler.removeCallbacks(runnable) }
            },
            onResolved = { clicks -> callback.resolveClicks(player, clicks) }
        )

        callback = ShakecastSessionCallback(
            scope = scope,
            episodeRepository = episodeRepository,
            settingsRepository = settingsRepository,
            mediaItemFactory = mediaItemFactory,
            clickCounter = clickCounter,
            skipForward = ::skipForward,
            skipBack = ::skipBack,
            toggleAutoSkip = { scope.launch { settingsRepository.update { it.copy(autoSkipAdsEnabled = !it.autoSkipAdsEnabled) } } }
        )

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_NOW_PLAYING
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(callback)
            .setSessionActivity(sessionActivity)
            .setMediaButtonPreferences(buttonPreferences(settings))
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelName(R.string.notification_channel_playback)
                .build()
                .apply { setSmallIcon(R.drawable.ic_notification) }
        )

        adSkip = AdSkipController(player, onSkipped = { block ->
            haptics.doubleTick()
            Toast.makeText(this, getString(R.string.ad_skipped_toast, ((block.end - block.start)).toInt()), Toast.LENGTH_SHORT).show()
            Timber.i("Auto-skipped ad %.1f-%.1f", block.start, block.end)
        })
        adSkip.attach()

        positionPersister = PositionPersister(
            player = player,
            mediaItemFactory = mediaItemFactory,
            onPosition = { id, pos -> scope.launch { episodeRepository.updatePosition(id, pos) } },
            onEnded = { id -> scope.launch { markEpisodePlayed(id) } }
        )
        positionPersister.attach()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying && settings.shakeEnabled) accelerometer.start() else accelerometer.stop()
                if (!isPlaying) syncScheduler.enqueueSoon()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                loadBlocksFor(mediaItem?.mediaId)
                syncScheduler.enqueueSoon()
            }
        })

        scope.launch {
            syncEngine.remotePositions.collect { remote ->
                val currentId = mediaItemFactory.episodeId(player.currentMediaItem?.mediaId)
                if (currentId != remote.episodeId || player.isPlaying) return@collect
                if (abs(player.currentPosition - remote.positionMs) > REMOTE_SEEK_TOLERANCE_MS) {
                    Timber.i("Moving to position from other device: %d ms", remote.positionMs)
                    player.seekTo(remote.positionMs)
                }
            }
        }

        scope.launch {
            settingsRepository.settings.collectLatest { applySettings(it) }
        }
    }

    private fun applySettings(next: Settings) {
        val previous = settings
        settings = next
        haptics.enabled = next.hapticsEnabled
        shakeDetector.config = configFor(next)
        player.applyIncrements(next.skipForwardSec, next.skipBackSec)
        player.setVolumeChord(next.volumeChordEnabled)
        adSkip.enabled = next.autoSkipAdsEnabled && podcastAutoSkip
        if (next.shakeEnabled && player.isPlaying) accelerometer.start() else if (!next.shakeEnabled) accelerometer.stop()
        if (previous.skipForwardSec != next.skipForwardSec || previous.skipBackSec != next.skipBackSec) {
            mediaSession?.setMediaButtonPreferences(buttonPreferences(next))
        }
        if (player.playbackParameters.speed != next.playbackSpeed) player.setPlaybackSpeed(next.playbackSpeed)
    }

    private fun configFor(s: Settings) = ShakeConfig(
        thresholdG = s.shakeSensitivity.thresholdG,
        peaksRequired = s.shakeSensitivity.peaksRequired,
        windowMs = s.shakeSensitivity.windowMs
    )

    private fun buttonPreferences(s: Settings): List<CommandButton> {
        val backIcon = if (s.skipBackSec == 15) CommandButton.ICON_SKIP_BACK_15 else CommandButton.ICON_SKIP_BACK
        val forwardIcon = if (s.skipForwardSec == 30) CommandButton.ICON_SKIP_FORWARD_30 else CommandButton.ICON_SKIP_FORWARD
        val back = CommandButton.Builder(backIcon)
            .setPlayerCommand(Player.COMMAND_SEEK_BACK)
            .setSlots(CommandButton.SLOT_BACK)
            .setDisplayName(getString(R.string.skip_back_label, s.skipBackSec))
            .build()
        val forward = CommandButton.Builder(forwardIcon)
            .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
            .setSlots(CommandButton.SLOT_FORWARD)
            .setDisplayName(getString(R.string.skip_forward_label, s.skipForwardSec))
            .build()
        return listOf(back, forward)
    }

    private fun loadBlocksFor(mediaId: String?) {
        blocksJob?.cancel()
        val id = mediaItemFactory.episodeId(mediaId)
        if (id == null) {
            adSkip.setBlocks(null, emptyList())
            return
        }
        blocksJob = scope.launch {
            episodeRepository.observe(id)
                .distinctUntilChanged { a, b -> a?.adSource == b?.adSource && a?.adBlocks == b?.adBlocks && a?.podcastId == b?.podcastId }
                .collectLatest { episode ->
                if (episode == null) return@collectLatest
                val podcast = podcastRepository.get(episode.podcastId)
                podcastAutoSkip = podcast?.autoSkipAds ?: true
                adSkip.enabled = settings.autoSkipAdsEnabled && podcastAutoSkip
                val usable = episode.adSource == AdSource.SKIPLIST || episode.adSource == AdSource.TRANSCRIPT
                adSkip.setBlocks(mediaId, if (usable) episode.adBlocks else emptyList())
                if (episode.adBlocks.isEmpty() && (episode.adSource == AdSource.NONE || episode.adSource == AdSource.MISALIGNED)) {
                    val durationSec = player.duration.takeIf { it != C.TIME_UNSET }?.let { it / 1000.0 }
                    val force = episode.adSource == AdSource.MISALIGNED
                    launch(Dispatchers.IO) { resolveAdBlocks.resolve(id, force = force, actualDurationSecOverride = durationSec) }
                }
            }
        }
    }

    private fun skipForward(source: String) {
        haptics.heavyClick()
        player.seekForward()
        Timber.i("skip source=%s forward=%dms", source, player.skipForwardMs)
    }

    private fun skipBack(source: String) {
        haptics.heavyClick()
        player.seekBack()
        Timber.i("skip source=%s back=%dms", source, player.skipBackMs)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            pauseAllPlayersAndStopSelf()
        }
    }

    override fun onDestroy() {
        accelerometer.stop()
        positionPersister.detach()
        adSkip.detach()
        mediaSession?.release()
        mediaSession = null
        player.release()
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val REMOTE_SEEK_TOLERANCE_MS = 3_000L
    }
}
