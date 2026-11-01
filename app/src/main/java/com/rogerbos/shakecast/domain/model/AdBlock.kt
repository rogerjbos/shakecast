package com.rogerbos.shakecast.domain.model

import kotlinx.serialization.Serializable

/** An ad segment in seconds from the start of the episode. */
@Serializable
data class AdBlock(
    val start: Double,
    val end: Double,
    val reason: String
) {
    val duration: Double get() = end - start
}
