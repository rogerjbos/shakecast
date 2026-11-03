package com.rogerbos.shakecast.domain.model

import kotlinx.serialization.Serializable

/** A chapter marker in seconds from the start of the episode. */
@Serializable
data class Chapter(
    val start: Double,
    val end: Double? = null,
    val title: String,
    val url: String? = null
)
