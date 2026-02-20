package io.github.kmpfacelink.sample

import android.content.Context
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.internal.PreviewableFaceTracker
import io.github.kmpfacelink.sample.ui.FpsOverlay
import io.github.kmpfacelink.sample.ui.SampleColors
import io.github.kmpfacelink.stream.ParameterConverter
import io.github.kmpfacelink.stream.StreamState
import io.github.kmpfacelink.stream.vts.VtsConfig
import io.github.kmpfacelink.stream.vts.VtsStreamClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val PREFS_NAME = "stream_settings"
private const val KEY_HOST = "host"
private const val KEY_PORT = "port"
private const val KEY_PLUGIN = "plugin_name"
private const val KEY_DEVELOPER = "developer_name"
private const val KEY_TOKEN = "auth_token"
private const val DEFAULT_HOST = "localhost"
private const val DEFAULT_PORT = "8001"
private const val DEFAULT_PLUGIN = "KMP-FaceLink"
private const val DEFAULT_DEVELOPER = "FaceLink Dev"

@Suppress("LongMethod")
@Composable
internal fun StreamTrackingScreen(
    activity: MainActivity,
    onModeChange: (TrackingMode) -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val state by activity.faceTracker.state.collectAsState()
    val trackingData by activity.faceTrackingDataState.collectAsState()
    val isTracking = state == TrackingState.TRACKING
    val context = LocalContext.current

    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var host by remember { mutableStateOf(prefs.getString(KEY_HOST, DEFAULT_HOST) ?: DEFAULT_HOST) }
    var port by remember { mutableStateOf(prefs.getString(KEY_PORT, DEFAULT_PORT) ?: DEFAULT_PORT) }
    var pluginName by remember { mutableStateOf(prefs.getString(KEY_PLUGIN, DEFAULT_PLUGIN) ?: DEFAULT_PLUGIN) }
    var devName by remember { mutableStateOf(prefs.getString(KEY_DEVELOPER, DEFAULT_DEVELOPER) ?: DEFAULT_DEVELOPER) }

    var client by remember { mutableStateOf<VtsStreamClient?>(null) }
    var streamState by remember { mutableStateOf<StreamState>(StreamState.Disconnected) }
    var streamJob by remember { mutableStateOf<Job?>(null) }
    var stateJob by remember { mutableStateOf<Job?>(null) }
    var paramPreview by remember { mutableStateOf<Map<String, Float>>(emptyMap()) }

    DisposableEffect(Unit) {
        onDispose {
            streamJob?.cancel()
            stateJob?.cancel()
            client?.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (activity.faceTracker is PreviewableFaceTracker) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        (activity.faceTracker as PreviewableFaceTracker).setSurfaceProvider(surfaceProvider)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            StreamTopBar(state, isTracking, trackingData?.timestampMs ?: 0L, onStartClick, onStopClick)
            ModeToggle(currentMode = TrackingMode.STREAM, onModeChange = onModeChange)

            Spacer(modifier = Modifier.weight(1f))

            StreamBottomPanel(
                host, port, pluginName, devName,
                streamState, paramPreview,
                onHostChange = { host = it },
                onPortChange = { port = it },
                onPluginChange = { pluginName = it },
                onDevChange = { devName = it },
                onConnect = {
                    savePrefs(prefs, host, port, pluginName, devName)
                    val savedToken = prefs.getString(KEY_TOKEN, null)
                    val newClient = VtsStreamClient(
                        VtsConfig(
                            host = host,
                            port = port.toIntOrNull() ?: STREAM_DEFAULT_PORT,
                            pluginName = pluginName,
                            pluginDeveloper = devName,
                            authToken = savedToken,
                            onTokenReceived = { token ->
                                prefs.edit().putString(KEY_TOKEN, token).apply()
                            },
                        ),
                    )
                    client?.release()
                    client = newClient
                    stateJob?.cancel()
                    stateJob = scope.launch {
                        newClient.state.collect { streamState = it }
                    }
                    scope.launch { newClient.connect() }
                    streamJob?.cancel()
                    streamJob = scope.launch {
                        activity.faceTracker.trackingData.collect { data ->
                            val params = ParameterConverter.convert(data)
                            paramPreview = params
                            newClient.sendParameters(params, data.isTracking)
                        }
                    }
                },
                onDisconnect = {
                    scope.launch {
                        streamJob?.cancel()
                        client?.disconnect()
                    }
                },
            )
        }
    }
}

