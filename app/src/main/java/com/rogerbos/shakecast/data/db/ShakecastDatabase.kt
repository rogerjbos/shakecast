package com.rogerbos.shakecast.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [PodcastEntity::class, EpisodeEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class ShakecastDatabase : RoomDatabase() {
    abstract fun podcastDao(): PodcastDao
    abstract fun episodeDao(): EpisodeDao
}
