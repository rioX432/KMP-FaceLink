package io.github.kmpfacelink.capture

import android.content.Context
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import io.github.kmpfacelink.model.InferenceRunningMode

/**
 * The study's own MediaPipe instance, configured explicitly and reported in every take.
 *
 * Deliberately not the library's live tracker. That one exists to drive an avatar, so it smooths,
 * enhances and correlates its output before anything sees it — measuring the estimator through it
 * would measure the correction. This holds the options in one visible place instead, because the
 * options *are* the condition: delegate, running mode and the three confidences all change what the
 * same picture produces.
 *
 * Not thread-safe. Every call has to come from the one analyzer thread; in LIVE_STREAM the results
 * arrive on MediaPipe's own thread instead.
 */
internal class StudyLandmarker(
    private val context: Context,
    private val modelAssetPath: String,
) {

    /** What a result was for. LIVE_STREAM cannot carry a sequence, so it is looked up by timestamp. */
    fun interface Listener {
        fun onResult(result: FaceLandmarkerResult, inferenceTimestampMs: Long)
    }

    var listener: Listener? = null

    /** Errors cannot be tied to a frame, so they are counted rather than recorded as results. */
    var onError: (() -> Unit)? = null

    private var landmarker: FaceLandmarker? = null

    /** The delegate that was actually created, which is not always the one asked for. */
    var delegate: String = "unknown"
        private set

    var runningMode: InferenceRunningMode = InferenceRunningMode.LIVE_STREAM
        private set

    val isOpen: Boolean get() = landmarker != null

    /**
     * Opens the landmarker, falling back from GPU to CPU rather than failing the session.
     *
     * The fallback is reported, not hidden: a take recorded on CPU when GPU was asked for is a
     * different condition, and the manifest has to say which one it was.
     */
    fun open(preferGpu: Boolean, mode: InferenceRunningMode) {
        close()
        runningMode = mode
        val order = if (preferGpu) listOf(Delegate.GPU, Delegate.CPU) else listOf(Delegate.CPU)
        var lastFailure: Throwable? = null
        for (candidate in order) {
            val attempt = runCatching { create(candidate, mode) }
            attempt.getOrNull()?.let {
                landmarker = it
                delegate = candidate.name
                return
            }
            lastFailure = attempt.exceptionOrNull()
        }
        throw IllegalStateException(
            "could not open the face landmarker on ${order.joinToString()}",
            lastFailure,
        )
    }

    private fun create(delegate: Delegate, mode: InferenceRunningMode): FaceLandmarker {
        val base = BaseOptions.builder()
            .setModelAssetPath(modelAssetPath)
            .setDelegate(delegate)
            .build()
        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(mode.toMediaPipe())
            .setNumFaces(NUM_FACES)
            .setMinFaceDetectionConfidence(MIN_DETECTION_CONFIDENCE)
            .setMinTrackingConfidence(MIN_TRACKING_CONFIDENCE)
            .setMinFacePresenceConfidence(MIN_PRESENCE_CONFIDENCE)
            .setOutputFaceBlendshapes(true)
            .setOutputFacialTransformationMatrixes(true)
            .apply {
                // MediaPipe throws if a listener is attached to a synchronous mode.
                if (mode == InferenceRunningMode.LIVE_STREAM) {
                    // The result carries the timestamp the frame was submitted under, which is the
                    // only handle back to the frame it came from.
                    setResultListener { result, _ ->
                        listener?.onResult(result, result.timestampMs())
                    }
                }
            }
            .setErrorListener { onError?.invoke() }
            .build()
        return FaceLandmarker.createFromOptions(context, options)
    }

    /**
     * Submits one frame.
     *
     * LIVE_STREAM returns immediately and answers through the listener, dropping frames to keep up —
     * which is the behaviour being measured, so it must not be worked around. VIDEO and IMAGE block
     * the analyzer thread and return the result inline.
     */
    fun submit(image: MPImage, inferenceTimestampMs: Long): FaceLandmarkerResult? {
        val open = checkNotNull(landmarker) { "landmarker is not open" }
        return when (runningMode) {
            InferenceRunningMode.LIVE_STREAM -> {
                open.detectAsync(image, inferenceTimestampMs)
                null
            }

            InferenceRunningMode.VIDEO -> open.detectForVideo(image, inferenceTimestampMs)
            InferenceRunningMode.IMAGE -> open.detect(image)
        }
    }

    fun close() {
        landmarker?.let { runCatching { it.close() } }
        landmarker = null
    }

    private fun InferenceRunningMode.toMediaPipe(): RunningMode = when (this) {
        InferenceRunningMode.IMAGE -> RunningMode.IMAGE
        InferenceRunningMode.VIDEO -> RunningMode.VIDEO
        InferenceRunningMode.LIVE_STREAM -> RunningMode.LIVE_STREAM
    }

    companion object {
        // Recorded in CaptureCondition on every take, because they are part of what produced it.
        const val NUM_FACES = 1
        const val MIN_DETECTION_CONFIDENCE = 0.5f
        const val MIN_TRACKING_CONFIDENCE = 0.5f
        const val MIN_PRESENCE_CONFIDENCE = 0.5f
    }
}

/** Pulls the raw arrays out of a result, with no correction of any kind applied. */
internal object RawResult {

    /** x, y, z per landmark, interleaved, which is how every consumer of this format reads it. */
    private const val COORDS_PER_LANDMARK = 3

    fun hasFace(result: FaceLandmarkerResult): Boolean = result.faceLandmarks().isNotEmpty()

    /**
     * 478 * 3 interleaved x, y, z — or null when there is no face, and also when the point count is
     * not exactly 478. The second case is a detected face that produced a malformed result, which
     * the caller records as an inference failure rather than as an absent face.
     */
    fun landmarks(result: FaceLandmarkerResult): FloatArray? {
        val face = result.faceLandmarks().firstOrNull() ?: return null
        if (face.size != InferenceRecordFormat.LANDMARK_COUNT) return null
        val out = FloatArray(InferenceRecordFormat.LANDMARK_FLOATS)
        face.forEachIndexed { i, point ->
            val at = i * COORDS_PER_LANDMARK
            out[at] = point.x()
            out[at + 1] = point.y()
            out[at + 2] = point.z()
        }
        return out
    }

    /** 52 raw scores at their category index, or null when absent or not exactly 52. */
    fun blendshapes(result: FaceLandmarkerResult): FloatArray? {
        val categories = result.faceBlendshapes().orElse(null)?.firstOrNull() ?: return null
        if (categories.size != InferenceRecordFormat.BLENDSHAPE_COUNT) return null
        val out = FloatArray(InferenceRecordFormat.BLENDSHAPE_COUNT)
        categories.forEach { category ->
            val index = category.index()
            if (index in out.indices) out[index] = category.score()
        }
        return out
    }

    /** The names MediaPipe itself returned, at their index. Authoritative over any fixed list. */
    fun blendshapeNames(result: FaceLandmarkerResult): List<String>? {
        val categories = result.faceBlendshapes().orElse(null)?.firstOrNull() ?: return null
        val names = arrayOfNulls<String>(InferenceRecordFormat.BLENDSHAPE_COUNT)
        categories.forEach { category ->
            val index = category.index()
            if (index in names.indices) names[index] = category.categoryName()
        }
        return if (names.any { it == null }) null else names.map { it!! }
    }

    /** The 4x4 facial transformation matrix, 16 floats, or null when absent. */
    fun transform(result: FaceLandmarkerResult): FloatArray? =
        result.facialTransformationMatrixes().orElse(null)?.firstOrNull()
}
