package com.rogerbos.shakecast.playback

import android.os.Bundle
import androidx.media3.session.SessionCommand

object PlaybackCommands {
    const val ACTION_SKIP_FORWARD = "com.rogerbos.shakecast.SKIP_FORWARD"
    const val ACTION_SKIP_BACK = "com.rogerbos.shakecast.SKIP_BACK"
    const val ACTION_TOGGLE_AUTO_SKIP = "com.rogerbos.shakecast.TOGGLE_AUTO_SKIP"

    val skipForward = SessionCommand(ACTION_SKIP_FORWARD, Bundle.EMPTY)
    val skipBack = SessionCommand(ACTION_SKIP_BACK, Bundle.EMPTY)
    val toggleAutoSkip = SessionCommand(ACTION_TOGGLE_AUTO_SKIP, Bundle.EMPTY)

    val all = listOf(skipForward, skipBack, toggleAutoSkip)
}
