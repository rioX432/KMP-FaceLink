package io.github.kmpfacelink.sample

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kmpfacelink.api.CheekPuffContourObservation
import io.github.kmpfacelink.api.CheekPuffContourObservationSession
import io.github.kmpfacelink.api.FaceTracker
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.sample.ui.FaceCameraPreview
import io.github.kmpfacelink.sample.ui.FaceLandmarkOverlay
import io.github.kmpfacelink.sample.ui.SampleColors
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

private const val TRACE_CAPACITY = 180
private const val GEOMETRY_SETTLING_MS = 1_500L
private val CHEEK_COLOR = Color(0xFFB197FC)
private val CONTROL_COLOR = Color(0xFF51CF66)
private val GEOMETRY_COLOR = Color(0xFFFFB74D)
private val PASS_COLOR = Color(0xFF69DB7C)
private val FAIL_COLOR = Color(0xFFFF6B6B)

private data class EvidencePoint(
    val cheekPuff: Float,
    val jawOpen: Float,
)

private sealed interface GeometryStage {
    data object NeedsNeutral : GeometryStage
    data class Settling(val startedAtMs: Long) : GeometryStage
    data class Collecting(val frames: Int) : GeometryStage
    data object Ready : GeometryStage
}

/**
 * A recording-oriented Android screen that keeps the camera, landmarks, target coefficient and a
 * known-responsive control visible at the same time.
 *
 * The tracker supplied by [TrackingStateHolder] has smoothing, enhancement and calibration off.
 * This screen is evidence presentation only: it does not classify cheek puff and does not change
 * the SDK or product route.
 */
@Composable
internal fun FaceEvidenceScreen(
    faceTracker: FaceTracker,
    trackingDataState: StateFlow<FaceTrackingData?>,
    onBackClick: () -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
) {
    val state by faceTracker.state.collectAsState()
    val trackingData by trackingDataState.collectAsState()
    val trace = remember { mutableStateListOf<EvidencePoint>() }
    var geometrySession by remember { mutableStateOf<CheekPuffContourObservationSession?>(null) }
    var geometryStage by remember { mutableStateOf<GeometryStage>(GeometryStage.NeedsNeutral) }
    var geometryObservation by remember { mutableStateOf<CheekPuffContourObservation?>(null) }

    LaunchedEffect(trackingData?.timestampMs) {
        val data = trackingData?.takeIf { it.isTracking } ?: return@LaunchedEffect
        trace += EvidencePoint(
            cheekPuff = data.blendShapes[BlendShape.CHEEK_PUFF] ?: 0f,
            jawOpen = data.blendShapes[BlendShape.JAW_OPEN] ?: 0f,
        )
        if (trace.size > TRACE_CAPACITY) trace.removeAt(0)

        val landmarks = data.landmarks?.takeIf {
            it.size == CheekPuffContourObservationSession.LANDMARK_COUNT
        } ?: return@LaunchedEffect
        val session = geometrySession ?: return@LaunchedEffect
        when (val stage = geometryStage) {
            GeometryStage.NeedsNeutral -> Unit
            is GeometryStage.Settling -> {
                if (data.timestampMs - stage.startedAtMs >= GEOMETRY_SETTLING_MS) {
                    geometryStage = GeometryStage.Collecting(frames = 0)
                }
            }
            is GeometryStage.Collecting -> {
                session.addNeutralFrame(landmarks, data.timestampMs)
                val count = stage.frames + 1
                if (count >= CheekPuffContourObservationSession.RECOMMENDED_NEUTRAL_FRAMES) {
                    session.finishNeutralCalibration()
                    geometryStage = GeometryStage.Ready
                } else {
                    geometryStage = GeometryStage.Collecting(count)
                }
            }
            GeometryStage.Ready -> {
                geometryObservation = session.observe(landmarks, data.timestampMs)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(SampleColors.Background)) {
        FaceCameraPreview(faceTracker = faceTracker, modifier = Modifier.fillMaxSize())
        val landmarks = trackingData?.landmarks
        if (trackingData?.isTracking == true && !landmarks.isNullOrEmpty()) {
            FaceLandmarkOverlay(
                landmarks = landmarks,
                sourceImageWidth = trackingData?.sourceImageWidth ?: 0,
                sourceImageHeight = trackingData?.sourceImageHeight ?: 0,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            EvidenceTopBar(
                state = state,
                receivingFrames = trackingData?.isTracking == true,
                onBackClick = onBackClick,
                onStartClick = onStartClick,
                onStopClick = onStopClick,
            )
            Spacer(modifier = Modifier.weight(1f))
            EvidencePanel(
                trackingData = trackingData,
                trace = trace,
                geometryStage = geometryStage,
                geometryObservation = geometryObservation,
                onCalibrateGeometry = {
                    val data = trackingData
                    val landmarks = data?.landmarks
                    if (
                        data?.isTracking == true &&
                        landmarks?.size == CheekPuffContourObservationSession.LANDMARK_COUNT &&
                        data.sourceImageWidth > 0 &&
                        data.sourceImageHeight > 0
                    ) {
                        geometrySession = CheekPuffContourObservationSession(
                            imageWidth = data.sourceImageWidth,
                            imageHeight = data.sourceImageHeight,
                        )
                        geometryObservation = null
                        geometryStage = GeometryStage.Settling(data.timestampMs)
                    }
                },
            )
        }
    }
}

@Composable
private fun EvidenceTopBar(
    state: TrackingState,
    receivingFrames: Boolean,
    onBackClick: () -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
) {
    val isTracking = state == TrackingState.TRACKING || receivingFrames
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SampleColors.Overlay)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBackClick) { Text("Back") }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Live face-tracking evidence",
                    color = SampleColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "MediaPipe raw path · no smoothing · no enhancement",
                    color = SampleColors.TextSecondary,
                    fontSize = 12.sp,
                )
            }
            Button(onClick = if (isTracking) onStopClick else onStartClick) {
                Text(if (isTracking) "Stop" else "Start")
            }
        }
    }
}

