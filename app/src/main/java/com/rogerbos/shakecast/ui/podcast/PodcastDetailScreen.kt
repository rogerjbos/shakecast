package com.rogerbos.shakecast.ui.podcast

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rogerbos.shakecast.R
import com.rogerbos.shakecast.domain.model.Episode
import com.rogerbos.shakecast.ui.components.EpisodeRow
import com.rogerbos.shakecast.ui.components.PodcastArtwork
import com.rogerbos.shakecast.ui.components.stripHtml

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastDetailScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    viewModel: PodcastDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmUnsubscribe by remember { mutableStateOf(false) }
    var menuEpisode by remember { mutableStateOf<Episode?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(state.podcast?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back)) }
            },
            actions = {
                TextButton(onClick = { confirmUnsubscribe = true }) { Text(stringResource(R.string.podcast_unsubscribe)) }
            }
        )
        PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    state.podcast?.let { podcast ->
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            PodcastArtwork(modifier = Modifier.size(96.dp), url = podcast.artworkUrl, contentDescription = null)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(podcast.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    stripHtml(podcast.description),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 5,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        ToggleRow(
                            label = stringResource(R.string.podcast_auto_skip_ads),
                            checked = podcast.autoSkipAds,
                            onCheckedChange = viewModel::setAutoSkipAds
                        )
                        ToggleRow(
                            label = stringResource(R.string.podcast_auto_download),
                            checked = podcast.autoDownload,
                            onCheckedChange = viewModel::setAutoDownload
                        )
                        HorizontalDivider()
                    }
                }
                items(state.episodes, key = { it.id }) { episode ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EpisodeRow(
                            modifier = Modifier.weight(1f),
                            episode = episode,
                            isCurrent = state.player.episodeId == episode.id,
                            isPlaying = state.player.isPlaying,
                            showPodcastTitle = false,
                            onPlayPause = { viewModel.playOrPause(episode) },
                            onDownload = { viewModel.download(episode) },
                            onOpen = { viewModel.playOrPause(episode); onOpenNowPlaying() }
                        )
                        IconButton(onClick = { menuEpisode = episode }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_more))
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    menuEpisode?.let { episode ->
        DropdownMenu(expanded = true, onDismissRequest = { menuEpisode = null }) {
            DropdownMenuItem(
                text = { Text(stringResource(if (episode.isPlayed) R.string.episode_mark_unplayed else R.string.episode_mark_played)) },
                onClick = { viewModel.togglePlayed(episode); menuEpisode = null }
            )
            if (episode.isDownloaded) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.episode_delete_download)) },
                    onClick = { viewModel.deleteDownload(episode); menuEpisode = null }
                )
            } else {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.episode_download_now)) },
                    onClick = { viewModel.download(episode); menuEpisode = null }
                )
            }
        }
    }

    if (confirmUnsubscribe) {
        AlertDialog(
            onDismissRequest = { confirmUnsubscribe = false },
            title = { Text(stringResource(R.string.podcast_unsubscribe)) },
            text = { Text(stringResource(R.string.podcast_unsubscribe_body)) },
            confirmButton = {
                TextButton(onClick = { confirmUnsubscribe = false; viewModel.unsubscribe(onBack) }) {
                    Text(stringResource(R.string.podcast_unsubscribe))
                }
            },
            dismissButton = { TextButton(onClick = { confirmUnsubscribe = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun ToggleRow(modifier: Modifier = Modifier, label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
