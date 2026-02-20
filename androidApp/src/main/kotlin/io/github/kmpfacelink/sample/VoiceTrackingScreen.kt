package io.github.kmpfacelink.sample

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.sample.ui.SampleColors
import io.github.kmpfacelink.voice.VoicePipeline
import io.github.kmpfacelink.voice.VoicePipelineConfig
import io.github.kmpfacelink.voice.VoicePipelineState
import io.github.kmpfacelink.voice.audio.createAudioPlayer
import io.github.kmpfacelink.voice.audio.createAudioRecorder
import io.github.kmpfacelink.voice.tts.TtsConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private enum class TtsProvider { VOICEVOX, OPENAI, ELEVENLABS }

@Suppress("LongMethod")
@Composable
internal fun VoiceTrackingScreen(
    onModeChange: (TrackingMode) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var selectedProvider by remember { mutableStateOf(TtsProvider.VOICEVOX) }
    var apiKey by remember { mutableStateOf("") }
    var voiceId by remember { mutableStateOf("alloy") }
    var voicevoxHost by remember { mutableStateOf("localhost") }
    var speakText by remember { mutableStateOf("Hello, I am FaceLink!") }

    var pipeline by remember { mutableStateOf<VoicePipeline?>(null) }
    var pipelineState by remember { mutableStateOf<VoicePipelineState>(VoicePipelineState.Idle) }
    val lipSyncShapes = remember { mutableStateMapOf<BlendShape, Float>() }
    var speakJob by remember { mutableStateOf<Job?>(null) }
    var stateJob by remember { mutableStateOf<Job?>(null) }
    var lipSyncJob by remember { mutableStateOf<Job?>(null) }
    var transcription by remember { mutableStateOf("") }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED,
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasMicPermission = granted }

    DisposableEffect(Unit) {
        onDispose {
            speakJob?.cancel()
            stateJob?.cancel()
            lipSyncJob?.cancel()
            pipeline?.release()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SampleColors.Background)
            .verticalScroll(rememberScrollState()),
    ) {
        VoiceTopBar(onModeChange)

        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Provider selection
            Text("TTS Provider", color = SampleColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            ProviderChips(selectedProvider) { selectedProvider = it }

            // Provider config
            ProviderConfigForm(
                provider = selectedProvider,
                apiKey = apiKey,
                voiceId = voiceId,
                voicevoxHost = voicevoxHost,
                onApiKeyChange = { apiKey = it },
                onVoiceIdChange = { voiceId = it },
                onHostChange = { voicevoxHost = it },
            )

            // Speak controls
            Text("Speak", color = SampleColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = speakText,
                onValueChange = { speakText = it },
                label = { Text("Text to speak") },
                colors = voiceTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val config = buildTtsConfig(selectedProvider, apiKey, voiceId, voicevoxHost)
                        pipeline?.release()
                        val newPipeline = VoicePipeline(
                            config = VoicePipelineConfig(ttsConfig = config),
                            audioRecorder = createAudioRecorder(),
                            audioPlayer = createAudioPlayer(),
                        )
                        pipeline = newPipeline
                        stateJob?.cancel()
                        stateJob = scope.launch { newPipeline.state.collect { pipelineState = it } }
                        lipSyncJob?.cancel()
                        lipSyncJob = scope.launch {
                            newPipeline.lipSyncOutput.collect { shapes ->
                                lipSyncShapes.clear()
                                lipSyncShapes.putAll(shapes)
                            }
                        }
                        speakJob?.cancel()
                        speakJob = scope.launch { newPipeline.speak(speakText).collect { } }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SampleColors.Primary),
                ) { Text("Speak") }

                // Microphone button
                Button(
                    onClick = {
                        if (!hasMicPermission) {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            return@Button
                        }
                        val p = pipeline ?: return@Button
                        if (pipelineState == VoicePipelineState.Listening) {
                            scope.launch {
                                val result = p.stopListening()
                                transcription = result?.text ?: "(no result)"
                            }
                        } else {
                            scope.launch { p.startListening() }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (pipelineState == VoicePipelineState.Listening) {
                            SampleColors.ErrorRed
                        } else {
                            SampleColors.Primary
                        },
                    ),
                ) {
                    Text(
                        if (pipelineState == VoicePipelineState.Listening) "Stop Mic" else "Listen",
                    )
                }
            }

            // Pipeline state
            PipelineStateBadge(pipelineState)

            // Transcription
            if (transcription.isNotEmpty()) {
                Text("Transcription", color = SampleColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(transcription, color = SampleColors.TextSecondary, fontSize = 12.sp)
            }

            // Lip sync bars
            Text("Lip Sync", color = SampleColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            LipSyncBars(lipSyncShapes)
        }

        Spacer(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(16.dp),
        )
    }
}

@Composable
private fun VoiceTopBar(onModeChange: (TrackingMode) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SampleColors.Overlay)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text("Voice", color = SampleColors.TextPrimary, fontSize = 14.sp)
        ModeToggle(currentMode = TrackingMode.VOICE, onModeChange = onModeChange)
    }
}

@Composable
private fun ProviderChips(selected: TtsProvider, onSelect: (TtsProvider) -> Unit) {
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
private fun ProviderConfigForm(
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
private fun PipelineStateBadge(state: VoicePipelineState) {
    val (text, color) = when (state) {
        is VoicePipelineState.Idle -> "Idle" to SampleColors.StatusInactive
        is VoicePipelineState.Processing -> "Processing..." to SampleColors.WarningYellow
        is VoicePipelineState.Speaking -> "Speaking" to SampleColors.StatusActive
        is VoicePipelineState.Listening -> "Listening..." to SampleColors.Primary
        is VoicePipelineState.Error -> "Error: ${state.message}" to SampleColors.ErrorRed
    }
    Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun LipSyncBars(shapes: Map<BlendShape, Float>) {
    val mouthShapes = shapes.entries
        .filter { it.key.arKitName.startsWith("jaw") || it.key.arKitName.startsWith("mouth") }
        .sortedByDescending { it.value }
        .take(8)

    if (mouthShapes.isEmpty()) {
        Text("No lip sync data", color = SampleColors.TextTertiary, fontSize = 11.sp)
    } else {
        mouthShapes.forEach { (shape, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = shape.arKitName,
                    color = SampleColors.TextTertiary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(0.4f),
                )
                Box(
                    modifier = Modifier
                        .weight(0.6f)
                        .height(6.dp)
                        .background(SampleColors.ChipDefault),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(value.coerceIn(0f, 1f))
                            .height(6.dp)
                            .background(SampleColors.BarLow),
                    )
                }
            }
        }
    }
}

@Composable
private fun voiceTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = SampleColors.TextPrimary,
    unfocusedTextColor = SampleColors.TextSecondary,
    focusedLabelColor = SampleColors.Primary,
    unfocusedLabelColor = SampleColors.TextTertiary,
)

private fun buildTtsConfig(
    provider: TtsProvider,
    apiKey: String,
    voiceId: String,
    voicevoxHost: String,
): TtsConfig = when (provider) {
    TtsProvider.VOICEVOX -> TtsConfig.Voicevox(host = voicevoxHost)
    TtsProvider.OPENAI -> TtsConfig.OpenAiTts(apiKey = apiKey, voice = voiceId)
    TtsProvider.ELEVENLABS -> TtsConfig.ElevenLabs(apiKey = apiKey, voiceId = voiceId)
}
