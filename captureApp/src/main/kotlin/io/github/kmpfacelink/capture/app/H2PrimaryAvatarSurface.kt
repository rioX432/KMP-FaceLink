package io.github.kmpfacelink.capture.app

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import io.github.kmpfacelink.capture.H2CandidateRole
import io.github.kmpfacelink.capture.H2RenderedAvatarFrame
import io.github.kmpfacelink.capture.H2SurfaceVisibilityMode

/** Actual Compose consumer for the H2 primary avatar pixels; it cannot accept comparator frames. */
@Composable
internal fun H2PrimaryAvatarSurface(
    frame: H2RenderedAvatarFrame?,
    visibility: H2SurfaceVisibilityMode,
    onDrawn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = h2ParticipantFrameForVisibility(frame, visibility) ?: return
    val bitmap = remember(visible.rgba, visible.event.sequence) { visible.toBitmap() }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "Avatar output",
        modifier = modifier.size(192.dp).drawWithContent {
            drawContent()
            onDrawn()
        },
    )
}

internal fun h2ParticipantFrameForVisibility(
    primary: H2RenderedAvatarFrame?,
    visibility: H2SurfaceVisibilityMode,
): H2RenderedAvatarFrame? = when (visibility) {
    H2SurfaceVisibilityMode.PRIMARY_ONLY -> primary?.also {
        require(it.role == H2CandidateRole.PRIMARY) { "participant surface accepts primary only" }
    }
    H2SurfaceVisibilityMode.NEITHER,
    H2SurfaceVisibilityMode.UNDECIDED,
    -> null
}

private fun H2RenderedAvatarFrame.toBitmap(): Bitmap {
    val colors = IntArray(width * height) { pixel ->
        val offset = pixel * RGBA_CHANNELS
        val red = rgba[offset].toInt() and BYTE_MASK
        val green = rgba[offset + 1].toInt() and BYTE_MASK
        val blue = rgba[offset + 2].toInt() and BYTE_MASK
        val alpha = rgba[offset + 3].toInt() and BYTE_MASK
        alpha shl ALPHA_SHIFT or red shl RED_SHIFT or green shl GREEN_SHIFT or blue
    }
    return Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888)
}

private const val RGBA_CHANNELS = 4
private const val BYTE_MASK = 0xff
private const val ALPHA_SHIFT = 24
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
