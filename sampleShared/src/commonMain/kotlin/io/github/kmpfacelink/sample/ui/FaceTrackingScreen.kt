package io.github.kmpfacelink.sample.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

    Box(modifier = Modifier.fillMaxSize()) {
        FaceCameraPreview(
            faceTracker = faceTracker,
            modifier = Modifier.fillMaxSize(),
        )

        LandmarkOverlayLayer(trackingData)

        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(
                state = state,
                headTransform = trackingData?.headTransform,
                onStartClick = onStartClick,
                onStopClick = onStopClick,
            )
            if (state == TrackingState.ERROR) {
                FaceErrorBanner(errorMessage)
            }
            ModeToggle(currentMode = TrackingMode.FACE, onModeChange = onModeChange)
            SmoothingFilterChips(faceTracker = faceTracker)
            StreamingToggle()

            Spacer(modifier = Modifier.weight(1f))

            BlendShapeBars(
                trackingData = trackingData,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars),
            )
        }
    }
}

@Composable
private fun FaceErrorBanner(errorMessage: String?) {
    val message = errorMessage ?: "An unknown error occurred"
    Text(
        text = message,
        color = SampleColors.TextPrimary,
        fontSize = 13.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .background(SampleColors.ErrorRed.copy(alpha = 0.85f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
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
