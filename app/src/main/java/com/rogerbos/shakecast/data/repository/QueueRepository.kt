package com.rogerbos.shakecast.data.repository

import com.rogerbos.shakecast.data.db.QueueDao
import com.rogerbos.shakecast.data.db.QueueItemEntity
import com.rogerbos.shakecast.domain.model.Episode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QueueRepository @Inject constructor(
    private val queueDao: QueueDao,
    private val json: Json
) {
    fun observe(): Flow<List<Episode>> = queueDao.observeQueue().map { list -> list.map { it.toDomain(json) } }

    suspend fun head(): Episode? = queueDao.queue().firstOrNull()?.toDomain(json)

    suspend fun playNext(episodeId: Long) {
        queueDao.upsert(QueueItemEntity(episodeId, queueDao.minPosition() - 1, System.currentTimeMillis()))
    }

    suspend fun addToEnd(episodeId: Long) {
        if (queueDao.count(episodeId) > 0) return
        queueDao.upsert(QueueItemEntity(episodeId, queueDao.maxPosition() + 1, System.currentTimeMillis()))
    }

    suspend fun remove(episodeId: Long) = queueDao.delete(episodeId)

    suspend fun moveUp(episodeId: Long) = queueDao.swapWithPrevious(episodeId)

    suspend fun contains(episodeId: Long): Boolean = queueDao.count(episodeId) > 0
}
