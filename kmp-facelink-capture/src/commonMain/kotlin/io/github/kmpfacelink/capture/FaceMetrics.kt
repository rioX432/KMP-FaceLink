package io.github.kmpfacelink.capture

/** Per-frame scalar features fed to soft validation, using the y*aspect isotropy convention. */
public object FaceMetrics {
    private const val MOUTH_CORNER_LEFT = 61
    private const val MOUTH_CORNER_RIGHT = 291
    private const val EYE_OUTER_LEFT = 33
    private const val EYE_OUTER_RIGHT = 263

    public fun earAverage(landmarks: FloatArray, aspect: Float): Float = Ear.averageEar(landmarks, aspect)

    /** Inter-ocular distance between the outer eye corners (aspect-corrected), for the distance gate. */
    public fun iod(landmarks: FloatArray, aspect: Float): Float = Geometry.distance(
        Geometry.point(landmarks, EYE_OUTER_LEFT, aspect),
        Geometry.point(landmarks, EYE_OUTER_RIGHT, aspect),
    )

    /** Mouth corner spread normalized by inter-ocular distance; a smile widens this. */
    public fun mouthWidthOverIod(landmarks: FloatArray, aspect: Float): Float {
        val mouthWidth = Geometry.distance(
            Geometry.point(landmarks, MOUTH_CORNER_LEFT, aspect),
            Geometry.point(landmarks, MOUTH_CORNER_RIGHT, aspect),
        )
        val iod = Geometry.distance(
            Geometry.point(landmarks, EYE_OUTER_LEFT, aspect),
            Geometry.point(landmarks, EYE_OUTER_RIGHT, aspect),
        )
        return if (iod <= 0f) 0f else mouthWidth / iod
    }
}
