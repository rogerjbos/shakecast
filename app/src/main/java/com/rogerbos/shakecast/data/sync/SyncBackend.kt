package com.rogerbos.shakecast.data.sync

import com.rogerbos.shakecast.BuildConfig

/**
 * The Supabase project compiled into this build, if any. When present the Settings screen hides
 * the URL and key fields and every phone talks to this project; otherwise the user supplies their own.
 */
object SyncBackend {
    val url: String = BuildConfig.SUPABASE_URL
    val key: String = BuildConfig.SUPABASE_KEY
    val builtIn: Boolean = url.isNotBlank() && key.isNotBlank()
}
