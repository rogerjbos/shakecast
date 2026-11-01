package com.rogerbos.shakecast.data.remote.skiplist

import com.rogerbos.shakecast.domain.model.AdBlock
import kotlinx.serialization.Serializable

/** Matches the JSON written by podcast3r/skip_ads.py --save-skiplist. */
@Serializable
data class SkipListDto(
    val totalDuration: Double,
    val adBlocks: List<AdBlockDto> = emptyList(),
    val contentRanges: List<RangeDto> = emptyList(),
    val adTimeSaved: Double = 0.0
) {
    fun toAdBlocks(): List<AdBlock> = adBlocks.map { AdBlock(it.start, it.end, it.reason) }
}

@Serializable
data class AdBlockDto(val start: Double, val end: Double, val reason: String = "")

@Serializable
data class RangeDto(val start: Double, val end: Double)
