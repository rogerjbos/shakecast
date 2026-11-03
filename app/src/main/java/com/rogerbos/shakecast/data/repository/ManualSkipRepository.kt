package com.rogerbos.shakecast.data.repository

import com.rogerbos.shakecast.data.db.ManualSkipDao
import com.rogerbos.shakecast.data.db.ManualSkipEntity
import com.rogerbos.shakecast.domain.model.AdBlock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ManualSkipRepository @Inject constructor(
    private val manualSkipDao: ManualSkipDao
) {
    fun observe(feedUrl: String, guid: String): Flow<List<AdBlock>> =
        manualSkipDao.observeFor(feedUrl, guid).map { list -> list.map { it.toAdBlock() } }

    /** Records a span skipped by hand on this device; merges with an existing span at the same start. */
    suspend fun record(feedUrl: String, guid: String, startMs: Long, endMs: Long) {
        val now = System.currentTimeMillis()
        val existing = manualSkipDao.find(feedUrl, guid, startMs)
        if (existing == null) {
            manualSkipDao.insertIgnore(ManualSkipEntity(0, feedUrl, guid, startMs, endMs, now, deleted = false, syncDirty = true))
        } else if (existing.deleted || existing.endMs < endMs) {
            manualSkipDao.update(existing.id, maxOf(existing.endMs, endMs), now, deleted = false, dirty = true)
        }
    }

    /** Applies a span from another device; never marks it dirty. */
    suspend fun applyRemote(feedUrl: String, guid: String, startMs: Long, endMs: Long, deleted: Boolean, updatedAtMs: Long) {
        val existing = manualSkipDao.find(feedUrl, guid, startMs)
        if (existing == null) {
            manualSkipDao.insertIgnore(ManualSkipEntity(0, feedUrl, guid, startMs, endMs, updatedAtMs, deleted, syncDirty = false))
        } else if (updatedAtMs > existing.updatedAtMs) {
            manualSkipDao.update(existing.id, endMs, updatedAtMs, deleted, dirty = false)
        }
    }

    suspend fun forgetAll(feedUrl: String, guid: String) = manualSkipDao.forgetAll(feedUrl, guid, System.currentTimeMillis())

    suspend fun dirty(): List<ManualSkipEntity> = manualSkipDao.dirty()

    suspend fun clearDirty(id: Long, upToMs: Long) = manualSkipDao.clearDirty(id, upToMs)

    private fun ManualSkipEntity.toAdBlock() = AdBlock(startMs / 1000.0, endMs / 1000.0, REASON)

    companion object {
        const val REASON = "manual"
    }
}
