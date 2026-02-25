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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.api.createFaceTracker
import io.github.kmpfacelink.api.createHandTracker
import io.github.kmpfacelink.api.createHolisticTracker
import io.github.kmpfacelink.avatar.Live2DParameterMapper
import io.github.kmpfacelink.avatar.toAvatarParameters
import io.github.kmpfacelink.internal.PreviewableFaceTracker
import io.github.kmpfacelink.live2d.Live2DModelInfo
import io.github.kmpfacelink.live2d.Live2DRenderer
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HandTrackerConfig
import io.github.kmpfacelink.model.HandTrackingData
import io.github.kmpfacelink.model.HolisticTrackerConfig
import io.github.kmpfacelink.model.HolisticTrackingData
import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.sample.rive.RiveTrackingScreen
import io.github.kmpfacelink.sample.ui.SampleColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

internal enum class TrackingMode {
    FACE, HAND, AVATAR, HOLISTIC, ACTIONS, EFFECTS, STREAM, VOICE, RIVE
}

internal data class Live2DTransformCallbacks(
    val getScale: () -> Float,
    val setScale: (Float) -> Unit,
    val drag: (Float, Float) -> Unit,
    val resetTransform: () -> Unit,
)

@Suppress("TooManyFunctions")
class MainActivity : ComponentActivity() {

    private val platformContext by lazy { PlatformContext(this, this) }

    internal val faceTracker by lazy {
        createFaceTracker(
            platformContext = platformContext,
            config = FaceTrackerConfig(
                smoothingConfig = SmoothingConfig.Ema(alpha = 0.4f),
            ),
        )
    }

    private val handTracker by lazy {
        createHandTracker(
            platformContext = platformContext,
            config = HandTrackerConfig(
                smoothingConfig = SmoothingConfig.Ema(alpha = 0.4f),
            ),
        )
    }

    private val holisticTracker by lazy {
        createHolisticTracker(
            platformContext = platformContext,
            config = HolisticTrackerConfig(
                faceConfig = FaceTrackerConfig(smoothingConfig = SmoothingConfig.Ema(alpha = 0.4f)),
                handConfig = HandTrackerConfig(smoothingConfig = SmoothingConfig.Ema(alpha = 0.4f)),
            ),
        )
    }

    internal val faceTrackingDataState = MutableStateFlow<FaceTrackingData?>(null)
    private val handTrackingDataState = MutableStateFlow<HandTrackingData?>(null)
    private val holisticTrackingDataState = MutableStateFlow<HolisticTrackingData?>(null)
    internal val trackingModeState = MutableStateFlow(TrackingMode.FACE)
    internal val errorState = MutableStateFlow<String?>(null)

    internal var faceCollectorJob: Job? = null
    private var handCollectorJob: Job? = null
    private var avatarCollectorJob: Job? = null
    private var holisticCollectorJob: Job? = null

    private val avatarMapper by lazy { Live2DParameterMapper() }
    private val live2dAvailable by lazy { isLive2DAvailable() }
    private var live2dRenderer: Live2DRenderer? = null
    private var live2dGLSurfaceRenderer: android.opengl.GLSurfaceView.Renderer? = null
    private var live2dTransformCallbacks: Live2DTransformCallbacks? = null

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

