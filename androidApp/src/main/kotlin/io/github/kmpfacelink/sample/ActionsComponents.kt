package io.github.kmpfacelink.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kmpfacelink.actions.emotion.Emotion
import io.github.kmpfacelink.actions.emotion.EmotionResult
import io.github.kmpfacelink.actions.record.TrackingSession
import io.github.kmpfacelink.sample.ui.SampleColors

@Composable
internal fun EmotionBars(result: EmotionResult?) {
    if (result == null) {
        Text(
            text = "No emotion data",
            color = SampleColors.TextTertiary,
            fontSize = 11.sp,
        )
        return
    }
    val scores = result.scores.ifEmpty { Emotion.entries.associateWith { 0f } }
    Emotion.entries.forEach { emotion ->
        val score = scores[emotion] ?: 0f
        val isDominant = result.emotion == emotion
        val dominantLabel = if (isDominant) ", dominant emotion" else ""
        val scorePercent = (score.coerceIn(0f, 1f) * 100).toInt()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .semantics {
                    contentDescription = "${emotion.name} emotion: $scorePercent%$dominantLabel"
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = emotion.name,
                color = if (isDominant) SampleColors.BarLow else SampleColors.TextTertiary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(0.3f),
            )
            Box(
                modifier = Modifier
                    .weight(0.7f)
                    .height(8.dp)
                    .background(SampleColors.ChipDefault),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(score.coerceIn(0f, 1f))
                        .height(8.dp)
                        .background(if (isDominant) SampleColors.BarLow else SampleColors.BarMedium),
                )
            }
        }
    }
}

@Composable
internal fun TriggerLeds(activeTriggers: List<String>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        TRIGGER_NAMES.forEach { name ->
            val isActive = name in activeTriggers
            val stateLabel = if (isActive) "active" else "inactive"
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics { contentDescription = "$name trigger: $stateLabel" },
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (isActive) SampleColors.StatusActive else SampleColors.StatusInactive)
                        .semantics { contentDescription = "Emotion: $name" },
                )
                Text(
                    text = name,
                    color = SampleColors.TextTertiary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
internal fun RecordingControls(
    isRecording: Boolean,
    session: TrackingSession?,
    onRecordToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        Button(
            onClick = onRecordToggle,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRecording) SampleColors.ErrorRed else SampleColors.Primary,
            ),
        ) {
            Text(if (isRecording) "Stop Rec" else "Record")
        }
        session?.let { s ->
            Text(
                text = "${s.frameCount} frames, ${s.durationMs}ms, %.1f fps".format(s.averageFps),
                color = SampleColors.TextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
