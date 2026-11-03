package com.rogerbos.shakecast.data.settings

/** Thresholds are linear acceleration in g, with gravity already removed. */
enum class ShakeSensitivity(val thresholdG: Float, val peaksRequired: Int, val windowMs: Long) {
    LOW(thresholdG = 2.2f, peaksRequired = 3, windowMs = 800),
    MEDIUM(thresholdG = 1.5f, peaksRequired = 2, windowMs = 700),
    HIGH(thresholdG = 1.0f, peaksRequired = 2, windowMs = 700)
}

data class Settings(
    val skipForwardSec: Int,
    val skipBackSec: Int,
    val shakeEnabled: Boolean,
    val shakeSensitivity: ShakeSensitivity,
    val volumeChordEnabled: Boolean,
    val autoSkipAdsEnabled: Boolean,
    val hapticsEnabled: Boolean,
    val playbackSpeed: Float,
    val downloadWifiOnly: Boolean,
    val episodesToKeepPerShow: Int,
    val autoDeleteWhenPlayed: Boolean,
    val refreshIntervalHours: Int,
    val syncEnabled: Boolean,
    val supabaseUrl: String,
    val supabaseKey: String,
    val syncKey: String,
    val autoPlayNext: Boolean,
    val rememberManualSkips: Boolean
) {
    companion object {
        val DEFAULT = Settings(
            skipForwardSec = 30,
            skipBackSec = 15,
            shakeEnabled = true,
            shakeSensitivity = ShakeSensitivity.MEDIUM,
            volumeChordEnabled = false,
            autoSkipAdsEnabled = true,
            hapticsEnabled = true,
            playbackSpeed = 1.0f,
            downloadWifiOnly = true,
            episodesToKeepPerShow = 3,
            autoDeleteWhenPlayed = true,
            refreshIntervalHours = 3,
            syncEnabled = false,
            supabaseUrl = "",
            supabaseKey = "",
            syncKey = "",
            autoPlayNext = true,
            rememberManualSkips = true
        )
    }
}
