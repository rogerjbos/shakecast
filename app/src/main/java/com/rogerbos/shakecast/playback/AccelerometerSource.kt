package com.rogerbos.shakecast.playback

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import timber.log.Timber

/** Thin adapter that feeds accelerometer samples into a [ShakeDetector]. */
class AccelerometerSource(
    context: Context,
    private val detector: ShakeDetector
) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var running = false

    val isAvailable: Boolean get() = accelerometer != null

    fun start() {
        if (running) return
        val sm = sensorManager ?: return
        val sensor = accelerometer ?: run { Timber.w("No accelerometer; shake-to-skip disabled"); return }
        detector.reset()
        running = sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME, 0)
    }

    fun stop() {
        if (!running) return
        sensorManager?.unregisterListener(this)
        running = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        detector.onSample(event.timestamp, event.values[0], event.values[1], event.values[2])
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
