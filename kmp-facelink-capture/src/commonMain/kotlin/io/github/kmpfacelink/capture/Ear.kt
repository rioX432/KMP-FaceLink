package io.github.kmpfacelink.capture

/**
 * Eyelid 6-point Eye Aspect Ratio. Iris landmarks (468-477) are intentionally NOT used.
 * EAR = (|p2-p6| + |p3-p5|) / (2*|p1-p4|); self-normalized by eye width, so it must NOT be
 * additionally normalized by IOD. Distances use the y*aspect isotropy convention.
 */
public object Ear {
    // eye A = subject's right / image left
    private val EYE_A = intArrayOf(33, 160, 158, 133, 153, 144)

    // eye B = subject's left / image right
    private val EYE_B = intArrayOf(362, 385, 387, 263, 373, 380)

    public fun eyeEar(landmarks: FloatArray, eye: IntArray, aspect: Float): Float {
        val p1 = Geometry.point(landmarks, eye[0], aspect)
        val p2 = Geometry.point(landmarks, eye[1], aspect)
        val p3 = Geometry.point(landmarks, eye[2], aspect)
        val p4 = Geometry.point(landmarks, eye[3], aspect)
        val p5 = Geometry.point(landmarks, eye[4], aspect)
        val p6 = Geometry.point(landmarks, eye[5], aspect)
        val vertical = Geometry.distance(p2, p6) + Geometry.distance(p3, p5)
        val horizontal = 2f * Geometry.distance(p1, p4)
        return if (horizontal <= 0f) 0f else vertical / horizontal
    }

    /** Mean EAR across both eyes. */
    public fun averageEar(landmarks: FloatArray, aspect: Float): Float =
        (eyeEar(landmarks, EYE_A, aspect) + eyeEar(landmarks, EYE_B, aspect)) / 2f
}
