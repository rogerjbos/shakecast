package com.rogerbos.shakecast.domain.model

data class TimeRange(val start: Double, val end: Double)

data class AdDetection(
    val totalDuration: Double,
    val adBlocks: List<AdBlock>,
    val contentRanges: List<TimeRange>,
    val adTimeSaved: Double
)