@Composable
private fun EvidencePanel(
    trackingData: FaceTrackingData?,
    trace: List<EvidencePoint>,
    geometryStage: GeometryStage,
    geometryObservation: CheekPuffContourObservation?,
    onCalibrateGeometry: () -> Unit,
) {
    val active = trackingData?.isTracking == true
    val cheekPuff = if (active) trackingData.blendShapes[BlendShape.CHEEK_PUFF] ?: 0f else 0f
    val jawOpen = if (active) trackingData.blendShapes[BlendShape.JAW_OPEN] ?: 0f else 0f
    val landmarkCount = if (active) trackingData.landmarks?.size ?: 0 else 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SampleColors.Overlay)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EvidenceValue(
                label = "cheekPuff",
                role = "target coefficient",
                value = cheekPuff,
                color = CHEEK_COLOR,
                modifier = Modifier.weight(1f),
            )
            EvidenceValue(
                label = "jawOpen",
                role = "responsive control",
                value = jawOpen,
                color = CONTROL_COLOR,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        SignalTrace(trace = trace, modifier = Modifier.fillMaxWidth().height(92.dp))
        Text(
            text = if (active) "$landmarkCount landmarks · live estimator output" else "Tap Start to begin",
            color = SampleColors.TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.18f)),
        )
        GeometryEvidence(
            active = active && landmarkCount == CheekPuffContourObservationSession.LANDMARK_COUNT,
            stage = geometryStage,
            observation = geometryObservation,
            onCalibrate = onCalibrateGeometry,
        )
    }
}

