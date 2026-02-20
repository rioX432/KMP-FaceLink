package io.github.kmpfacelink.sample

import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.kmpfacelink.api.HolisticTracker
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.internal.PreviewableHolisticTracker
import io.github.kmpfacelink.model.HolisticTrackingData
import kotlinx.coroutines.flow.MutableStateFlow

@Suppress("LongMethod")
@Composable
internal fun HolisticTrackingScreen(
    holisticTracker: HolisticTracker,
    trackingDataState: MutableStateFlow<HolisticTrackingData?>,
    onModeChange: (TrackingMode) -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
) {
    val state by holisticTracker.state.collectAsState()
    val trackingData by trackingDataState.collectAsState()
    val isTracking = state == TrackingState.TRACKING

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview
        if (holisticTracker is PreviewableHolisticTracker) {
            HolisticCameraPreview(previewableTracker = holisticTracker)
        }

        // Face landmark overlay
        val faceData = trackingData?.face?.takeIf { it.isTracking && it.landmarks.isNotEmpty() }
        if (faceData != null) {
            FaceLandmarkOverlay(
                landmarks = faceData.landmarks,
                sourceImageWidth = faceData.sourceImageWidth,
                sourceImageHeight = faceData.sourceImageHeight,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Hand landmark overlay
        val handData = trackingData?.hand?.takeIf { it.isTracking && it.hands.isNotEmpty() }
        if (handData != null) {
            for (hand in handData.hands) {
                HandLandmarkOverlay(
                    landmarks = hand.landmarks,
                    sourceImageWidth = handData.sourceImageWidth,
                    sourceImageHeight = handData.sourceImageHeight,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Body landmark overlay
        val bodyData = trackingData?.body?.takeIf { it.isTracking && it.bodies.isNotEmpty() }
        if (bodyData != null) {
            for (body in bodyData.bodies) {
                BodyLandmarkOverlay(
                    landmarks = body.landmarks,
                    sourceImageWidth = bodyData.sourceImageWidth,
                    sourceImageHeight = bodyData.sourceImageHeight,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // UI overlay
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Holistic", color = Color.White, fontSize = 14.sp)
                    Spacer(Modifier.weight(1f))
                    HolisticStatusBadges(trackingData)
                }
                ModeToggle(currentMode = TrackingMode.HOLISTIC, onModeChange = onModeChange)
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(12.dp),
            ) {
                Button(
                    onClick = if (isTracking) onStopClick else onStartClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (isTracking) "Stop" else "Start")
                }
            }
        }
    }
}

@Composable
private fun HolisticStatusBadges(data: HolisticTrackingData?) {
    Row {
        StatusDot(label = "F", active = data?.face?.isTracking == true)
        StatusDot(label = "H", active = data?.hand?.isTracking == true)
        StatusDot(label = "B", active = data?.body?.isTracking == true)
    }
}

@Composable
private fun StatusDot(label: String, active: Boolean) {
    val color = if (active) Color.Green else Color.Gray
    Text(
        text = label,
        color = color,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun HolisticCameraPreview(previewableTracker: PreviewableHolisticTracker) {
    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).also { previewView ->
                previewableTracker.setSurfaceProvider(previewView.surfaceProvider)
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
internal fun BodyLandmarkOverlay(
    landmarks: List<io.github.kmpfacelink.model.BodyLandmarkPoint>,
    sourceImageWidth: Int,
    sourceImageHeight: Int,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (sourceImageWidth == 0 || sourceImageHeight == 0) return@Canvas

        val scaleX = size.width / sourceImageWidth
        val scaleY = size.height / sourceImageHeight

        for (landmark in landmarks) {
            drawCircle(
                color = Color.Cyan,
                radius = 4f,
                center = Offset(landmark.x * scaleX, landmark.y * scaleY),
            )
        }
    }
}
