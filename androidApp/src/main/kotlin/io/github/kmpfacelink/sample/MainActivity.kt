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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.github.kmpfacelink.api.FaceTracker
import io.github.kmpfacelink.api.HandTracker
import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.api.createFaceTracker
import io.github.kmpfacelink.api.createHandTracker
import io.github.kmpfacelink.avatar.Live2DParameterMapper
import io.github.kmpfacelink.avatar.toAvatarParameters
import io.github.kmpfacelink.internal.PreviewableFaceTracker
import io.github.kmpfacelink.internal.PreviewableHandTracker
import io.github.kmpfacelink.live2d.Live2DModelInfo
import io.github.kmpfacelink.live2d.Live2DRenderer
import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.FaceLandmark
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HandGesture
import io.github.kmpfacelink.model.HandJoint
import io.github.kmpfacelink.model.HandLandmarkPoint
import io.github.kmpfacelink.model.HandTrackerConfig
import io.github.kmpfacelink.model.HandTrackingData
import io.github.kmpfacelink.model.HeadTransform
import io.github.kmpfacelink.model.SmoothingConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private enum class TrackingMode { FACE, HAND, AVATAR }

private data class Live2DTransformCallbacks(
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
                faceTracker = faceTracker,
                handTracker = handTracker,
                faceTrackingDataState = faceTrackingDataState,
                handTrackingDataState = handTrackingDataState,
                live2dRenderer = live2dRenderer,
                live2dGLSurfaceRenderer = live2dGLSurfaceRenderer,
                live2dTransformCallbacks = live2dTransformCallbacks,
                live2dAvailable = live2dAvailable,
                onModeChange = { newMode -> switchMode(newMode) },
                onStartClick = { requestCameraAndStart() },
                onStopClick = { stopTracking() },
            )
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
                    // Clear stale preview surface provider so CameraX only binds ImageAnalysis
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

            // Build transform callbacks via reflection (avoids compile-time dependency on Live2D classes)
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

// ---------------------------------------------------------------------------
// Main Screen
// ---------------------------------------------------------------------------

