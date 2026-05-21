package io.github.kmpfacelink.sample.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CARD_CORNER_RADIUS = 12.dp
private val CARD_MIN_WIDTH = 240.dp
private val CARD_PADDING = 16.dp
private val ICON_SIZE = 40.dp
private val ICON_TEXT_SPACING = 16.dp
private val TEXT_CHEVRON_SPACING = 12.dp

/**
 * Home/landing screen showing feature cards for each tracking mode.
 */
@Composable
public fun HomeScreen(
    onModeChange: (TrackingMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SampleColors.Background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        HomeHeader()
        FeatureCardList(
            onModeChange = onModeChange,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HomeHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            text = "KMP-FaceLink",
            color = SampleColors.TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Cross-platform face, hand & body tracking SDK",
            color = SampleColors.TextTertiary,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun FeatureCardList(
    onModeChange: (TrackingMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        items(featureCards) { card ->
            FeatureCard(card = card, onClick = { onModeChange(card.mode) })
        }
    }
}

@Composable
private fun FeatureCard(
    card: FeatureCardData,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .widthIn(min = CARD_MIN_WIDTH)
            .fillMaxWidth()
            .clip(RoundedCornerShape(CARD_CORNER_RADIUS))
            .background(SampleColors.Surface)
            .semantics { role = Role.Button }
            .clickable(onClick = onClick)
            .padding(CARD_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = card.icon,
            fontSize = 28.sp,
            modifier = Modifier.size(ICON_SIZE),
        )
        Spacer(modifier = Modifier.width(ICON_TEXT_SPACING))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = card.title,
                color = SampleColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = card.description,
                color = SampleColors.TextTertiary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(TEXT_CHEVRON_SPACING))
        Text(
            text = "\u203A",
            color = SampleColors.TextTertiary,
            fontSize = 20.sp,
        )
    }
}

private data class FeatureCardData(
    val mode: TrackingMode,
    val icon: String,
    val title: String,
    val description: String,
)

private val featureCards = listOf(
    FeatureCardData(
        mode = TrackingMode.FACE,
        icon = "\uD83D\uDE42",
        title = "Face Tracking",
        description = "52 ARKit blend shapes, head pose, and facial landmarks with real-time visualization.",
    ),
    FeatureCardData(
        mode = TrackingMode.HAND,
        icon = "\u270B",
        title = "Hand Tracking",
        description = "21-point hand landmarks with gesture recognition for both hands.",
    ),
    FeatureCardData(
        mode = TrackingMode.HOLISTIC,
        icon = "\uD83E\uDDD1",
        title = "Holistic Tracking",
        description = "Combined face + hand tracking in a single pipeline for full upper-body capture.",
    ),
    FeatureCardData(
        mode = TrackingMode.ACTIONS,
        icon = "\u26A1",
        title = "Actions",
        description = "High-level action detection built on top of face tracking data.",
    ),
    FeatureCardData(
        mode = TrackingMode.VOICE,
        icon = "\uD83C\uDFA4",
        title = "Voice",
        description = "TTS with real-time lip sync blend shapes and speech recognition.",
    ),
    FeatureCardData(
        mode = TrackingMode.INTERACT,
        icon = "💬",
        title = "Interact",
        description = "Live stream event feed with mock chat, gifts, raids, and action triggers.",
    ),
)
