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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import io.github.kmpfacelink.avatar.Live2DParameterMapper
import io.github.kmpfacelink.avatar.toAvatarParameters
import io.github.kmpfacelink.internal.PreviewableFaceTracker
import io.github.kmpfacelink.live2d.Live2DModelInfo
import io.github.kmpfacelink.live2d.Live2DRenderer
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HandTrackerConfig
import io.github.kmpfacelink.model.HandTrackingData
import io.github.kmpfacelink.model.SmoothingConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

internal enum class TrackingMode { FACE, HAND, AVATAR }

internal data class Live2DTransformCallbacks(
    val getScale: () -> Float,
    val setScale: (Float) -> Unit,
    val drag: (Float, Float) -> Unit,
    val resetTransform: () -> Unit,
)

class MainActivity : ComponentActivity() {

    private val platformContext by lazy { PlatformContext(this, this) }

    private val faceTracker by lazy {
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

    private val faceTrackingDataState = MutableStateFlow<FaceTrackingData?>(null)
    private val handTrackingDataState = MutableStateFlow<HandTrackingData?>(null)
    private val trackingModeState = MutableStateFlow(TrackingMode.FACE)

    private var faceCollectorJob: Job? = null
    private var handCollectorJob: Job? = null
    private var avatarCollectorJob: Job? = null

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
            TrackingMode.AVATAR -> {
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
                        onStartClick = onStartClick,
                        onStopClick = onStopClick,
                    )
                } else {
                    Live2DUnavailableScreen(onModeChange = onModeChange)
                }
            }
        }
    }

    private fun switchMode(newMode: TrackingMode) {
        val currentMode = trackingModeState.value
        if (currentMode == newMode) return

        val wasTracking = when (currentMode) {
            TrackingMode.FACE, TrackingMode.AVATAR ->
                faceTracker.state.value == TrackingState.TRACKING
            TrackingMode.HAND -> handTracker.state.value == TrackingState.TRACKING
        }

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

    private suspend fun stopCurrentTracker(mode: TrackingMode) {
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
        lifecycleScope.launch {
            when (trackingModeState.value) {
                TrackingMode.FACE -> {
                    faceCollectorJob = lifecycleScope.launch {
                        faceTracker.trackingData.collect { data ->
                            faceTrackingDataState.value = data
                        }
                    }
                    faceTracker.start()
                }
                TrackingMode.HAND -> {
                    handCollectorJob = lifecycleScope.launch {
                        handTracker.trackingData.collect { data ->
                            handTrackingDataState.value = data
                        }
                    }
                    handTracker.start()
                }
                TrackingMode.AVATAR -> {
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
            }
        }
    }

    private fun stopTracking() {
        lifecycleScope.launch {
            stopCurrentTracker(trackingModeState.value)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        faceTracker.release()
        handTracker.release()
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
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        TrackingMode.entries.forEach { mode ->
            FilterChip(
                selected = currentMode == mode,
                onClick = { onModeChange(mode) },
                label = { Text(mode.name, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.White.copy(alpha = 0.15f),
                    labelColor = Color.White,
                    selectedContainerColor = Color(0xFF4488FF).copy(alpha = 0.6f),
                    selectedLabelColor = Color.White,
                ),
            )
        }
    }
}
