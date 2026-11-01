package com.rogerbos.shakecast.playback

import android.os.Handler
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.rogerbos.shakecast.domain.model.AdBlock
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class AdSkipControllerTest {

    private val player: Player = mockk(relaxed = true)
    private val handler: Handler = mockk(relaxed = true)
    private val skipped = mutableListOf<AdBlock>()

    private fun controller(): AdSkipController {
        val item = MediaItem.Builder().setMediaId("42").build()
        every { player.currentMediaItem } returns item
        every { player.isPlaying } returns true
        return AdSkipController(player, onSkipped = { skipped += it }, handler = handler).also {
            it.setBlocks("42", listOf(AdBlock(100.0, 160.0, "ad")))
        }
    }

    @Test
    fun crossingBlockStartJumpsToEnd() {
        every { player.currentPosition } returns 99_000L
        val c = controller()
        c.check()
        every { player.currentPosition } returns 100_300L
        c.check()
        verify { player.seekTo(160_000L) }
        assertEquals(1, skipped.size)
    }

    @Test
    fun userSeekIntoBlockIsRespected() {
        every { player.currentPosition } returns 10_000L
        val c = controller()
        c.check()
        c.onPositionDiscontinuity(
            Player.PositionInfo(null, 0, null, null, 0, 10_000L, 10_000L, -1, -1),
            Player.PositionInfo(null, 0, null, null, 0, 120_000L, 120_000L, -1, -1),
            Player.DISCONTINUITY_REASON_SEEK
        )
        every { player.currentPosition } returns 120_400L
        c.check()
        verify(exactly = 0) { player.seekTo(any<Long>()) }
    }

    @Test
    fun nearlyFinishedBlockIsNotSkipped() {
        every { player.currentPosition } returns 99_000L
        val c = controller()
        c.check()
        every { player.currentPosition } returns 158_000L
        c.check()
        verify(exactly = 0) { player.seekTo(any<Long>()) }
    }

    @Test
    fun disabledControllerDoesNothing() {
        every { player.currentPosition } returns 99_000L
        val c = controller()
        c.enabled = false
        c.check()
        every { player.currentPosition } returns 100_300L
        c.check()
        verify(exactly = 0) { player.seekTo(any<Long>()) }
    }
}
