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
        val autoPlayNext = booleanPreferencesKey("autoPlayNext")
        val rememberManualSkips = booleanPreferencesKey("rememberManualSkips")
        val trimSilence = booleanPreferencesKey("trimSilence")
        val volumeBoostDb = intPreferencesKey("volumeBoostDb")
        val shakeToNextChapter = booleanPreferencesKey("shakeToNextChapter")
        val lastSyncAtMs = longPreferencesKey("lastSyncAtMs")
        val lastSyncError = stringPreferencesKey("lastSyncError")
        val settingsUpdatedAtMs = longPreferencesKey("settingsUpdatedAtMs")
        val settingsDirty = booleanPreferencesKey("settingsDirty")
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

    /** Local change clock for settings sync. */
    data class SettingsSyncState(val updatedAtMs: Long, val dirty: Boolean)

    suspend fun syncState(): SettingsSyncState = context.settingsStore.data.first().let { p ->
        SettingsSyncState(p[Keys.settingsUpdatedAtMs] ?: 0L, p[Keys.settingsDirty] ?: false)
    }

    /** Forces the next sync to push settings, used after signing in to a new account. */
    suspend fun markDirty() {
        context.settingsStore.edit { p ->
            p[Keys.settingsUpdatedAtMs] = maxOf(p[Keys.settingsUpdatedAtMs] ?: 0L, System.currentTimeMillis())
            p[Keys.settingsDirty] = true
        }
    }

    suspend fun markSynced(upToMs: Long) {
        context.settingsStore.edit { p -> if ((p[Keys.settingsUpdatedAtMs] ?: 0L) <= upToMs) p[Keys.settingsDirty] = false }
    }

    /** Applies settings from another device without marking them dirty. */
    suspend fun applyRemote(remoteJson: String, updatedAtMs: Long) {
        val merged = SettingsSync.merge(current(), remoteJson)
        write(merged) { p ->
            p[Keys.settingsUpdatedAtMs] = updatedAtMs
            p[Keys.settingsDirty] = false
        }
    }

    suspend fun update(transform: (Settings) -> Settings) {
        val before = current()
        val next = transform(before)
        write(next) { p ->
            if (SettingsSync.toRemoteJson(next) != SettingsSync.toRemoteJson(before)) {
                p[Keys.settingsUpdatedAtMs] = System.currentTimeMillis()
                p[Keys.settingsDirty] = true
            }
        }
    }

    private suspend fun write(next: Settings, extra: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsStore.edit { p ->
            extra(p)
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
            p[Keys.autoPlayNext] = next.autoPlayNext
            p[Keys.rememberManualSkips] = next.rememberManualSkips
            p[Keys.trimSilence] = next.trimSilence
            p[Keys.volumeBoostDb] = next.volumeBoostDb
            p[Keys.shakeToNextChapter] = next.shakeToNextChapter
        }
    }
}
