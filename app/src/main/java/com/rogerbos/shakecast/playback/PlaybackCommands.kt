package com.rogerbos.shakecast.playback

import android.os.Bundle
import androidx.media3.session.SessionCommand

object PlaybackCommands {
    const val ACTION_SKIP_FORWARD = "com.rogerbos.shakecast.SKIP_FORWARD"
    const val ACTION_SKIP_BACK = "com.rogerbos.shakecast.SKIP_BACK"
    const val ACTION_TOGGLE_AUTO_SKIP = "com.rogerbos.shakecast.TOGGLE_AUTO_SKIP"
    const val ACTION_SET_SLEEP_TIMER = "com.rogerbos.shakecast.SET_SLEEP_TIMER"

    /** Minutes until pause; 0 cancels; [SLEEP_END_OF_EPISODE] pauses when the episode ends. */
    const val ARG_SLEEP_MINUTES = "minutes"
    const val SLEEP_END_OF_EPISODE = -1

    /** Session extras keys the service publishes for controllers. */
    const val EXTRA_SLEEP_END_AT_MS = "sleepEndAtMs"
    const val EXTRA_SLEEP_END_OF_EPISODE = "sleepEndOfEpisode"

    val skipForward = SessionCommand(ACTION_SKIP_FORWARD, Bundle.EMPTY)
    val skipBack = SessionCommand(ACTION_SKIP_BACK, Bundle.EMPTY)
    val toggleAutoSkip = SessionCommand(ACTION_TOGGLE_AUTO_SKIP, Bundle.EMPTY)
    val setSleepTimer = SessionCommand(ACTION_SET_SLEEP_TIMER, Bundle.EMPTY)

    val all = listOf(skipForward, skipBack, toggleAutoSkip, setSleepTimer)
}
