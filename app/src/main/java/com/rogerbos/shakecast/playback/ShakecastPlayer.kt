package com.rogerbos.shakecast.playback

import android.media.AudioManager
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import androidx.media3.common.ForwardingSimpleBasePlayer

/**
 * Wraps ExoPlayer to apply tunable seek increments, hide next/previous track
 * commands, and optionally present itself as a remote-volume device so that
 * hardware volume keys reach [VolumeChordDetector] while the screen is off.
 */
@UnstableApi
class ShakecastPlayer(
    private val exoPlayer: ExoPlayer,
    private val audioManager: AudioManager,
    private val chord: VolumeChordDetector
) : ForwardingSimpleBasePlayer(exoPlayer) {

    @Volatile var skipForwardMs: Long = 30_000
        private set
    @Volatile var skipBackMs: Long = 15_000
        private set
    @Volatile var volumeChordEnabled: Boolean = false
        private set

    fun applyIncrements(forwardSec: Int, backSec: Int) {
        skipForwardMs = forwardSec * 1000L
        skipBackMs = backSec * 1000L
        invalidateState()
    }

    fun setVolumeChord(enabled: Boolean) {
        if (volumeChordEnabled == enabled) return
        volumeChordEnabled = enabled
        chord.reset()
        invalidateState()
    }

    override fun getState(): State {
        val base = super.getState()
        val commands = base.availableCommands.buildUpon()
            .removeAll(
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
            )
        val builder = base.buildUpon()
            .setSeekForwardIncrementMs(skipForwardMs)
            .setSeekBackIncrementMs(skipBackMs)
        if (volumeChordEnabled) {
            commands.addAll(
                Player.COMMAND_GET_DEVICE_VOLUME,
                Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS,
                Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS
            )
            builder
                .setDeviceInfo(
                    DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE)
                        .setMinVolume(0)
                        .setMaxVolume(audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
                        .build()
                )
                .setDeviceVolume(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
        }
        return builder.setAvailableCommands(commands.build()).build()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        return when (seekCommand) {
            Player.COMMAND_SEEK_FORWARD, Player.COMMAND_SEEK_BACK -> {
                val clamped = if (positionMs == C.TIME_UNSET) positionMs else positionMs.coerceAtLeast(0)
                exoPlayer.seekTo(mediaItemIndex, clamped)
                Futures.immediateVoidFuture()
            }
            else -> super.handleSeek(mediaItemIndex, positionMs, seekCommand)
        }
    }

    override fun handleIncreaseDeviceVolume(flags: Int): ListenableFuture<*> {
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
        chord.onPress(VolumeChordDetector.Key.UP, System.currentTimeMillis())
        return Futures.immediateVoidFuture()
    }

    override fun handleDecreaseDeviceVolume(flags: Int): ListenableFuture<*> {
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
        chord.onPress(VolumeChordDetector.Key.DOWN, System.currentTimeMillis())
        return Futures.immediateVoidFuture()
    }

    override fun handleSetDeviceVolume(deviceVolume: Int, flags: Int): ListenableFuture<*> {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, deviceVolume, AudioManager.FLAG_SHOW_UI)
        return Futures.immediateVoidFuture()
    }

    override fun handleSetDeviceMuted(muted: Boolean, flags: Int): ListenableFuture<*> {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (muted) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE,
            AudioManager.FLAG_SHOW_UI
        )
        return Futures.immediateVoidFuture()
    }

    companion object {
        fun hasSession(session: MediaSession?): Boolean = session != null
    }
}
