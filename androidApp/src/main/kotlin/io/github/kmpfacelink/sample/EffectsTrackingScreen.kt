package io.github.kmpfacelink.sample

import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.effects.EffectEngine
import io.github.kmpfacelink.effects.EffectOutput
import io.github.kmpfacelink.effects.cartoonEyesEffect
import io.github.kmpfacelink.effects.catEarsEffect
import io.github.kmpfacelink.effects.glassesEffect
import io.github.kmpfacelink.effects.openPalmParticlesEffect
import io.github.kmpfacelink.effects.smileHeartsEffect
import io.github.kmpfacelink.effects.winkSparkleEffect
import io.github.kmpfacelink.internal.PreviewableFaceTracker
import io.github.kmpfacelink.sample.ui.FpsOverlay
import io.github.kmpfacelink.sample.ui.SampleColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private data class EffectToggle(val id: String, val label: String)

private val BUILT_IN_EFFECTS = listOf(
    EffectToggle("catEars", "Cat Ears"),
    EffectToggle("glasses", "Glasses"),
    EffectToggle("smileHearts", "Smile Hearts"),
    EffectToggle("winkSparkle", "Wink Sparkle"),
    EffectToggle("cartoonEyes", "Cartoon Eyes"),
    EffectToggle("openPalmParticles", "Palm Particles"),
)

@Suppress("LongMethod")
@Composable
internal fun EffectsTrackingScreen(
    activity: MainActivity,
    onModeChange: (TrackingMode) -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val state by activity.faceTracker.state.collectAsState()
    val trackingData by activity.faceTrackingDataState.collectAsState()
    val isTracking = state == TrackingState.TRACKING

    val engine = remember { EffectEngine() }
    var effectOutput by remember { mutableStateOf(EffectOutput.EMPTY) }
    var enabledEffects by remember { mutableStateOf(emptySet<String>()) }
    var processJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(isTracking) {
        if (isTracking) {
            processJob = scope.launch {
                activity.faceTracker.trackingData.collect { data ->
                    effectOutput = engine.processFace(data)
                }
            }
        } else {
            processJob?.cancel()
        }
        onDispose { }
    }

    DisposableEffect(Unit) {
        onDispose {
            processJob?.cancel()
            engine.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (activity.faceTracker is PreviewableFaceTracker) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        (activity.faceTracker as PreviewableFaceTracker)
                            .setSurfaceProvider(surfaceProvider)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Anchor overlay
        AnchorOverlay(effectOutput, modifier = Modifier.fillMaxSize())

        Column(modifier = Modifier.fillMaxSize()) {
            EffectsTopBar(state, isTracking, trackingData?.timestampMs ?: 0L, onStartClick, onStopClick)
            ModeToggle(currentMode = TrackingMode.EFFECTS, onModeChange = onModeChange)

            // Effect toggles
            EffectToggles(
                enabledEffects = enabledEffects,
                engine = engine,
                scope = scope,
                onToggle = { enabledEffects = it },
            )

            Spacer(modifier = Modifier.weight(1f))

            // Active effects panel
            EffectsBottomPanel(effectOutput)
        }
    }
}

@Composable
private fun EffectsTopBar(
    state: TrackingState,
    isTracking: Boolean,
    frameTimestamp: Long,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SampleColors.Overlay)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Effects", color = SampleColors.TextPrimary, fontSize = 14.sp)
        Text(" — $state", color = SampleColors.TextSecondary, fontSize = 12.sp)
        if (isTracking) {
            FpsOverlay(frameTimestamp, modifier = Modifier.padding(start = 8.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = { if (isTracking) onStopClick() else onStartClick() }) {
            Text(if (isTracking) "Stop" else "Start")
        }
    }
}

@Composable
private fun EffectToggles(
    enabledEffects: Set<String>,
    engine: EffectEngine,
    scope: kotlinx.coroutines.CoroutineScope,
    onToggle: (Set<String>) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(SampleColors.OverlayLight)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        BUILT_IN_EFFECTS.forEach { toggle ->
            val isEnabled = toggle.id in enabledEffects
            FilterChip(
                selected = isEnabled,
                onClick = {
                    scope.launch {
                        if (isEnabled) {
                            engine.removeEffect(toggle.id)
                            onToggle(enabledEffects - toggle.id)
                        } else {
                            engine.addEffect(createEffect(toggle.id))
                            onToggle(enabledEffects + toggle.id)
                        }
                    }
                },
                label = { Text(toggle.label, fontSize = 9.sp) },
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

@Composable
private fun AnchorOverlay(output: EffectOutput, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val viewW = size.width
        val viewH = size.height
        for ((id, anchor) in output.anchors) {
            val px = anchor.position.x * viewW
            val py = anchor.position.y * viewH
            drawCircle(
                color = SampleColors.LandmarkEye,
                radius = 8.dp.toPx(),
                center = Offset(px, py),
            )
        }
    }
}

@Composable
private fun EffectsBottomPanel(output: EffectOutput) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SampleColors.Overlay)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(8.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Active Effects", color = SampleColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        if (output.activeEffects.isEmpty()) {
            Text("No active effects", color = SampleColors.TextTertiary, fontSize = 11.sp)
        } else {
            output.activeEffects.forEach { active ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${active.id} (${active.type.name})",
                        color = SampleColors.TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(0.5f),
                    )
                    Box(
                        modifier = Modifier
                            .weight(0.5f)
                            .height(6.dp)
                            .background(SampleColors.ChipDefault),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(active.intensity.coerceIn(0f, 1f))
                                .height(6.dp)
                                .background(SampleColors.BarLow),
                        )
                    }
                }
            }
        }
    }
}

@Suppress("TooGenericExceptionCaught")
@OptIn(io.github.kmpfacelink.ExperimentalFaceLinkApi::class)
private fun createEffect(id: String): io.github.kmpfacelink.effects.Effect = when (id) {
    "catEars" -> catEarsEffect(id)
    "glasses" -> glassesEffect(id)
    "smileHearts" -> smileHeartsEffect(id)
    "winkSparkle" -> winkSparkleEffect(id)
    "cartoonEyes" -> cartoonEyesEffect(id)
    "openPalmParticles" -> openPalmParticlesEffect(id)
    else -> error("Unknown effect: $id")
}
