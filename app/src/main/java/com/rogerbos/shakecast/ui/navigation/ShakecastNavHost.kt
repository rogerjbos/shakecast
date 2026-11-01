package com.rogerbos.shakecast.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.rogerbos.shakecast.R
import com.rogerbos.shakecast.playback.PlayerConnection
import com.rogerbos.shakecast.ui.components.MiniPlayerBar
import com.rogerbos.shakecast.ui.home.HomeScreen
import com.rogerbos.shakecast.ui.player.NowPlayingScreen
import com.rogerbos.shakecast.ui.podcast.PodcastDetailScreen
import com.rogerbos.shakecast.ui.search.SearchScreen
import com.rogerbos.shakecast.ui.settings.SettingsScreen

@Composable
fun ShakecastNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    playerConnection: PlayerConnection
) {
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val snapshot by playerConnection.state.collectAsStateWithLifecycle()
    val onNowPlaying = destination?.hasRoute<Route.NowPlaying>() == true
    val openNowPlaying: () -> Unit = { navController.navigate(Route.NowPlaying) { launchSingleTop = true } }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (!onNowPlaying) {
                Column {
                    if (snapshot.episodeId != null) {
                        MiniPlayerBar(
                            snapshot = snapshot,
                            onTogglePlay = playerConnection::togglePlayPause,
                            onSkipForward = playerConnection::skipForward,
                            onOpen = openNowPlaying
                        )
                    }
                    NavigationBar {
                        NavigationBarItem(
                            selected = destination?.hasRoute<Route.Home>() == true,
                            onClick = { navController.navigate(Route.Home) { popUpTo(Route.Home) { inclusive = false }; launchSingleTop = true } },
                            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                            label = { Text(stringResource(R.string.nav_home)) }
                        )
                        NavigationBarItem(
                            selected = destination?.hasRoute<Route.Search>() == true,
                            onClick = { navController.navigate(Route.Search) { popUpTo(Route.Home); launchSingleTop = true } },
                            icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            label = { Text(stringResource(R.string.nav_search)) }
                        )
                        NavigationBarItem(
                            selected = destination?.hasRoute<Route.Settings>() == true,
                            onClick = { navController.navigate(Route.Settings) { popUpTo(Route.Home); launchSingleTop = true } },
                            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                            label = { Text(stringResource(R.string.nav_settings)) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Route.Home,
            modifier = Modifier.padding(padding)
        ) {
            composable<Route.Home> {
                HomeScreen(
                    onOpenPodcast = { id -> navController.navigate(Route.PodcastDetail(id)) },
                    onOpenSearch = { navController.navigate(Route.Search) { launchSingleTop = true } },
                    onOpenNowPlaying = openNowPlaying
                )
            }
            composable<Route.Search> { SearchScreen() }
            composable<Route.PodcastDetail> {
                PodcastDetailScreen(onBack = { navController.popBackStack() }, onOpenNowPlaying = openNowPlaying)
            }
            composable<Route.NowPlaying> { NowPlayingScreen(onBack = { navController.popBackStack() }) }
            composable<Route.Settings> { SettingsScreen() }
        }
    }
}
