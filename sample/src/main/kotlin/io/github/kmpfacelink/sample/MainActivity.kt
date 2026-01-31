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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
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
import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.api.createFaceTracker
import io.github.kmpfacelink.internal.PreviewableFaceTracker
import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.FaceLandmark
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HeadTransform
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

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

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

        // Landmark overlay on camera preview
        val currentLandmarks = trackingData?.takeIf { it.isTracking }?.landmarks
        if (!currentLandmarks.isNullOrEmpty()) {
            LandmarkOverlay(
                landmarks = currentLandmarks,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Overlay UI
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            TopBar(
                state = state,
                headTransform = trackingData?.headTransform,
                isTracking = isTracking,
                onStartClick = onStartClick,
                onStopClick = onStopClick,
            )

            // Face avatar (center)
            FaceAvatar(
                trackingData = trackingData,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )

            // Bottom blend shape bars
            BlendShapeBars(
                trackingData = trackingData,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars),
            )
        }
    }
}

@Composable
private fun CameraPreview(previewableFaceTracker: PreviewableFaceTracker) {
    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
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
            String.format("P=%.1f  Y=%.1f  R=%.1f", ht.pitch, ht.yaw, ht.roll)
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

// ---------------------------------------------------------------------------
// Face avatar (Canvas)
// ---------------------------------------------------------------------------

@Composable
private fun FaceAvatar(
    trackingData: FaceTrackingData?,
    modifier: Modifier = Modifier,
) {
    val bs = trackingData?.blendShapes ?: emptyMap()
    val ht = trackingData?.headTransform ?: HeadTransform()
    val tracking = trackingData?.isTracking == true

    Canvas(modifier = modifier.padding(16.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = minOf(size.width, size.height) * 0.32f

        if (!tracking) {
            drawIdleFace(cx, cy, r)
            return@Canvas
        }

        val yawPx = ht.yaw * r / 40f
        val pitchPx = -ht.pitch * r / 40f

        rotate(degrees = -ht.roll, pivot = Offset(cx, cy)) {
            translate(left = yawPx, top = pitchPx) {
                drawTrackedFace(cx, cy, r, bs)
            }
        }
    }
}

private fun DrawScope.drawIdleFace(cx: Float, cy: Float, r: Float) {
    // Dim placeholder face
    val faceColor = Color.White.copy(alpha = 0.15f)
    val lineColor = Color.White.copy(alpha = 0.3f)

    // Head oval
    drawOval(
        color = faceColor,
        topLeft = Offset(cx - r, cy - r * 1.15f),
        size = Size(r * 2, r * 2.3f),
    )

    // Eyes
    val eyeY = cy - r * 0.2f
    val eyeGap = r * 0.38f
    drawOval(lineColor, Offset(cx - eyeGap - r * 0.15f, eyeY - r * 0.07f), Size(r * 0.3f, r * 0.14f))
    drawOval(lineColor, Offset(cx + eyeGap - r * 0.15f, eyeY - r * 0.07f), Size(r * 0.3f, r * 0.14f))

    // Mouth
    drawArc(
        color = lineColor,
        startAngle = 10f,
        sweepAngle = 160f,
        useCenter = false,
        topLeft = Offset(cx - r * 0.25f, cy + r * 0.35f),
        size = Size(r * 0.5f, r * 0.18f),
        style = Stroke(width = 2.dp.toPx()),
    )
}

private fun DrawScope.drawTrackedFace(
    cx: Float,
    cy: Float,
    r: Float,
    bs: Map<BlendShape, Float>,
) {
    val skinColor = Color(0xFFFFE0BD)
    val skinStroke = Color(0xFFE0C0A0)
    val eyeWhite = Color.White
    val irisColor = Color(0xFF4A3728)
    val pupilColor = Color(0xFF1A1008)
    val browColor = Color(0xFF5C3A1E)
    val lipColor = Color(0xFFD4736A)
    val mouthInside = Color(0xFF6B2030)

    fun bs(shape: BlendShape): Float = bs[shape] ?: 0f

    // --- Head oval ---
    val cheekPuff = bs(BlendShape.CHEEK_PUFF)
    val faceW = r * (1f + cheekPuff * 0.12f)
    drawOval(
        color = skinColor,
        topLeft = Offset(cx - faceW, cy - r * 1.15f),
        size = Size(faceW * 2, r * 2.3f),
    )
    drawOval(
        color = skinStroke,
        topLeft = Offset(cx - faceW, cy - r * 1.15f),
        size = Size(faceW * 2, r * 2.3f),
        style = Stroke(width = 2.dp.toPx()),
    )

    // --- Nose ---
    val nosePath = Path().apply {
        moveTo(cx - r * 0.06f, cy + r * 0.08f)
        quadraticTo(cx, cy + r * 0.18f, cx + r * 0.06f, cy + r * 0.08f)
    }
    drawPath(nosePath, skinStroke, style = Stroke(width = 2.dp.toPx()))

    // --- Eyes ---
    val eyeY = cy - r * 0.2f
    val eyeGap = r * 0.38f
    drawEye(cx - eyeGap, eyeY, r, bs, isLeft = true, eyeWhite, irisColor, pupilColor)
    drawEye(cx + eyeGap, eyeY, r, bs, isLeft = false, eyeWhite, irisColor, pupilColor)

    // --- Eyebrows ---
    val browInnerUp = bs(BlendShape.BROW_INNER_UP)
    val browDownL = bs(BlendShape.BROW_DOWN_LEFT)
    val browDownR = bs(BlendShape.BROW_DOWN_RIGHT)
    val browOuterUpL = bs(BlendShape.BROW_OUTER_UP_LEFT)
    val browOuterUpR = bs(BlendShape.BROW_OUTER_UP_RIGHT)
    val browW = r * 0.3f
    val browStroke = Stroke(width = 3.dp.toPx())

    // Left brow
    val lbY = eyeY - r * 0.2f
    val lbInnerOff = -(browInnerUp - browDownL) * r * 0.12f
    val lbOuterOff = -(browOuterUpL - browDownL) * r * 0.1f
    drawLine(browColor, Offset(cx - eyeGap + browW * 0.4f, lbY + lbInnerOff), Offset(cx - eyeGap - browW * 0.5f, lbY + lbOuterOff), strokeWidth = browStroke.width)

    // Right brow
    val rbInnerOff = -(browInnerUp - browDownR) * r * 0.12f
    val rbOuterOff = -(browOuterUpR - browDownR) * r * 0.1f
    drawLine(browColor, Offset(cx + eyeGap - browW * 0.4f, lbY + rbInnerOff), Offset(cx + eyeGap + browW * 0.5f, lbY + rbOuterOff), strokeWidth = browStroke.width)

    // --- Mouth ---
    drawMouth(cx, cy, r, bs, lipColor, mouthInside)
}

private fun DrawScope.drawEye(
    ex: Float,
    ey: Float,
    r: Float,
    bs: Map<BlendShape, Float>,
    isLeft: Boolean,
    eyeWhite: Color,
    irisColor: Color,
    pupilColor: Color,
) {
    fun bs(shape: BlendShape): Float = bs[shape] ?: 0f

    val blink = if (isLeft) bs(BlendShape.EYE_BLINK_LEFT) else bs(BlendShape.EYE_BLINK_RIGHT)
    val wide = if (isLeft) bs(BlendShape.EYE_WIDE_LEFT) else bs(BlendShape.EYE_WIDE_RIGHT)
    val squint = if (isLeft) bs(BlendShape.EYE_SQUINT_LEFT) else bs(BlendShape.EYE_SQUINT_RIGHT)

    val eyeW = r * 0.3f
    val openness = (1f - blink + wide * 0.3f - squint * 0.2f).coerceIn(0.05f, 1.2f)
    val eyeH = r * 0.16f * openness

    // Eye white
    drawOval(eyeWhite, Offset(ex - eyeW / 2, ey - eyeH / 2), Size(eyeW, eyeH))
    drawOval(Color.Gray, Offset(ex - eyeW / 2, ey - eyeH / 2), Size(eyeW, eyeH), style = Stroke(1.dp.toPx()))

    if (openness > 0.1f) {
        // Iris position based on look direction
        val lookIn = if (isLeft) bs(BlendShape.EYE_LOOK_IN_LEFT) else bs(BlendShape.EYE_LOOK_IN_RIGHT)
        val lookOut = if (isLeft) bs(BlendShape.EYE_LOOK_OUT_LEFT) else bs(BlendShape.EYE_LOOK_OUT_RIGHT)
        val lookUp = if (isLeft) bs(BlendShape.EYE_LOOK_UP_LEFT) else bs(BlendShape.EYE_LOOK_UP_RIGHT)
        val lookDown = if (isLeft) bs(BlendShape.EYE_LOOK_DOWN_LEFT) else bs(BlendShape.EYE_LOOK_DOWN_RIGHT)

        // For left eye: lookIn = looking right, lookOut = looking left
        // For right eye: lookIn = looking left, lookOut = looking right
        val lookH = if (isLeft) (lookIn - lookOut) else (lookOut - lookIn)
        val lookV = lookDown - lookUp

        val irisR = r * 0.065f
        val maxShift = eyeW * 0.2f
        val irisX = ex + lookH * maxShift
        val irisY = ey + lookV * maxShift * 0.6f

        drawCircle(irisColor, irisR, Offset(irisX, irisY))
        drawCircle(pupilColor, irisR * 0.5f, Offset(irisX, irisY))
        // Highlight
        drawCircle(Color.White.copy(alpha = 0.6f), irisR * 0.22f, Offset(irisX - irisR * 0.25f, irisY - irisR * 0.25f))
    }
}

private fun DrawScope.drawMouth(
    cx: Float,
    cy: Float,
    r: Float,
    bs: Map<BlendShape, Float>,
    lipColor: Color,
    mouthInside: Color,
) {
    fun bs(shape: BlendShape): Float = bs[shape] ?: 0f

    val jawOpen = bs(BlendShape.JAW_OPEN)
    val smileL = bs(BlendShape.MOUTH_SMILE_LEFT)
    val smileR = bs(BlendShape.MOUTH_SMILE_RIGHT)
    val frownL = bs(BlendShape.MOUTH_FROWN_LEFT)
    val frownR = bs(BlendShape.MOUTH_FROWN_RIGHT)
    val pucker = bs(BlendShape.MOUTH_PUCKER)
    val funnel = bs(BlendShape.MOUTH_FUNNEL)

    val mouthY = cy + r * 0.45f
    val mouthW = r * 0.35f * (1f - pucker * 0.45f - funnel * 0.3f)
    val mouthOpenH = jawOpen * r * 0.28f
    val smileAvg = (smileL + smileR) / 2f
    val frownAvg = (frownL + frownR) / 2f
    val cornerLift = (smileAvg - frownAvg) * r * 0.1f

    // Mouth interior (dark when open)
    if (mouthOpenH > 1f) {
        val interiorPath = Path().apply {
            moveTo(cx - mouthW, mouthY - cornerLift)
            quadraticTo(cx, mouthY - mouthOpenH * 0.3f, cx + mouthW, mouthY - cornerLift)
            quadraticTo(cx, mouthY + mouthOpenH, cx - mouthW, mouthY - cornerLift)
            close()
        }
        drawPath(interiorPath, mouthInside, style = Fill)
    }

    // Upper lip
    val upperLip = Path().apply {
        moveTo(cx - mouthW, mouthY - cornerLift)
        cubicTo(
            cx - mouthW * 0.4f, mouthY - r * 0.06f - cornerLift,
            cx + mouthW * 0.4f, mouthY - r * 0.06f - cornerLift,
            cx + mouthW, mouthY - cornerLift,
        )
    }
    drawPath(upperLip, lipColor, style = Stroke(width = 2.5f.dp.toPx()))

    // Lower lip
    val lowerLipY = mouthY + mouthOpenH * 0.6f
    val lowerLip = Path().apply {
        moveTo(cx - mouthW, mouthY - cornerLift)
        cubicTo(
            cx - mouthW * 0.3f, lowerLipY + r * 0.06f,
            cx + mouthW * 0.3f, lowerLipY + r * 0.06f,
            cx + mouthW, mouthY - cornerLift,
        )
    }
    drawPath(lowerLip, lipColor, style = Stroke(width = 2.5f.dp.toPx()))

    // Pucker / funnel: draw circle mouth
    if (pucker > 0.4f || funnel > 0.4f) {
        val ovalR = r * 0.08f * (pucker + funnel).coerceAtMost(1f)
        drawOval(
            lipColor,
            Offset(cx - ovalR, mouthY - ovalR * 0.6f),
            Size(ovalR * 2, ovalR * 1.2f),
            style = Stroke(width = 2.dp.toPx()),
        )
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
// Landmark overlay
// ---------------------------------------------------------------------------

@Composable
private fun LandmarkOverlay(
    landmarks: List<FaceLandmark>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Front camera preview is mirrored: flip x
        fun lx(index: Int): Float = (1f - landmarks[index].x) * w
        fun ly(index: Int): Float = landmarks[index].y * h

        // Draw face mesh contour connections
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

        // Face oval
        drawContour(FACE_OVAL)

        // Eyes
        drawContour(LEFT_EYE, Color(0xFF00CCFF).copy(alpha = 0.6f))
        drawContour(RIGHT_EYE, Color(0xFF00CCFF).copy(alpha = 0.6f))

        // Eyebrows
        drawContour(LEFT_EYEBROW, Color(0xFFFFCC00).copy(alpha = 0.6f))
        drawContour(RIGHT_EYEBROW, Color(0xFFFFCC00).copy(alpha = 0.6f))

        // Lips
        drawContour(LIPS_OUTER, Color(0xFFFF6688).copy(alpha = 0.6f))
        drawContour(LIPS_INNER, Color(0xFFFF6688).copy(alpha = 0.6f))

        // Irises
        drawContour(LEFT_IRIS, Color(0xFF00CCFF).copy(alpha = 0.7f))
        drawContour(RIGHT_IRIS, Color(0xFF00CCFF).copy(alpha = 0.7f))

        // Draw all 478 landmark points
        val dotRadius = 1.2f.dp.toPx()
        val dotColor = Color(0xFF00FF88).copy(alpha = 0.35f)
        for (lm in landmarks) {
            drawCircle(
                color = dotColor,
                radius = dotRadius,
                center = Offset((1f - lm.x) * w, lm.y * h),
            )
        }
    }
}

// MediaPipe face mesh contour indices
// See: https://github.com/google-ai-edge/mediapipe/blob/master/mediapipe/python/solutions/face_mesh_connections.py

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
