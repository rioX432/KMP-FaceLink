package io.github.kmpfacelink.sample.ui

import androidx.compose.ui.window.ComposeUIViewController
import io.github.kmpfacelink.api.HandTracker
import io.github.kmpfacelink.model.HandTrackingData
import kotlinx.coroutines.flow.StateFlow
import platform.UIKit.UIViewController

/**
 * Creates a UIViewController hosting the shared [HandTrackingScreen] via Compose Multiplatform.
 *
 * Use from Swift:
 * ```swift
 * let vc = HandTrackingViewControllerKt.HandTrackingViewController(
 *     handTracker: tracker,
 *     trackingDataState: dataState,
 *     onModeChange: { mode in ... },
 *     onStartClick: { ... },
 *     onStopClick: { ... }
 * )
 * ```
 */
@Suppress("FunctionName")
public fun HandTrackingViewController(
    handTracker: HandTracker,
    trackingDataState: StateFlow<HandTrackingData?>,
    onModeChange: (TrackingMode) -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
): UIViewController = ComposeUIViewController {
    HandTrackingScreen(
        handTracker = handTracker,
        trackingDataState = trackingDataState,
        onModeChange = onModeChange,
        onStartClick = onStartClick,
        onStopClick = onStopClick,
        showModeToggle = false,
    )
}
