package io.github.kmpfacelink.sample

import androidx.compose.runtime.Composable
import io.github.kmpfacelink.api.FaceTracker
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.SmoothingConfig
import kotlinx.coroutines.flow.MutableStateFlow
import io.github.kmpfacelink.sample.ui.FaceTrackingScreen as SharedFaceTrackingScreen

/**
 * Android face tracking screen that delegates to the shared CMP implementation.
 */
@Composable
internal fun FaceTrackingScreen(
    faceTracker: FaceTracker,
    trackingDataState: MutableStateFlow<FaceTrackingData?>,
    initialSmoothingConfig: SmoothingConfig,
    onModeChange: (TrackingMode) -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
) {
    SharedFaceTrackingScreen(
        faceTracker = faceTracker,
        trackingDataState = trackingDataState,
        initialSmoothingConfig = initialSmoothingConfig,
        onModeChange = { sharedMode -> onModeChange(sharedMode.toLocal()) },
        onStartClick = onStartClick,
        onStopClick = onStopClick,
    )
}
