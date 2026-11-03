package com.rogerbos.shakecast.data.sync

import com.rogerbos.shakecast.data.db.EpisodeDao
import com.rogerbos.shakecast.data.db.PodcastDao
import com.rogerbos.shakecast.data.db.PodcastEntity
import com.rogerbos.shakecast.data.db.SyncDao
import com.rogerbos.shakecast.data.remote.sync.EpisodeStateRow
import com.rogerbos.shakecast.data.remote.sync.ManualSkipRow
import com.rogerbos.shakecast.data.remote.sync.SubscriptionRow
import com.rogerbos.shakecast.data.remote.sync.SupabaseConfig
import com.rogerbos.shakecast.data.remote.sync.SupabaseSyncApi
import com.rogerbos.shakecast.data.repository.ManualSkipRepository
import com.rogerbos.shakecast.data.repository.PodcastRepository
import com.rogerbos.shakecast.data.settings.SettingsRepository
import com.rogerbos.shakecast.di.IoDispatcher
import com.rogerbos.shakecast.domain.usecase.MarkEpisodePlayed
import com.rogerbos.shakecast.domain.usecase.RefreshFeeds
import com.rogerbos.shakecast.domain.usecase.UnsubscribePodcast
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** A playback position that arrived from another device for an episode on this one. */
data class RemotePosition(val episodeId: Long, val positionMs: Long)

/**
 * Two-way sync with Supabase: push local changes, then pull every row for the
 * sync key and apply the ones that are newer than what this device has.
 */
