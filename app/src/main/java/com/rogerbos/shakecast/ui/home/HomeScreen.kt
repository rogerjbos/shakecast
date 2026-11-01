package com.rogerbos.shakecast.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rogerbos.shakecast.R
import com.rogerbos.shakecast.ui.components.EpisodeRow
import com.rogerbos.shakecast.ui.components.PodcastArtwork

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onOpenPodcast: (Long) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.app_name)) })
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize()
        ) {
            if (state.podcasts.isEmpty()) {
                EmptyHome(onOpenSearch = onOpenSearch)
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                            items(state.podcasts, key = { it.id }) { podcast ->
                                Column(
                                    modifier = Modifier.width(96.dp).padding(end = 12.dp).clickable { onOpenPodcast(podcast.id) },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    PodcastArtwork(modifier = Modifier.size(84.dp), url = podcast.artworkUrl, contentDescription = podcast.title)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        podcast.title,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        HorizontalDivider()
                        Text(
                            text = stringResource(R.string.home_up_next),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(state.episodes, key = { it.id }) { episode ->
                        EpisodeRow(
                            episode = episode,
                            isCurrent = state.player.episodeId == episode.id,
                            isPlaying = state.player.isPlaying,
                            showPodcastTitle = true,
                            onPlayPause = { viewModel.playOrPause(episode) },
                            onDownload = { viewModel.download(episode) },
                            onOpen = {
                                if (state.player.episodeId == episode.id) onOpenNowPlaying() else viewModel.playOrPause(episode).also { onOpenNowPlaying() }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHome(modifier: Modifier = Modifier, onOpenSearch: () -> Unit) {
    Box(modifier = modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.home_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onOpenSearch) { Text(stringResource(R.string.home_find_podcasts)) }
        }
    }
}
