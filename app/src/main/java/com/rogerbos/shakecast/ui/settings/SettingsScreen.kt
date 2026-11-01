package com.rogerbos.shakecast.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rogerbos.shakecast.R
import com.rogerbos.shakecast.data.settings.ShakeSensitivity
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state.settings

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.settings_title)) })
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            SectionHeader(stringResource(R.string.settings_section_skipping))
            StepperRow(
                label = stringResource(R.string.settings_skip_forward),
                value = s.skipForwardSec,
                range = 5..120,
                step = 5,
                onChange = { v -> viewModel.update { it.copy(skipForwardSec = v) } }
            )
            StepperRow(
                label = stringResource(R.string.settings_skip_back),
                value = s.skipBackSec,
                range = 5..60,
                step = 5,
                onChange = { v -> viewModel.update { it.copy(skipBackSec = v) } }
            )
            SwitchRow(stringResource(R.string.settings_shake_enabled), s.shakeEnabled) { v -> viewModel.update { it.copy(shakeEnabled = v) } }
            Text(stringResource(R.string.settings_shake_sensitivity), modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                ShakeSensitivity.entries.forEach { level ->
                    FilterChip(
                        selected = s.shakeSensitivity == level,
                        onClick = { viewModel.update { it.copy(shakeSensitivity = level) } },
                        label = { Text(sensitivityLabel(level)) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
            if (s.shakeSensitivity == ShakeSensitivity.HIGH) {
                Text(
                    stringResource(R.string.settings_shake_high_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = viewModel::toggleShakeTest) {
                    Text(stringResource(if (state.shakeTest.active) R.string.settings_shake_test_stop else R.string.settings_shake_test_start))
                }
                Spacer(Modifier.width(16.dp))
                if (state.shakeTest.active) {
                    Text(
                        stringResource(
                            R.string.settings_shake_test_readout,
                            String.format(Locale.US, "%.1f", state.shakeTest.magnitudeG),
                            state.shakeTest.peaks,
                            state.shakeTest.shakes
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            SwitchRow(stringResource(R.string.settings_volume_chord), s.volumeChordEnabled) { v -> viewModel.update { it.copy(volumeChordEnabled = v) } }
            Text(
                stringResource(R.string.settings_volume_chord_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            SwitchRow(stringResource(R.string.settings_haptics), s.hapticsEnabled) { v -> viewModel.update { it.copy(hapticsEnabled = v) } }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(R.string.settings_section_ads))
            SwitchRow(stringResource(R.string.settings_auto_skip_ads), s.autoSkipAdsEnabled) { v -> viewModel.update { it.copy(autoSkipAdsEnabled = v) } }
            var baseUrl by remember(s.skipListBaseUrl) { mutableStateOf(s.skipListBaseUrl) }
            LaunchedEffect(baseUrl) {
                if (baseUrl != s.skipListBaseUrl) {
                    kotlinx.coroutines.delay(600)
                    viewModel.update { it.copy(skipListBaseUrl = baseUrl.trim()) }
                }
            }
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text(stringResource(R.string.settings_skiplist_url)) },
                placeholder = { Text(stringResource(R.string.settings_skiplist_url_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = viewModel::testConnection, enabled = s.skipListBaseUrl.isNotBlank()) {
                    Text(stringResource(R.string.settings_test_connection))
                }
                Spacer(Modifier.width(16.dp))
                state.connectionResult?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(R.string.settings_section_downloads))
            SwitchRow(stringResource(R.string.settings_wifi_only), s.downloadWifiOnly) { v -> viewModel.update { it.copy(downloadWifiOnly = v) } }
            StepperRow(
                label = stringResource(R.string.settings_keep_per_show),
                value = s.episodesToKeepPerShow,
                range = 1..10,
                step = 1,
                onChange = { v -> viewModel.update { it.copy(episodesToKeepPerShow = v) } }
            )
            SwitchRow(stringResource(R.string.settings_auto_delete), s.autoDeleteWhenPlayed) { v -> viewModel.update { it.copy(autoDeleteWhenPlayed = v) } }
            StepperRow(
                label = stringResource(R.string.settings_refresh_interval),
                value = s.refreshIntervalHours,
                range = 1..24,
                step = 1,
                onChange = { v -> viewModel.update { it.copy(refreshIntervalHours = v) } }
            )
            Button(onClick = viewModel::refreshNow, modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.settings_refresh_now))
            }
        }
    }
}

@Composable
private fun sensitivityLabel(level: ShakeSensitivity): String = when (level) {
    ShakeSensitivity.LOW -> stringResource(R.string.sensitivity_low)
    ShakeSensitivity.MEDIUM -> stringResource(R.string.sensitivity_medium)
    ShakeSensitivity.HIGH -> stringResource(R.string.sensitivity_high)
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun StepperRow(label: String, value: Int, range: IntRange, step: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = { onChange((value - step).coerceIn(range)) }, enabled = value - step >= range.first) { Text("-") }
        Text(value.toString(), modifier = Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { onChange((value + step).coerceIn(range)) }, enabled = value + step <= range.last) { Text("+") }
    }
}