@Composable
private fun GeometryEvidence(
    active: Boolean,
    stage: GeometryStage,
    observation: CheekPuffContourObservation?,
    onCalibrate: () -> Unit,
) {
    val calibrating = stage is GeometryStage.Settling || stage is GeometryStage.Collecting
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Frozen contour candidate",
                color = GEOMETRY_COLOR,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "independent signal · canonical cheekPuff is unchanged",
                color = SampleColors.TextSecondary,
                fontSize = 11.sp,
            )
        }
        OutlinedButton(
            onClick = onCalibrate,
            enabled = active && !calibrating,
        ) {
            Text(if (stage == GeometryStage.Ready) "Recalibrate" else "Calibrate neutral")
        }
    }

    val status = when (stage) {
        GeometryStage.NeedsNeutral -> "Start tracking, keep a neutral face, then calibrate"
        is GeometryStage.Settling -> "Neutral: hold still · settling"
        is GeometryStage.Collecting ->
            "Neutral: ${stage.frames}/${CheekPuffContourObservationSession.RECOMMENDED_NEUTRAL_FRAMES} frames"
        GeometryStage.Ready -> "Ready · puff either or both cheeks"
    }
    Text(
        text = status,
        color = SampleColors.TextPrimary,
        fontSize = 13.sp,
        modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
    )

    if (stage == GeometryStage.Ready) {
        val fired = observation?.fired == true
        Text(
            text = "PUKU_FACE_GESTURE  ${if (fired) "ON" else "OFF"}",
            color = if (fired) GEOMETRY_COLOR else SampleColors.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        if (observation != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RuleMetric(
                    label = "mouth width",
                    actual = observation.mouthWidthDelta,
                    operator = "≤",
                    threshold = CheekPuffContourObservationSession.MOUTH_WIDTH_MAXIMUM,
                    passes = observation.mouthWidthDelta <=
                        CheekPuffContourObservationSession.MOUTH_WIDTH_MAXIMUM,
                    modifier = Modifier.weight(1f),
                )
                RuleMetric(
                    label = "oval jaw",
                    actual = observation.ovalJawDelta,
                    operator = "≥",
                    threshold = CheekPuffContourObservationSession.OVAL_JAW_MINIMUM,
                    passes = observation.ovalJawDelta >=
                        CheekPuffContourObservationSession.OVAL_JAW_MINIMUM,
                    modifier = Modifier.weight(1f),
                )
                RuleMetric(
                    label = "oval chin",
                    actual = observation.ovalChinDelta,
                    operator = "≤",
                    threshold = CheekPuffContourObservationSession.OVAL_CHIN_MAXIMUM,
                    passes = observation.ovalChinDelta <=
                        CheekPuffContourObservationSession.OVAL_CHIN_MAXIMUM,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun RuleMetric(
    label: String,
    actual: Double,
    operator: String,
    threshold: Double,
    passes: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(text = label, color = SampleColors.TextSecondary, fontSize = 11.sp)
        Text(
            text = formatGeometryValue(actual),
            color = if (passes) PASS_COLOR else FAIL_COLOR,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = "$operator ${formatGeometryValue(threshold)}  ${if (passes) "PASS" else "WAIT"}",
            color = if (passes) PASS_COLOR else FAIL_COLOR,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun EvidenceValue(
    label: String,
    role: String,
    value: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.semantics {
            contentDescription = "$label $role ${formatEvidenceValue(value)}"
        },
    ) {
        Text(text = label, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(text = role, color = SampleColors.TextSecondary, fontSize = 11.sp)
        Text(
            text = formatEvidenceValue(value),
            color = SampleColors.TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun SignalTrace(trace: List<EvidencePoint>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.background(Color.Black.copy(alpha = 0.35f))) {
        val bottom = size.height - 1f
        drawLine(Color.White.copy(alpha = 0.25f), Offset(0f, bottom), Offset(size.width, bottom))
        drawLine(
            Color.White.copy(alpha = 0.12f),
            Offset(0f, size.height / 2f),
            Offset(size.width, size.height / 2f),
        )
        if (trace.size < 2) return@Canvas

        fun pathOf(value: (EvidencePoint) -> Float): Path = Path().apply {
            trace.forEachIndexed { index, point ->
                val x = index.toFloat() / (TRACE_CAPACITY - 1) * size.width
                val y = bottom - value(point).coerceIn(0f, 1f) * bottom
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
        }

        drawPath(
            path = pathOf(EvidencePoint::cheekPuff),
            color = CHEEK_COLOR,
            style = androidx.compose.ui.graphics.drawscope.Stroke(3f),
        )
        drawPath(
            path = pathOf(EvidencePoint::jawOpen),
            color = CONTROL_COLOR,
            style = androidx.compose.ui.graphics.drawscope.Stroke(3f),
        )
    }
}

internal fun formatEvidenceValue(value: Float): String = String.format(Locale.US, "%.6f", value)

internal fun formatGeometryValue(value: Double): String = String.format(Locale.US, "%.2f", value)
