package com.rogerbos.shakecast.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val skipForwardSec = intPreferencesKey("skipForwardSec")
        val skipBackSec = intPreferencesKey("skipBackSec")
        val shakeEnabled = booleanPreferencesKey("shakeEnabled")
        val shakeSensitivity = stringPreferencesKey("shakeSensitivity")
        val volumeChordEnabled = booleanPreferencesKey("volumeChordEnabled")
        val autoSkipAdsEnabled = booleanPreferencesKey("autoSkipAdsEnabled")
        val hapticsEnabled = booleanPreferencesKey("hapticsEnabled")
        val playbackSpeed = floatPreferencesKey("playbackSpeed")
        val downloadWifiOnly = booleanPreferencesKey("downloadWifiOnly")
        val episodesToKeepPerShow = intPreferencesKey("episodesToKeepPerShow")
        val autoDeleteWhenPlayed = booleanPreferencesKey("autoDeleteWhenPlayed")
        val refreshIntervalHours = intPreferencesKey("refreshIntervalHours")
        val syncEnabled = booleanPreferencesKey("syncEnabled")
        val supabaseUrl = stringPreferencesKey("supabaseUrl")
        val supabaseKey = stringPreferencesKey("supabaseKey")
        val syncKey = stringPreferencesKey("syncKey")
        val autoPlayNext = booleanPreferencesKey("autoPlayNext")
        val rememberManualSkips = booleanPreferencesKey("rememberManualSkips")
        val trimSilence = booleanPreferencesKey("trimSilence")
        val volumeBoostDb = intPreferencesKey("volumeBoostDb")
        val shakeToNextChapter = booleanPreferencesKey("shakeToNextChapter")
        val lastSyncAtMs = longPreferencesKey("lastSyncAtMs")
        val lastSyncError = stringPreferencesKey("lastSyncError")
    }

    val settings: Flow<Settings> = context.settingsStore.data.map { p ->
        val d = Settings.DEFAULT
        Settings(
            skipForwardSec = p[Keys.skipForwardSec] ?: d.skipForwardSec,
            skipBackSec = p[Keys.skipBackSec] ?: d.skipBackSec,
            shakeEnabled = p[Keys.shakeEnabled] ?: d.shakeEnabled,
            shakeSensitivity = p[Keys.shakeSensitivity]?.let { runCatching { ShakeSensitivity.valueOf(it) }.getOrNull() } ?: d.shakeSensitivity,
            volumeChordEnabled = p[Keys.volumeChordEnabled] ?: d.volumeChordEnabled,
            autoSkipAdsEnabled = p[Keys.autoSkipAdsEnabled] ?: d.autoSkipAdsEnabled,
            hapticsEnabled = p[Keys.hapticsEnabled] ?: d.hapticsEnabled,
            playbackSpeed = p[Keys.playbackSpeed] ?: d.playbackSpeed,
            downloadWifiOnly = p[Keys.downloadWifiOnly] ?: d.downloadWifiOnly,
            episodesToKeepPerShow = p[Keys.episodesToKeepPerShow] ?: d.episodesToKeepPerShow,
            autoDeleteWhenPlayed = p[Keys.autoDeleteWhenPlayed] ?: d.autoDeleteWhenPlayed,
            refreshIntervalHours = p[Keys.refreshIntervalHours] ?: d.refreshIntervalHours,
            syncEnabled = p[Keys.syncEnabled] ?: d.syncEnabled,
            supabaseUrl = p[Keys.supabaseUrl] ?: d.supabaseUrl,
            supabaseKey = p[Keys.supabaseKey] ?: d.supabaseKey,
            syncKey = p[Keys.syncKey] ?: d.syncKey,
            autoPlayNext = p[Keys.autoPlayNext] ?: d.autoPlayNext,
            rememberManualSkips = p[Keys.rememberManualSkips] ?: d.rememberManualSkips,
            trimSilence = p[Keys.trimSilence] ?: d.trimSilence,
            volumeBoostDb = p[Keys.volumeBoostDb] ?: d.volumeBoostDb,
            shakeToNextChapter = p[Keys.shakeToNextChapter] ?: d.shakeToNextChapter
        )
    }

    /** Outcome of the most recent sync run; `error` is null after a success. */
    data class SyncStatus(val lastSyncAtMs: Long?, val error: String?)

    val syncStatus: Flow<SyncStatus> = context.settingsStore.data.map { p ->
        SyncStatus(p[Keys.lastSyncAtMs], p[Keys.lastSyncError])
    }

    suspend fun recordSyncResult(error: String?) {
        context.settingsStore.edit { p ->
            if (error == null) {
                p[Keys.lastSyncAtMs] = System.currentTimeMillis()
                p.remove(Keys.lastSyncError)
            } else {
                p[Keys.lastSyncError] = error
            }
        }
    }

    suspend fun current(): Settings = settings.first()

    suspend fun update(transform: (Settings) -> Settings) {
        context.settingsStore.edit { p ->
            val next = transform(settings.first())
            p[Keys.skipForwardSec] = next.skipForwardSec
            p[Keys.skipBackSec] = next.skipBackSec
            p[Keys.shakeEnabled] = next.shakeEnabled
            p[Keys.shakeSensitivity] = next.shakeSensitivity.name
            p[Keys.volumeChordEnabled] = next.volumeChordEnabled
            p[Keys.autoSkipAdsEnabled] = next.autoSkipAdsEnabled
            p[Keys.hapticsEnabled] = next.hapticsEnabled
            p[Keys.playbackSpeed] = next.playbackSpeed
            p[Keys.downloadWifiOnly] = next.downloadWifiOnly
            p[Keys.episodesToKeepPerShow] = next.episodesToKeepPerShow
            p[Keys.autoDeleteWhenPlayed] = next.autoDeleteWhenPlayed
            p[Keys.refreshIntervalHours] = next.refreshIntervalHours
            p[Keys.syncEnabled] = next.syncEnabled
            p[Keys.supabaseUrl] = next.supabaseUrl
            p[Keys.supabaseKey] = next.supabaseKey
            p[Keys.syncKey] = next.syncKey
            p[Keys.autoPlayNext] = next.autoPlayNext
            p[Keys.rememberManualSkips] = next.rememberManualSkips
            p[Keys.trimSilence] = next.trimSilence
            p[Keys.volumeBoostDb] = next.volumeBoostDb
            p[Keys.shakeToNextChapter] = next.shakeToNextChapter
        }
    }
}
