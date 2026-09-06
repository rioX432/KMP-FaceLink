package io.github.kmpfacelink.capture.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min

/**
 * Draws normalized MediaPipe landmarks over the separately rendered platform camera preview.
 *
 * [contentScale] must match the platform preview's centered FIT/FILL choice. This keeps both layers
 * on the same source-level aspect-ratio mapping without stretching landmarks. It does not establish
 * physical alignment: platform orientation, mirroring, camera crop, and native-view behavior still
 * require an exact-binary device control.
 */
@Composable
public fun FaceMeshOverlay(
    landmarks: FloatArray?,
    aspect: Float,
    modifier: Modifier = Modifier,
    contentScale: PreviewContentScale = PreviewContentScale.FIT,
) {
    Canvas(modifier) {
        val points = landmarks ?: return@Canvas
        val viewport = centeredPreviewViewport(
            sourceHeightOverWidth = aspect,
            targetWidth = size.width,
            targetHeight = size.height,
            contentScale = contentScale,
        )
        var i = 0
        while (i + 1 < points.size) {
            drawCircle(
                color = Color(0xFF66E0A3),
                radius = POINT_RADIUS,
                center = Offset(
                    viewport.mapX(points[i]),
                    viewport.mapY(points[i + 1]),
                ),
            )
            i += 3 // x, y, z interleaved; z is not drawn
        }
    }
}

/** Centered aspect-ratio policy shared by the camera preview and normalized overlay. */
public enum class PreviewContentScale {
    /** Show the complete source, letterboxing the shorter target dimension when necessary. */
    FIT,

    /** Cover the complete target, cropping the longer source dimension equally at both edges. */
    FILL,
}

internal data class PreviewViewport(
    val drawWidth: Float,
    val drawHeight: Float,
    val offsetX: Float,
    val offsetY: Float,
) {
    fun mapX(normalizedX: Float): Float = offsetX + normalizedX * drawWidth

    fun mapY(normalizedY: Float): Float = offsetY + normalizedY * drawHeight
}

/**
 * Maps a normalized source frame into a centered target without stretching it.
 *
 * `sourceHeightOverWidth` matches [io.github.kmpfacelink.capture.CaptureUiState.aspect]. This pure
 * geometry contract deliberately excludes camera rotation and mirroring; platform adapters must
 * resolve those independently and physical alignment remains a device observation.
 */
internal fun centeredPreviewViewport(
    sourceHeightOverWidth: Float,
    targetWidth: Float,
    targetHeight: Float,
    contentScale: PreviewContentScale,
): PreviewViewport {
    val sourceAspect = sourceHeightOverWidth.takeIf { it.isFinite() && it > 0f } ?: 1f
    if (targetWidth <= 0f || targetHeight <= 0f) return PreviewViewport(0f, 0f, 0f, 0f)
    val widthScale = targetWidth
    val heightScale = targetHeight / sourceAspect
    val scale = when (contentScale) {
        PreviewContentScale.FIT -> min(widthScale, heightScale)
        PreviewContentScale.FILL -> max(widthScale, heightScale)
    }
    val drawWidth = scale
    val drawHeight = scale * sourceAspect
    return PreviewViewport(
        drawWidth = drawWidth,
        drawHeight = drawHeight,
        offsetX = (targetWidth - drawWidth) / 2f,
        offsetY = (targetHeight - drawHeight) / 2f,
    )
}

private const val POINT_RADIUS = 1.6f
