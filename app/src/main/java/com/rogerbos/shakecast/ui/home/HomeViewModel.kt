package com.rogerbos.shakecast.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rogerbos.shakecast.data.download.DownloadScheduler
import com.rogerbos.shakecast.data.refresh.RefreshScheduler
import com.rogerbos.shakecast.data.repository.EpisodeRepository
import com.rogerbos.shakecast.data.repository.PodcastRepository
import com.rogerbos.shakecast.domain.model.Episode
import com.rogerbos.shakecast.domain.model.Podcast
import com.rogerbos.shakecast.playback.PlayerConnection
import com.rogerbos.shakecast.playback.PlayerSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val podcasts: List<Podcast>,
    val episodes: List<Episode>,
    val isRefreshing: Boolean,
    val player: PlayerSnapshot
) {
    companion object {
        val EMPTY = HomeUiState(emptyList(), emptyList(), false, PlayerSnapshot.EMPTY)
    }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val podcastRepository: PodcastRepository,
    private val episodeRepository: EpisodeRepository,
    private val refreshScheduler: RefreshScheduler,
    private val downloadScheduler: DownloadScheduler,
    private val playerConnection: PlayerConnection
) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        podcastRepository.observeAll(),
        episodeRepository.observeLatest(limit = 150),
        refreshScheduler.isRefreshing(),
        playerConnection.state
    ) { podcasts, episodes, refreshing, player ->
        val unplayed = episodes.filter { !it.isPlayed }
        HomeUiState(
            podcasts = podcasts,
            episodes = unplayed.sortedWith(compareByDescending<Episode> { it.isDownloaded }.thenByDescending { it.pubDateMs }),
            isRefreshing = refreshing,
            player = player
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.EMPTY)

    fun refresh() = refreshScheduler.enqueueNow()

    fun playOrPause(episode: Episode) {
        val snapshot = playerConnection.state.value
        if (snapshot.episodeId == episode.id) playerConnection.togglePlayPause() else playerConnection.play(episode)
    }

    fun download(episode: Episode) {
        viewModelScope.launch { downloadScheduler.enqueue(episode.id, ignoreNetworkPolicy = true) }
    }
}
