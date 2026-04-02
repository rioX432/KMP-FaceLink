package io.github.kmpfacelink.sample

import androidx.compose.runtime.Composable
import io.github.kmpfacelink.sample.ui.HomeScreen
import io.github.kmpfacelink.sample.ui.TrackingMode as SharedTrackingMode

/**
 * Top-level screen router that delegates to the appropriate tracking screen
 * based on the current [TrackingMode].
 */
@Composable
internal fun MainScreen(
    mode: TrackingMode,
    stateHolder: TrackingStateHolder,
    onModeChange: (TrackingMode) -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
) {
    when (mode) {
        TrackingMode.HOME -> HomeScreen(
            onModeChange = { sharedMode -> onModeChange(sharedMode.toLocal()) },
        )
        TrackingMode.FACE -> FaceTrackingScreen(
            faceTracker = stateHolder.faceTracker,
            trackingDataState = stateHolder.faceTrackingDataState,
            onModeChange = onModeChange,
            onStartClick = onStartClick,
            onStopClick = onStopClick,
        )
        TrackingMode.HAND -> HandTrackingScreen(
            handTracker = stateHolder.handTracker,
            trackingDataState = stateHolder.handTrackingDataState,
            onModeChange = onModeChange,
            onStartClick = onStartClick,
            onStopClick = onStopClick,
        )
        TrackingMode.HOLISTIC -> HolisticTrackingScreen(
            holisticTracker = stateHolder.holisticTracker,
            trackingDataState = stateHolder.holisticTrackingDataState,
            onModeChange = onModeChange,
            onStartClick = onStartClick,
            onStopClick = onStopClick,
        )
        TrackingMode.ACTIONS -> ActionsTrackingScreen(
            faceTracker = stateHolder.faceTracker,
            faceTrackingDataState = stateHolder.faceTrackingDataState,
            onModeChange = onModeChange,
            onStartClick = onStartClick,
            onStopClick = onStopClick,
        )
        TrackingMode.VOICE -> VoiceTrackingScreen(
            onModeChange = onModeChange,
        )
    }
}

internal fun SharedTrackingMode.toLocal(): TrackingMode = when (this) {
    SharedTrackingMode.HOME -> TrackingMode.HOME
    SharedTrackingMode.FACE -> TrackingMode.FACE
    SharedTrackingMode.HAND -> TrackingMode.HAND
    SharedTrackingMode.HOLISTIC -> TrackingMode.HOLISTIC
    SharedTrackingMode.ACTIONS -> TrackingMode.ACTIONS
    SharedTrackingMode.VOICE -> TrackingMode.VOICE
}
