package io.github.kmpfacelink.sample.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Common scaffold for tracking screens.
 *
 * Provides the shared layout of camera background, landmark overlay,
 * and a column with top content, [ModeToggle], spacer, and bottom content.
 *
 * @param currentMode The currently active [TrackingMode] shown in the mode toggle.
 * @param onModeChange Callback when the user selects a different mode.
 * @param camera Slot for the full-screen camera preview.
 * @param overlay Slot for the landmark overlay drawn on top of the camera.
 * @param topContent Slot for controls above the mode toggle (e.g. top bar, error banner).
 * @param bottomContent Slot for content anchored to the bottom (e.g. blend shape bars).
 * @param showModeToggle Whether this Compose shell owns mode navigation.
 * @param modifier Modifier applied to the root [Box].
 */
@Composable
internal fun TrackingScreenScaffold(
    currentMode: TrackingMode,
    onModeChange: (TrackingMode) -> Unit,
    camera: @Composable () -> Unit,
    overlay: @Composable () -> Unit,
    topContent: @Composable ColumnScope.() -> Unit,
    bottomContent: @Composable ColumnScope.() -> Unit,
    showModeToggle: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        camera()

        overlay()

        Column(modifier = Modifier.fillMaxSize()) {
            topContent()
            if (showModeToggle) {
                ModeToggle(currentMode = currentMode, onModeChange = onModeChange)
            }

            Spacer(modifier = Modifier.weight(1f))

            bottomContent()
        }
    }
}
