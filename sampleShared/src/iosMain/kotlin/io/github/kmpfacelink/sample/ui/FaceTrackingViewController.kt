package io.github.kmpfacelink.sample.ui

import androidx.compose.ui.window.ComposeUIViewController
import io.github.kmpfacelink.api.FaceTracker
import io.github.kmpfacelink.model.FaceTrackingData
import kotlinx.coroutines.flow.StateFlow
import platform.UIKit.UIViewController

/**
 * Creates a UIViewController hosting the shared [FaceTrackingScreen] via Compose Multiplatform.
 *
 * Use from Swift:
 * ```swift
 * let vc = FaceTrackingViewControllerKt.FaceTrackingViewController(
 *     faceTracker: tracker,
 *     trackingDataState: dataState,
 *     onModeChange: { mode in ... },
 *     onStartClick: { ... },
 *     onStopClick: { ... }
 * )
 * ```
 */
@Suppress("FunctionName")
public fun FaceTrackingViewController(
    faceTracker: FaceTracker,
    trackingDataState: StateFlow<FaceTrackingData?>,
    onModeChange: (TrackingMode) -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
): UIViewController = ComposeUIViewController {
    FaceTrackingScreen(
        faceTracker = faceTracker,
        trackingDataState = trackingDataState,
        onModeChange = onModeChange,
        onStartClick = onStartClick,
        onStopClick = onStopClick,
    )
}