private const val STREAM_DEFAULT_PORT = 8001

@Composable
private fun StreamTopBar(
    state: TrackingState,
    isTracking: Boolean,
    frameTimestamp: Long,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SampleColors.Overlay)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Stream", color = SampleColors.TextPrimary, fontSize = 14.sp)
        Text(" — $state", color = SampleColors.TextSecondary, fontSize = 12.sp)
        if (isTracking) {
            FpsOverlay(frameTimestamp, modifier = Modifier.padding(start = 8.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = { if (isTracking) onStopClick() else onStartClick() }) {
            Text(if (isTracking) "Stop" else "Start")
        }
    }
}

@Suppress("LongMethod", "LongParameterList")
@Composable
private fun StreamBottomPanel(
    host: String,
    port: String,
    pluginName: String,
    devName: String,
    streamState: StreamState,
    paramPreview: Map<String, Float>,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onPluginChange: (String) -> Unit,
    onDevChange: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val tfColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = SampleColors.TextPrimary,
        unfocusedTextColor = SampleColors.TextSecondary,
        focusedLabelColor = SampleColors.Primary,
        unfocusedLabelColor = SampleColors.TextTertiary,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SampleColors.Overlay)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(8.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        // Connection state badge
        StreamStateBadge(streamState)

        Spacer(modifier = Modifier.height(8.dp))

        // Connection form
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = host,
                onValueChange = onHostChange,
                label = { Text("Host") },
                colors = tfColors,
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedTextField(
                value = port,
                onValueChange = onPortChange,
                label = { Text("Port") },
                colors = tfColors,
                modifier = Modifier.weight(0.4f),
                singleLine = true,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = pluginName,
                onValueChange = onPluginChange,
                label = { Text("Plugin") },
                colors = tfColors,
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedTextField(
                value = devName,
                onValueChange = onDevChange,
                label = { Text("Developer") },
                colors = tfColors,
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val isConnected = streamState is StreamState.Connected
            Button(
                onClick = if (isConnected) onDisconnect else onConnect,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected) SampleColors.ErrorRed else SampleColors.Primary,
                ),
            ) {
                Text(if (isConnected) "Disconnect" else "Connect")
            }
        }

        // Parameter preview (first 10)
        if (paramPreview.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Parameters", color = SampleColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            paramPreview.entries.take(10).forEach { (key, value) ->
                Text(
                    text = "$key: %.3f".format(value),
                    color = SampleColors.TextTertiary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            if (paramPreview.size > 10) {
                Text(
                    text = "... +${paramPreview.size - 10} more",
                    color = SampleColors.TextTertiary,
                    fontSize = 9.sp,
                )
            }
        }
    }
}

@Composable
private fun StreamStateBadge(streamState: StreamState) {
    val (text, color) = when (streamState) {
        is StreamState.Connected -> "Connected" to SampleColors.StatusActive
        is StreamState.Connecting -> "Connecting..." to SampleColors.WarningYellow
        is StreamState.Authenticating -> "Authenticating..." to SampleColors.WarningYellow
        is StreamState.Disconnected -> "Disconnected" to SampleColors.StatusInactive
        is StreamState.Reconnecting -> "Reconnecting (${streamState.attempt})" to SampleColors.WarningYellow
        is StreamState.Error -> "Error: ${streamState.message}" to SampleColors.ErrorRed
    }
    Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
}

private fun savePrefs(
    prefs: android.content.SharedPreferences,
    host: String,
    port: String,
    pluginName: String,
    devName: String,
) {
    prefs.edit()
        .putString(KEY_HOST, host)
        .putString(KEY_PORT, port)
        .putString(KEY_PLUGIN, pluginName)
        .putString(KEY_DEVELOPER, devName)
        .apply()
}
