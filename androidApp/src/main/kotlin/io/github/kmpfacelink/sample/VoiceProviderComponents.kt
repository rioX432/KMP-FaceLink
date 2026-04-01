package io.github.kmpfacelink.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kmpfacelink.sample.ui.SampleColors

@Composable
internal fun ProviderChips(selected: TtsProvider, onSelect: (TtsProvider) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TtsProvider.entries.forEach { provider ->
            FilterChip(
                selected = selected == provider,
                onClick = { onSelect(provider) },
                label = { Text(provider.name, fontSize = 11.sp) },
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

@Suppress("LongParameterList")
@Composable
internal fun ProviderConfigForm(
    provider: TtsProvider,
    apiKey: String,
    voiceId: String,
    voicevoxHost: String,
    onApiKeyChange: (String) -> Unit,
    onVoiceIdChange: (String) -> Unit,
    onHostChange: (String) -> Unit,
) {
    val tfColors = voiceTextFieldColors()
    when (provider) {
        TtsProvider.VOICEVOX -> {
            OutlinedTextField(
                value = voicevoxHost,
                onValueChange = onHostChange,
                label = { Text("Host") },
                colors = tfColors,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        TtsProvider.OPENAI -> {
            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                label = { Text("API Key") },
                colors = tfColors,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = voiceId,
                onValueChange = onVoiceIdChange,
                label = { Text("Voice") },
                colors = tfColors,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        TtsProvider.ELEVENLABS -> {
            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                label = { Text("API Key") },
                colors = tfColors,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = voiceId,
                onValueChange = onVoiceIdChange,
                label = { Text("Voice ID") },
                colors = tfColors,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
    }
}

@Composable
internal fun voiceTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = SampleColors.TextPrimary,
    unfocusedTextColor = SampleColors.TextSecondary,
    focusedLabelColor = SampleColors.Primary,
    unfocusedLabelColor = SampleColors.TextTertiary,
)
