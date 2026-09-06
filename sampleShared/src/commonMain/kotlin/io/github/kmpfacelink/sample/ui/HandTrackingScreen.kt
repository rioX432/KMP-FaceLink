package io.github.kmpfacelink.sample.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kmpfacelink.api.HandTracker
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.model.HandGesture
import io.github.kmpfacelink.model.HandTrackingData
import kotlinx.coroutines.flow.StateFlow

/**
 * Shared hand tracking screen used on both Android and iOS.
 *
 * Displays camera preview, hand landmark overlay, gesture info,
 * mode toggle, and hand count status.
 */
@Composable
public fun HandTrackingScreen(
    handTracker: HandTracker,
    trackingDataState: StateFlow<HandTrackingData?>,
    onModeChange: (TrackingMode) -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    showModeToggle: Boolean = true,
) {
    val state by handTracker.state.collectAsState()
    val trackingData by trackingDataState.collectAsState()
    val errorMessage by handTracker.errorMessage.collectAsState()
    val isTracking = state == TrackingState.TRACKING

    TrackingScreenScaffold(
        currentMode = TrackingMode.HAND,
        onModeChange = onModeChange,
        camera = {
            HandCameraPreview(
                handTracker = handTracker,
                modifier = Modifier.fillMaxSize(),
            )
        },
        overlay = { HandOverlayLayer(trackingData) },
        topContent = {
            HandTopBar(
                state = state,
                trackingData = trackingData,
                isTracking = isTracking,
                onStartClick = onStartClick,
                onStopClick = onStopClick,
            )
            if (state == TrackingState.STARTING) {
                HandStartingIndicator()
            }
            if (state == TrackingState.ERROR) {
                ErrorBanner(errorMessage, onRetry = onStartClick)
            }
        },
        bottomContent = {
            HandBottomBar(trackingData)
        },
        showModeToggle = showModeToggle,
    )
}

@Composable
private fun HandOverlayLayer(trackingData: HandTrackingData?) {
    val currentData = trackingData?.takeIf { it.isTracking && it.hands.isNotEmpty() }
    if (currentData != null) {
        for (hand in currentData.hands) {
            HandLandmarkOverlay(
                landmarks = hand.landmarks,
                sourceImageWidth = currentData.sourceImageWidth,
                sourceImageHeight = currentData.sourceImageHeight,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun HandTopBar(
    state: TrackingState,
    trackingData: HandTrackingData?,
    isTracking: Boolean,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SampleColors.Overlay)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Status: $state",
                color = SampleColors.TextPrimary,
                fontSize = 14.sp,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Tracking status: $state" },
            )
            val buttonLabel = if (isTracking) "Stop" else "Start"
            Button(
                onClick = { if (isTracking) onStopClick() else onStartClick() },
                modifier = Modifier.semantics {
                    contentDescription = "$buttonLabel hand tracking"
                },
            ) {
                Text(buttonLabel)
            }
        }

        GestureText(trackingData)
    }
}

@Composable
private fun GestureText(trackingData: HandTrackingData?) {
    val currentData = trackingData?.takeIf { it.isTracking && it.hands.isNotEmpty() }
    val gestureText = currentData?.hands?.joinToString(" | ") { hand ->
        val h = hand.handedness.name
        val g = if (hand.gesture != HandGesture.NONE) {
            "${hand.gesture.name} (${(hand.gestureConfidence * PERCENT_MULTIPLIER).toInt()}%)"
        } else {
            "\u2014"
        }
        "$h: $g"
    } ?: "No hands detected"
    val a11yDescription = currentData?.hands?.joinToString(". ") { hand ->
        val g = if (hand.gesture != HandGesture.NONE) {
            "${hand.gesture.name}, ${(hand.gestureConfidence * PERCENT_MULTIPLIER).toInt()} percent"
        } else {
            "no gesture"
        }
        "${hand.handedness.name} hand: $g"
    } ?: "No hands detected"
    Text(
        text = gestureText,
        color = SampleColors.TextSecondary,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.semantics { contentDescription = a11yDescription },
    )
}

@Composable
private fun HandBottomBar(trackingData: HandTrackingData?) {
    val currentData = trackingData?.takeIf { it.isTracking && it.hands.isNotEmpty() }
    val handsCount = currentData?.hands?.size ?: 0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SampleColors.Overlay)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = if (handsCount > 0) "Hands detected: $handsCount" else "Tap Start to begin hand tracking",
            color = SampleColors.TextTertiary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(8.dp),
        )
    }
}

@Composable
private fun HandStartingIndicator() {
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
private fun ErrorBanner(errorMessage: String?, onRetry: () -> Unit) {
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

private const val PERCENT_MULTIPLIER = 100
