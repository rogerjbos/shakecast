package com.rogerbos.shakecast.data.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * Converts settings to and from the JSON document shared between devices.
 * Sync credentials and the crash-report consent never leave the device; unknown keys from newer builds are ignored.
 */
object SettingsSync {
    val localOnlyKeys = setOf("syncEnabled", "supabaseUrl", "supabaseKey", "crashReportsEnabled")

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun toRemoteJson(settings: Settings): String {
        val obj = json.encodeToJsonElement(settings).jsonObject
        return json.encodeToString(JsonObject(obj.filterKeys { it !in localOnlyKeys }))
    }

    /** Applies every synced key from [remoteJson] onto [current], keeping local-only keys as they are. */
    fun merge(current: Settings, remoteJson: String): Settings {
        val remote = runCatching { json.parseToJsonElement(remoteJson).jsonObject }.getOrNull() ?: return current
        val base = json.encodeToJsonElement(current).jsonObject
        val merged = JsonObject(base + remote.filterKeys { it !in localOnlyKeys && it in base })
        return runCatching { json.decodeFromJsonElement(Settings.serializer(), merged) }.getOrDefault(current)
    }
}
