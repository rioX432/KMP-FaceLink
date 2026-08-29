package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.FaceLandmark
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Cheek-puff detector built on the face-oval contour instead of the MediaPipe `cheekPuff`
 * coefficient, which does not respond to a real cheek puff on the recordings this rule was
 * frozen against.
 *
 * The rule is three conditions on IOD-normalized, rigid-aligned, rolling-calibrated contour
 * features (all in %IOD, all measured as a signed offset from the running neutral baseline):
 *
 * ```
 * mouth_width <= 3.02   the mouth has not widened
 * oval_jaw    >= 1.54   the contour has moved outward at jaw height
 * oval_chin   <= 2.68   it has not moved outward at the chin
 * ```
 *
 * All three are required; dropping any one lets a different negative through (a widened mouth,
 * a blow/pucker, or a nose sneer respectively). The decision is per frame: there is no debounce,
 * hysteresis or temporal smoothing in the frozen rule.
 *
 * Source of the constants: `scripts/plan2/frozen_rule.json` of the talks-2026 research repo,
 * SHA-256 `32829cea15a5fb3bf2e734ab3249bcf0b8d949e5e17694bfb0ae6f2c181cb231`. The thresholds are
 * the centre of the interval over which the whole rule still passes session A, chosen without
 * reference to the session-A firing rates. The reference implementation is
 * `scripts/plan2/geometry.py` + `rule_freeze_runtime.py`; this class reproduces it exactly.
 *
 * Nothing here is a claim about ARKit accuracy: the rule was frozen on one capture session and
 * its confirmatory evaluation is a separate, single-shot scoring of a second session.
 *
 * Usage is two-phase, mirroring the runtime readiness gate of the reference implementation:
 * feed the opening neutral (after its scripted burn-in) through [addCalibrationFrame], call
 * [finishCalibration], then feed every later frame through [update]. Frames before calibration
 * completes have no decision at all and must be treated as not firing.
 *
 * @param imageWidth width in pixels of the frame the landmarks were normalized against
 * @param imageHeight height in pixels of that frame
 */
