package io.github.kmpfacelink.sample

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.kmpfacelink.actions.ActionBinding
import io.github.kmpfacelink.actions.ActionEvent
import io.github.kmpfacelink.actions.ActionSystem
import io.github.kmpfacelink.actions.emotion.Emotion
import io.github.kmpfacelink.actions.emotion.EmotionClassifier
import io.github.kmpfacelink.actions.emotion.EmotionResult
import io.github.kmpfacelink.actions.record.TrackingRecorder
import io.github.kmpfacelink.actions.record.TrackingSession
import io.github.kmpfacelink.actions.smileTrigger
import io.github.kmpfacelink.actions.surprisedTrigger
import io.github.kmpfacelink.actions.tongueOutTrigger
import io.github.kmpfacelink.actions.winkLeftTrigger
import io.github.kmpfacelink.actions.winkRightTrigger
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.internal.PreviewableFaceTracker
import io.github.kmpfacelink.sample.ui.FpsOverlay
import io.github.kmpfacelink.sample.ui.SampleColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val TRIGGER_NAMES = listOf("smile", "winkL", "winkR", "tongueOut", "surprised")

@Suppress("LongMethod")
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

    val actionSystem = remember { ActionSystem() }
    val classifier = remember { EmotionClassifier() }
    val recorder = remember { TrackingRecorder() }

    var emotionResult by remember { mutableStateOf<EmotionResult?>(null) }
    val activeTriggers = remember { mutableStateListOf<String>() }
    var session by remember { mutableStateOf<TrackingSession?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var actionsJob by remember { mutableStateOf<Job?>(null) }
    var eventsJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(Unit) {
        scope.launch {
            registerBuiltInTriggers(actionSystem)
        }
        onDispose {
            actionsJob?.cancel()
            eventsJob?.cancel()
            actionSystem.release()
        }
    }

    DisposableEffect(isTracking) {
        if (isTracking) {
            eventsJob = scope.launch {
                actionSystem.events.collect { event ->
                    handleActionEvent(event, activeTriggers)
                }
            }
            actionsJob = scope.launch {
                activity.faceTracker.trackingData.collect { data ->
                    emotionResult = classifier.classify(data)
                    actionSystem.processFace(data)
                    if (recorder.isRecording) recorder.record(data)
                }
            }
        } else {
            actionsJob?.cancel()
            eventsJob?.cancel()
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
                if (isRecording) {
                    session = recorder.stop()
                    isRecording = false
                } else {
                    recorder.start()
                    isRecording = true
                    session = null
                }
            }
        },
    )
}

@Suppress("LongMethod", "LongParameterList")
@Composable
private fun ActionsScreenContent(
    faceTracker: io.github.kmpfacelink.api.FaceTracker,
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
        Text("Actions", color = SampleColors.TextPrimary, fontSize = 14.sp)
        Text(
            " — $state",
            color = SampleColors.TextSecondary,
            fontSize = 12.sp,
        )
        if (isTracking) {
            FpsOverlay(frameTimestamp, modifier = Modifier.padding(start = 8.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = { if (isTracking) onStopClick() else onStartClick() }) {
            Text(if (isTracking) "Stop" else "Start")
        }
    }
}

@Suppress("LongMethod")
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
        // Emotion section
        Text("Emotions", color = SampleColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        EmotionBars(emotionResult)

        Spacer(modifier = Modifier.height(8.dp))

        // Triggers section
        Text("Triggers", color = SampleColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        TriggerLeds(activeTriggers)

        Spacer(modifier = Modifier.height(8.dp))

        // Recording section
        Text("Recording", color = SampleColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        RecordingControls(isRecording, session, onRecordToggle)
    }
}

@Composable
private fun EmotionBars(result: EmotionResult?) {
    val scores = result?.scores ?: Emotion.entries.associateWith { 0f }
    Emotion.entries.forEach { emotion ->
        val score = scores[emotion] ?: 0f
        val isDominant = result?.emotion == emotion
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = emotion.name,
                color = if (isDominant) SampleColors.BarLow else SampleColors.TextTertiary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(0.3f),
            )
            Box(
                modifier = Modifier
                    .weight(0.7f)
                    .height(8.dp)
                    .background(SampleColors.ChipDefault),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(score.coerceIn(0f, 1f))
                        .height(8.dp)
                        .background(if (isDominant) SampleColors.BarLow else SampleColors.BarMedium),
                )
            }
        }
    }
}

@Composable
private fun TriggerLeds(activeTriggers: List<String>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        TRIGGER_NAMES.forEach { name ->
            val isActive = name in activeTriggers
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (isActive) SampleColors.StatusActive else SampleColors.StatusInactive),
                )
                Text(
                    text = name,
                    color = SampleColors.TextTertiary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun RecordingControls(
    isRecording: Boolean,
    session: TrackingSession?,
    onRecordToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        Button(
            onClick = onRecordToggle,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRecording) SampleColors.ErrorRed else SampleColors.Primary,
            ),
        ) {
            Text(if (isRecording) "Stop Rec" else "Record")
        }
        session?.let { s ->
            Text(
                text = "${s.frameCount} frames, ${s.durationMs}ms, %.1f fps".format(s.averageFps),
                color = SampleColors.TextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

private suspend fun registerBuiltInTriggers(system: ActionSystem) {
    system.register(ActionBinding("smile", smileTrigger()))
    system.register(ActionBinding("winkL", winkLeftTrigger()))
    system.register(ActionBinding("winkR", winkRightTrigger()))
    system.register(ActionBinding("tongueOut", tongueOutTrigger()))
    system.register(ActionBinding("surprised", surprisedTrigger()))
}

private fun handleActionEvent(event: ActionEvent, activeTriggers: MutableList<String>) {
    when (event) {
        is ActionEvent.Started -> {
            if (event.actionId !in activeTriggers) activeTriggers.add(event.actionId)
        }
        is ActionEvent.Released -> activeTriggers.remove(event.actionId)
        is ActionEvent.Held -> { /* Keep active */ }
    }
}
