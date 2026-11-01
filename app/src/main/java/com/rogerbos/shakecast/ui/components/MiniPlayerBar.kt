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
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rogerbos.shakecast.R
import com.rogerbos.shakecast.playback.PlayerSnapshot

@Composable
fun MiniPlayerBar(
    modifier: Modifier = Modifier,
    snapshot: PlayerSnapshot,
    onTogglePlay: () -> Unit,
    onSkipForward: () -> Unit,
    onOpen: () -> Unit
) {
    Surface(modifier = modifier.fillMaxWidth(), tonalElevation = 3.dp) {
        Column {
            val progress = if (snapshot.durationMs > 0) (snapshot.positionMs.toFloat() / snapshot.durationMs).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PodcastArtwork(modifier = Modifier.size(44.dp), url = snapshot.artworkUri, contentDescription = null, corner = 6.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(snapshot.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        snapshot.podcastTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onTogglePlay, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = if (snapshot.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(if (snapshot.isPlaying) R.string.cd_pause else R.string.cd_play)
                    )
                }
                IconButton(onClick = onSkipForward, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Forward30, contentDescription = stringResource(R.string.cd_skip_forward))
                }
            }
        }
    }
}
