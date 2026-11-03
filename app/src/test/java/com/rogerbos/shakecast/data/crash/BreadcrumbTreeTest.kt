package com.rogerbos.shakecast.data.crash

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class BreadcrumbTreeTest {
    private val tree = CrashReporting.BreadcrumbTree()

    @Test
    fun feedTitlesAndUrlsAreDropped() {
        assertEquals("Refresh failed (IOException)", tree.redact("Refresh failed for Planet Money", IOException("https://feeds.npr.org/x")))
        assertEquals("Transcript fetch failed", tree.redact("Transcript fetch failed for https://host/t.srt", null))
        assertEquals("Sign-out call failed", tree.redact("Sign-out call failed: HTTP 500: secret", null))
    }

    @Test
    fun appendedStackTraceIsDropped() {
        val timberStyle = "LoudnessEnhancer unavailable\njava.lang.RuntimeException: AudioEffect: bad session\n\tat android.media.audiofx.AudioEffect.<init>"
        assertEquals("LoudnessEnhancer unavailable (RuntimeException)", tree.redact(timberStyle, RuntimeException("AudioEffect: bad session")))
    }

    @Test
    fun plainMessagesPassThrough() {
        assertEquals("No accelerometer; shake-to-skip disabled", tree.redact("No accelerometer; shake-to-skip disabled", null))
    }
}
