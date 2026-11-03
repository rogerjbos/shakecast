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
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import com.rogerbos.shakecast.BuildConfig
import com.rogerbos.shakecast.data.crash.CrashReporting
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rogerbos.shakecast.R
import com.rogerbos.shakecast.data.settings.SettingsRepository
import com.rogerbos.shakecast.data.settings.ShakeSensitivity
import kotlinx.coroutines.delay
import android.text.format.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state.settings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val opmlStrings = SettingsViewModel.OpmlStrings(
        empty = stringResource(R.string.opml_empty),
        error = { stringResource_error(context, it) },
        progress = { done, total -> context.getString(R.string.opml_progress, done, total) },
        done = { added, skipped, failed -> context.getString(R.string.opml_done, added, skipped, failed) }
    )
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val reader = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader() }.getOrNull() }
            if (reader == null) viewModel.setOpmlStatus(context.getString(R.string.opml_error, "cannot open file")) else viewModel.importOpml(reader, opmlStrings)
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-opml")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = viewModel.exportOpmlText()
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } != null }.getOrDefault(false)
            }
            viewModel.setOpmlStatus(context.getString(if (ok) R.string.opml_exported else R.string.opml_export_failed))
        }
    }

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
            SwitchRow(stringResource(R.string.settings_shake_chapter), s.shakeToNextChapter) { v -> viewModel.update { it.copy(shakeToNextChapter = v) } }
            HelpText(stringResource(R.string.settings_shake_chapter_help))

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(R.string.settings_section_playback))
            SwitchRow(stringResource(R.string.settings_auto_play_next), s.autoPlayNext) { v -> viewModel.update { it.copy(autoPlayNext = v) } }
            HelpText(stringResource(R.string.settings_auto_play_next_help))
            SwitchRow(stringResource(R.string.settings_trim_silence), s.trimSilence) { v -> viewModel.update { it.copy(trimSilence = v) } }
            HelpText(stringResource(R.string.settings_trim_silence_help))
            Text(stringResource(R.string.settings_volume_boost), modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                listOf(0, 3, 6, 9).forEach { db ->
                    FilterChip(
                        selected = s.volumeBoostDb == db,
                        onClick = { viewModel.update { it.copy(volumeBoostDb = db) } },
                        label = { Text(if (db == 0) stringResource(R.string.settings_volume_boost_off) else stringResource(R.string.settings_volume_boost_db, db)) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
            HelpText(stringResource(R.string.settings_volume_boost_help))

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(R.string.settings_section_ads))
            SwitchRow(stringResource(R.string.settings_auto_skip_ads), s.autoSkipAdsEnabled) { v -> viewModel.update { it.copy(autoSkipAdsEnabled = v) } }
            HelpText(stringResource(R.string.settings_auto_skip_ads_help))
            SwitchRow(stringResource(R.string.settings_remember_skips), s.rememberManualSkips) { v -> viewModel.update { it.copy(rememberManualSkips = v) } }
            HelpText(stringResource(R.string.settings_remember_skips_help))

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

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(R.string.settings_section_subscriptions))
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.opml_import)) }
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = { exportLauncher.launch("shakecast-subscriptions.opml") }) { Text(stringResource(R.string.opml_export)) }
            }
            state.opmlStatus?.let { HelpText(it) }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(R.string.settings_section_sync))
            Text(
                stringResource(R.string.settings_sync_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            SwitchRow(stringResource(R.string.settings_sync_enabled), s.syncEnabled) { v -> viewModel.update { it.copy(syncEnabled = v) } }
            DebouncedTextField(
                value = s.supabaseUrl,
                label = stringResource(R.string.settings_supabase_url),
                placeholder = stringResource(R.string.settings_supabase_url_hint),
                onCommit = { v -> viewModel.update { it.copy(supabaseUrl = v) } }
            )
            DebouncedTextField(
                value = s.supabaseKey,
                label = stringResource(R.string.settings_supabase_key),
                placeholder = stringResource(R.string.settings_supabase_key_hint),
                onCommit = { v -> viewModel.update { it.copy(supabaseKey = v) } }
            )
            val session = state.auth.session
            if (session == null) {
                SignInForm(
                    enabled = !state.auth.busy && s.supabaseUrl.isNotBlank() && s.supabaseKey.isNotBlank(),
                    onSignIn = viewModel::signIn,
                    onSignUp = viewModel::signUp
                )
            } else {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.settings_signed_in_as, session.email),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(onClick = viewModel::signOut, enabled = !state.auth.busy) { Text(stringResource(R.string.settings_sign_out)) }
                }
            }
            when (val message = state.auth.message) {
                AuthMessage.ConfirmEmail -> HelpText(stringResource(R.string.settings_auth_confirm_email))
                AuthMessage.AccountDeleted -> HelpText(stringResource(R.string.settings_account_deleted))
                is AuthMessage.Error -> HelpText(message.text)
                null -> Unit
            }
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = viewModel::testSync, enabled = session != null) {
                    Text(stringResource(R.string.settings_test_connection))
                }
                Spacer(Modifier.width(12.dp))
                state.syncTestResult?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = viewModel::syncNow, enabled = s.syncEnabled && session != null && !state.syncing) {
                    Text(stringResource(if (state.syncing) R.string.settings_syncing else R.string.settings_sync_now))
                }
                Spacer(Modifier.width(16.dp))
                Text(syncStatusText(state.syncStatus), style = MaterialTheme.typography.bodySmall)
            }
            if (session != null) {
                var confirmDelete by remember { mutableStateOf(false) }
                TextButton(
                    onClick = { confirmDelete = true },
                    enabled = !state.auth.busy,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) { Text(stringResource(R.string.settings_delete_account), color = MaterialTheme.colorScheme.error) }
                if (confirmDelete) {
                    AlertDialog(
                        onDismissRequest = { confirmDelete = false },
                        title = { Text(stringResource(R.string.settings_delete_account)) },
                        text = { Text(stringResource(R.string.settings_delete_account_body)) },
                        confirmButton = {
                            TextButton(onClick = { confirmDelete = false; viewModel.deleteAccount() }) {
                                Text(stringResource(R.string.settings_delete_account_confirm), color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(R.string.settings_section_about))
            HelpText(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
            if (CrashReporting.available) {
                SwitchRow(stringResource(R.string.settings_crash_reports), s.crashReportsEnabled) { v -> viewModel.update { it.copy(crashReportsEnabled = v) } }
                HelpText(stringResource(R.string.settings_crash_reports_help))
            }
            TextButton(
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.privacy_policy_url)))) },
                modifier = Modifier.padding(horizontal = 8.dp)
            ) { Text(stringResource(R.string.settings_privacy_policy)) }
        }
    }
}

