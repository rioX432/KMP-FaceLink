package io.github.kmpfacelink.sample.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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

        val contourStroke = 1.4f.dp.toPx()
        val contourHaloStroke = 3.4f.dp.toPx()

        fun drawContour(indices: IntArray, color: Color) {
            for (i in 0 until indices.size - 1) {
                val a = indices[i]
                val b = indices[i + 1]
                if (a < landmarks.size && b < landmarks.size) {
                    drawLine(
                        color = Color.Black.copy(alpha = 0.8f),
                        start = Offset(lx(a), ly(a)),
                        end = Offset(lx(b), ly(b)),
                        strokeWidth = contourHaloStroke,
                    )
                    drawLine(
                        color = color.copy(alpha = 0.95f),
                        start = Offset(lx(a), ly(a)),
                        end = Offset(lx(b), ly(b)),
                        strokeWidth = contourStroke,
                    )
                }
            }
        }

        drawContour(FaceContours.FACE_OVAL, SampleColors.LandmarkFace)
        drawContour(FaceContours.LEFT_EYE, SampleColors.LandmarkEye)
        drawContour(FaceContours.RIGHT_EYE, SampleColors.LandmarkEye)
        drawContour(FaceContours.LEFT_EYEBROW, SampleColors.LandmarkBrow)
        drawContour(FaceContours.RIGHT_EYEBROW, SampleColors.LandmarkBrow)
        drawContour(FaceContours.LIPS_OUTER, SampleColors.LandmarkLip)
        drawContour(FaceContours.LIPS_INNER, SampleColors.LandmarkLip)
        drawContour(FaceContours.LEFT_IRIS, SampleColors.LandmarkEye)
        drawContour(FaceContours.RIGHT_IRIS, SampleColors.LandmarkEye)

        val dotRadius = 1.35f.dp.toPx()
        val haloRadius = 2.35f.dp.toPx()
        val dotColor = SampleColors.LandmarkFace
        for (lm in landmarks) {
            val px = lm.x * sourceImageWidth * scaleFactor + offsetX
            val py = lm.y * sourceImageHeight * scaleFactor + offsetY
            drawCircle(
                color = Color.Black.copy(alpha = 0.85f),
                radius = haloRadius,
                center = Offset(px, py),
            )
            drawCircle(
                color = dotColor,
                radius = dotRadius,
                center = Offset(px, py),
            )
        }
    }
}
