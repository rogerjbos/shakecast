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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HomeFilter { UNPLAYED, IN_PROGRESS, DOWNLOADED, ALL }

data class HomeUiState(
    val podcasts: List<Podcast>,
    val queue: List<Episode>,
    val episodes: List<Episode>,
    val isRefreshing: Boolean,
    val player: PlayerSnapshot,
    val filter: HomeFilter,
    val podcastFilterId: Long?,
    val query: String
) {
    companion object {
        val EMPTY = HomeUiState(emptyList(), emptyList(), emptyList(), false, PlayerSnapshot.EMPTY, HomeFilter.UNPLAYED, null, "")
    }
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
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

    private val filter = MutableStateFlow(HomeFilter.UNPLAYED)
    private val podcastFilterId = MutableStateFlow<Long?>(null)
    private val query = MutableStateFlow("")

    private val episodes = combine(podcastFilterId, query.debounce(250)) { id, q -> id to q }
        .flatMapLatest { (id, q) -> episodeRepository.observeFiltered(id, q) }

    private val listing = combine(episodes, filter, podcastFilterId, query) { episodes, filter, podcastId, q -> Listing(episodes, filter, podcastId, q) }

    private data class Listing(val episodes: List<Episode>, val filter: HomeFilter, val podcastId: Long?, val query: String)

    val state: StateFlow<HomeUiState> = combine(
        podcastRepository.observeAll(),
        queueRepository.observe(),
        listing,
        refreshScheduler.isRefreshing(),
        playerConnection.state
    ) { podcasts, queue, listing, refreshing, player ->
        val queuedIds = queue.map { it.id }.toSet()
        val shown = listing.episodes.filter { e ->
            e.id !in queuedIds && when (listing.filter) {
                HomeFilter.UNPLAYED -> !e.isPlayed
                HomeFilter.IN_PROGRESS -> !e.isPlayed && e.positionMs > 0
                HomeFilter.DOWNLOADED -> e.isDownloaded
                HomeFilter.ALL -> true
            }
        }
        HomeUiState(
            podcasts = podcasts,
            queue = queue,
            episodes = shown.sortedWith(compareByDescending<Episode> { it.isDownloaded }.thenByDescending { it.pubDateMs }),
            isRefreshing = refreshing,
            player = player,
            filter = listing.filter,
            podcastFilterId = listing.podcastId,
            query = listing.query
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.EMPTY)

    fun setFilter(value: HomeFilter) { filter.value = value }
    fun setPodcastFilter(id: Long?) { podcastFilterId.value = id }
    fun setQuery(value: String) { query.value = value }

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
