package com.rogerbos.shakecast.ui.podcast

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.rogerbos.shakecast.data.download.DownloadScheduler
import com.rogerbos.shakecast.data.repository.EpisodeRepository
import com.rogerbos.shakecast.data.repository.PodcastRepository
import com.rogerbos.shakecast.domain.model.Episode
import com.rogerbos.shakecast.domain.model.Podcast
import com.rogerbos.shakecast.domain.usecase.MarkEpisodePlayed
import com.rogerbos.shakecast.domain.usecase.RefreshFeeds
import com.rogerbos.shakecast.domain.usecase.UnsubscribePodcast
import com.rogerbos.shakecast.playback.PlayerConnection
import com.rogerbos.shakecast.playback.PlayerSnapshot
import com.rogerbos.shakecast.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PodcastDetailUiState(
    val podcast: Podcast?,
    val episodes: List<Episode>,
    val isRefreshing: Boolean,
    val player: PlayerSnapshot
) {
    companion object {
        val EMPTY = PodcastDetailUiState(null, emptyList(), false, PlayerSnapshot.EMPTY)
    }
}

@HiltViewModel
class PodcastDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val podcastRepository: PodcastRepository,
    private val episodeRepository: EpisodeRepository,
    private val refreshFeeds: RefreshFeeds,
    private val unsubscribePodcast: UnsubscribePodcast,
    private val markEpisodePlayed: MarkEpisodePlayed,
    private val downloadScheduler: DownloadScheduler,
    private val playerConnection: PlayerConnection
) : ViewModel() {

    private val podcastId: Long = savedStateHandle.toRoute<Route.PodcastDetail>().podcastId
    private val refreshing = MutableStateFlow(false)

    val state: StateFlow<PodcastDetailUiState> = combine(
        podcastRepository.observe(podcastId),
        episodeRepository.observeForPodcast(podcastId),
        refreshing,
        playerConnection.state
    ) { podcast, episodes, isRefreshing, player ->
        PodcastDetailUiState(podcast, episodes, isRefreshing, player)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PodcastDetailUiState.EMPTY)

    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            refreshFeeds.refresh(podcastId)
            refreshing.value = false
        }
    }

    fun playOrPause(episode: Episode) {
        if (playerConnection.state.value.episodeId == episode.id) playerConnection.togglePlayPause() else playerConnection.play(episode)
    }

    fun download(episode: Episode) {
        viewModelScope.launch { downloadScheduler.enqueue(episode.id, ignoreNetworkPolicy = true) }
    }

    fun deleteDownload(episode: Episode) {
        viewModelScope.launch { markEpisodePlayed.deleteDownload(episode.id) }
    }

    fun togglePlayed(episode: Episode) {
        viewModelScope.launch {
            if (episode.isPlayed) episodeRepository.markUnplayed(episode.id) else markEpisodePlayed(episode.id)
        }
    }

    fun setAutoSkipAds(enabled: Boolean) {
        viewModelScope.launch { podcastRepository.setAutoSkipAds(podcastId, enabled) }
    }

    fun setAutoDownload(enabled: Boolean) {
        viewModelScope.launch { podcastRepository.setAutoDownload(podcastId, enabled) }
    }

    fun unsubscribe(onDone: () -> Unit) {
        viewModelScope.launch {
            unsubscribePodcast(podcastId)
            onDone()
        }
    }
}