// Literal 3s below index the flat [x, y, z, ...] landmark layout.
@Suppress("MagicNumber")
internal class CheekPuffContourDetector(
    imageWidth: Int,
    imageHeight: Int,
) {

    /** One frame's decision plus the baseline-relative features it was taken from, in %IOD. */
    class Decision(
        val fired: Boolean,
        val mouthWidth: Double,
        val ovalJaw: Double,
        val ovalChin: Double,
    )

    private val pixelScaleX = imageWidth.toDouble()
    private val pixelScaleY = imageHeight.toDouble()
    private val pixelScaleZ = imageWidth.toDouble()

    private val calibrationFrames = mutableListOf<DoubleArray>()
    private var lastCalibrationSeconds = 0.0

    private var reference: DoubleArray? = null
    private var iod = 0.0
    private var midX = 0.0
    private var bands: Array<IntArray> = emptyArray()
    private var baseline = DoubleArray(FEATURE_COUNT)
    private val ring = ArrayDeque<DoubleArray>()
    private var lastRecalSeconds = 0.0
    private val aligned = DoubleArray(LANDMARK_COUNT * 3)

    /**
     * Buffers one opening-neutral frame. The caller is responsible for excluding the scripted
     * burn-in: the reference shape is the mean of exactly the frames passed here.
     */
    fun addCalibrationFrame(landmarks: List<FaceLandmark>, timestampSeconds: Double) {
        check(reference == null) { "calibration is already finished" }
        calibrationFrames += toPixelSpace(landmarks)
        lastCalibrationSeconds = timestampSeconds
    }

    /** Builds the reference shape, the initial baseline and the seeded quiet ring. */
    fun finishCalibration() {
        check(reference == null) { "calibration is already finished" }
        require(calibrationFrames.size >= ROLL_MIN) {
            "need at least $ROLL_MIN neutral frames, got ${calibrationFrames.size}"
        }
        // The reference is the mean of the neutral frames after each has been aligned onto the
        // middle neutral frame; averaging without that first alignment would smear head motion in.
        val seed = calibrationFrames[calibrationFrames.size / 2]
        val accumulated = DoubleArray(LANDMARK_COUNT * 3)
        for (frame in calibrationFrames) {
            SimilarityAlign3D.estimate(frame, seed, STABLE).apply(frame, aligned)
            for (i in accumulated.indices) accumulated[i] += aligned[i]
        }
        val count = calibrationFrames.size.toDouble()
        val shape = DoubleArray(accumulated.size) { accumulated[it] / count }
        reference = shape
        iod = planarDistance(shape, IRIS_LEFT, IRIS_RIGHT)
        midX = MID_LINE.sumOf { shape[it * 3] } / MID_LINE.size
        bands = resolveOvalBands(shape)

        val neutralFeatures = calibrationFrames.map { extractFeatures(it) }
        baseline = componentMedians(neutralFeatures)
        for (features in neutralFeatures) {
            if (isQuiet(features, baseline)) ring.addLast(features)
        }
        while (ring.size > ROLL_RING) ring.removeFirst()
        lastRecalSeconds = lastCalibrationSeconds
        calibrationFrames.clear()
    }

    /**
     * Decides one post-calibration frame and advances the rolling baseline. Frames must arrive in
     * capture order; [timestampSeconds] is used only for the recalibration interval.
     */
    fun update(landmarks: List<FaceLandmark>, timestampSeconds: Double): Decision {
        check(reference != null) { "finishCalibration() has not been called" }
        val raw = extractFeatures(toPixelSpace(landmarks))
        // The decision uses the baseline in force on arrival; this frame may then move it.
        val mouthWidth = raw[F_MOUTH_WIDTH] - baseline[F_MOUTH_WIDTH]
        val ovalJaw = raw[F_OVAL_JAW] - baseline[F_OVAL_JAW]
        val ovalChin = raw[F_OVAL_CHIN] - baseline[F_OVAL_CHIN]
        val fired = mouthWidth <= MOUTH_WIDTH_MAX && ovalJaw >= OVAL_JAW_MIN && ovalChin <= OVAL_CHIN_MAX

        if (isQuiet(raw, baseline)) {
            ring.addLast(raw)
            if (ring.size > ROLL_RING) ring.removeFirst()
            if (ring.size >= ROLL_MIN && timestampSeconds - lastRecalSeconds >= ROLL_INTERVAL_S) {
                baseline = componentMedians(ring.toList())
                lastRecalSeconds = timestampSeconds
            }
        }
        return Decision(fired, mouthWidth, ovalJaw, ovalChin)
    }

    private fun toPixelSpace(landmarks: List<FaceLandmark>): DoubleArray {
        require(landmarks.size == LANDMARK_COUNT) {
            "expected $LANDMARK_COUNT landmarks, got ${landmarks.size}"
        }
        val out = DoubleArray(LANDMARK_COUNT * 3)
        for (i in 0 until LANDMARK_COUNT) {
            val point = landmarks[i]
            out[i * 3] = point.x.toDouble() * pixelScaleX
            out[i * 3 + 1] = point.y.toDouble() * pixelScaleY
            out[i * 3 + 2] = point.z.toDouble() * pixelScaleZ
        }
        return out
    }

    /** Rigid-aligns one frame onto the reference shape and reads the raw (un-offset) features. */
    private fun extractFeatures(points: DoubleArray): DoubleArray {
        val shape = checkNotNull(reference)
        SimilarityAlign3D.estimate(points, shape, STABLE).apply(points, aligned)
        val out = DoubleArray(FEATURE_COUNT)
        out[F_MOUTH_WIDTH] = planarDistance(aligned, MOUTH_LEFT, MOUTH_RIGHT) / iod * PERCENT
        out[F_LIP_GAP] = planarDistance(aligned, LIP_TOP, LIP_BOTTOM) / iod * PERCENT
        out[F_OVAL_EYE] = outset(bands[BAND_EYE])
        out[F_OVAL_CHEEK_LOW] = outset(bands[BAND_CHEEK_LOW])
        out[F_OVAL_JAW] = outset(bands[BAND_JAW])
        out[F_OVAL_CHIN] = outset(bands[BAND_CHIN])
        return out
    }

    /** Mean lateral distance of one contour band from the face mid-line, in %IOD. */
    private fun outset(indices: IntArray): Double {
        var sum = 0.0
        for (index in indices) sum += abs(aligned[index * 3] - midX)
        return sum / indices.size / iod * PERCENT
    }

    /**
     * Splits the face oval into height bands using the reference shape, so band membership is
     * fixed at calibration and cannot drift with expression.
     */
    private fun resolveOvalBands(shape: DoubleArray): Array<IntArray> {
        val top = shape[FOREHEAD * 3 + 1]
        val height = shape[CHIN * 3 + 1] - top
        return Array(BAND_LIMITS.size) { band ->
            val low = BAND_LIMITS[band][0]
            val high = BAND_LIMITS[band][1]
            val members = FACE_OVAL.filter {
                val fraction = (shape[it * 3 + 1] - top) / height
                fraction >= low && fraction < high
            }
            require(members.isNotEmpty()) { "oval band $band is empty for this reference shape" }
            members.toIntArray()
        }
    }

    /**
     * Whether a frame may teach the rolling baseline. The lower-cheek/jaw outset relative to the
     * eye band is part of the gate, so a held puff cannot be learned as the new neutral.
     */
    private fun isQuiet(raw: DoubleArray, against: DoubleArray): Boolean {
        val widthRelative = abs(raw[F_MOUTH_WIDTH] - against[F_MOUTH_WIDTH]) /
            max(abs(against[F_MOUTH_WIDTH]), MIN_DENOMINATOR)
        val jawLocal = (raw[F_OVAL_JAW] - raw[F_OVAL_EYE]) - (against[F_OVAL_JAW] - against[F_OVAL_EYE])
        val lowLocal = (raw[F_OVAL_CHEEK_LOW] - raw[F_OVAL_EYE]) - (against[F_OVAL_CHEEK_LOW] - against[F_OVAL_EYE])
        val localOk = max(abs(jawLocal), abs(lowLocal)) < QUIET_LOCAL_DEFORMATION_MAX
        return raw[F_LIP_GAP] < QUIET_LIP_GAP_MAX && widthRelative < QUIET_MOUTH_RELATIVE_MAX && localOk
    }

    private fun planarDistance(points: DoubleArray, a: Int, b: Int): Double {
        val dx = points[a * 3] - points[b * 3]
        val dy = points[a * 3 + 1] - points[b * 3 + 1]
        return sqrt(dx * dx + dy * dy)
    }

    private fun componentMedians(samples: List<DoubleArray>): DoubleArray =
        DoubleArray(FEATURE_COUNT) { k -> median(DoubleArray(samples.size) { samples[it][k] }) }

    private fun median(values: DoubleArray): Double {
        values.sort()
        val middle = values.size / 2
        return if (values.size % 2 == 1) values[middle] else (values[middle - 1] + values[middle]) / 2.0
    }

    companion object {
        /** `mouth_width` upper bound, %IOD relative to the rolling neutral. */
        const val MOUTH_WIDTH_MAX: Double = 3.02

        /** `oval_jaw` lower bound, %IOD relative to the rolling neutral. */
        const val OVAL_JAW_MIN: Double = 1.54

        /** `oval_chin` upper bound, %IOD relative to the rolling neutral. */
        const val OVAL_CHIN_MAX: Double = 2.68

        const val LANDMARK_COUNT: Int = 478

        // Rolling calibration, as pre-registered: a causal 45-frame quiet ring, usable after 15
        // samples and refreshed no more than every 2 s.
        private const val ROLL_RING = 45
        private const val ROLL_MIN = 15
        private const val ROLL_INTERVAL_S = 2.0
        private const val QUIET_LIP_GAP_MAX = 8.0
        private const val QUIET_MOUTH_RELATIVE_MAX = 0.10
        private const val QUIET_LOCAL_DEFORMATION_MAX = 1.5
        private const val MIN_DENOMINATOR = 1e-12
        private const val PERCENT = 100.0

        private const val F_MOUTH_WIDTH = 0
        private const val F_LIP_GAP = 1
        private const val F_OVAL_EYE = 2
        private const val F_OVAL_CHEEK_LOW = 3
        private const val F_OVAL_JAW = 4
        private const val F_OVAL_CHIN = 5
        private const val FEATURE_COUNT = 6

        private const val BAND_EYE = 0
        private const val BAND_CHEEK_LOW = 1
        private const val BAND_JAW = 2
        private const val BAND_CHIN = 3

        // Height fractions from forehead (0) to chin (1). Only the four bands the rule and the
        // quiet gate read are resolved; the brow and upper-cheek bands of the reference
        // implementation are unused here and each band is independent of the others.
        private val BAND_LIMITS = arrayOf(
            doubleArrayOf(0.20, 0.40),
            doubleArrayOf(0.55, 0.70),
            doubleArrayOf(0.70, 0.85),
            doubleArrayOf(0.85, 1.00),
        )

        // Skull, eye corners and nose bridge: rigid under an expression of the mouth and cheeks.
        // 234/454 are deliberately absent - they sit on the cheek contour the rule measures.
        private val STABLE = intArrayOf(10, 168, 6, 197, 195, 33, 133, 362, 263)
        private val MID_LINE = intArrayOf(10, 168, 6, 195, 152)
        private const val IRIS_LEFT = 468
        private const val IRIS_RIGHT = 473
        private const val MOUTH_LEFT = 61
        private const val MOUTH_RIGHT = 291
        private const val LIP_TOP = 13
        private const val LIP_BOTTOM = 14
        private const val FOREHEAD = 10
        private const val CHIN = 152

        private val FACE_OVAL = intArrayOf(
            10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365,
            379, 378, 400, 377, 152, 148, 176, 149, 150, 136, 172, 58, 132, 93,
            234, 127, 162, 21, 54, 103, 67, 109,
        )
    }
}
