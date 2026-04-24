package com.dev1lroot.aapps.observer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferencesManager: PreferencesManager,
    onNavigateBack: () -> Unit
) {
    var rtmpUrl by remember { mutableStateOf(preferencesManager.rtmpUrl) }
    var videoWidth by remember { mutableStateOf(preferencesManager.videoWidth.toString()) }
    var videoHeight by remember { mutableStateOf(preferencesManager.videoHeight.toString()) }
    var fps by remember { mutableStateOf(preferencesManager.fps.toString()) }
    var videoBitrate by remember { mutableStateOf(preferencesManager.videoBitrateKbps.toString()) }
    var audioBitrate by remember { mutableStateOf(preferencesManager.audioBitrateKbps.toString()) }
    var saved by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionHeader("RTMP Server")

            OutlinedTextField(
                value = rtmpUrl,
                onValueChange = { rtmpUrl = it; saved = false },
                label = { Text("RTMP URL") },
                placeholder = { Text("rtmp://server:1935/live/stream") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = rtmpUrl.isNotEmpty() &&
                    !rtmpUrl.startsWith("rtmp://") && !rtmpUrl.startsWith("rtmps://"),
                supportingText = {
                    if (rtmpUrl.isNotEmpty() &&
                        !rtmpUrl.startsWith("rtmp://") && !rtmpUrl.startsWith("rtmps://")
                    ) {
                        Text("Must start with rtmp:// or rtmps://")
                    }
                }
            )

            HorizontalDivider()
            SectionHeader("Video")

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = videoWidth,
                    onValueChange = { videoWidth = it.filter(Char::isDigit); saved = false },
                    label = { Text("Width px") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(
                    value = videoHeight,
                    onValueChange = { videoHeight = it.filter(Char::isDigit); saved = false },
                    label = { Text("Height px") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }

            OutlinedTextField(
                value = fps,
                onValueChange = { fps = it.filter(Char::isDigit); saved = false },
                label = { Text("FPS") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )

            OutlinedTextField(
                value = videoBitrate,
                onValueChange = { videoBitrate = it.filter(Char::isDigit); saved = false },
                label = { Text("Video bitrate (kbps)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )

            HorizontalDivider()
            SectionHeader("Audio")

            OutlinedTextField(
                value = audioBitrate,
                onValueChange = { audioBitrate = it.filter(Char::isDigit); saved = false },
                label = { Text("Audio bitrate (kbps)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    preferencesManager.rtmpUrl = rtmpUrl
                    preferencesManager.videoWidth =
                        videoWidth.toIntOrNull() ?: PreferencesManager.DEFAULT_WIDTH
                    preferencesManager.videoHeight =
                        videoHeight.toIntOrNull() ?: PreferencesManager.DEFAULT_HEIGHT
                    preferencesManager.fps =
                        fps.toIntOrNull() ?: PreferencesManager.DEFAULT_FPS
                    preferencesManager.videoBitrateKbps =
                        videoBitrate.toIntOrNull() ?: PreferencesManager.DEFAULT_VIDEO_BITRATE
                    preferencesManager.audioBitrateKbps =
                        audioBitrate.toIntOrNull() ?: PreferencesManager.DEFAULT_AUDIO_BITRATE
                    saved = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Settings")
            }

            if (saved) {
                Text(
                    text = "Saved. Video/audio changes apply on next stream start.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp)
    )
}
