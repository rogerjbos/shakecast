package com.rogerbos.shakecast.playback

import com.rogerbos.shakecast.data.settings.ShakeSensitivity
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class ShakeDetectorTest {

    private fun configFor(s: ShakeSensitivity) = ShakeConfig(s.thresholdG, s.peaksRequired, s.windowMs)

    private fun detector(s: ShakeSensitivity, onShake: () -> Unit) = ShakeDetector(configFor(s), onShake)

    /** Feeds |a| = 1g + amplitude*sin(2πft) along one axis at 50 Hz for [seconds]. */
    private fun feedOscillation(d: ShakeDetector, frequencyHz: Double, amplitudeG: Double, seconds: Double, startMs: Long = 1_000) {
        val samples = (seconds * 50).toInt()
        for (i in 0 until samples) {
            val tMs = startMs + i * 20L
            val tSec = i * 0.02
            val magnitudeG = 1.0 + amplitudeG * sin(2 * PI * frequencyHz * tSec)
            val y = (magnitudeG * ShakeDetector.GRAVITY).toFloat()
            d.onSample(tMs * 1_000_000L, 0f, y, 0f)
        }
    }

    /** One second of the phone lying still, so the settle window has passed. */
    private fun feedRest(d: ShakeDetector, startMs: Long = 0) {
        for (i in 0 until 50) d.onSample((startMs + i * 20L) * 1_000_000L, 0f, ShakeDetector.GRAVITY, 0f)
    }

    @Test
    fun violentShakeTriggersOnce() {
        var shakes = 0
        val d = detector(ShakeSensitivity.MEDIUM) { shakes++ }
        feedRest(d)
        feedOscillation(d, frequencyHz = 5.0, amplitudeG = 3.0, seconds = 0.8)
        assertEquals(1, shakes)
    }

    @Test
    fun secondShakeAfterCooldownTriggersAgain() {
        var shakes = 0
        val d = detector(ShakeSensitivity.MEDIUM) { shakes++ }
        feedRest(d)
        feedOscillation(d, 5.0, 3.0, 0.8, startMs = 1_000)
        feedOscillation(d, 5.0, 3.0, 0.8, startMs = 4_000)
        assertEquals(2, shakes)
    }

    @Test
    fun walkingNeverTriggers() {
        var shakes = 0
        val d = detector(ShakeSensitivity.HIGH) { shakes++ }
        feedRest(d)
        feedOscillation(d, frequencyHz = 2.0, amplitudeG = 0.5, seconds = 10.0)
        assertEquals(0, shakes)
    }

    @Test
    fun runningDoesNotTriggerMediumButMayTriggerHigh() {
        var medium = 0
        val dm = detector(ShakeSensitivity.MEDIUM) { medium++ }
        feedRest(dm)
        feedOscillation(dm, frequencyHz = 3.0, amplitudeG = 1.5, seconds = 10.0)
        assertEquals(0, medium)

        var high = 0
        val dh = detector(ShakeSensitivity.HIGH) { high++ }
        feedRest(dh)
        feedOscillation(dh, frequencyHz = 3.0, amplitudeG = 1.5, seconds = 10.0)
        assertEquals(true, high >= 1)
    }

    @Test
    fun ignoresSamplesDuringSettleWindow() {
        var shakes = 0
        val d = detector(ShakeSensitivity.MEDIUM) { shakes++ }
        feedOscillation(d, 5.0, 3.0, 0.4, startMs = 0)
        assertEquals(0, shakes)
    }
}
