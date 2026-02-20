package io.github.kmpfacelink.sample

import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.kmpfacelink.api.HandTracker
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.internal.PreviewableHandTracker
import io.github.kmpfacelink.model.HandGesture
import io.github.kmpfacelink.model.HandJoint
import io.github.kmpfacelink.model.HandLandmarkPoint
import io.github.kmpfacelink.model.HandTrackingData
import kotlinx.coroutines.flow.MutableStateFlow

@Suppress("LongMethod")
@Composable
internal fun HandTrackingScreen(
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

@Suppress("LongMethod")
@Composable
internal fun HandLandmarkOverlay(
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
            val px = lm.x * sourceImageWidth * scale - offsetX
            val py = lm.y * sourceImageHeight * scale - offsetY
            return Offset(px, py)
        }

        val boneColor = Color(0xFF00FF88).copy(alpha = 0.7f)
        val boneStroke = 2.dp.toPx()

        fun drawBone(from: HandJoint, to: HandJoint) {
            val a = pos(from) ?: return
            val b = pos(to) ?: return
            drawLine(color = boneColor, start = a, end = b, strokeWidth = boneStroke)
        }

        drawBone(HandJoint.WRIST, HandJoint.THUMB_CMC)
        drawBone(HandJoint.WRIST, HandJoint.INDEX_FINGER_MCP)
        drawBone(HandJoint.WRIST, HandJoint.MIDDLE_FINGER_MCP)
        drawBone(HandJoint.WRIST, HandJoint.RING_FINGER_MCP)
        drawBone(HandJoint.WRIST, HandJoint.PINKY_MCP)

        drawBone(HandJoint.THUMB_CMC, HandJoint.THUMB_MCP)
        drawBone(HandJoint.THUMB_MCP, HandJoint.THUMB_IP)
        drawBone(HandJoint.THUMB_IP, HandJoint.THUMB_TIP)

        drawBone(HandJoint.INDEX_FINGER_MCP, HandJoint.INDEX_FINGER_PIP)
        drawBone(HandJoint.INDEX_FINGER_PIP, HandJoint.INDEX_FINGER_DIP)
        drawBone(HandJoint.INDEX_FINGER_DIP, HandJoint.INDEX_FINGER_TIP)

        drawBone(HandJoint.MIDDLE_FINGER_MCP, HandJoint.MIDDLE_FINGER_PIP)
        drawBone(HandJoint.MIDDLE_FINGER_PIP, HandJoint.MIDDLE_FINGER_DIP)
        drawBone(HandJoint.MIDDLE_FINGER_DIP, HandJoint.MIDDLE_FINGER_TIP)

        drawBone(HandJoint.RING_FINGER_MCP, HandJoint.RING_FINGER_PIP)
        drawBone(HandJoint.RING_FINGER_PIP, HandJoint.RING_FINGER_DIP)
        drawBone(HandJoint.RING_FINGER_DIP, HandJoint.RING_FINGER_TIP)

        drawBone(HandJoint.PINKY_MCP, HandJoint.PINKY_PIP)
        drawBone(HandJoint.PINKY_PIP, HandJoint.PINKY_DIP)
        drawBone(HandJoint.PINKY_DIP, HandJoint.PINKY_TIP)

        drawBone(HandJoint.INDEX_FINGER_MCP, HandJoint.MIDDLE_FINGER_MCP)
        drawBone(HandJoint.MIDDLE_FINGER_MCP, HandJoint.RING_FINGER_MCP)
        drawBone(HandJoint.RING_FINGER_MCP, HandJoint.PINKY_MCP)

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
