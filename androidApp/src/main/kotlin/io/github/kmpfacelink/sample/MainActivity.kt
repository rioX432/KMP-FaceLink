package io.github.kmpfacelink.sample

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.api.createFaceTracker
import io.github.kmpfacelink.api.createHandTracker
import io.github.kmpfacelink.api.createHolisticTracker
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HandTrackerConfig
import io.github.kmpfacelink.model.HandTrackingData
import io.github.kmpfacelink.model.HolisticTrackerConfig
import io.github.kmpfacelink.model.HolisticTrackingData
import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.sample.ui.HomeScreen
import io.github.kmpfacelink.sample.ui.SampleColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import io.github.kmpfacelink.sample.ui.TrackingMode as SharedTrackingMode

internal enum class TrackingMode {
    HOME, FACE, HAND, HOLISTIC, ACTIONS, VOICE
}

@Suppress("TooManyFunctions")
class MainActivity : ComponentActivity() {

    private val platformContext by lazy { PlatformContext(this, this) }

    internal val faceTracker by lazy {
        createFaceTracker(
            platformContext = platformContext,
            config = FaceTrackerConfig(
                smoothingConfig = SmoothingConfig.OneEuro(),
            ),
        )
    }

    private val handTracker by lazy {
        createHandTracker(
            platformContext = platformContext,
            config = HandTrackerConfig(
                smoothingConfig = SmoothingConfig.OneEuro(),
            ),
        )
    }

    private val holisticTracker by lazy {
        createHolisticTracker(
            platformContext = platformContext,
            config = HolisticTrackerConfig(
                faceConfig = FaceTrackerConfig(smoothingConfig = SmoothingConfig.OneEuro()),
                handConfig = HandTrackerConfig(smoothingConfig = SmoothingConfig.OneEuro()),
            ),
        )
    }

    internal val faceTrackingDataState = MutableStateFlow<FaceTrackingData?>(null)
    private val handTrackingDataState = MutableStateFlow<HandTrackingData?>(null)
    private val holisticTrackingDataState = MutableStateFlow<HolisticTrackingData?>(null)
    internal val trackingModeState = MutableStateFlow(TrackingMode.HOME)
    internal val errorState = MutableStateFlow<String?>(null)

