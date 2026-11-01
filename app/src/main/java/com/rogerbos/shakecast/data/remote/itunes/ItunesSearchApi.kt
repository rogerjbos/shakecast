package com.rogerbos.shakecast.data.remote.itunes

import retrofit2.http.GET
import retrofit2.http.Query

/** iTunes Search API, same call as podcast3r/podcast_scraper.ts line 108. */
interface ItunesSearchApi {
    @GET("search")
    suspend fun search(
        @Query("term") term: String,
        @Query("media") media: String = "podcast",
        @Query("entity") entity: String = "podcast",
        @Query("limit") limit: Int = 25
    ): ItunesSearchResponse
}
