package com.rogerbos.shakecast.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rogerbos.shakecast.data.refresh.RefreshScheduler
import com.rogerbos.shakecast.data.remote.sync.SupabaseConfig
import com.rogerbos.shakecast.data.remote.sync.SupabaseSyncApi
import com.rogerbos.shakecast.data.settings.Settings
import com.rogerbos.shakecast.data.settings.SettingsRepository
import com.rogerbos.shakecast.data.settings.ShakeSensitivity
import com.rogerbos.shakecast.data.sync.SyncScheduler
import com.rogerbos.shakecast.domain.usecase.ExportOpml
import com.rogerbos.shakecast.domain.usecase.ImportOpml
import java.io.Reader
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
import java.util.UUID
import javax.inject.Inject

data class ShakeTestState(val active: Boolean, val magnitudeG: Float, val peaks: Int, val shakes: Int)

data class SettingsUiState(
    val settings: Settings,
    val shakeTest: ShakeTestState,
    val syncTestResult: String?,
    val syncStatus: SettingsRepository.SyncStatus,
    val syncing: Boolean,
    val opmlStatus: String?
) {
    companion object {
        val EMPTY = SettingsUiState(
            Settings.DEFAULT, ShakeTestState(false, 0f, 0, 0), null, SettingsRepository.SyncStatus(null, null), false, null
        )
    }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val refreshScheduler: RefreshScheduler,
    private val syncScheduler: SyncScheduler,
    private val syncApi: SupabaseSyncApi,
    private val importOpml: ImportOpml,
    private val exportOpml: ExportOpml
) : ViewModel() {

    private val opmlStatus = MutableStateFlow<String?>(null)

    private val shakeTest = MutableStateFlow(ShakeTestState(false, 0f, 0, 0))
    private val syncTestResult = MutableStateFlow<String?>(null)
    private var detector: ShakeDetector? = null
    private var source: AccelerometerSource? = null
    private var pollJob: Job? = null

    val state: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings, shakeTest, syncTestResult,
        settingsRepository.syncStatus, syncScheduler.isSyncing(), opmlStatus
    ) { values ->
        SettingsUiState(
            settings = values[0] as Settings,
            shakeTest = values[1] as ShakeTestState,
            syncTestResult = values[2] as String?,
            syncStatus = values[3] as SettingsRepository.SyncStatus,
            syncing = values[4] as Boolean,
            opmlStatus = values[5] as String?
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState.EMPTY)

    fun update(transform: (Settings) -> Settings) {
        viewModelScope.launch {
            val before = settingsRepository.current()
            settingsRepository.update(transform)
            val next = settingsRepository.current()
            detector?.config = configFor(next.shakeSensitivity)
            refreshScheduler.schedulePeriodic(next.refreshIntervalHours)
            if (next.syncEnabled && !before.syncEnabled) {
                syncScheduler.schedulePeriodic()
                syncScheduler.enqueueNow()
            } else if (!next.syncEnabled && before.syncEnabled) {
                syncScheduler.cancelPeriodic()
            } else if (next.syncEnabled) {
                syncScheduler.enqueueSoon(delaySeconds = 20)
            }
        }
    }

    fun refreshNow() = refreshScheduler.enqueueNow()

    /** Parses and subscribes; progress and the final tally are published through [SettingsUiState.opmlStatus]. */
    fun importOpml(reader: Reader, strings: OpmlStrings) {
        viewModelScope.launch {
            val feeds = withContext(Dispatchers.IO) { importOpml.parse(reader) }.getOrElse {
                opmlStatus.value = strings.error(it.message ?: it.javaClass.simpleName)
                return@launch
            }
            if (feeds.isEmpty()) {
                opmlStatus.value = strings.empty
                return@launch
            }
            opmlStatus.value = strings.progress(0, feeds.size)
            val report = importOpml.import(feeds) { done, total -> opmlStatus.value = strings.progress(done, total) }
            opmlStatus.value = strings.done(report.added, report.alreadySubscribed, report.failed)
            syncScheduler.enqueueSoon()
        }
    }

    suspend fun exportOpmlText(): String = exportOpml()

    fun setOpmlStatus(text: String?) { opmlStatus.value = text }

    /** Localised messages supplied by the screen so the view model stays free of resources. */
    class OpmlStrings(
        val empty: String,
        val error: (String) -> String,
        val progress: (Int, Int) -> String,
        val done: (added: Int, skipped: Int, failed: Int) -> String
    )

    fun syncNow() = syncScheduler.enqueueNow()

    fun generateSyncKey() {
        val key = "shakecast-" + UUID.randomUUID().toString().replace("-", "").take(10)
        update { it.copy(syncKey = key) }
    }

    fun testSync() {
        viewModelScope.launch {
            val s = settingsRepository.current()
            syncTestResult.value = withContext(Dispatchers.IO) {
                syncApi.ping(SupabaseConfig(s.supabaseUrl, s.supabaseKey, s.syncKey)).fold(
                    onSuccess = { it },
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
        shakeTest.value = ShakeTestState(active = true, magnitudeG = 0f, peaks = 0, shakes = 0)
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
