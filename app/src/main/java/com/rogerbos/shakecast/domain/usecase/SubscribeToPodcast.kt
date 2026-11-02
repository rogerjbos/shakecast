package com.rogerbos.shakecast.domain.usecase

import com.rogerbos.shakecast.data.repository.PodcastRepository
import com.rogerbos.shakecast.data.sync.SyncScheduler
import com.rogerbos.shakecast.domain.model.PodcastSearchResult
import javax.inject.Inject

class SubscribeToPodcast @Inject constructor(
    private val podcastRepository: PodcastRepository,
    private val refreshFeeds: RefreshFeeds,
    private val syncScheduler: SyncScheduler
) {
    suspend operator fun invoke(result: PodcastSearchResult): Result<Long> {
        val id = podcastRepository.subscribe(result)
        syncScheduler.enqueueSoon()
        return refreshFeeds.refresh(id).map { id }
    }
}