@Suppress("LongParameterList")
@Composable
private fun MainScreen(
    mode: TrackingMode,
    faceTracker: FaceTracker,
    handTracker: HandTracker,
    faceTrackingDataState: MutableStateFlow<FaceTrackingData?>,
    handTrackingDataState: MutableStateFlow<HandTrackingData?>,
    live2dRenderer: Live2DRenderer?,
    live2dGLSurfaceRenderer: android.opengl.GLSurfaceView.Renderer?,
    live2dTransformCallbacks: Live2DTransformCallbacks?,
    live2dAvailable: Boolean,
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
                    renderer = live2dRenderer,
                    glSurfaceRenderer = live2dGLSurfaceRenderer,
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

// ---------------------------------------------------------------------------
// Mode toggle
// ---------------------------------------------------------------------------

@Composable
private fun ModeToggle(
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

// ---------------------------------------------------------------------------
// Face Tracking Screen (existing)
// ---------------------------------------------------------------------------

@Composable
private fun FaceTrackingScreen(
    faceTracker: FaceTracker,
    trackingDataState: MutableStateFlow<FaceTrackingData?>,
    onModeChange: (TrackingMode) -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
) {
    val state by faceTracker.state.collectAsState()
    val trackingData by trackingDataState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        if (faceTracker is PreviewableFaceTracker) {
            CameraPreview(previewableFaceTracker = faceTracker)
        }

        val currentData = trackingData?.takeIf { it.isTracking && it.landmarks.isNotEmpty() }
        if (currentData != null) {
            FaceLandmarkOverlay(
                landmarks = currentData.landmarks,
                sourceImageWidth = currentData.sourceImageWidth,
                sourceImageHeight = currentData.sourceImageHeight,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(
                state = state,
                headTransform = trackingData?.headTransform,
                onStartClick = onStartClick,
                onStopClick = onStopClick,
            )
            ModeToggle(currentMode = TrackingMode.FACE, onModeChange = onModeChange)
            SmoothingFilterChips(faceTracker = faceTracker)

            Spacer(modifier = Modifier.weight(1f))

            BlendShapeBars(
                trackingData = trackingData,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Hand Tracking Screen
// ---------------------------------------------------------------------------

@Suppress("LongMethod")
@Composable
private fun HandTrackingScreen(
    handTracker: HandTracker,
    trackingDataState: MutableStateFlow<HandTrackingData?>,
    onModeChange: (TrackingMode) -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
) {
    val state by handTracker.state.collectAsState()
    val trackingData by trackingDataState.collectAsState()
    val isTracking = state == TrackingState.TRACKING

    Box(modifier = Modifier.fillMaxSize()) {
        if (handTracker is PreviewableHandTracker) {
            HandCameraPreview(previewableHandTracker = handTracker)
        }

        val currentData = trackingData?.takeIf { it.isTracking && it.hands.isNotEmpty() }
        if (currentData != null) {
            for (hand in currentData.hands) {
                HandLandmarkOverlay(
                    landmarks = hand.landmarks,
                    sourceImageWidth = currentData.sourceImageWidth,
                    sourceImageHeight = currentData.sourceImageHeight,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Status: $state",
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { if (isTracking) onStopClick() else onStartClick() }) {
                        Text(if (isTracking) "Stop" else "Start")
                    }
                }

                // Gesture display
                val gestureText = currentData?.hands?.joinToString(" | ") { hand ->
                    val h = hand.handedness.name
                    val g = if (hand.gesture != HandGesture.NONE) {
                        "${hand.gesture.name} (${(hand.gestureConfidence * 100).toInt()}%)"
                    } else {
                        "—"
                    }
                    "$h: $g"
                } ?: "No hands detected"
                Text(
                    text = gestureText,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }

            ModeToggle(currentMode = TrackingMode.HAND, onModeChange = onModeChange)

            Spacer(modifier = Modifier.weight(1f))

            // Hand count info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                val handsCount = currentData?.hands?.size ?: 0
                Text(
                    text = if (handsCount > 0) "Hands detected: $handsCount" else "Tap Start to begin hand tracking",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun HandCameraPreview(previewableHandTracker: PreviewableHandTracker) {
    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
                previewableHandTracker.setSurfaceProvider(surfaceProvider)
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

// ---------------------------------------------------------------------------
// Hand Landmark Overlay
// ---------------------------------------------------------------------------

@Suppress("LongMethod")
@Composable
private fun HandLandmarkOverlay(
    landmarks: List<HandLandmarkPoint>,
    sourceImageWidth: Int,
    sourceImageHeight: Int,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val viewW = size.width
        val viewH = size.height

        if (sourceImageWidth <= 0 || sourceImageHeight <= 0) return@Canvas

        val imageAspect = sourceImageWidth.toFloat() / sourceImageHeight
        val viewAspect = viewW / viewH

        val scale: Float
        val offsetX: Float
        val offsetY: Float
        if (imageAspect > viewAspect) {
            scale = viewH / sourceImageHeight
            val renderedW = sourceImageWidth * scale
            offsetX = (renderedW - viewW) / 2f
            offsetY = 0f
        } else {
            scale = viewW / sourceImageWidth
            val renderedH = sourceImageHeight * scale
            offsetX = 0f
            offsetY = (renderedH - viewH) / 2f
        }

        val landmarkMap = landmarks.associateBy { it.joint }

        fun pos(joint: HandJoint): Offset? {
            val lm = landmarkMap[joint] ?: return null
            // Front camera preview is mirrored: flip x
            val px = lm.x * sourceImageWidth * scale - offsetX
            val py = lm.y * sourceImageHeight * scale - offsetY
            return Offset(px, py)
        }

        // Draw skeleton connections
        val boneColor = Color(0xFF00FF88).copy(alpha = 0.7f)
        val boneStroke = 2.dp.toPx()

        fun drawBone(from: HandJoint, to: HandJoint) {
            val a = pos(from) ?: return
            val b = pos(to) ?: return
            drawLine(color = boneColor, start = a, end = b, strokeWidth = boneStroke)
        }

        // Wrist to each finger MCP
        drawBone(HandJoint.WRIST, HandJoint.THUMB_CMC)
        drawBone(HandJoint.WRIST, HandJoint.INDEX_FINGER_MCP)
        drawBone(HandJoint.WRIST, HandJoint.MIDDLE_FINGER_MCP)
        drawBone(HandJoint.WRIST, HandJoint.RING_FINGER_MCP)
        drawBone(HandJoint.WRIST, HandJoint.PINKY_MCP)

        // Thumb chain
        drawBone(HandJoint.THUMB_CMC, HandJoint.THUMB_MCP)
        drawBone(HandJoint.THUMB_MCP, HandJoint.THUMB_IP)
        drawBone(HandJoint.THUMB_IP, HandJoint.THUMB_TIP)

        // Index finger chain
        drawBone(HandJoint.INDEX_FINGER_MCP, HandJoint.INDEX_FINGER_PIP)
        drawBone(HandJoint.INDEX_FINGER_PIP, HandJoint.INDEX_FINGER_DIP)
        drawBone(HandJoint.INDEX_FINGER_DIP, HandJoint.INDEX_FINGER_TIP)

        // Middle finger chain
        drawBone(HandJoint.MIDDLE_FINGER_MCP, HandJoint.MIDDLE_FINGER_PIP)
        drawBone(HandJoint.MIDDLE_FINGER_PIP, HandJoint.MIDDLE_FINGER_DIP)
        drawBone(HandJoint.MIDDLE_FINGER_DIP, HandJoint.MIDDLE_FINGER_TIP)

        // Ring finger chain
        drawBone(HandJoint.RING_FINGER_MCP, HandJoint.RING_FINGER_PIP)
        drawBone(HandJoint.RING_FINGER_PIP, HandJoint.RING_FINGER_DIP)
        drawBone(HandJoint.RING_FINGER_DIP, HandJoint.RING_FINGER_TIP)

        // Pinky chain
        drawBone(HandJoint.PINKY_MCP, HandJoint.PINKY_PIP)
        drawBone(HandJoint.PINKY_PIP, HandJoint.PINKY_DIP)
        drawBone(HandJoint.PINKY_DIP, HandJoint.PINKY_TIP)

        // MCP connections across palm
        drawBone(HandJoint.INDEX_FINGER_MCP, HandJoint.MIDDLE_FINGER_MCP)
        drawBone(HandJoint.MIDDLE_FINGER_MCP, HandJoint.RING_FINGER_MCP)
        drawBone(HandJoint.RING_FINGER_MCP, HandJoint.PINKY_MCP)

        // Draw landmark points
        val dotRadius = 3.dp.toPx()
        val tipColor = Color(0xFFFF6B6B)
        val jointColor = Color(0xFF00CCFF)
        val tipJoints = setOf(
            HandJoint.THUMB_TIP,
            HandJoint.INDEX_FINGER_TIP,
            HandJoint.MIDDLE_FINGER_TIP,
            HandJoint.RING_FINGER_TIP,
            HandJoint.PINKY_TIP,
        )

        for (lm in landmarks) {
            val p = pos(lm.joint) ?: continue
            val color = if (lm.joint in tipJoints) tipColor else jointColor
            drawCircle(color = color, radius = dotRadius, center = p)
        }
    }
}

// ---------------------------------------------------------------------------
// Camera Preview (Face)
// ---------------------------------------------------------------------------

@Composable
private fun CameraPreview(previewableFaceTracker: PreviewableFaceTracker) {
    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
                previewableFaceTracker.setSurfaceProvider(surfaceProvider)
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

// ---------------------------------------------------------------------------
// Top bar
// ---------------------------------------------------------------------------

@Composable
private fun TopBar(
    state: TrackingState,
    headTransform: HeadTransform?,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
) {
    val isTracking = state == TrackingState.TRACKING
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.7f))
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Status: $state",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { if (isTracking) onStopClick() else onStartClick() }) {
                Text(if (isTracking) "Stop" else "Start")
            }
        }
        val ht = headTransform
        val headText = if (ht != null) {
            String.format(java.util.Locale.US, "P=%.1f  Y=%.1f  R=%.1f", ht.pitch, ht.yaw, ht.roll)
        } else {
            "P=0.0  Y=0.0  R=0.0"
        }
        Text(
            text = headText,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun SmoothingFilterChips(faceTracker: FaceTracker) {
    var selectedFilter by remember { mutableStateOf("EMA") }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        listOf("None", "EMA", "1Euro").forEach { label ->
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
                    containerColor = Color.White.copy(alpha = 0.15f),
                    labelColor = Color.White,
                    selectedContainerColor = Color.White.copy(alpha = 0.35f),
                    selectedLabelColor = Color.White,
                ),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Blend shape bars
// ---------------------------------------------------------------------------

@Composable
private fun BlendShapeBars(
    trackingData: FaceTrackingData?,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val textMeasurer = rememberTextMeasurer()

    val entries = if (trackingData?.isTracking == true) {
        trackingData.blendShapes.entries
            .sortedByDescending { it.value }
            .take(15)
    } else {
        emptyList()
    }

    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .verticalScroll(scrollState),
    ) {
        if (entries.isEmpty()) {
            Text(
                text = "Tap Start to begin face tracking",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                modifier = Modifier.padding(8.dp),
            )
        } else {
            entries.forEach { (shape, value) ->
                BlendShapeBarRow(shape, value, textMeasurer)
            }
        }
    }
}

@Composable
private fun BlendShapeBarRow(
    shape: BlendShape,
    value: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = shape.arKitName,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            modifier = Modifier.width(130.dp),
        )

        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(12.dp),
        ) {
            val barW = size.width
            val barH = size.height

            // Background track
            drawRoundRect(
                color = Color.White.copy(alpha = 0.15f),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barH / 2),
            )

            // Filled portion
            val fillW = (barW * value.coerceIn(0f, 1f))
            if (fillW > 0f) {
                val barColor = when {
                    value > 0.7f -> Color(0xFFFF6B6B)
                    value > 0.3f -> Color(0xFFFFA94D)
                    else -> Color(0xFF51CF66)
                }
                drawRoundRect(
                    color = barColor,
                    size = Size(fillW, barH),
                    cornerRadius = CornerRadius(barH / 2),
                )
            }

            // Value label
            val label = "%.0f%%".format(value * 100)
            val textResult = textMeasurer.measure(
                text = label,
                style = TextStyle(
                    color = Color.White,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                ),
            )
            drawText(
                textLayoutResult = textResult,
                topLeft = Offset(barW - textResult.size.width - 4f, (barH - textResult.size.height) / 2),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Face Landmark overlay
// ---------------------------------------------------------------------------

@Composable
private fun FaceLandmarkOverlay(
    landmarks: List<FaceLandmark>,
    sourceImageWidth: Int,
    sourceImageHeight: Int,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val viewW = size.width
        val viewH = size.height

        if (sourceImageWidth <= 0 || sourceImageHeight <= 0) return@Canvas

        // FILL_CENTER mapping: scale to fill the view, center the overflow.
        // Uses max() so the image covers both dimensions (matching PreviewView).
        val scaleFactor = maxOf(viewW / sourceImageWidth, viewH / sourceImageHeight)
        val offsetX = (viewW - sourceImageWidth * scaleFactor) / 2f
        val offsetY = (viewH - sourceImageHeight * scaleFactor) / 2f

        // No x-flip needed: image is pre-mirrored before MediaPipe processing
        fun lx(index: Int): Float =
            landmarks[index].x * sourceImageWidth * scaleFactor + offsetX
        fun ly(index: Int): Float =
            landmarks[index].y * sourceImageHeight * scaleFactor + offsetY

        val contourColor = Color(0xFF00FF88).copy(alpha = 0.5f)
        val contourStroke = 1.dp.toPx()

        fun drawContour(indices: IntArray, color: Color = contourColor) {
            for (i in 0 until indices.size - 1) {
                val a = indices[i]
                val b = indices[i + 1]
                if (a < landmarks.size && b < landmarks.size) {
                    drawLine(
                        color = color,
                        start = Offset(lx(a), ly(a)),
                        end = Offset(lx(b), ly(b)),
                        strokeWidth = contourStroke,
                    )
                }
            }
        }

        drawContour(FACE_OVAL)
        drawContour(LEFT_EYE, Color(0xFF00CCFF).copy(alpha = 0.6f))
        drawContour(RIGHT_EYE, Color(0xFF00CCFF).copy(alpha = 0.6f))
        drawContour(LEFT_EYEBROW, Color(0xFFFFCC00).copy(alpha = 0.6f))
        drawContour(RIGHT_EYEBROW, Color(0xFFFFCC00).copy(alpha = 0.6f))
        drawContour(LIPS_OUTER, Color(0xFFFF6688).copy(alpha = 0.6f))
        drawContour(LIPS_INNER, Color(0xFFFF6688).copy(alpha = 0.6f))
        drawContour(LEFT_IRIS, Color(0xFF00CCFF).copy(alpha = 0.7f))
        drawContour(RIGHT_IRIS, Color(0xFF00CCFF).copy(alpha = 0.7f))

        val dotRadius = 1.2f.dp.toPx()
        val dotColor = Color(0xFF00FF88).copy(alpha = 0.35f)
        for (lm in landmarks) {
            val px = lm.x * sourceImageWidth * scaleFactor + offsetX
            val py = lm.y * sourceImageHeight * scaleFactor + offsetY
            drawCircle(
                color = dotColor,
                radius = dotRadius,
                center = Offset(px, py),
            )
        }
    }
}

// MediaPipe face mesh contour indices

private val FACE_OVAL = intArrayOf(
    10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288,
    397, 365, 379, 378, 400, 377, 152, 148, 176, 149, 150, 136,
    172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109, 10,
)

private val LEFT_EYE = intArrayOf(
    33, 7, 163, 144, 145, 153, 154, 155, 133, 173, 157, 158, 159, 160, 161, 246, 33,
)

private val RIGHT_EYE = intArrayOf(
    362, 382, 381, 380, 374, 373, 390, 249, 263, 466, 388, 387, 386, 385, 384, 398, 362,
)

private val LEFT_EYEBROW = intArrayOf(70, 63, 105, 66, 107, 55, 65, 52, 53, 46)

private val RIGHT_EYEBROW = intArrayOf(300, 293, 334, 296, 336, 285, 295, 282, 283, 276)

private val LIPS_OUTER = intArrayOf(
    61, 146, 91, 181, 84, 17, 314, 405, 321, 375, 291,
    409, 270, 269, 267, 0, 37, 39, 40, 185, 61,
)

private val LIPS_INNER = intArrayOf(
    78, 95, 88, 178, 87, 14, 317, 402, 318, 324, 308,
    415, 310, 311, 312, 13, 82, 81, 80, 191, 78,
)

private val LEFT_IRIS = intArrayOf(468, 469, 470, 471, 472, 468)

private val RIGHT_IRIS = intArrayOf(473, 474, 475, 476, 477, 473)
