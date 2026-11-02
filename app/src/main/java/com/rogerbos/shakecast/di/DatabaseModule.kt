package com.rogerbos.shakecast.di

import android.content.Context
import androidx.room.Room
import com.rogerbos.shakecast.data.db.EpisodeDao
import com.rogerbos.shakecast.data.db.PodcastDao
import com.rogerbos.shakecast.data.db.ShakecastDatabase
import com.rogerbos.shakecast.data.db.SyncDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ShakecastDatabase =
        Room.databaseBuilder(context, ShakecastDatabase::class.java, "shakecast.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun providePodcastDao(db: ShakecastDatabase): PodcastDao = db.podcastDao()

    @Provides
    fun provideEpisodeDao(db: ShakecastDatabase): EpisodeDao = db.episodeDao()

    @Provides
    fun provideSyncDao(db: ShakecastDatabase): SyncDao = db.syncDao()
}
