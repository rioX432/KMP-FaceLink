package io.github.kmpfacelink.sample

import androidx.compose.runtime.Composable
import io.github.kmpfacelink.api.HandTracker
import io.github.kmpfacelink.model.HandTrackingData
import kotlinx.coroutines.flow.MutableStateFlow
import io.github.kmpfacelink.sample.ui.HandTrackingScreen as SharedHandTrackingScreen
import io.github.kmpfacelink.sample.ui.TrackingMode as SharedTrackingMode

/**
 * Android hand tracking screen that delegates to the shared CMP implementation.
 */
@Composable
internal fun HandTrackingScreen(
    handTracker: HandTracker,
    trackingDataState: MutableStateFlow<HandTrackingData?>,
    onModeChange: (TrackingMode) -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
) {
    SharedHandTrackingScreen(
        handTracker = handTracker,
        trackingDataState = trackingDataState,
        onModeChange = { sharedMode -> onModeChange(sharedMode.toLocal()) },
        onStartClick = onStartClick,
        onStopClick = onStopClick,
    )
}

private fun SharedTrackingMode.toLocal(): TrackingMode = when (this) {
    SharedTrackingMode.FACE -> TrackingMode.FACE
    SharedTrackingMode.HAND -> TrackingMode.HAND
    SharedTrackingMode.HOLISTIC -> TrackingMode.HOLISTIC
    SharedTrackingMode.ACTIONS -> TrackingMode.ACTIONS
    SharedTrackingMode.VOICE -> TrackingMode.VOICE
}
