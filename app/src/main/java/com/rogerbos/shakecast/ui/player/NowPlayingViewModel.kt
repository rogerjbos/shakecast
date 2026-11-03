package com.rogerbos.shakecast.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rogerbos.shakecast.data.repository.EpisodeRepository
import com.rogerbos.shakecast.data.repository.ManualSkipRepository
import com.rogerbos.shakecast.data.settings.Settings
import com.rogerbos.shakecast.data.settings.SettingsRepository
import com.rogerbos.shakecast.domain.model.AdBlock
import com.rogerbos.shakecast.domain.model.AdSource
import com.rogerbos.shakecast.domain.model.Chapter
import com.rogerbos.shakecast.domain.usecase.MarkEpisodePlayed
import com.rogerbos.shakecast.playback.PlayerConnection
import com.rogerbos.shakecast.playback.PlayerSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NowPlayingUiState(
    val player: PlayerSnapshot,
    val adBlocks: List<AdBlock>,
    val manualSkips: List<AdBlock>,
    val chapters: List<Chapter>,
    val adSource: AdSource,
    val settings: Settings,
    val isPlayed: Boolean
) {
    companion object {
        val EMPTY = NowPlayingUiState(PlayerSnapshot.EMPTY, emptyList(), emptyList(), emptyList(), AdSource.NONE, Settings.DEFAULT, false)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val playerConnection: PlayerConnection,
    private val episodeRepository: EpisodeRepository,
    private val settingsRepository: SettingsRepository,
    private val markEpisodePlayed: MarkEpisodePlayed,
    private val manualSkipRepository: ManualSkipRepository
) : ViewModel() {

    private val episode = playerConnection.state.map { it.episodeId }.distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else episodeRepository.observe(id) }

    private val manualSkips = episode.map { it?.let { e -> e.podcastFeedUrl to e.guid } }.distinctUntilChanged()
        .flatMapLatest { key -> if (key == null) flowOf(emptyList()) else manualSkipRepository.observe(key.first, key.second) }

    val state: StateFlow<NowPlayingUiState> = combine(
        playerConnection.state,
        episode,
        manualSkips,
        settingsRepository.settings
    ) { player, ep, manual, settings ->
        NowPlayingUiState(
            player = player,
            adBlocks = ep?.adBlocks.orEmpty(),
            manualSkips = manual,
            chapters = ep?.chapters.orEmpty(),
            adSource = ep?.adSource ?: AdSource.NONE,
            settings = settings,
            isPlayed = ep?.isPlayed ?: false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NowPlayingUiState.EMPTY)

    fun setSleepTimer(minutes: Int) = playerConnection.setSleepTimer(minutes)
    fun nextChapter() = playerConnection.nextChapter()
    fun previousChapter() = playerConnection.previousChapter()

    fun forgetManualSkips() {
        val id = playerConnection.state.value.episodeId ?: return
        viewModelScope.launch {
            val ep = episodeRepository.get(id) ?: return@launch
            manualSkipRepository.forgetAll(ep.podcastFeedUrl, ep.guid)
        }
    }

    fun togglePlayPause() = playerConnection.togglePlayPause()
    fun skipForward() = playerConnection.skipForward()
    fun skipBack() = playerConnection.skipBack()
    fun seekTo(ms: Long) = playerConnection.seekTo(ms)

    fun setSpeed(speed: Float) {
        playerConnection.setSpeed(speed)
        viewModelScope.launch { settingsRepository.update { it.copy(playbackSpeed = speed) } }
    }

    fun toggleAutoSkip() {
        viewModelScope.launch { settingsRepository.update { it.copy(autoSkipAdsEnabled = !it.autoSkipAdsEnabled) } }
    }

    fun markPlayed() {
        val id = playerConnection.state.value.episodeId ?: return
        viewModelScope.launch { markEpisodePlayed(id) }
    }
}
