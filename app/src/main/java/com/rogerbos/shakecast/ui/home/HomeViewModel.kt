package com.rogerbos.shakecast.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rogerbos.shakecast.data.download.DownloadScheduler
import com.rogerbos.shakecast.data.refresh.RefreshScheduler
import com.rogerbos.shakecast.data.repository.EpisodeRepository
import com.rogerbos.shakecast.data.repository.PodcastRepository
import com.rogerbos.shakecast.data.repository.QueueRepository
import com.rogerbos.shakecast.domain.usecase.MarkEpisodePlayed
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
    val queue: List<Episode>,
    val episodes: List<Episode>,
    val isRefreshing: Boolean,
    val player: PlayerSnapshot
) {
    companion object {
        val EMPTY = HomeUiState(emptyList(), emptyList(), emptyList(), false, PlayerSnapshot.EMPTY)
    }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val podcastRepository: PodcastRepository,
    private val episodeRepository: EpisodeRepository,
    private val refreshScheduler: RefreshScheduler,
    private val downloadScheduler: DownloadScheduler,
    private val playerConnection: PlayerConnection,
    private val queueRepository: QueueRepository,
    private val markEpisodePlayed: MarkEpisodePlayed
) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        podcastRepository.observeAll(),
        queueRepository.observe(),
        episodeRepository.observeLatest(limit = 150),
        refreshScheduler.isRefreshing(),
        playerConnection.state
    ) { podcasts, queue, episodes, refreshing, player ->
        val queuedIds = queue.map { it.id }.toSet()
        val unplayed = episodes.filter { !it.isPlayed && it.id !in queuedIds }
        HomeUiState(
            podcasts = podcasts,
            queue = queue,
            episodes = unplayed.sortedWith(compareByDescending<Episode> { it.isDownloaded }.thenByDescending { it.pubDateMs }),
            isRefreshing = refreshing,
            player = player
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.EMPTY)

    fun playNext(episode: Episode) { viewModelScope.launch { queueRepository.playNext(episode.id) } }
    fun addToQueue(episode: Episode) { viewModelScope.launch { queueRepository.addToEnd(episode.id) } }
    fun removeFromQueue(episode: Episode) { viewModelScope.launch { queueRepository.remove(episode.id) } }
    fun moveUp(episode: Episode) { viewModelScope.launch { queueRepository.moveUp(episode.id) } }
    fun markPlayed(episode: Episode) { viewModelScope.launch { markEpisodePlayed(episode.id) } }

    fun refresh() = refreshScheduler.enqueueNow()

    fun playOrPause(episode: Episode) {
        val snapshot = playerConnection.state.value
        if (snapshot.episodeId == episode.id) playerConnection.togglePlayPause() else playerConnection.play(episode)
    }

    fun download(episode: Episode) {
        viewModelScope.launch { downloadScheduler.enqueue(episode.id, ignoreNetworkPolicy = true) }
    }
}