    internal var faceCollectorJob: Job? = null
    private var handCollectorJob: Job? = null
    private var holisticCollectorJob: Job? = null

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startTracking()
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val mode by trackingModeState.collectAsState()
            MainScreen(
                mode = mode,
                onModeChange = { newMode -> switchMode(newMode) },
                onStartClick = { requestCameraAndStart() },
                onStopClick = { stopTracking() },
            )
        }
    }

    @Composable
    private fun MainScreen(
        mode: TrackingMode,
        onModeChange: (TrackingMode) -> Unit,
        onStartClick: () -> Unit,
        onStopClick: () -> Unit,
    ) {
        when (mode) {
            TrackingMode.HOME -> HomeScreen(
                onModeChange = { sharedMode -> onModeChange(sharedMode.toLocal()) },
            )
            TrackingMode.FACE -> FaceTrackingScreen(
                faceTracker = faceTracker,
                trackingDataState = faceTrackingDataState,
                onModeChange = onModeChange,
                onStartClick = onStartClick,
                onStopClick = onStopClick,
            )
            TrackingMode.HAND -> HandTrackingScreen(
                handTracker = handTracker,
                trackingDataState = handTrackingDataState,
                onModeChange = onModeChange,
                onStartClick = onStartClick,
                onStopClick = onStopClick,
            )
            TrackingMode.HOLISTIC -> HolisticTrackingScreen(
                holisticTracker = holisticTracker,
                trackingDataState = holisticTrackingDataState,
                onModeChange = onModeChange,
                onStartClick = onStartClick,
                onStopClick = onStopClick,
            )
            TrackingMode.ACTIONS -> ActionsTrackingScreen(
                activity = this,
                onModeChange = onModeChange,
                onStartClick = onStartClick,
                onStopClick = onStopClick,
            )
            TrackingMode.VOICE -> VoiceTrackingScreen(
                onModeChange = onModeChange,
            )
        }
    }

    private fun switchMode(newMode: TrackingMode) {
        val currentMode = trackingModeState.value
        if (currentMode == newMode) return
        errorState.value = null

        val wasTracking = isCurrentlyTracking(currentMode)

        if (wasTracking) {
            lifecycleScope.launch {
                stopCurrentTracker(currentMode)
                trackingModeState.value = newMode
                startTracking()
            }
        } else {
            trackingModeState.value = newMode
        }
    }

    private fun isCurrentlyTracking(mode: TrackingMode): Boolean = when (mode) {
        TrackingMode.HOME -> false
        TrackingMode.FACE, TrackingMode.ACTIONS,
        TrackingMode.VOICE,
        -> faceTracker.state.value == TrackingState.TRACKING
        TrackingMode.HAND -> handTracker.state.value == TrackingState.TRACKING
        TrackingMode.HOLISTIC -> holisticTracker.state.value == TrackingState.TRACKING
    }

    private suspend fun stopCurrentTracker(mode: TrackingMode) {
        try {
            when (mode) {
                TrackingMode.HOME -> { /* no tracker to stop */ }
                TrackingMode.FACE -> {
                    faceCollectorJob?.cancel()
                    faceTracker.stop()
                }
                TrackingMode.HAND -> {
                    handCollectorJob?.cancel()
                    handTracker.stop()
                }
                TrackingMode.HOLISTIC -> {
                    holisticCollectorJob?.cancel()
                    holisticTracker.stop()
                }
                TrackingMode.ACTIONS, TrackingMode.VOICE -> {
                    faceCollectorJob?.cancel()
                    faceTracker.stop()
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Best-effort stop: tracker may fail during shutdown (e.g., already released)
            Log.w("MainActivity", "Error stopping tracker", e)
        }
    }

    private fun requestCameraAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startTracking()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startTracking() {
        errorState.value = null
        lifecycleScope.launch {
            try {
                startTrackingForMode(trackingModeState.value)
            } catch (e: Exception) {
                // Tracker start can fail with various exceptions from MediaPipe/CameraX
                errorState.value = e.message ?: "Failed to start tracking"
                Log.e("MainActivity", "Failed to start tracking", e)
            }
        }
    }

    private suspend fun startTrackingForMode(mode: TrackingMode) {
        when (mode) {
            TrackingMode.HOME -> { /* no tracking on home screen */ }
            TrackingMode.FACE -> startFaceTracking()
            TrackingMode.HAND -> startHandTracking()
            TrackingMode.HOLISTIC -> startHolisticTracking()
            TrackingMode.ACTIONS, TrackingMode.VOICE -> startFaceTracking()
        }
    }

    private suspend fun startFaceTracking() {
        faceCollectorJob = lifecycleScope.launch {
            faceTracker.trackingData.collect { data ->
                faceTrackingDataState.value = data
            }
        }
        faceTracker.start()
    }

    private suspend fun startHandTracking() {
        handCollectorJob = lifecycleScope.launch {
            handTracker.trackingData.collect { data ->
                handTrackingDataState.value = data
            }
        }
        handTracker.start()
    }

    private suspend fun startHolisticTracking() {
        holisticCollectorJob = lifecycleScope.launch {
            holisticTracker.trackingData.collect { data ->
                holisticTrackingDataState.value = data
            }
        }
        holisticTracker.start()
    }

    private fun stopTracking() {
        lifecycleScope.launch {
            stopCurrentTracker(trackingModeState.value)
        }
    }

    override fun onStop() {
        super.onStop()
        val mode = trackingModeState.value
        if (isCurrentlyTracking(mode)) {
            lifecycleScope.launch { stopCurrentTracker(mode) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        faceTracker.release()
        handTracker.release()
        holisticTracker.release()
    }
}

private fun SharedTrackingMode.toLocal(): TrackingMode = when (this) {
    SharedTrackingMode.HOME -> TrackingMode.HOME
    SharedTrackingMode.FACE -> TrackingMode.FACE
    SharedTrackingMode.HAND -> TrackingMode.HAND
    SharedTrackingMode.HOLISTIC -> TrackingMode.HOLISTIC
    SharedTrackingMode.ACTIONS -> TrackingMode.ACTIONS
    SharedTrackingMode.VOICE -> TrackingMode.VOICE
}

@Composable
internal fun ModeToggle(
    currentMode: TrackingMode,
    onModeChange: (TrackingMode) -> Unit,
) {
    val scrollState = rememberScrollState()
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(SampleColors.OverlayLight)
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        TrackingMode.entries.filter { it != TrackingMode.HOME }.forEach { mode ->
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
