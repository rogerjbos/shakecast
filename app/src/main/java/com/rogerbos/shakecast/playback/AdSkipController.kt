package com.rogerbos.shakecast.playback

import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import com.rogerbos.shakecast.domain.model.AdBlock
import timber.log.Timber

/**
 * Polls the playback position and jumps over known ad blocks. Only boundaries
 * crossed by natural playback trigger a jump; a user seek into an ad block is
 * respected.
 */
class AdSkipController(
    private val player: Player,
    private val onSkipped: (AdBlock) -> Unit,
    private val handler: Handler = Handler(Looper.getMainLooper()),
    private val tickMs: Long = 500,
    private val minRemainingMs: Long = 3_000
) : Player.Listener {

    @Volatile var enabled: Boolean = true
        set(value) { field = value; sync() }

    private var blocksMs: List<Pair<Long, Long>> = emptyList()
    private var currentMediaId: String? = null
    private var lastPositionMs: Long = 0
    private var ticking = false

    private val tick = object : Runnable {
        override fun run() {
            if (!ticking) return
            check()
            handler.postDelayed(this, tickMs)
        }
    }

    fun attach() {
        player.addListener(this)
        sync()
    }

    fun detach() {
        player.removeListener(this)
        stopTicking()
    }

    /** Called with the blocks for the item that is currently loaded. */
    fun setBlocks(mediaId: String?, blocks: List<AdBlock>) {
        val next = blocks.map { (it.start * 1000).toLong() to (it.end * 1000).toLong() }.sortedBy { it.first }
        if (mediaId == currentMediaId && next == blocksMs) return
        Timber.d("AdSkip setBlocks mediaId=%s blocks=%d", mediaId, blocks.size)
        currentMediaId = mediaId
        blocksMs = next
        lastPositionMs = player.currentPosition
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) = sync()

    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        lastPositionMs = newPosition.positionMs
    }

    private fun sync() {
        if (enabled && player.isPlaying) startTicking() else stopTicking()
    }

    private fun startTicking() {
        if (ticking) return
        Timber.d("AdSkip ticking start (blocks=%d)", blocksMs.size)
        ticking = true
        lastPositionMs = player.currentPosition
        handler.postDelayed(tick, tickMs)
    }

    private fun stopTicking() {
        if (ticking) Timber.d("AdSkip ticking stop")
        ticking = false
        handler.removeCallbacks(tick)
    }

    internal fun check() {
        if (!enabled || blocksMs.isEmpty()) {
            lastPositionMs = player.currentPosition
            return
        }
        if (player.currentMediaItem?.mediaId != currentMediaId) return
        val prev = lastPositionMs
        val pos = player.currentPosition
        lastPositionMs = pos
        val crossed = blocksMs.firstOrNull { (start, end) -> prev < start && pos >= start && pos < end }
            ?: return
        val (start, end) = crossed
        Timber.d("AdSkip crossed block %d-%d at pos=%d prev=%d", start, end, pos, prev)
        if (end - pos < minRemainingMs) return
        player.seekTo(end)
        lastPositionMs = end
        onSkipped(AdBlock(start / 1000.0, end / 1000.0, ""))
    }
}
