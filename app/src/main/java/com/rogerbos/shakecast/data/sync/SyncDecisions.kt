package com.rogerbos.shakecast.data.sync

/** What to do with a remote subscription row, given what this device knows. */
enum class SubscriptionAction { NONE, SUBSCRIBE, UNSUBSCRIBE, ACCEPT_TIMESTAMP }

/** Pure last-writer-wins rules shared by the sync engine and its tests. */
object SyncDecisions {
    fun episodeStateIsNewer(remoteUpdatedAtMs: Long, localUpdatedAtMs: Long): Boolean =
        remoteUpdatedAtMs > localUpdatedAtMs

    /**
     * @param localUpdatedAtMs timestamp of the local subscription, or null when not subscribed.
     * @param tombstoneAtMs timestamp of a local, not yet pushed, unsubscribe for this feed.
     */
    fun subscriptionAction(
        remoteSubscribed: Boolean,
        remoteUpdatedAtMs: Long,
        localUpdatedAtMs: Long?,
        tombstoneAtMs: Long?
    ): SubscriptionAction = when {
        remoteSubscribed && localUpdatedAtMs == null ->
            if (tombstoneAtMs != null && tombstoneAtMs >= remoteUpdatedAtMs) SubscriptionAction.NONE else SubscriptionAction.SUBSCRIBE
        localUpdatedAtMs == null -> SubscriptionAction.NONE
        remoteSubscribed ->
            if (remoteUpdatedAtMs > localUpdatedAtMs) SubscriptionAction.ACCEPT_TIMESTAMP else SubscriptionAction.NONE
        remoteUpdatedAtMs > localUpdatedAtMs -> SubscriptionAction.UNSUBSCRIBE
        else -> SubscriptionAction.NONE
    }

    /** Timestamp to publish for a local subscription; rows created before sync existed carry 0. */
    fun subscriptionTimestamp(subscriptionUpdatedAtMs: Long, subscribedAt: Long): Long =
        if (subscriptionUpdatedAtMs > 0) subscriptionUpdatedAtMs else subscribedAt

    /** Timestamp to publish for an episode state; rows touched before sync existed carry 0. */
    fun episodeStateTimestamp(stateUpdatedAtMs: Long, lastPlayedAt: Long?, playedAt: Long?, nowMs: Long): Long =
        if (stateUpdatedAtMs > 0) stateUpdatedAtMs else lastPlayedAt ?: playedAt ?: nowMs
}
