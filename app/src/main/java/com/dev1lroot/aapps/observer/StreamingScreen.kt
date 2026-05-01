package com.dev1lroot.aapps.observer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamingScreen(
    preferencesManager: PreferencesManager,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current

    var streamingState by remember { mutableStateOf<StreamingState>(StreamingState.Idle) }
    var statusMessage by remember { mutableStateOf("") }
    var timestamp by remember { mutableStateOf("") }
    // Camera + audio are required to stream; location is optional (GPS overlay).
    var permissionsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionsGranted =
            permissions[Manifest.permission.CAMERA] == true &&
            permissions[Manifest.permission.RECORD_AUDIO] == true
    }

    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val streamer = remember {
        FFmpegStreamer(context) { state, msg ->
            mainHandler.post {
                streamingState = state
                statusMessage = msg
            }
        }
    }
    val textureViewRef = remember { mutableStateOf<TextureView?>(null) }

    LaunchedEffect(streamingState) {
        if (streamingState is StreamingState.Streaming) {
            while (true) {
                timestamp = SimpleDateFormat("yyyy-MM-dd  HH:mm:ss", Locale.getDefault()).format(Date())
                delay(1000)
            }
        } else {
            timestamp = ""
        }
    }

    DisposableEffect(Unit) {
        onDispose { streamer.release() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Observer") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(androidx.compose.ui.graphics.Color.Black)
            ) {
                if (permissionsGranted) {
                    AndroidView(
                        factory = { ctx ->
                            TextureView(ctx).also { tv ->
                                textureViewRef.value = tv
                                tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(
                                        surface: SurfaceTexture, width: Int, height: Int
                                    ) {
                                        streamer.openCamera(
                                            tv,
                                            preferencesManager.videoWidth,
                                            preferencesManager.videoHeight,
                                        )
                                    }
                                    override fun onSurfaceTextureSizeChanged(
                                        surface: SurfaceTexture, width: Int, height: Int
                                    ) {
                                        streamer.configureTransform(tv)
                                    }
                                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                                        if (streamer.isStreaming) streamer.stopStream(tv)
                                        return true
                                    }
                                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Camera and microphone access required",
                            color = androidx.compose.ui.graphics.Color.White
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.CAMERA,
                                    Manifest.permission.RECORD_AUDIO,
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                )
                            )
                        }) { Text("Grant Permissions") }
                    }
                }

                if (timestamp.isNotEmpty()) {
                    Text(
                        text = timestamp,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .background(
                                androidx.compose.ui.graphics.Color(0x99000000),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        color = androidx.compose.ui.graphics.Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    )
                }

                if (statusMessage.isNotEmpty()) {
                    Text(
                        text = statusMessage,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp),
                        color = when (streamingState) {
                            is StreamingState.Streaming -> androidx.compose.ui.graphics.Color.Green
                            is StreamingState.Error -> androidx.compose.ui.graphics.Color.Red
                            else -> androidx.compose.ui.graphics.Color.Yellow
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                val isStreaming = streamingState is StreamingState.Streaming
                val isConnecting = streamingState is StreamingState.Connecting

                Button(
                    onClick = {
                        val tv = textureViewRef.value ?: return@Button
                        if (isStreaming || isConnecting) {
                            streamer.stopStream(tv)
                            streamingState = StreamingState.Idle
                            statusMessage = ""
                        } else {
                            val url = preferencesManager.rtmpUrl
                            if (!url.startsWith("rtmp://") && !url.startsWith("rtmps://")) {
                                statusMessage = "Invalid URL — check Settings"
                                streamingState = StreamingState.Error("Bad URL")
                                return@Button
                            }
                            streamer.overlayConfig = OverlayConfig(
                                showTimestamp = preferencesManager.showTimestamp,
                                showGps = preferencesManager.showGps,
                                showCompass = preferencesManager.showCompass,
                                showCrosshair = preferencesManager.showCrosshair,
                            )
                            streamer.startStream(
                                tv,
                                url,
                                preferencesManager.fps,
                                preferencesManager.videoBitrateKbps,
                                preferencesManager.audioBitrateKbps,
                            )
                        }
                    },
                    enabled = permissionsGranted,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isStreaming || isConnecting)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .height(56.dp)
                        .defaultMinSize(minWidth = 180.dp)
                ) {
                    Text(
                        text = when {
                            isConnecting -> "Connecting…"
                            isStreaming -> "Stop Stream"
                            else -> "Start Stream"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}
