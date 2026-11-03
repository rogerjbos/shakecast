package com.rogerbos.shakecast.data.remote.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Row of the `subscriptions` table; `subscribed = false` is an unsubscribe tombstone. */
@Serializable
data class SubscriptionRow(
    @SerialName("sync_key") val syncKey: String,
    @SerialName("feed_url") val feedUrl: String,
    val title: String? = null,
    val author: String? = null,
    @SerialName("itunes_id") val itunesId: Long? = null,
    @SerialName("artwork_url") val artworkUrl: String? = null,
    val subscribed: Boolean,
    @SerialName("updated_at_ms") val updatedAtMs: Long
)

/** Row of the `episode_states` table. */
@Serializable
data class EpisodeStateRow(
    @SerialName("sync_key") val syncKey: String,
    @SerialName("feed_url") val feedUrl: String,
    val guid: String,
    @SerialName("position_ms") val positionMs: Long,
    @SerialName("is_played") val isPlayed: Boolean,
    @SerialName("played_at_ms") val playedAtMs: Long? = null,
    @SerialName("updated_at_ms") val updatedAtMs: Long
)

/** Row of the `manual_skips` table; `deleted` marks a span the user chose to forget. */
@Serializable
data class ManualSkipRow(
    @SerialName("sync_key") val syncKey: String,
    @SerialName("feed_url") val feedUrl: String,
    val guid: String,
    @SerialName("start_ms") val startMs: Long,
    @SerialName("end_ms") val endMs: Long,
    val deleted: Boolean = false,
    @SerialName("updated_at_ms") val updatedAtMs: Long
)
