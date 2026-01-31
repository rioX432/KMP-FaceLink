package io.github.kmpfacelink.sample

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.github.kmpfacelink.api.FaceTracker
import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.api.createFaceTracker
import io.github.kmpfacelink.internal.PreviewableFaceTracker
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.FaceTrackingData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val faceTracker by lazy {
        createFaceTracker(
            platformContext = PlatformContext(this, this),
            config = FaceTrackerConfig(
                enableSmoothing = true,
                smoothingFactor = 0.4f,
            ),
        )
    }

    private val trackingDataState = MutableStateFlow<FaceTrackingData?>(null)

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

        lifecycleScope.launch {
            faceTracker.trackingData.collect { data ->
                trackingDataState.value = data
            }
        }

        setContent {
            FaceTrackingScreen(
                faceTracker = faceTracker,
                trackingDataState = trackingDataState,
                onStartClick = { requestCameraAndStart() },
                onStopClick = { stopTracking() },
            )
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
            faceTracker.start()
        }
    }

    private fun stopTracking() {
        lifecycleScope.launch {
            faceTracker.stop()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        faceTracker.release()
    }
}

@Composable
private fun FaceTrackingScreen(
    faceTracker: FaceTracker,
    trackingDataState: MutableStateFlow<FaceTrackingData?>,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
) {
    val state by faceTracker.state.collectAsState()
    val trackingData by trackingDataState.collectAsState()
    val isTracking = state == TrackingState.TRACKING

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview (full screen background)
        if (faceTracker is PreviewableFaceTracker) {
            CameraPreview(previewableFaceTracker = faceTracker)
        }

        // Overlay UI
        Column(modifier = Modifier.fillMaxSize()) {
            // Top info bar (with status bar inset)
            TopInfoBar(
                state = state,
                trackingData = trackingData,
                isTracking = isTracking,
                onStartClick = onStartClick,
                onStopClick = onStopClick,
            )

            Spacer(modifier = Modifier.weight(1f))

            // Bottom blend shapes panel (with navigation bar inset)
            BlendShapesPanel(trackingData = trackingData)
        }
    }
}

@Composable
private fun CameraPreview(previewableFaceTracker: PreviewableFaceTracker) {
    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                // Use TextureView for Compose compatibility (SurfaceView punches through)
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                previewableFaceTracker.setSurfaceProvider(surfaceProvider)
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun TopInfoBar(
    state: TrackingState,
    trackingData: FaceTrackingData?,
    isTracking: Boolean,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
) {
    val overlayBg = Color.Black.copy(alpha = 0.67f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(overlayBg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Status: $state",
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { if (isTracking) onStopClick() else onStartClick() }) {
                Text(if (isTracking) "Stop" else "Start")
            }
        }

        val headText = if (trackingData != null && trackingData.isTracking) {
            val ht = trackingData.headTransform
            String.format("Head: P=%.1f Y=%.1f R=%.1f", ht.pitch, ht.yaw, ht.roll)
        } else {
            "Head: P=0.0 Y=0.0 R=0.0"
        }

        Text(
            text = headText,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun BlendShapesPanel(trackingData: FaceTrackingData?) {
    val overlayBg = Color.Black.copy(alpha = 0.67f)
    val scrollState = rememberScrollState()

    val blendShapesText = if (trackingData != null && trackingData.isTracking) {
        val sb = StringBuilder()
        for ((shape, value) in trackingData.blendShapes.entries.sortedBy { it.key.name }) {
            val bar = "\u2588".repeat((value * 20).toInt()).padEnd(20, '\u2591')
            sb.appendLine("${shape.arKitName.padEnd(25)} $bar ${"%.2f".format(value)}")
        }
        sb.toString()
    } else {
        "Tap Start to begin face tracking"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(overlayBg)
            .padding(8.dp)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .verticalScroll(scrollState),
    ) {
        Text(
            text = blendShapesText,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}
