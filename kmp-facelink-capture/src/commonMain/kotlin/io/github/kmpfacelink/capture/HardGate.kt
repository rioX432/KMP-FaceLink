package io.github.kmpfacelink.capture

/**
 * Blocking pre-recording gate: a face must be present, roughly centered, adequately sized and
 * adequately lit before the auto-countdown starts. All thresholds are on the analysis bitmap
 * coordinate space (normalized bbox) and frame luma (0..255).
 */
public object HardGate {

    // Inter-ocular-distance proxy band derived from recorded examples. It controls countdown
    // eligibility, not a validated physical-distance or take-quality range.
    public const val IOD_MIN: Float = 0.29f
    public const val IOD_MAX: Float = 0.37f

    public data class Thresholds(
        val centerMin: Float = 0.32f,
        val centerMax: Float = 0.68f,
        val minFaceHeight: Float = 0.25f,
        val maxFaceHeight: Float = 0.90f,
        val minLuma: Float = 45f,
        val maxLuma: Float = 225f,
        val minIod: Float = IOD_MIN,
        val maxIod: Float = IOD_MAX,
    )

    public data class FaceBox(val centerX: Float, val centerY: Float, val width: Float, val height: Float)

    public data class Result(
        val hasFace: Boolean,
        val centered: Boolean,
        val sized: Boolean,
        val bright: Boolean,
        val distanced: Boolean,
    ) {
        val passed: Boolean get() = hasFace && centered && sized && bright && distanced
    }

    /** Which way the subject must move to reach the IOD band (NONE = in band or no face). */
    public enum class DistanceHint { NONE, MOVE_CLOSER, MOVE_BACK }

    /** Axis-aligned normalized bounding box over all landmarks. */
    public fun boundingBox(landmarks: FloatArray): FaceBox {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var i = 0
        while (i < landmarks.size) {
            val x = landmarks[i]
            val y = landmarks[i + 1]
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
            i += 3
        }
        return FaceBox(
            centerX = (minX + maxX) / 2f,
            centerY = (minY + maxY) / 2f,
            width = maxX - minX,
            height = maxY - minY,
        )
    }

    public fun evaluate(box: FaceBox?, luma: Float, iod: Float, t: Thresholds = Thresholds()): Result {
        if (box == null) {
            return Result(hasFace = false, centered = false, sized = false, bright = false, distanced = false)
        }
        val centered = box.centerX in t.centerMin..t.centerMax && box.centerY in t.centerMin..t.centerMax
        val sized = box.height in t.minFaceHeight..t.maxFaceHeight
        val bright = luma in t.minLuma..t.maxLuma
        val distanced = iod in t.minIod..t.maxIod
        return Result(hasFace = true, centered = centered, sized = sized, bright = bright, distanced = distanced)
    }

    public fun distanceHint(iod: Float, hasFace: Boolean, t: Thresholds = Thresholds()): DistanceHint = when {
        !hasFace -> DistanceHint.NONE
        iod < t.minIod -> DistanceHint.MOVE_CLOSER
        iod > t.maxIod -> DistanceHint.MOVE_BACK
        else -> DistanceHint.NONE
    }

    public fun inBand(iod: Float, t: Thresholds = Thresholds()): Boolean = iod in t.minIod..t.maxIod
}
