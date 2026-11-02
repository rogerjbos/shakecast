package com.rogerbos.shakecast.playback

import kotlin.math.sqrt

data class ShakeConfig(
    val thresholdG: Float,
    val peaksRequired: Int,
    val windowMs: Long,
    val cooldownMs: Long = 1500,
    val minPeakSpacingMs: Long = 80,
    val settleMs: Long = 500
)

/**
 * Pure peak-counting shake detector. Feed it raw accelerometer samples; it
 * removes gravity with a low-pass filter and fires [onShake] when
 * [ShakeConfig.peaksRequired] rising edges of the remaining (linear)
 * acceleration cross [ShakeConfig.thresholdG] inside [ShakeConfig.windowMs].
 * Thresholds are therefore "g above rest": walking peaks near 0.5 g, running
 * near 1.5 g. No Android dependencies, so it is unit-testable.
 */
class ShakeDetector(
    @Volatile var config: ShakeConfig,
    private val onShake: () -> Unit
) {
    private val peakTimesMs = ArrayDeque<Long>()
    private var prevG = 0f
    private var lastPeakMs = -1L
    private var cooldownUntilMs = -1L
    private var firstSampleMs = -1L
    private var lastSampleMs = -1L
    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 0f

    /** Live peak count inside the window, for the Settings "test shake" row. */
    @Volatile
    var recentPeaks: Int = 0
        private set

    /** Most recent linear acceleration magnitude in g, with gravity removed. */
    @Volatile
    var lastMagnitudeG: Float = 0f
        private set

    fun reset() {
        peakTimesMs.clear()
        prevG = 0f
        lastPeakMs = -1L
        cooldownUntilMs = -1L
        firstSampleMs = -1L
        lastSampleMs = -1L
        recentPeaks = 0
        lastMagnitudeG = 0f
    }

    fun onSample(timestampNs: Long, x: Float, y: Float, z: Float) {
        val nowMs = timestampNs / 1_000_000L
        val cfg = config
        if (firstSampleMs < 0) {
            firstSampleMs = nowMs
            gravityX = x
            gravityY = y
            gravityZ = z
        }
        val dtSec = if (lastSampleMs < 0) 0.02f else ((nowMs - lastSampleMs).coerceIn(1, 200)) / 1000f
        lastSampleMs = nowMs
        val alpha = GRAVITY_TAU_SEC / (GRAVITY_TAU_SEC + dtSec)
        gravityX = alpha * gravityX + (1 - alpha) * x
        gravityY = alpha * gravityY + (1 - alpha) * y
        gravityZ = alpha * gravityZ + (1 - alpha) * z
        val lx = x - gravityX
        val ly = y - gravityY
        val lz = z - gravityZ
        val g = sqrt(lx * lx + ly * ly + lz * lz) / GRAVITY
        lastMagnitudeG = g
        val wasBelow = prevG < cfg.thresholdG
        prevG = g

        while (peakTimesMs.isNotEmpty() && nowMs - peakTimesMs.first() > cfg.windowMs) peakTimesMs.removeFirst()
        recentPeaks = peakTimesMs.size

        if (nowMs - firstSampleMs < cfg.settleMs) return
        if (nowMs < cooldownUntilMs) return

        val risingEdge = wasBelow && g >= cfg.thresholdG
        if (!risingEdge) return
        if (lastPeakMs >= 0 && nowMs - lastPeakMs < cfg.minPeakSpacingMs) return
        lastPeakMs = nowMs
        peakTimesMs.addLast(nowMs)
        recentPeaks = peakTimesMs.size

        if (peakTimesMs.size >= cfg.peaksRequired) {
            peakTimesMs.clear()
            recentPeaks = 0
            cooldownUntilMs = nowMs + cfg.cooldownMs
            onShake()
        }
    }

    companion object {
        const val GRAVITY = 9.80665f

        /** Low-pass time constant for the gravity estimate (cut-off near 0.8 Hz). */
        private const val GRAVITY_TAU_SEC = 0.2f
    }
}
