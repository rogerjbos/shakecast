package com.rogerbos.shakecast.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        PodcastEntity::class, EpisodeEntity::class, SubscriptionTombstoneEntity::class,
        QueueItemEntity::class, ManualSkipEntity::class
    ],
    version = 5,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5)]
)
@TypeConverters(Converters::class)
abstract class ShakecastDatabase : RoomDatabase() {
    abstract fun podcastDao(): PodcastDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun syncDao(): SyncDao
    abstract fun queueDao(): QueueDao
    abstract fun manualSkipDao(): ManualSkipDao
}
