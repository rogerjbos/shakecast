package com.rogerbos.shakecast.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rogerbos.shakecast.R
import com.rogerbos.shakecast.domain.model.DownloadState
import com.rogerbos.shakecast.domain.model.Episode

@Composable
fun EpisodeRow(
    modifier: Modifier = Modifier,
    episode: Episode,
    isCurrent: Boolean,
    isPlaying: Boolean,
    showPodcastTitle: Boolean,
    onPlayPause: () -> Unit,
    onDownload: () -> Unit,
    onOpen: () -> Unit
) {
    Column(modifier = modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PodcastArtwork(
                modifier = Modifier.size(56.dp),
                url = episode.podcastArtworkUrl,
                contentDescription = null
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (showPodcastTitle) {
                    Text(
                        text = episode.podcastTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = listOf(formatDate(episode.pubDateMs), formatDurationShort(episode.durationSec))
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            DownloadIndicator(episode = episode, onDownload = onDownload)
            FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = if (isCurrent && isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(if (isCurrent && isPlaying) R.string.cd_pause else R.string.cd_play)
                )
            }
        }
        val duration = episode.durationSec?.let { it * 1000L } ?: 0L
        if (!episode.isPlayed && episode.positionMs > 0 && duration > 0) {
            LinearProgressIndicator(
                progress = { (episode.positionMs.toFloat() / duration).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun DownloadIndicator(modifier: Modifier = Modifier, episode: Episode, onDownload: () -> Unit) {
    when {
        episode.isPlayed -> Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = stringResource(R.string.cd_played),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(end = 8.dp).size(24.dp)
        )
        episode.downloadState == DownloadState.DOWNLOADED -> Icon(
            imageVector = Icons.Filled.DownloadDone,
            contentDescription = stringResource(R.string.cd_downloaded),
            tint = MaterialTheme.colorScheme.primary,
            modifier = modifier.padding(end = 8.dp).size(24.dp)
        )
        episode.downloadState == DownloadState.DOWNLOADING || episode.downloadState == DownloadState.QUEUED ->
            CircularProgressIndicator(modifier = modifier.padding(end = 8.dp).size(24.dp), strokeWidth = 2.dp)
        episode.downloadState == DownloadState.FAILED -> IconButton(onClick = onDownload, modifier = modifier) {
            Icon(Icons.Filled.Error, contentDescription = stringResource(R.string.cd_download_failed), tint = MaterialTheme.colorScheme.error)
        }
        else -> IconButton(onClick = onDownload, modifier = modifier) {
            Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.cd_download))
        }
    }
}
