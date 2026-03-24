package io.github.kmpfacelink.sample.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.FaceTrackingData

private const val MAX_DISPLAYED_SHAPES = 15
private const val HIGH_THRESHOLD = 0.7f
private const val MEDIUM_THRESHOLD = 0.3f
private const val PERCENT_MULTIPLIER = 100

/**
 * Scrollable list of blend shape bars showing the top values.
 */
@Composable
public fun BlendShapeBars(
    trackingData: FaceTrackingData?,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val textMeasurer = rememberTextMeasurer()

    val entries = if (trackingData?.isTracking == true) {
        trackingData.blendShapes.entries
            .sortedByDescending { it.value }
            .take(MAX_DISPLAYED_SHAPES)
    } else {
        emptyList()
    }

    Column(
        modifier = modifier
            .background(SampleColors.Overlay)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .verticalScroll(scrollState),
    ) {
        if (entries.isEmpty()) {
            Text(
                text = "Tap Start to begin face tracking",
                color = SampleColors.TextTertiary,
                fontSize = 12.sp,
                modifier = Modifier.padding(8.dp),
            )
        } else {
            entries.forEach { (shape, value) ->
                BlendShapeBarRow(shape, value, textMeasurer)
            }
        }
    }
}

@Composable
private fun BlendShapeBarRow(
    shape: BlendShape,
    value: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = shape.arKitName,
            color = SampleColors.TextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            modifier = Modifier.width(130.dp),
        )

        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(12.dp),
        ) {
            val barW = size.width
            val barH = size.height

            drawRoundRect(
                color = SampleColors.ChipDefault,
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barH / 2),
            )

            val fillW = (barW * value.coerceIn(0f, 1f))
            if (fillW > 0f) {
                val barColor = when {
                    value > HIGH_THRESHOLD -> SampleColors.BarHigh
                    value > MEDIUM_THRESHOLD -> SampleColors.BarMedium
                    else -> SampleColors.BarLow
                }
                drawRoundRect(
                    color = barColor,
                    size = Size(fillW, barH),
                    cornerRadius = CornerRadius(barH / 2),
                )
            }

            val label = "${(value * PERCENT_MULTIPLIER).toInt()}%"
            val textResult = textMeasurer.measure(
                text = label,
                style = TextStyle(
                    color = SampleColors.TextPrimary,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                ),
            )
            drawText(
                textLayoutResult = textResult,
                topLeft = Offset(
                    barW - textResult.size.width - 4f,
                    (barH - textResult.size.height) / 2,
                ),
            )
        }
    }
}
