package io.github.kmpfacelink.sample

import android.annotation.SuppressLint
import android.opengl.GLSurfaceView
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
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
import io.github.kmpfacelink.api.FaceTracker
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.live2d.Live2DRenderState
import io.github.kmpfacelink.live2d.Live2DRenderer
import io.github.kmpfacelink.model.FaceTrackingData
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Compose screen showing a Live2D avatar driven by face tracking data.
 *
 * Uses only [Live2DRenderer] interface and [GLSurfaceView.Renderer] — no direct
 * reference to Cubism SDK classes, so this compiles without Live2D SDK installed.
 */
@Suppress("LongParameterList")
@Composable
internal fun AvatarTrackingScreen(
    faceTracker: FaceTracker,
    trackingDataState: MutableStateFlow<FaceTrackingData?>,
    renderer: Live2DRenderer,
    glSurfaceRenderer: GLSurfaceView.Renderer,
    onScaleChanged: (Float) -> Unit,
    onResetTransform: () -> Unit,
    getScale: () -> Float,
    onDrag: (Float, Float) -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
) {
    val trackingState by faceTracker.state.collectAsState()
    val renderState by renderer.state.collectAsState()
    val trackingData by trackingDataState.collectAsState()
    val isTracking = trackingState == TrackingState.TRACKING

    Box(modifier = Modifier.fillMaxSize()) {
        Live2DGLSurface(
            glSurfaceRenderer = glSurfaceRenderer,
            onScaleChanged = onScaleChanged,
            onResetTransform = onResetTransform,
            getScale = getScale,
            onDrag = onDrag,
        )

        Column(modifier = Modifier.fillMaxSize()) {
            AvatarTopBar(
                trackingState = trackingState,
                renderState = renderState,
                trackingData = trackingData,
                isTracking = isTracking,
                onStartClick = onStartClick,
                onStopClick = onStopClick,
            )

            Spacer(modifier = Modifier.weight(1f))

            AvatarBottomBar(renderState = renderState)
        }
    }
}

@SuppressLint("ClickableViewAccessibility")
@Composable
private fun Live2DGLSurface(
    glSurfaceRenderer: GLSurfaceView.Renderer,
    onScaleChanged: (Float) -> Unit,
    onResetTransform: () -> Unit,
    getScale: () -> Float,
    onDrag: (Float, Float) -> Unit,
) {
    AndroidView(
        factory = { context ->
            GLSurfaceView(context).apply {
                setEGLContextClientVersion(2)
                setRenderer(glSurfaceRenderer)
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

                val scaleDetector = ScaleGestureDetector(
                    context,
                    object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                        override fun onScale(detector: ScaleGestureDetector): Boolean {
                            onScaleChanged(getScale() * detector.scaleFactor)
                            return true
                        }
                    },
                )
                val gestureDetector = GestureDetector(
                    context,
                    object : GestureDetector.SimpleOnGestureListener() {
                        override fun onDoubleTap(e: MotionEvent): Boolean {
                            onResetTransform()
                            return true
                        }

                        override fun onScroll(
                            e1: MotionEvent?,
                            e2: MotionEvent,
                            distanceX: Float,
                            distanceY: Float,
                        ): Boolean {
                            // Convert pixel delta to GL coordinate delta ([-1,1] range per half-screen)
                            val dx = -distanceX / width * 2f
                            val dy = distanceY / height * 2f
                            onDrag(dx, dy)
                            return true
                        }
                    },
                )
                setOnTouchListener { _, event ->
                    scaleDetector.onTouchEvent(event)
                    gestureDetector.onTouchEvent(event)
                    true
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Suppress("LongParameterList")
@Composable
private fun AvatarTopBar(
    trackingState: TrackingState,
    renderState: Live2DRenderState,
    trackingData: FaceTrackingData?,
    isTracking: Boolean,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
) {
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
                text = "Track: $trackingState | Render: $renderState",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { if (isTracking) onStopClick() else onStartClick() }) {
                Text(if (isTracking) "Stop" else "Start")
            }
        }

        val ht = trackingData?.headTransform
        val headText = if (ht != null) {
            String.format(
                java.util.Locale.US,
                "P=%.1f  Y=%.1f  R=%.1f",
                ht.pitch,
                ht.yaw,
                ht.roll,
            )
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
private fun AvatarBottomBar(renderState: Live2DRenderState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.7f))
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        val statusText = when (renderState) {
            Live2DRenderState.UNINITIALIZED -> "Live2D: Initializing..."
            Live2DRenderState.READY -> "Live2D: Model loaded"
            Live2DRenderState.RENDERING -> "Live2D: Rendering"
            Live2DRenderState.ERROR -> "Live2D: Error loading model"
            Live2DRenderState.RELEASED -> "Live2D: Released"
        }
        Text(
            text = statusText,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
            modifier = Modifier.padding(8.dp),
        )
    }
}