@Composable
private fun syncStatusText(status: SettingsRepository.SyncStatus): String = when {
    status.error != null -> stringResource(R.string.settings_sync_status_error, status.error)
    status.lastSyncAtMs != null -> stringResource(
        R.string.settings_sync_status_ok,
        DateFormat.getTimeFormat(LocalContext.current).format(Date(status.lastSyncAtMs))
    )
    else -> stringResource(R.string.settings_sync_status_never)
}

/** Email and password entry; the password lives only in this composable and is never stored. */
@Composable
private fun SignInForm(enabled: Boolean, onSignIn: (String, String) -> Unit, onSignUp: (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val ready = enabled && email.contains('@') && password.length >= 6
    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text(stringResource(R.string.settings_auth_email)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text(stringResource(R.string.settings_auth_password)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    )
    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = { onSignIn(email.trim(), password) }, enabled = ready) { Text(stringResource(R.string.settings_sign_in)) }
        Spacer(Modifier.width(12.dp))
        OutlinedButton(onClick = { onSignUp(email.trim(), password) }, enabled = ready) { Text(stringResource(R.string.settings_sign_up)) }
    }
    HelpText(stringResource(R.string.settings_auth_help))
}

/**
 * Text field that writes the trimmed value to settings 600 ms after typing stops.
 * The stored value only replaces what is on screen when it differs from the last
 * commit, so a write landing mid-typing cannot drop characters.
 */
@Composable
private fun DebouncedTextField(value: String, label: String, placeholder: String, onCommit: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    var lastCommitted by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        if (value != lastCommitted) {
            lastCommitted = value
            text = value
        }
    }
    LaunchedEffect(text) {
        val trimmed = text.trim()
        if (trimmed != lastCommitted) {
            delay(600)
            lastCommitted = trimmed
            onCommit(trimmed)
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

@Composable
private fun sensitivityLabel(level: ShakeSensitivity): String = when (level) {
    ShakeSensitivity.LOW -> stringResource(R.string.sensitivity_low)
    ShakeSensitivity.MEDIUM -> stringResource(R.string.sensitivity_medium)
    ShakeSensitivity.HIGH -> stringResource(R.string.sensitivity_high)
}

private fun stringResource_error(context: android.content.Context, message: String): String =
    context.getString(R.string.opml_error, message)

@Composable
private fun HelpText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
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
