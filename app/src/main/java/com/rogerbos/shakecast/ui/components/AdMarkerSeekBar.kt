package com.rogerbos.shakecast.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.rogerbos.shakecast.domain.model.AdBlock

/** Seek bar with detected ad blocks drawn as red spans over the track. */
@Composable
fun AdMarkerSeekBar(
    modifier: Modifier = Modifier,
    positionMs: Long,
    durationMs: Long,
    adBlocks: List<AdBlock>,
    onSeek: (Long) -> Unit
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val adColor = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)

    Box(modifier = modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
        Slider(
            value = dragging ?: fraction,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSeek((it * durationMs).toLong()) }
                dragging = null
            },
            modifier = Modifier.fillMaxWidth()
        )
        if (durationMs > 0 && adBlocks.isNotEmpty()) {
            // Drawn after the slider so it sits on top; it has no pointer handling, so drags still reach the slider.
            Canvas(modifier = Modifier.fillMaxWidth().height(6.dp).padding(horizontal = 12.dp)) {
                for (block in adBlocks) {
                    val startX = (block.start * 1000f / durationMs).toFloat().coerceIn(0f, 1f) * size.width
                    val endX = (block.end * 1000f / durationMs).toFloat().coerceIn(0f, 1f) * size.width
                    drawRoundRect(
                        color = adColor,
                        topLeft = Offset(startX, 0f),
                        size = Size((endX - startX).coerceAtLeast(3f), size.height),
                        cornerRadius = CornerRadius(3f, 3f)
                    )
                }
            }
        }
    }
}
