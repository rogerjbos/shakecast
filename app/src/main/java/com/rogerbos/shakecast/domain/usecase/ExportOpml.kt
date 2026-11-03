package com.rogerbos.shakecast.domain.usecase

import com.rogerbos.shakecast.data.db.PodcastDao
import com.rogerbos.shakecast.data.opml.OpmlFeed
import com.rogerbos.shakecast.data.opml.OpmlWriter
import javax.inject.Inject

class ExportOpml @Inject constructor(
    private val podcastDao: PodcastDao
) {
    suspend operator fun invoke(): String =
        OpmlWriter.write(podcastDao.getAll().sortedBy { it.title.lowercase() }.map { OpmlFeed(it.title, it.feedUrl) })
}
