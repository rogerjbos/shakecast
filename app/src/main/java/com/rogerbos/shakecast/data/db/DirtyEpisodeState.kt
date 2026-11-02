package com.rogerbos.shakecast.data.db

/** Projection of an episode whose play state has not been pushed to the sync server yet. */
data class DirtyEpisodeState(
    val id: Long,
    val feedUrl: String,
    val guid: String,
    val positionMs: Long,
    val isPlayed: Boolean,
    val playedAt: Long?,
    val lastPlayedAt: Long?,
    val stateUpdatedAtMs: Long
)
