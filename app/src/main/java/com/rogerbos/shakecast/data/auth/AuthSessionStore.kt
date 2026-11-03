package com.rogerbos.shakecast.data.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authStore: DataStore<Preferences> by preferencesDataStore(name = "auth")

/** Persists the signed-in session in the app's private storage; it is never synced. */
@Singleton
class AuthSessionStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val userId = stringPreferencesKey("userId")
        val email = stringPreferencesKey("email")
        val accessToken = stringPreferencesKey("accessToken")
        val refreshToken = stringPreferencesKey("refreshToken")
        val expiresAtMs = longPreferencesKey("expiresAtMs")
    }

    val session: Flow<AuthSession?> = context.authStore.data.map { p ->
        val userId = p[Keys.userId]
        val access = p[Keys.accessToken]
        val refresh = p[Keys.refreshToken]
        if (userId == null || access == null || refresh == null) {
            null
        } else {
            AuthSession(userId, p[Keys.email].orEmpty(), access, refresh, p[Keys.expiresAtMs] ?: 0L)
        }
    }

    suspend fun current(): AuthSession? = session.first()

    suspend fun save(session: AuthSession) {
        context.authStore.edit { p ->
            p[Keys.userId] = session.userId
            p[Keys.email] = session.email
            p[Keys.accessToken] = session.accessToken
            p[Keys.refreshToken] = session.refreshToken
            p[Keys.expiresAtMs] = session.expiresAtMs
        }
    }

    suspend fun clear() {
        context.authStore.edit { it.clear() }
    }
}
