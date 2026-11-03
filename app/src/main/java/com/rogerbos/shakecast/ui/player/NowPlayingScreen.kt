package com.rogerbos.shakecast.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rogerbos.shakecast.R
import com.rogerbos.shakecast.playback.PlaybackCommands
import com.rogerbos.shakecast.domain.model.AdSource
import com.rogerbos.shakecast.ui.components.AdMarkerSeekBar
import com.rogerbos.shakecast.ui.components.BigSkipButton
import com.rogerbos.shakecast.ui.components.PodcastArtwork
import com.rogerbos.shakecast.ui.components.formatClock
import kotlinx.coroutines.delay
import java.util.Locale

private val SPEEDS = listOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    viewModel: NowPlayingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player = state.player
    var showSleepDialog by remember { mutableStateOf(false) }
    var showForgetDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(player.podcastTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.cd_back)) }
            },
            actions = {
                SleepTimerAction(
                    endAtMs = player.sleepEndAtMs,
                    endOfEpisode = player.sleepEndOfEpisode,
                    onClick = { showSleepDialog = true }
                )
            }
        )
        Column(
            modifier = Modifier.weight(0.62f).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PodcastArtwork(
                modifier = Modifier.weight(1f, fill = false).fillMaxWidth(0.6f).aspectRatio(1f),
                url = player.artworkUri,
                contentDescription = null,
                corner = 16.dp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                player.title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            AdMarkerSeekBar(
                positionMs = player.positionMs,
                durationMs = player.durationMs,
                adBlocks = state.adBlocks + state.manualSkips,
                onSeek = viewModel::seekTo
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatClock(player.positionMs), style = MaterialTheme.typography.labelMedium)
                Text(formatClock(player.durationMs), style = MaterialTheme.typography.labelMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledIconButton(onClick = viewModel::togglePlayPause, modifier = Modifier.size(64.dp)) {
                    Icon(
                        imageVector = if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(if (player.isPlaying) R.string.cd_pause else R.string.cd_play),
                        modifier = Modifier.size(36.dp)
                    )
                }
                val nextSpeed = SPEEDS[(SPEEDS.indexOf(player.speed).takeIf { it >= 0 }?.plus(1) ?: 0) % SPEEDS.size]
                AssistChip(
                    onClick = { viewModel.setSpeed(nextSpeed) },
                    label = { Text(String.format(Locale.US, "%.2fx", player.speed).replace(".00x", "x").replace("0x", "x")) }
                )
                FilterChip(
                    selected = state.settings.autoSkipAdsEnabled,
                    onClick = viewModel::toggleAutoSkip,
                    label = { Text(adLabel(state.adSource, state.adBlocks.size)) }
                )
                if (state.manualSkips.isNotEmpty()) {
                    AssistChip(
                        onClick = { showForgetDialog = true },
                        label = { Text(stringResource(R.string.skips_remembered, state.manualSkips.size), maxLines = 1) }
                    )
                }
            }
        }
        BigSkipButton(
            modifier = Modifier.weight(0.38f).padding(horizontal = 16.dp, vertical = 12.dp),
            forwardSec = state.settings.skipForwardSec,
            backSec = state.settings.skipBackSec,
            onForward = viewModel::skipForward,
            onBack = viewModel::skipBack
        )
    }

    if (showSleepDialog) {
        SleepTimerDialog(
            active = player.sleepEndAtMs != null || player.sleepEndOfEpisode,
            onPick = { minutes -> viewModel.setSleepTimer(minutes); showSleepDialog = false },
            onDismiss = { showSleepDialog = false }
        )
    }
    if (showForgetDialog) {
        AlertDialog(
            onDismissRequest = { showForgetDialog = false },
            title = { Text(stringResource(R.string.skips_forget)) },
            text = { Text(stringResource(R.string.skips_forget_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.forgetManualSkips(); showForgetDialog = false }) { Text(stringResource(R.string.action_forget)) }
            },
            dismissButton = { TextButton(onClick = { showForgetDialog = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

@Composable
private fun SleepTimerAction(endAtMs: Long?, endOfEpisode: Boolean, onClick: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endAtMs) {
        while (endAtMs != null) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (endAtMs != null) {
            Text(stringResource(R.string.sleep_timer_remaining, formatClock((endAtMs - now).coerceAtLeast(0))), style = MaterialTheme.typography.labelMedium)
        } else if (endOfEpisode) {
            Text(stringResource(R.string.sleep_timer_end_of_episode_short), style = MaterialTheme.typography.labelMedium)
        }
        IconButton(onClick = onClick) {
            Icon(
                Icons.Filled.Bedtime,
                contentDescription = stringResource(R.string.sleep_timer),
                tint = if (endAtMs != null || endOfEpisode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SleepTimerDialog(active: Boolean, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sleep_timer)) },
        text = {
            Column {
                listOf(15, 30, 45, 60).forEach { minutes ->
                    TextButton(onClick = { onPick(minutes) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.sleep_timer_minutes, minutes)) }
                }
                TextButton(onClick = { onPick(PlaybackCommands.SLEEP_END_OF_EPISODE) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.sleep_timer_end_of_episode))
                }
                if (active) {
                    TextButton(onClick = { onPick(0) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.sleep_timer_off)) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun adLabel(source: AdSource, count: Int): String = when (source) {
    AdSource.SKIPLIST, AdSource.TRANSCRIPT -> stringResource(R.string.ads_known, count)
    AdSource.MISALIGNED -> stringResource(R.string.ads_misaligned)
    AdSource.NONE -> stringResource(R.string.ads_unknown)
}
