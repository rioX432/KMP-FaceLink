package io.github.kmpfacelink.sample

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kmpfacelink.sample.ui.HomeScreen
import io.github.kmpfacelink.sample.ui.InteractDemoScreen
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
            initialSmoothingConfig = stateHolder.faceSmoothingConfig,
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
        TrackingMode.INTERACT -> InteractDemoScreen()
    }
}

@Composable
internal fun ModeToggle(
    currentMode: TrackingMode,
    onModeChange: (TrackingMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TrackingMode.entries.filter { it != TrackingMode.HOME }.forEach { mode ->
            FilterChip(
                selected = mode == currentMode,
                onClick = { onModeChange(mode) },
                label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
            )
        }
    }
}

internal fun SharedTrackingMode.toLocal(): TrackingMode = when (this) {
    SharedTrackingMode.HOME -> TrackingMode.HOME
    SharedTrackingMode.FACE -> TrackingMode.FACE
    SharedTrackingMode.HAND -> TrackingMode.HAND
    SharedTrackingMode.HOLISTIC -> TrackingMode.HOLISTIC
    SharedTrackingMode.ACTIONS -> TrackingMode.ACTIONS
    SharedTrackingMode.VOICE -> TrackingMode.VOICE
    SharedTrackingMode.INTERACT -> TrackingMode.INTERACT
}
