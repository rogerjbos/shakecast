package com.rogerbos.shakecast

import android.Manifest
import android.app.SearchManager
import android.provider.MediaStore
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.rogerbos.shakecast.data.refresh.RefreshScheduler
import com.rogerbos.shakecast.data.settings.SettingsRepository
import com.rogerbos.shakecast.data.sync.SyncScheduler
import com.rogerbos.shakecast.playback.PlayerConnection
import com.rogerbos.shakecast.ui.navigation.Route
import com.rogerbos.shakecast.ui.navigation.ShakecastNavHost
import com.rogerbos.shakecast.ui.theme.ShakecastTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    companion object {
        const val ACTION_OPEN_NOW_PLAYING = "com.rogerbos.shakecast.OPEN_NOW_PLAYING"
    }

    @Inject lateinit var playerConnection: PlayerConnection
    @Inject lateinit var refreshScheduler: RefreshScheduler
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var syncScheduler: SyncScheduler

    private var openNowPlayingRequests by mutableIntStateOf(0)
    private var pendingSearch by mutableStateOf<String?>(null)

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()
        lifecycleScope.launch {
            val settings = settingsRepository.settings.first()
            refreshScheduler.schedulePeriodic(settings.refreshIntervalHours)
            if (settings.syncEnabled) syncScheduler.schedulePeriodic()
        }
        intent?.let(::handleIntent)

        setContent {
            ShakecastTheme {
                val navController = rememberNavController()
                LaunchedEffect(openNowPlayingRequests) {
                    if (openNowPlayingRequests > 0) navController.navigate(Route.NowPlaying) { launchSingleTop = true }
                }
                LaunchedEffect(pendingSearch) {
                    pendingSearch?.let { query ->
                        navController.navigate(Route.Search(query)) { launchSingleTop = true }
                        pendingSearch = null
                    }
                }
                ShakecastNavHost(navController = navController, playerConnection = playerConnection)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Notification taps open Now Playing; a voice search opens the Search tab with the spoken words. */
    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            ACTION_OPEN_NOW_PLAYING -> openNowPlayingRequests++
            MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH -> {
                val query = intent.getStringExtra(SearchManager.QUERY).orEmpty().trim()
                if (query.isEmpty()) openNowPlayingRequests++ else pendingSearch = query
            }
        }
    }

    override fun onStart() {
        super.onStart()
        playerConnection.connect()
        lifecycleScope.launch { if (settingsRepository.settings.first().syncEnabled) syncScheduler.enqueueNow() }
    }

    override fun onStop() {
        playerConnection.release()
        super.onStop()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
