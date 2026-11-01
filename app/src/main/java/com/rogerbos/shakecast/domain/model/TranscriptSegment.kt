package com.rogerbos.shakecast.domain.model

data class TranscriptSegment(
    val id: Int,
    val start: Double,
    val end: Double,
    val text: String
)
