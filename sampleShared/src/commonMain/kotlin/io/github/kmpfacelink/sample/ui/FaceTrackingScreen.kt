package io.github.kmpfacelink.sample.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kmpfacelink.api.FaceTracker
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.model.FaceTrackingData
import kotlinx.coroutines.flow.StateFlow

/**
 * Shared face tracking screen used on both Android and iOS.
 *
 * Displays camera preview, face landmark overlay, tracking controls,
 * smoothing filter selection, and blend shape bars.
 */
@Composable
public fun FaceTrackingScreen(
    faceTracker: FaceTracker,
    trackingDataState: StateFlow<FaceTrackingData?>,
    onModeChange: (TrackingMode) -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
) {
    val state by faceTracker.state.collectAsState()
    val trackingData by trackingDataState.collectAsState()
    val errorMessage by faceTracker.errorMessage.collectAsState()

    TrackingScreenScaffold(
        currentMode = TrackingMode.FACE,
        onModeChange = onModeChange,
        camera = {
            FaceCameraPreview(
                faceTracker = faceTracker,
                modifier = Modifier.fillMaxSize(),
            )
        },
        overlay = { LandmarkOverlayLayer(trackingData) },
        topContent = {
            TopBar(
                state = state,
                headTransform = trackingData?.headTransform,
                onStartClick = onStartClick,
                onStopClick = onStopClick,
            )
            if (state == TrackingState.STARTING) {
                StartingIndicator()
            }
            if (state == TrackingState.ERROR) {
                FaceErrorBanner(errorMessage, onRetry = onStartClick)
            }
            SmoothingFilterChips(faceTracker = faceTracker)
            StreamingToggle()
        },
        bottomContent = {
            BlendShapeBars(
                trackingData = trackingData,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars),
            )
        },
    )
}

@Composable
private fun StartingIndicator() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .background(SampleColors.Overlay)
            .padding(vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = SampleColors.TextPrimary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Starting tracker\u2026",
                color = SampleColors.TextPrimary,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun FaceErrorBanner(errorMessage: String?, onRetry: () -> Unit) {
    val message = errorMessage ?: "An unknown error occurred"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(SampleColors.ErrorRed.copy(alpha = 0.85f))
            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
    ) {
        Text(
            text = message,
            color = SampleColors.TextPrimary,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) {
            Text("Retry", color = SampleColors.TextPrimary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun LandmarkOverlayLayer(trackingData: FaceTrackingData?) {
    val currentData = trackingData?.takeIf { it.isTracking && it.landmarks.isNotEmpty() }
    if (currentData != null) {
        FaceLandmarkOverlay(
            landmarks = currentData.landmarks,
            sourceImageWidth = currentData.sourceImageWidth,
            sourceImageHeight = currentData.sourceImageHeight,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
