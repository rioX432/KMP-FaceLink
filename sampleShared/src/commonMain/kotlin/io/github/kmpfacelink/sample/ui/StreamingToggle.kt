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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Toggle chips for enabling/disabling VMC and iFacialMocap protocol output.
 *
 * This is a UI-only placeholder; actual streaming integration requires
 * wiring to the kmp-facelink-vmc / kmp-facelink-ifm modules.
 */
@Composable
public fun StreamingToggle(
    modifier: Modifier = Modifier,
    onVmcToggle: (Boolean) -> Unit = {},
    onIfmToggle: (Boolean) -> Unit = {},
) {
    var vmcEnabled by remember { mutableStateOf(false) }
    var ifmEnabled by remember { mutableStateOf(false) }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(SampleColors.OverlayLight)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            text = "Stream:",
            color = SampleColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        val vmcState = if (vmcEnabled) "enabled" else "disabled"
        FilterChip(
            selected = vmcEnabled,
            onClick = {
                vmcEnabled = !vmcEnabled
                onVmcToggle(vmcEnabled)
            },
            modifier = Modifier.semantics {
                contentDescription = "VMC streaming, $vmcState"
            },
            label = { Text("VMC", fontSize = 12.sp) },
            colors = streamingChipColors(),
        )
        val ifmState = if (ifmEnabled) "enabled" else "disabled"
        FilterChip(
            selected = ifmEnabled,
            onClick = {
                ifmEnabled = !ifmEnabled
                onIfmToggle(ifmEnabled)
            },
            modifier = Modifier.semantics {
                contentDescription = "iFacialMocap streaming, $ifmState"
            },
            label = { Text("iFacialMocap", fontSize = 12.sp) },
            colors = streamingChipColors(),
        )
    }
}

@Composable
private fun streamingChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = SampleColors.ChipDefault,
    labelColor = SampleColors.TextPrimary,
    selectedContainerColor = SampleColors.StatusActive.copy(alpha = 0.4f),
    selectedLabelColor = SampleColors.TextPrimary,
)
