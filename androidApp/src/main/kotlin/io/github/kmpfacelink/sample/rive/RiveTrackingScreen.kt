package io.github.kmpfacelink.sample.rive

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Rive
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.rive.AndroidRiveRenderer
import io.github.kmpfacelink.rive.RiveDefaultMappings
import io.github.kmpfacelink.rive.RiveRenderState
import io.github.kmpfacelink.rive.toRiveInputs
import io.github.kmpfacelink.sample.MainActivity
import io.github.kmpfacelink.sample.ModeToggle
import io.github.kmpfacelink.sample.TrackingMode
import io.github.kmpfacelink.sample.ui.FpsOverlay
import io.github.kmpfacelink.sample.ui.SampleColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Suppress("LongMethod")
@Composable
internal fun RiveTrackingScreen(
    activity: MainActivity,
    onModeChange: (TrackingMode) -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val trackingState by activity.faceTracker.state.collectAsState()
    val trackingData by activity.faceTrackingDataState.collectAsState()
    val isTracking = trackingState == TrackingState.TRACKING
    val mapper = remember { RiveDefaultMappings.createMapper() }

    val context = LocalContext.current
    val riveInitError = remember {
        @Suppress("TooGenericExceptionCaught")
        try {
            Rive.init(context)
            null
        } catch (e: Throwable) {
            e.message ?: "Rive native library unavailable"
        }
    }

    var frameCount by remember { mutableIntStateOf(0) }
    var riveRenderer by remember { mutableStateOf<AndroidRiveRenderer?>(null) }
    var driveJob by remember { mutableStateOf<Job?>(null) }
    var riveLoadError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            driveJob?.cancel()
            riveRenderer?.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SampleColors.Background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            ModeToggle(currentMode = TrackingMode.RIVE, onModeChange = onModeChange)

            // Rive animation view
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                val errorMsg = riveInitError ?: riveLoadError
                if (errorMsg != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Rive Error", color = SampleColors.ErrorRed, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(errorMsg, color = SampleColors.TextSecondary, fontSize = 12.sp)
                    }
                } else {
                    AndroidView(
                        factory = { ctx ->
                            RiveAnimationView(ctx).also { view ->
                                val renderer = AndroidRiveRenderer(ctx, view)
                                riveRenderer = renderer
                                scope.launch {
                                    @Suppress("TooGenericExceptionCaught")
                                    try {
                                        renderer.loadModel("sample_avatar", "State Machine 1")
                                    } catch (e: Exception) {
                                        riveLoadError = e.message ?: "Failed to load Rive model"
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // Controls and stats
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SampleColors.Overlay)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(16.dp),
            ) {
                RiveControlBar(
                    isTracking = isTracking,
                    riveState = riveRenderer?.state?.collectAsState()?.value
                        ?: RiveRenderState.UNINITIALIZED,
                    onStartClick = {
                        onStartClick()
                        val renderer = riveRenderer
                        if (renderer != null) {
                            driveJob?.cancel()
                            driveJob = scope.launch {
                                activity.faceTracker.trackingData
                                    .toRiveInputs(mapper)
                                    .collect { inputs ->
                                        renderer.updateInputs(inputs)
                                        frameCount++
                                    }
                            }
                        }
                    },
                    onStopClick = {
                        driveJob?.cancel()
                        onStopClick()
                    },
                )

                Spacer(modifier = Modifier.height(8.dp))

                RiveStatsOverlay(
                    isTracking = isTracking,
                    frameCount = frameCount,
                    blendShapeCount = trackingData?.blendShapes?.size ?: 0,
                )
            }
        }

        FpsOverlay(
            frameTimestampMs = trackingData?.timestampMs ?: 0L,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

@Composable
private fun RiveControlBar(
    isTracking: Boolean,
    riveState: RiveRenderState,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "Rive: ${riveState.name}",
                color = SampleColors.TextSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = if (isTracking) "Tracking Active" else "Tracking Stopped",
                color = if (isTracking) SampleColors.StatusActive else SampleColors.StatusInactive,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Button(
            onClick = if (isTracking) onStopClick else onStartClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isTracking) SampleColors.ErrorRed else SampleColors.Primary,
            ),
        ) {
            Text(if (isTracking) "Stop" else "Start")
        }
    }
}

@Composable
private fun RiveStatsOverlay(
    isTracking: Boolean,
    frameCount: Int,
    blendShapeCount: Int,
) {
    if (!isTracking) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "Frames: $frameCount | Inputs: $blendShapeCount",
            color = SampleColors.TextTertiary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}
