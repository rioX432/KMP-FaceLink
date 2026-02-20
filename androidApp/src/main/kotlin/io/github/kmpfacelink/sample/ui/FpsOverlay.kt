package io.github.kmpfacelink.sample.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private const val FPS_DISPLAY_COUNT = 30
private const val FPS_UPDATE_INTERVAL_MS = 500L
private const val MILLIS_PER_SECOND = 1000f

@Composable
internal fun FpsOverlay(
    frameTimestampMs: Long,
    modifier: Modifier = Modifier,
) {
    var fps by remember { mutableFloatStateOf(0f) }

    val ringBuffer = remember { LongArray(FPS_DISPLAY_COUNT) }
    var ringIndex = remember { 0 }

    LaunchedEffect(frameTimestampMs) {
        if (frameTimestampMs > 0L) {
            ringBuffer[ringIndex % FPS_DISPLAY_COUNT] = frameTimestampMs
            ringIndex++
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(FPS_UPDATE_INTERVAL_MS)
            val filled = ringIndex.coerceAtMost(FPS_DISPLAY_COUNT)
            if (filled >= 2) {
                val newest = ringBuffer[(ringIndex - 1) % FPS_DISPLAY_COUNT]
                val oldest = ringBuffer[ringIndex % FPS_DISPLAY_COUNT]
                val deltaMs = newest - oldest
                if (deltaMs > 0) {
                    fps = (filled - 1) * MILLIS_PER_SECOND / deltaMs
                }
            }
        }
    }

    Text(
        text = "%.0f FPS".format(fps),
        color = SampleColors.TextSecondary,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        modifier = modifier
            .background(SampleColors.Overlay)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
