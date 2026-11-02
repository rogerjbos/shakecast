package com.rogerbos.shakecast.data.settings

enum class ShakeSensitivity(val thresholdG: Float, val peaksRequired: Int, val windowMs: Long) {
    LOW(thresholdG = 3.2f, peaksRequired = 3, windowMs = 700),
    MEDIUM(thresholdG = 2.7f, peaksRequired = 3, windowMs = 700),
    HIGH(thresholdG = 2.2f, peaksRequired = 2, windowMs = 600)
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
    val skipListBaseUrl: String,
    val syncEnabled: Boolean,
    val supabaseUrl: String,
    val supabaseKey: String,
    val syncKey: String
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
            skipListBaseUrl = "",
            syncEnabled = false,
            supabaseUrl = "",
            supabaseKey = "",
            syncKey = ""
        )
    }
}
