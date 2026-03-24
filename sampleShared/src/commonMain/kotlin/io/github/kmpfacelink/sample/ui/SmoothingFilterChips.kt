package io.github.kmpfacelink.sample.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kmpfacelink.api.FaceTracker
import io.github.kmpfacelink.model.SmoothingConfig

private val FILTER_LABELS = listOf("None", "EMA", "1Euro")

/**
 * Row of filter chips for selecting the smoothing algorithm.
 */
@Composable
public fun SmoothingFilterChips(
    faceTracker: FaceTracker,
    modifier: Modifier = Modifier,
) {
    var selectedFilter by remember { mutableStateOf("EMA") }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(SampleColors.OverlayLight)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        FILTER_LABELS.forEach { label ->
            FilterChip(
                selected = selectedFilter == label,
                onClick = {
                    selectedFilter = label
                    val config = when (label) {
                        "None" -> SmoothingConfig.None
                        "EMA" -> SmoothingConfig.Ema()
                        "1Euro" -> SmoothingConfig.OneEuro()
                        else -> return@FilterChip
                    }
                    faceTracker.updateSmoothing(config)
                },
                label = { Text(label, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = SampleColors.ChipDefault,
                    labelColor = SampleColors.TextPrimary,
                    selectedContainerColor = SampleColors.ChipSelected,
                    selectedLabelColor = SampleColors.TextPrimary,
                ),
            )
        }
    }
}
