package io.github.kmpfacelink.sample.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Horizontal scrollable chip row for switching tracking modes.
 */
@Composable
public fun ModeToggle(
    currentMode: TrackingMode,
    onModeChange: (TrackingMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(SampleColors.OverlayLight)
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        TrackingMode.entries.forEach { mode ->
            FilterChip(
                selected = currentMode == mode,
                onClick = { onModeChange(mode) },
                label = { Text(mode.name, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = SampleColors.ChipDefault,
                    labelColor = SampleColors.TextPrimary,
                    selectedContainerColor = SampleColors.PrimaryDim,
                    selectedLabelColor = SampleColors.TextPrimary,
                ),
            )
        }
    }
}