        initializeLive2D()

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
            TrackingMode.AVATAR -> renderAvatarScreen(onModeChange, onStartClick, onStopClick)
            TrackingMode.ACTIONS -> ActionsTrackingScreen(
                activity = this,
                onModeChange = onModeChange,
                onStartClick = onStartClick,
                onStopClick = onStopClick,
            )
            TrackingMode.EFFECTS -> EffectsTrackingScreen(
                activity = this,
                onModeChange = onModeChange,
                onStartClick = onStartClick,
                onStopClick = onStopClick,
            )
            TrackingMode.STREAM -> StreamTrackingScreen(
                activity = this,
                onModeChange = onModeChange,
                onStartClick = onStartClick,
                onStopClick = onStopClick,
            )
            TrackingMode.VOICE -> VoiceTrackingScreen(
                onModeChange = onModeChange,
            )
            TrackingMode.RIVE -> RiveTrackingScreen(
                activity = this@MainActivity,
                onModeChange = onModeChange,
                onStartClick = onStartClick,
                onStopClick = onStopClick,
            )
        }
    }

    @Composable
    private fun renderAvatarScreen(
        onModeChange: (TrackingMode) -> Unit,
        onStartClick: () -> Unit,
        onStopClick: () -> Unit,
    ) {
        if (live2dAvailable && live2dRenderer != null && live2dGLSurfaceRenderer != null) {
            val cb = live2dTransformCallbacks
            AvatarTrackingScreen(
                faceTracker = faceTracker,
                trackingDataState = faceTrackingDataState,
                renderer = live2dRenderer!!,
                glSurfaceRenderer = live2dGLSurfaceRenderer!!,
                onScaleChanged = cb?.setScale ?: {},
                onResetTransform = cb?.resetTransform ?: {},
                getScale = cb?.getScale ?: { 1f },
                onDrag = cb?.drag ?: { _, _ -> },
                onModeChange = onModeChange,
                onStartClick = onStartClick,
                onStopClick = onStopClick,
            )
        } else {
            Live2DUnavailableScreen(onModeChange = onModeChange)
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
        TrackingMode.FACE, TrackingMode.AVATAR, TrackingMode.ACTIONS,
        TrackingMode.EFFECTS, TrackingMode.STREAM, TrackingMode.VOICE,
        TrackingMode.RIVE,
        -> faceTracker.state.value == TrackingState.TRACKING
        TrackingMode.HAND -> handTracker.state.value == TrackingState.TRACKING
        TrackingMode.HOLISTIC -> holisticTracker.state.value == TrackingState.TRACKING
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun stopCurrentTracker(mode: TrackingMode) {
        try {
            when (mode) {
                TrackingMode.FACE -> {
                    faceCollectorJob?.cancel()
                    faceTracker.stop()
                }
                TrackingMode.HAND -> {
                    handCollectorJob?.cancel()
                    handTracker.stop()
                }
                TrackingMode.AVATAR -> {
                    avatarCollectorJob?.cancel()
                    faceCollectorJob?.cancel()
                    faceTracker.stop()
                }
                TrackingMode.HOLISTIC -> {
                    holisticCollectorJob?.cancel()
                    holisticTracker.stop()
                }
                TrackingMode.ACTIONS, TrackingMode.EFFECTS,
                TrackingMode.STREAM, TrackingMode.VOICE,
                TrackingMode.RIVE,
                -> {
                    faceCollectorJob?.cancel()
                    faceTracker.stop()
                }
            }
        } catch (e: Exception) {
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

    @Suppress("TooGenericExceptionCaught")
    private fun startTracking() {
        errorState.value = null
        lifecycleScope.launch {
            try {
                startTrackingForMode(trackingModeState.value)
            } catch (e: Exception) {
                errorState.value = e.message ?: "Failed to start tracking"
                Log.e("MainActivity", "Failed to start tracking", e)
            }
        }
    }

    private suspend fun startTrackingForMode(mode: TrackingMode) {
        when (mode) {
            TrackingMode.FACE -> startFaceTracking()
            TrackingMode.HAND -> startHandTracking()
            TrackingMode.HOLISTIC -> startHolisticTracking()
            TrackingMode.AVATAR -> startAvatarTracking()
            TrackingMode.ACTIONS, TrackingMode.EFFECTS,
            TrackingMode.STREAM, TrackingMode.VOICE,
            TrackingMode.RIVE,
            -> startFaceTracking()
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

    private suspend fun startAvatarTracking() {
        (faceTracker as? PreviewableFaceTracker)?.setSurfaceProvider(null)
        faceCollectorJob = lifecycleScope.launch {
            faceTracker.trackingData.collect { data ->
                faceTrackingDataState.value = data
            }
        }
        val renderer = live2dRenderer
        if (renderer != null) {
            avatarCollectorJob = lifecycleScope.launch {
                faceTracker.trackingData
                    .toAvatarParameters(avatarMapper)
                    .collect { params ->
                        renderer.updateParameters(params)
                    }
            }
        }
        faceTracker.start()
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
        live2dRenderer?.release()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun initializeLive2D() {
        if (!live2dAvailable) return
        try {
            val rendererClass = Class.forName(
                "io.github.kmpfacelink.sample.live2d.Live2DGLRenderer",
            )
            val constructor = rendererClass.getConstructor(
                android.content.res.AssetManager::class.java,
            )
            val instance = constructor.newInstance(assets)
            live2dRenderer = instance as Live2DRenderer
            live2dGLSurfaceRenderer = instance as android.opengl.GLSurfaceView.Renderer

            try {
                val scaleField = rendererClass.getField("userScale")
                val offsetXField = rendererClass.getField("offsetX")
                val offsetYField = rendererClass.getField("offsetY")
                val resetMethod = rendererClass.getMethod("resetTransform")
                val minScale = rendererClass.getField("MIN_SCALE").getFloat(null)
                val maxScale = rendererClass.getField("MAX_SCALE").getFloat(null)
                live2dTransformCallbacks = Live2DTransformCallbacks(
                    getScale = { scaleField.getFloat(instance) },
                    setScale = { v -> scaleField.setFloat(instance, v.coerceIn(minScale, maxScale)) },
                    drag = { dx, dy ->
                        offsetXField.setFloat(instance, offsetXField.getFloat(instance) + dx)
                        offsetYField.setFloat(instance, offsetYField.getFloat(instance) + dy)
                    },
                    resetTransform = { resetMethod.invoke(instance) },
                )
            } catch (_: Exception) {
                Log.w("MainActivity", "Failed to set up transform callbacks via reflection")
            }

            lifecycleScope.launch {
                val modelInfo = Live2DModelInfo(
                    modelId = "hiyori",
                    name = "Hiyori",
                    modelPath = "live2d/Hiyori/Hiyori.model3.json",
                )
                (instance as Live2DRenderer).initialize(modelInfo)
            }
        } catch (_: Exception) {
            // Live2D SDK classes not available at runtime
        }
    }

    private fun isLive2DAvailable(): Boolean {
        return try {
            assets.open("live2d/Hiyori/Hiyori.model3.json").close()
            Class.forName("com.live2d.sdk.cubism.framework.CubismFramework")
            true
        } catch (_: Exception) {
            false
        }
    }
}

@Composable
private fun Live2DUnavailableScreen(onModeChange: (TrackingMode) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Live2D SDK not installed",
                color = Color.White,
                fontSize = 16.sp,
            )
            Text(
                text = "Run scripts/setup-live2d.sh to download",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(
                onClick = { onModeChange(TrackingMode.FACE) },
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text("Switch to Face Tracking")
            }
        }
    }
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
