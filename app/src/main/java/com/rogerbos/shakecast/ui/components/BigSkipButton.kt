package com.rogerbos.shakecast.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rogerbos.shakecast.R

/** Glove-friendly skip controls: a small back strip above a huge forward button. */
@Composable
fun BigSkipButton(
    modifier: Modifier = Modifier,
    forwardSec: Int,
    backSec: Int,
    onForward: () -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().weight(0.3f).padding(bottom = 8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.filledTonalButtonColors()
        ) {
            Icon(Icons.Filled.FastRewind, contentDescription = null)
            Text(
                text = stringResource(R.string.skip_back_label, backSec),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Button(
            onClick = onForward,
            modifier = Modifier.fillMaxSize().weight(0.7f),
            shape = RoundedCornerShape(28.dp)
        ) {
            Icon(Icons.Filled.FastForward, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text(
                text = stringResource(R.string.skip_forward_label, forwardSec),
                style = MaterialTheme.typography.displaySmall
            )
        }
    }
}
