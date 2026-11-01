package com.rogerbos.shakecast.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rogerbos.shakecast.data.refresh.RefreshScheduler
import com.rogerbos.shakecast.data.remote.skiplist.SkipListClient
import com.rogerbos.shakecast.data.settings.Settings
import com.rogerbos.shakecast.data.settings.SettingsRepository
import com.rogerbos.shakecast.data.settings.ShakeSensitivity
import com.rogerbos.shakecast.playback.AccelerometerSource
import com.rogerbos.shakecast.playback.ShakeConfig
import com.rogerbos.shakecast.playback.ShakeDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ShakeTestState(val active: Boolean, val magnitudeG: Float, val peaks: Int, val shakes: Int)

data class SettingsUiState(
    val settings: Settings,
    val shakeTest: ShakeTestState,
    val connectionResult: String?
) {
    companion object {
        val EMPTY = SettingsUiState(Settings.DEFAULT, ShakeTestState(false, 1f, 0, 0), null)
    }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val refreshScheduler: RefreshScheduler,
    private val skipListClient: SkipListClient
) : ViewModel() {

    private val shakeTest = MutableStateFlow(ShakeTestState(false, 1f, 0, 0))
    private val connectionResult = MutableStateFlow<String?>(null)
    private var detector: ShakeDetector? = null
    private var source: AccelerometerSource? = null
    private var pollJob: Job? = null

    val state: StateFlow<SettingsUiState> = combine(settingsRepository.settings, shakeTest, connectionResult) { s, t, c ->
        SettingsUiState(s, t, c)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState.EMPTY)

    fun update(transform: (Settings) -> Settings) {
        viewModelScope.launch {
            settingsRepository.update(transform)
            val next = settingsRepository.current()
            detector?.config = configFor(next.shakeSensitivity)
            refreshScheduler.schedulePeriodic(next.refreshIntervalHours)
        }
    }

    fun refreshNow() = refreshScheduler.enqueueNow()

    fun testConnection() {
        viewModelScope.launch {
            val base = settingsRepository.current().skipListBaseUrl
            connectionResult.value = withContext(Dispatchers.IO) {
                skipListClient.ping(base).fold(
                    onSuccess = { "HTTP $it" },
                    onFailure = { it.message ?: it.javaClass.simpleName }
                )
            }
        }
    }

    fun toggleShakeTest() {
        if (shakeTest.value.active) stopShakeTest() else startShakeTest()
    }

    private fun startShakeTest() {
        val sensitivity = state.value.settings.shakeSensitivity
        val d = ShakeDetector(configFor(sensitivity)) { shakeTest.update { it.copy(shakes = it.shakes + 1) } }
        val s = AccelerometerSource(context, d)
        detector = d
        source = s
        s.start()
        shakeTest.value = ShakeTestState(active = true, magnitudeG = 1f, peaks = 0, shakes = 0)
        pollJob = viewModelScope.launch {
            while (isActive) {
                shakeTest.update { it.copy(magnitudeG = d.lastMagnitudeG, peaks = d.recentPeaks) }
                delay(100)
            }
        }
    }

    private fun stopShakeTest() {
        pollJob?.cancel()
        source?.stop()
        source = null
        detector = null
        shakeTest.update { it.copy(active = false) }
    }

    private fun configFor(s: ShakeSensitivity) = ShakeConfig(s.thresholdG, s.peaksRequired, s.windowMs)

    override fun onCleared() {
        stopShakeTest()
    }
}
