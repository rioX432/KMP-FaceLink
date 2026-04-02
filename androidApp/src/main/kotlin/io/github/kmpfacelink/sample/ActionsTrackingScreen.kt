package io.github.kmpfacelink.sample

import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.kmpfacelink.actions.emotion.EmotionResult
import io.github.kmpfacelink.actions.record.TrackingSession
import io.github.kmpfacelink.api.FaceTracker
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.internal.PreviewableFaceTracker
import io.github.kmpfacelink.sample.ui.FpsOverlay
import io.github.kmpfacelink.sample.ui.SampleColors
import kotlinx.coroutines.launch

@Composable
internal fun ActionsTrackingScreen(
    activity: MainActivity,
    onModeChange: (TrackingMode) -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val state by activity.faceTracker.state.collectAsState()
    val trackingData by activity.faceTrackingDataState.collectAsState()
    val isTracking = state == TrackingState.TRACKING

    val holder = remember { ActionsTrackingStateHolder(scope) }
    var emotionResult by remember { mutableStateOf<EmotionResult?>(null) }
    val activeTriggers = remember { mutableStateListOf<String>() }
    var session by remember { mutableStateOf<TrackingSession?>(null) }
    var isRecording by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        scope.launch { holder.registerTriggers() }
        onDispose { holder.release() }
    }

    DisposableEffect(isTracking) {
        if (isTracking) {
            holder.startTracking(activity.faceTracker, activeTriggers) { emotionResult = it }
        } else {
            holder.stopTracking()
        }
        onDispose { }
    }

    ActionsScreenContent(
        faceTracker = activity.faceTracker,
        state = state,
        isTracking = isTracking,
        emotionResult = emotionResult,
        activeTriggers = activeTriggers,
        isRecording = isRecording,
        session = session,
        frameTimestamp = trackingData?.timestampMs ?: 0L,
        onModeChange = onModeChange,
        onStartClick = onStartClick,
        onStopClick = onStopClick,
        onRecordToggle = {
            scope.launch {
                holder.toggleRecording(isRecording, { isRecording = it }, { session = it })
            }
        },
    )
}

@Suppress("LongParameterList")
@Composable
private fun ActionsScreenContent(
    faceTracker: FaceTracker,
    state: TrackingState,
    isTracking: Boolean,
    emotionResult: EmotionResult?,
    activeTriggers: List<String>,
    isRecording: Boolean,
    session: TrackingSession?,
    frameTimestamp: Long,
    onModeChange: (TrackingMode) -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onRecordToggle: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (faceTracker is PreviewableFaceTracker) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        faceTracker.setSurfaceProvider(surfaceProvider)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            ActionsTopBar(state, isTracking, frameTimestamp, onStartClick, onStopClick)
            ModeToggle(currentMode = TrackingMode.ACTIONS, onModeChange = onModeChange)
            Spacer(modifier = Modifier.weight(1f))
            ActionsBottomPanel(
                emotionResult = emotionResult,
                activeTriggers = activeTriggers,
                isRecording = isRecording,
                session = session,
                onRecordToggle = onRecordToggle,
            )
        }
    }
}

@Composable
private fun ActionsTopBar(
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
        Text(
            "Actions",
            color = SampleColors.TextPrimary,
            fontSize = 14.sp,
            modifier = Modifier.semantics { contentDescription = "Actions tracking mode" },
        )
        Text(
            " — $state",
            color = SampleColors.TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.semantics { contentDescription = "Tracking state: $state" },
        )
        if (isTracking) {
            FpsOverlay(frameTimestamp, modifier = Modifier.padding(start = 8.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = { if (isTracking) onStopClick() else onStartClick() },
            modifier = Modifier.semantics {
                contentDescription = if (isTracking) "Stop tracking" else "Start tracking"
            },
        ) {
            Text(if (isTracking) "Stop" else "Start")
        }
    }
}

@Composable
private fun ActionsBottomPanel(
    emotionResult: EmotionResult?,
    activeTriggers: List<String>,
    isRecording: Boolean,
    session: TrackingSession?,
    onRecordToggle: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SampleColors.Overlay)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(8.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        SectionHeader("Emotions")
        EmotionBars(emotionResult)

        SectionHeader("Triggers")
        TriggerLeds(activeTriggers)

        SectionHeader("Recording")
        RecordingControls(isRecording, session, onRecordToggle)
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = SampleColors.TextPrimary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}
