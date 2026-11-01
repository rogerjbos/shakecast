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
 * fires [onShake] when [ShakeConfig.peaksRequired] rising edges above
 * [ShakeConfig.thresholdG] happen inside [ShakeConfig.windowMs]. Walking and
 * running produce peaks that are too weak or too slow to satisfy the default
 * presets. No Android dependencies, so it is unit-testable.
 */
class ShakeDetector(
    @Volatile var config: ShakeConfig,
    private val onShake: () -> Unit
) {
    private val peakTimesMs = ArrayDeque<Long>()
    private var prevG = 1f
    private var lastPeakMs = -1L
    private var cooldownUntilMs = -1L
    private var firstSampleMs = -1L

    /** Live peak count inside the window, for the Settings "test shake" row. */
    @Volatile
    var recentPeaks: Int = 0
        private set

    @Volatile
    var lastMagnitudeG: Float = 1f
        private set

    fun reset() {
        peakTimesMs.clear()
        prevG = 1f
        lastPeakMs = -1L
        cooldownUntilMs = -1L
        firstSampleMs = -1L
        recentPeaks = 0
    }

    fun onSample(timestampNs: Long, x: Float, y: Float, z: Float) {
        val nowMs = timestampNs / 1_000_000L
        val cfg = config
        if (firstSampleMs < 0) firstSampleMs = nowMs
        val g = sqrt(x * x + y * y + z * z) / GRAVITY
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
    }
}
