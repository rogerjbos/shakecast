package com.rogerbos.shakecast.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSyncTest {

    private val local = Settings.DEFAULT.copy(
        syncEnabled = true, supabaseUrl = "https://x.supabase.co", supabaseKey = "sb_publishable_1",
        skipForwardSec = 45, shakeSensitivity = ShakeSensitivity.HIGH
    )

    @Test
    fun credentialsNeverLeaveTheDevice() {
        val json = SettingsSync.toRemoteJson(local)
        assertFalse(json.contains("supabase"))
        assertFalse(json.contains("sb_publishable_1"))
        assertFalse(json.contains("syncEnabled"))
        assertTrue(json.contains("\"skipForwardSec\":45"))
        assertTrue(json.contains("\"shakeSensitivity\":\"HIGH\""))
    }

    @Test
    fun mergeAppliesRemoteValuesButKeepsLocalCredentials() {
        val remote = SettingsSync.toRemoteJson(Settings.DEFAULT.copy(skipForwardSec = 60, playbackSpeed = 1.5f, trimSilence = true))
        val merged = SettingsSync.merge(local, remote)

        assertEquals(60, merged.skipForwardSec)
        assertEquals(1.5f, merged.playbackSpeed)
        assertTrue(merged.trimSilence)
        assertEquals("sb_publishable_1", merged.supabaseKey)
        assertEquals("https://x.supabase.co", merged.supabaseUrl)
        assertTrue(merged.syncEnabled)
    }

    @Test
    fun remoteCannotOverrideCredentialsOrInjectUnknownKeys() {
        val remote = """{"supabaseKey":"evil","supabaseUrl":"https://evil","skipBackSec":20,"futureSetting":true}"""
        val merged = SettingsSync.merge(local, remote)
        assertEquals("sb_publishable_1", merged.supabaseKey)
        assertEquals("https://x.supabase.co", merged.supabaseUrl)
        assertEquals(20, merged.skipBackSec)
    }

    @Test
    fun garbageLeavesSettingsUntouched() {
        assertEquals(local, SettingsSync.merge(local, "not json"))
    }
}