@Singleton
class SyncEngine @Inject constructor(
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    private val syncDao: SyncDao,
    private val api: SupabaseSyncApi,
    private val settingsRepository: SettingsRepository,
    private val podcastRepository: PodcastRepository,
    private val refreshFeeds: RefreshFeeds,
    private val markEpisodePlayed: MarkEpisodePlayed,
    private val unsubscribePodcast: UnsubscribePodcast,
    private val manualSkipRepository: ManualSkipRepository,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    private val mutex = Mutex()
    private val remotePositionEvents = MutableSharedFlow<RemotePosition>(extraBufferCapacity = 32)

    val remotePositions: SharedFlow<RemotePosition> = remotePositionEvents.asSharedFlow()

    suspend fun sync(): Result<Unit> = withContext(io) {
        mutex.withLock {
            val settings = settingsRepository.current()
            if (!settings.syncEnabled) return@withLock Result.success(Unit)
            val config = SupabaseConfig(settings.supabaseUrl, settings.supabaseKey, settings.syncKey)
            if (!config.isComplete) {
                settingsRepository.recordSyncResult("Sync is not configured")
                return@withLock Result.success(Unit)
            }
            runCatching {
                pushSubscriptions(config)
                pushEpisodeStates(config)
                val newPodcastIds = pullSubscriptions(config)
                for (id in newPodcastIds) refreshFeeds.refresh(id)
                pullEpisodeStates(config)
                syncManualSkips(config)
            }.onSuccess {
                settingsRepository.recordSyncResult(null)
                Timber.i("Sync complete")
            }.onFailure {
                settingsRepository.recordSyncResult(it.message ?: it.javaClass.simpleName)
            }
        }
    }

    private suspend fun pushSubscriptions(config: SupabaseConfig) {
        val dirty = podcastDao.dirty()
        val tombstones = syncDao.tombstones()
        val rows = dirty.map { p ->
            SubscriptionRow(
                syncKey = config.syncKey,
                feedUrl = p.feedUrl,
                title = p.title,
                author = p.author,
                itunesId = p.itunesId,
                artworkUrl = p.artworkUrl,
                subscribed = true,
                updatedAtMs = timestampOf(p)
            )
        } + tombstones.map { t ->
            SubscriptionRow(syncKey = config.syncKey, feedUrl = t.feedUrl, subscribed = false, updatedAtMs = t.deletedAtMs)
        }
        if (rows.isEmpty()) return
        api.pushSubscriptions(config, rows).getOrThrow()
        for (p in dirty) podcastDao.clearDirty(p.id, timestampOf(p))
        for (t in tombstones) syncDao.deleteTombstone(t.feedUrl, t.deletedAtMs)
        Timber.d("Pushed %d subscriptions, %d tombstones", dirty.size, tombstones.size)
    }

    private suspend fun pushEpisodeStates(config: SupabaseConfig) {
        val now = System.currentTimeMillis()
        val dirty = episodeDao.dirtyStates()
        if (dirty.isEmpty()) return
        val stamped = dirty.map { it to SyncDecisions.episodeStateTimestamp(it.stateUpdatedAtMs, it.lastPlayedAt, it.playedAt, now) }
        val rows = stamped.map { (e, ts) ->
            EpisodeStateRow(
                syncKey = config.syncKey,
                feedUrl = e.feedUrl,
                guid = e.guid,
                positionMs = e.positionMs,
                isPlayed = e.isPlayed,
                playedAtMs = e.playedAt,
                updatedAtMs = ts
            )
        }
        api.pushEpisodeStates(config, rows).getOrThrow()
        for ((e, ts) in stamped) episodeDao.clearDirty(e.id, maxOf(ts, e.stateUpdatedAtMs))
        Timber.d("Pushed %d episode states", rows.size)
    }

    /** Returns ids of podcasts subscribed because another device added them. */
    private suspend fun pullSubscriptions(config: SupabaseConfig): List<Long> {
        val rows = api.pullSubscriptions(config).getOrThrow()
        val added = mutableListOf<Long>()
        for (row in rows) {
            val local = podcastDao.getByFeedUrl(row.feedUrl)
            val tombstone = syncDao.tombstone(row.feedUrl)
            val action = SyncDecisions.subscriptionAction(
                remoteSubscribed = row.subscribed,
                remoteUpdatedAtMs = row.updatedAtMs,
                localUpdatedAtMs = local?.let { timestampOf(it) },
                tombstoneAtMs = tombstone?.deletedAtMs
            )
            when (action) {
                SubscriptionAction.SUBSCRIBE -> {
                    added += podcastRepository.subscribeFromRemote(row)
                    Timber.i("Sync subscribed to %s", row.title)
                }
                SubscriptionAction.ACCEPT_TIMESTAMP -> podcastDao.applyRemoteSubscription(checkNotNull(local).id, row.updatedAtMs)
                SubscriptionAction.UNSUBSCRIBE -> {
                    unsubscribePodcast(checkNotNull(local).id, recordTombstone = false)
                    Timber.i("Sync unsubscribed from %s", local.title)
                }
                SubscriptionAction.NONE -> Unit
            }
            if (!row.subscribed && tombstone != null) syncDao.deleteTombstone(row.feedUrl, row.updatedAtMs)
        }
        return added
    }

    private suspend fun pullEpisodeStates(config: SupabaseConfig) {
        val rows = api.pullEpisodeStates(config).getOrThrow()
        val podcastIds = HashMap<String, Long?>()
        var applied = 0
        for (row in rows) {
            val podcastId = podcastIds.getOrPut(row.feedUrl) { podcastDao.getByFeedUrl(row.feedUrl)?.id } ?: continue
            val episode = episodeDao.getByGuid(podcastId, row.guid) ?: continue
            if (!SyncDecisions.episodeStateIsNewer(row.updatedAtMs, episode.stateUpdatedAtMs)) continue
            val changed = markEpisodePlayed.applyRemote(
                episodeId = episode.id,
                positionMs = row.positionMs,
                isPlayed = row.isPlayed,
                playedAtMs = row.playedAtMs,
                updatedAtMs = row.updatedAtMs
            )
            if (!changed) continue
            applied++
            if (!row.isPlayed && row.positionMs != episode.positionMs) {
                remotePositionEvents.tryEmit(RemotePosition(episode.id, row.positionMs))
            }
        }
        Timber.d("Pulled %d episode states, applied %d", rows.size, applied)
    }

    private suspend fun syncManualSkips(config: SupabaseConfig) {
        val dirty = manualSkipRepository.dirty()
        if (dirty.isNotEmpty()) {
            val rows = dirty.map { ManualSkipRow(config.syncKey, it.feedUrl, it.guid, it.startMs, it.endMs, it.deleted, it.updatedAtMs) }
            api.pushManualSkips(config, rows).getOrThrow()
            for (skip in dirty) manualSkipRepository.clearDirty(skip.id, skip.updatedAtMs)
            Timber.d("Pushed %d manual skips", rows.size)
        }
        val remote = api.pullManualSkips(config).getOrThrow()
        for (row in remote) {
            manualSkipRepository.applyRemote(row.feedUrl, row.guid, row.startMs, row.endMs, row.deleted, row.updatedAtMs)
        }
        Timber.d("Pulled %d manual skips", remote.size)
    }

    private fun timestampOf(p: PodcastEntity) = SyncDecisions.subscriptionTimestamp(p.subscriptionUpdatedAtMs, p.subscribedAt)
}
