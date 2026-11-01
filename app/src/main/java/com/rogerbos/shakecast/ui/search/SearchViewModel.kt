package com.rogerbos.shakecast.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rogerbos.shakecast.data.repository.PodcastRepository
import com.rogerbos.shakecast.domain.model.PodcastSearchResult
import com.rogerbos.shakecast.domain.usecase.SubscribeToPodcast
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String,
    val results: List<PodcastSearchResult>,
    val isSearching: Boolean,
    val error: String?,
    val subscribedFeedUrls: Set<String>,
    val subscribingFeedUrls: Set<String>
) {
    companion object {
        val EMPTY = SearchUiState("", emptyList(), false, null, emptySet(), emptySet())
    }
}

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val podcastRepository: PodcastRepository,
    private val subscribeToPodcast: SubscribeToPodcast
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val results = MutableStateFlow<List<PodcastSearchResult>>(emptyList())
    private val searching = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val subscribing = MutableStateFlow<Set<String>>(emptySet())

    val state: StateFlow<SearchUiState> = combine(
        query, results, searching, error, subscribing,
        podcastRepository.observeAll().map { list -> list.map { it.feedUrl }.toSet() }
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        SearchUiState(
            query = values[0] as String,
            results = values[1] as List<PodcastSearchResult>,
            isSearching = values[2] as Boolean,
            error = values[3] as String?,
            subscribingFeedUrls = values[4] as Set<String>,
            subscribedFeedUrls = values[5] as Set<String>
        )
    }.stateIn(viewModelScope, SharingStarted_whileSubscribed, SearchUiState.EMPTY)

    init {
        query.debounce(400)
            .map { it.trim() }
            .distinctUntilChanged()
            .onEach { term -> if (term.length >= 2) search(term) else results.value = emptyList() }
            .launchIn(viewModelScope)
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    private suspend fun search(term: String) {
        searching.value = true
        error.value = null
        podcastRepository.search(term)
            .onSuccess { results.value = it }
            .onFailure { error.value = it.message ?: it.javaClass.simpleName }
        searching.value = false
    }

    fun subscribe(result: PodcastSearchResult) {
        if (result.feedUrl in subscribing.value) return
        subscribing.update { it + result.feedUrl }
        viewModelScope.launch {
            subscribeToPodcast(result).onFailure { error.value = it.message }
            subscribing.update { it - result.feedUrl }
        }
    }

    private companion object {
        val SharingStarted_whileSubscribed = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000)
    }
}
