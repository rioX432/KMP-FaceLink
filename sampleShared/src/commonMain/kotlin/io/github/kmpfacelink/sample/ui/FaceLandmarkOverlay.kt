package io.github.kmpfacelink.sample.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import io.github.kmpfacelink.model.FaceLandmark

/**
 * Draws face landmark dots and contour lines over the camera preview.
 */
@Composable
public fun FaceLandmarkOverlay(
    landmarks: List<FaceLandmark>,
    sourceImageWidth: Int,
    sourceImageHeight: Int,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val viewW = size.width
        val viewH = size.height

        if (sourceImageWidth <= 0 || sourceImageHeight <= 0) return@Canvas

        val scaleFactor = maxOf(viewW / sourceImageWidth, viewH / sourceImageHeight)
        val offsetX = (viewW - sourceImageWidth * scaleFactor) / 2f
        val offsetY = (viewH - sourceImageHeight * scaleFactor) / 2f

        fun lx(index: Int): Float =
            landmarks[index].x * sourceImageWidth * scaleFactor + offsetX
        fun ly(index: Int): Float =
            landmarks[index].y * sourceImageHeight * scaleFactor + offsetY

        val contourStroke = 1.dp.toPx()

        fun drawContour(indices: IntArray, color: androidx.compose.ui.graphics.Color) {
            for (i in 0 until indices.size - 1) {
                val a = indices[i]
                val b = indices[i + 1]
                if (a < landmarks.size && b < landmarks.size) {
                    drawLine(
                        color = color,
                        start = Offset(lx(a), ly(a)),
                        end = Offset(lx(b), ly(b)),
                        strokeWidth = contourStroke,
                    )
                }
            }
        }

        drawContour(FaceContours.FACE_OVAL, SampleColors.LandmarkFace.copy(alpha = 0.5f))
        drawContour(FaceContours.LEFT_EYE, SampleColors.LandmarkEye.copy(alpha = 0.6f))
        drawContour(FaceContours.RIGHT_EYE, SampleColors.LandmarkEye.copy(alpha = 0.6f))
        drawContour(FaceContours.LEFT_EYEBROW, SampleColors.LandmarkBrow.copy(alpha = 0.6f))
        drawContour(FaceContours.RIGHT_EYEBROW, SampleColors.LandmarkBrow.copy(alpha = 0.6f))
        drawContour(FaceContours.LIPS_OUTER, SampleColors.LandmarkLip.copy(alpha = 0.6f))
        drawContour(FaceContours.LIPS_INNER, SampleColors.LandmarkLip.copy(alpha = 0.6f))
        drawContour(FaceContours.LEFT_IRIS, SampleColors.LandmarkEye.copy(alpha = 0.7f))
        drawContour(FaceContours.RIGHT_IRIS, SampleColors.LandmarkEye.copy(alpha = 0.7f))

        val dotRadius = 1.2f.dp.toPx()
        val dotColor = SampleColors.LandmarkFace.copy(alpha = 0.35f)
        for (lm in landmarks) {
            val px = lm.x * sourceImageWidth * scaleFactor + offsetX
            val py = lm.y * sourceImageHeight * scaleFactor + offsetY
            drawCircle(
                color = dotColor,
                radius = dotRadius,
                center = Offset(px, py),
            )
        }
    }
}
