package com.rogerbos.shakecast.data.auth

import com.rogerbos.shakecast.data.db.EpisodeDao
import com.rogerbos.shakecast.data.db.ManualSkipDao
import com.rogerbos.shakecast.data.db.PodcastDao
import com.rogerbos.shakecast.data.remote.sync.SupabaseConfig
import com.rogerbos.shakecast.data.settings.SettingsRepository
import com.rogerbos.shakecast.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Thrown when sync needs a user and nobody is signed in, or the stored session can no longer be refreshed. */
class NotSignedInException(message: String) : Exception(message)

/**
 * Owns the Supabase Auth session: sign-up, sign-in, sign-out and silent token refresh.
 * Signing in marks the whole local library dirty so the account receives a full copy.
 */
@Singleton
class AuthManager @Inject constructor(
    private val api: SupabaseAuthApi,
    private val store: AuthSessionStore,
    private val settingsRepository: SettingsRepository,
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    private val manualSkipDao: ManualSkipDao,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    private val refreshMutex = Mutex()

    val session: Flow<AuthSession?> = store.session

    suspend fun signUp(email: String, password: String): Result<SignUpResult> = withContext(io) {
        val s = settingsRepository.current()
        api.signUp(s.supabaseUrl, s.supabaseKey, email.trim(), password).onSuccess { result ->
            if (result is SignUpResult.SignedIn) adopt(result.session)
        }
    }

    suspend fun signIn(email: String, password: String): Result<AuthSession> = withContext(io) {
        val s = settingsRepository.current()
        api.signIn(s.supabaseUrl, s.supabaseKey, email.trim(), password).onSuccess { adopt(it) }
    }

    suspend fun signOut() = withContext(io) {
        val s = settingsRepository.current()
        store.current()?.let { session ->
            api.signOut(s.supabaseUrl, s.supabaseKey, session).onFailure { Timber.w("Sign-out call failed: %s", it.message) }
        }
        store.clear()
    }

    /** Deletes the account and all of its rows on the server, then forgets the session locally. */
    suspend fun deleteAccount(): Result<Unit> = withContext(io) {
        runCatching {
            val s = settingsRepository.current()
            val session = validSession(s.supabaseUrl, s.supabaseKey)
            api.deleteAccount(s.supabaseUrl, s.supabaseKey, session).getOrElse { error ->
                if (error is AuthException && error.code == 404) {
                    throw IllegalStateException("The project is missing the delete_account function; rerun supabase/schema.sql")
                }
                throw error
            }
            store.clear()
            Timber.i("Account deleted")
        }
    }

    /**
     * Connection details with a fresh access token, refreshing it first when it is
     * about to expire. Fails with [NotSignedInException] when there is no usable session.
     */
    suspend fun config(): Result<SupabaseConfig> = withContext(io) {
        runCatching {
            val s = settingsRepository.current()
            if (s.supabaseUrl.isBlank() || s.supabaseKey.isBlank()) throw IllegalStateException("Sync is not configured")
            val session = validSession(s.supabaseUrl, s.supabaseKey)
            SupabaseConfig(s.supabaseUrl, s.supabaseKey, session.accessToken, session.userId)
        }
    }

    private suspend fun validSession(url: String, apiKey: String): AuthSession = refreshMutex.withLock {
        val session = store.current() ?: throw NotSignedInException("Not signed in")
        if (!session.needsRefresh(System.currentTimeMillis())) return@withLock session
        api.refresh(url, apiKey, session).fold(
            onSuccess = { fresh ->
                store.save(fresh)
                fresh
            },
            onFailure = { error ->
                if (error is AuthException && error.code in 400..403) {
                    Timber.w("Session refresh rejected (%d), signing out", error.code)
                    store.clear()
                    throw NotSignedInException("Signed out: ${error.message}")
                }
                throw error
            }
        )
    }

    private suspend fun adopt(session: AuthSession) {
        val previous = store.current()
        store.save(session)
        if (previous?.userId != session.userId) {
            podcastDao.markAllDirty()
            episodeDao.markAllStatesDirty()
            manualSkipDao.markAllDirty()
            settingsRepository.markDirty()
            Timber.i("Signed in as %s; full push scheduled", session.email)
        }
    }
}
