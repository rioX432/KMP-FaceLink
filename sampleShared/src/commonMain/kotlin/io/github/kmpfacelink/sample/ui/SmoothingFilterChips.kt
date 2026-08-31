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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kmpfacelink.api.FaceTracker
import io.github.kmpfacelink.model.SmoothingConfig

/**
 * Row of filter chips for selecting the smoothing algorithm.
 */
@Composable
public fun SmoothingFilterChips(
    faceTracker: FaceTracker,
    initialConfig: SmoothingConfig,
    modifier: Modifier = Modifier,
) {
    var selectedFilter by remember(initialConfig) {
        mutableStateOf(sampleSmoothingOption(initialConfig))
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(SampleColors.OverlayLight)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        SampleSmoothingOption.entries.forEach { option ->
            val isSelected = selectedFilter == option
            val stateLabel = if (isSelected) "selected" else "not selected"
            FilterChip(
                selected = isSelected,
                modifier = Modifier.semantics {
                    contentDescription = "Smoothing filter: ${option.label}, $stateLabel"
                },
                onClick = {
                    selectedFilter = option
                    faceTracker.updateSmoothing(option.toConfig())
                },
                label = { Text(option.label, fontSize = 12.sp) },
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
