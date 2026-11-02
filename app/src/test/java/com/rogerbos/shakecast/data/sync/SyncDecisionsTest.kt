package com.rogerbos.shakecast.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncDecisionsTest {

    @Test
    fun remoteSubscriptionUnknownLocallyIsAdded() {
        assertEquals(
            SubscriptionAction.SUBSCRIBE,
            SyncDecisions.subscriptionAction(remoteSubscribed = true, remoteUpdatedAtMs = 100, localUpdatedAtMs = null, tombstoneAtMs = null)
        )
    }

    @Test
    fun localUnsubscribeNewerThanRemoteSubscribeWins() {
        assertEquals(
            SubscriptionAction.NONE,
            SyncDecisions.subscriptionAction(remoteSubscribed = true, remoteUpdatedAtMs = 100, localUpdatedAtMs = null, tombstoneAtMs = 150)
        )
        assertEquals(
            SubscriptionAction.SUBSCRIBE,
            SyncDecisions.subscriptionAction(remoteSubscribed = true, remoteUpdatedAtMs = 200, localUpdatedAtMs = null, tombstoneAtMs = 150)
        )
    }

    @Test
    fun remoteUnsubscribeOnlyAppliesWhenNewer() {
        assertEquals(
            SubscriptionAction.UNSUBSCRIBE,
            SyncDecisions.subscriptionAction(remoteSubscribed = false, remoteUpdatedAtMs = 300, localUpdatedAtMs = 200, tombstoneAtMs = null)
        )
        assertEquals(
            SubscriptionAction.NONE,
            SyncDecisions.subscriptionAction(remoteSubscribed = false, remoteUpdatedAtMs = 100, localUpdatedAtMs = 200, tombstoneAtMs = null)
        )
        assertEquals(
            SubscriptionAction.NONE,
            SyncDecisions.subscriptionAction(remoteSubscribed = false, remoteUpdatedAtMs = 100, localUpdatedAtMs = null, tombstoneAtMs = null)
        )
    }

    @Test
    fun remoteSubscriptionNewerThanLocalOnlyMovesTimestamp() {
        assertEquals(
            SubscriptionAction.ACCEPT_TIMESTAMP,
            SyncDecisions.subscriptionAction(remoteSubscribed = true, remoteUpdatedAtMs = 300, localUpdatedAtMs = 200, tombstoneAtMs = null)
        )
        assertEquals(
            SubscriptionAction.NONE,
            SyncDecisions.subscriptionAction(remoteSubscribed = true, remoteUpdatedAtMs = 200, localUpdatedAtMs = 200, tombstoneAtMs = null)
        )
    }

    @Test
    fun episodeStateOnlyAppliesWhenStrictlyNewer() {
        assertTrue(SyncDecisions.episodeStateIsNewer(remoteUpdatedAtMs = 2, localUpdatedAtMs = 1))
        assertFalse(SyncDecisions.episodeStateIsNewer(remoteUpdatedAtMs = 1, localUpdatedAtMs = 1))
        assertFalse(SyncDecisions.episodeStateIsNewer(remoteUpdatedAtMs = 0, localUpdatedAtMs = 1))
    }

    @Test
    fun rowsFromBeforeSyncExistedFallBackToOlderTimestamps() {
        assertEquals(500, SyncDecisions.subscriptionTimestamp(subscriptionUpdatedAtMs = 0, subscribedAt = 500))
        assertEquals(700, SyncDecisions.subscriptionTimestamp(subscriptionUpdatedAtMs = 700, subscribedAt = 500))
        assertEquals(42, SyncDecisions.episodeStateTimestamp(stateUpdatedAtMs = 0, lastPlayedAt = 42, playedAt = 10, nowMs = 99))
        assertEquals(10, SyncDecisions.episodeStateTimestamp(stateUpdatedAtMs = 0, lastPlayedAt = null, playedAt = 10, nowMs = 99))
        assertEquals(99, SyncDecisions.episodeStateTimestamp(stateUpdatedAtMs = 0, lastPlayedAt = null, playedAt = null, nowMs = 99))
        assertEquals(77, SyncDecisions.episodeStateTimestamp(stateUpdatedAtMs = 77, lastPlayedAt = 42, playedAt = 10, nowMs = 99))
    }
}
