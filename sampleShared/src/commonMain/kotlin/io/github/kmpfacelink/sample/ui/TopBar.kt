package io.github.kmpfacelink.sample.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.model.HeadTransform

/**
 * Top status bar showing tracking state, head rotation, and start/stop button.
 */
@Composable
public fun TopBar(
    state: TrackingState,
    headTransform: HeadTransform?,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isTracking = state == TrackingState.TRACKING
    Column(
        modifier = modifier
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
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { if (isTracking) onStopClick() else onStartClick() }) {
                Text(if (isTracking) "Stop" else "Start")
            }
        }
        HeadTransformText(headTransform)
    }
}

@Composable
private fun HeadTransformText(headTransform: HeadTransform?) {
    val headText = if (headTransform != null) {
        formatHeadTransform(headTransform.pitch, headTransform.yaw, headTransform.roll)
    } else {
        "P=0.0  Y=0.0  R=0.0"
    }
    Text(
        text = headText,
        color = SampleColors.TextSecondary,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
    )
}

private fun formatHeadTransform(pitch: Float, yaw: Float, roll: Float): String {
    // Format with one decimal place using Kotlin multiplatform-safe approach
    fun fmt(v: Float): String {
        val rounded = (v * 10).toInt() / 10.0
        return if (rounded == rounded.toLong().toDouble()) {
            "${rounded.toLong()}.0"
        } else {
            rounded.toString()
        }
    }
    return "P=${fmt(pitch)}  Y=${fmt(yaw)}  R=${fmt(roll)}"
}
