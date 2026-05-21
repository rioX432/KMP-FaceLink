package io.github.kmpfacelink.gradle

/**
 * Registry of MediaPipe model metadata used for automated downloads.
 *
 * URLs follow the pattern:
 * `https://storage.googleapis.com/mediapipe-models/{task}/{variant}/{precision}/{version}/{filename}`
 */
public object MediaPipeModelRegistry {

    /** Descriptor for a single downloadable model. */
    public data class ModelEntry(
        /** Display name for logging. */
        val name: String,
        /** URL template with `{precision}` and `{version}` placeholders. */
        val urlTemplate: String,
        /** Local filename written to the output directory. */
        val filename: String,
        /** SHA-256 checksum for integrity verification (empty until populated). */
        val sha256: String,
    )

    private const val BASE_URL = "https://storage.googleapis.com/mediapipe-models"

    /** Face landmarker with ARKit-compatible blendshapes. */
    public val faceLandmarker: ModelEntry = ModelEntry(
        name = "Face Landmarker",
        urlTemplate = "$BASE_URL/face_landmarker/face_landmarker/{precision}/{version}/face_landmarker.task",
        filename = "face_landmarker_v2_with_blendshapes.task",
        sha256 = "",
    )

    /** Hand landmarker for 21-point hand skeleton tracking. */
    public val handLandmarker: ModelEntry = ModelEntry(
        name = "Hand Landmarker",
        urlTemplate = "$BASE_URL/hand_landmarker/hand_landmarker/{precision}/{version}/hand_landmarker.task",
        filename = "hand_landmarker.task",
        sha256 = "",
    )

    /** Pose landmarker (lite variant) for full-body skeleton tracking. */
    public val poseLandmarker: ModelEntry = ModelEntry(
        name = "Pose Landmarker Lite",
        urlTemplate = "$BASE_URL/pose_landmarker/pose_landmarker_lite/{precision}/{version}/pose_landmarker_lite.task",
        filename = "pose_landmarker_lite.task",
        sha256 = "",
    )

    /** Returns all registered models. */
    public fun all(): List<ModelEntry> = listOf(faceLandmarker, handLandmarker, poseLandmarker)

    /**
     * Resolves the download URL for a [model] by substituting precision and version placeholders.
     */
    public fun resolveUrl(model: ModelEntry, precision: String, version: String): String =
        model.urlTemplate
            .replace("{precision}", precision)
            .replace("{version}", version)
}
