package com.rogerbos.shakecast.playback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Haptics @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Volatile
    var enabled: Boolean = true

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun heavyClick() {
        if (!enabled) return
        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
    }

    fun doubleTick() {
        if (!enabled) return
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 80, 40), -1))
    }
}
