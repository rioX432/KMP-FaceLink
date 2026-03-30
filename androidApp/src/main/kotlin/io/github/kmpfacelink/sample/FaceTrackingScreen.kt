package io.github.kmpfacelink.sample

import androidx.compose.runtime.Composable
import io.github.kmpfacelink.api.FaceTracker
import io.github.kmpfacelink.model.FaceTrackingData
import kotlinx.coroutines.flow.MutableStateFlow
import io.github.kmpfacelink.sample.ui.FaceTrackingScreen as SharedFaceTrackingScreen
import io.github.kmpfacelink.sample.ui.TrackingMode as SharedTrackingMode

/**
 * Android face tracking screen that delegates to the shared CMP implementation.
 */
@Composable
internal fun FaceTrackingScreen(
    faceTracker: FaceTracker,
    trackingDataState: MutableStateFlow<FaceTrackingData?>,
    onModeChange: (TrackingMode) -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
) {
    SharedFaceTrackingScreen(
        faceTracker = faceTracker,
        trackingDataState = trackingDataState,
        onModeChange = { sharedMode -> onModeChange(sharedMode.toLocal()) },
        onStartClick = onStartClick,
        onStopClick = onStopClick,
    )
}

private fun SharedTrackingMode.toLocal(): TrackingMode = when (this) {
    SharedTrackingMode.HOME -> TrackingMode.HOME
    SharedTrackingMode.FACE -> TrackingMode.FACE
    SharedTrackingMode.HAND -> TrackingMode.HAND
    SharedTrackingMode.HOLISTIC -> TrackingMode.HOLISTIC
    SharedTrackingMode.ACTIONS -> TrackingMode.ACTIONS
    SharedTrackingMode.VOICE -> TrackingMode.VOICE
}
