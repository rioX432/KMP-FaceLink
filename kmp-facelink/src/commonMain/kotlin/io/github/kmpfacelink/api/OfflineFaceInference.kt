package io.github.kmpfacelink.api

import io.github.kmpfacelink.ExperimentalFaceLinkApi
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.TrackingContextEpoch

/**
 * One frame handed to [OfflineFaceInference], in the same pixel layout the capture module records.
 *
 * @property width pixel width
 * @property height pixel height
 * @property rgba straight (non-premultiplied) RGBA, one byte per channel, row-major, no padding
 * @property timestampMs monotonically increasing frame time. Only
 *   [io.github.kmpfacelink.model.InferenceRunningMode.VIDEO] uses it, and it must not go backwards
 *   there: MediaPipe carries tracking state forward between frames and rejects a timestamp that
 *   does not advance.
 */
@ExperimentalFaceLinkApi
public class OfflineFrame(
    public val width: Int,
    public val height: Int,
    public val rgba: ByteArray,
    public val timestampMs: Long,
    /** Whether the stored pixels were horizontally mirrored before inference. */
    public val mirroredBeforeInference: Boolean = false,
) {
    init {
        require(width > 0 && height > 0) { "frame must be non-empty, was ${width}x$height" }
        require(rgba.size == width * height * RGBA_CHANNEL_COUNT) {
            val expected = width * height * RGBA_CHANNEL_COUNT
            "expected $expected RGBA bytes for ${width}x$height, got ${rgba.size}"
        }
    }
}

/** Monotonic clock used for every duration in [OfflineFaceTiming]. */
@ExperimentalFaceLinkApi
public enum class OfflineFaceTimingClock {
    /** Android's `SystemClock.elapsedRealtimeNanos()`. */
    ANDROID_ELAPSED_REALTIME_NANOS,
}

/**
 * Non-overlapping durations from one estimator invocation and the SDK processing of that exact
 * result. Bitmap creation, RGBA copy, stored-frame reads, artifact writes, camera work, queues,
 * consumers, and rendering are outside these boundaries.
 */
@ExperimentalFaceLinkApi
public data class OfflineFaceTiming(
    public val clock: OfflineFaceTimingClock,
    public val estimatorCallNs: Long,
    public val sdkProcessToOutputReadyNs: Long,
    public val estimatorToSdkOutputReadyNs: Long,
    public val rawOutputMaterializationNs: Long,
) {
    init {
        require(estimatorCallNs >= 0) { "estimatorCallNs must not be negative" }
        require(sdkProcessToOutputReadyNs >= 0) {
            "sdkProcessToOutputReadyNs must not be negative"
        }
        require(rawOutputMaterializationNs >= 0) {
            "rawOutputMaterializationNs must not be negative"
        }
        require(estimatorToSdkOutputReadyNs == estimatorCallNs + sdkProcessToOutputReadyNs) {
            "continuous estimator-to-output interval must close exactly"
        }
    }
}

/**
 * Raw and processed views of one synchronous Android inference result, with exact runtime context.
 * The processed output uses the same mapper, enhancer, calibrator, smoother, active-shape filter,
 * per-face provenance, and `FaceTrackingData` assembly as the live Android SDK path.
 */
@ExperimentalFaceLinkApi
public data class OfflineMeasuredFaceResult(
    public val raw: OfflineFaceResult,
    public val processed: FaceTrackingData?,
    public val contextEpoch: TrackingContextEpoch,
    public val timing: OfflineFaceTiming,
)

private const val RGBA_CHANNEL_COUNT: Int = 4

/**
 * What one offline inference produced. Arrays are null when no face was found, rather than being
 * zero-filled, so a caller cannot mistake "nothing detected" for "detected at the origin".
 *
 * @property landmarks 478 * 3 floats (x, y, z), normalised to the frame
 * @property blendshapes 52 floats in MediaPipe's category order
 * @property transform 4x4 facial transformation matrix, column-major
 */
@ExperimentalFaceLinkApi
public class OfflineFaceResult(
    public val landmarks: FloatArray?,
    public val blendshapes: FloatArray?,
    public val transform: FloatArray?,
) {
    public val hasFace: Boolean get() = landmarks != null
}

/**
 * Runs face inference over frames the caller supplies, instead of over a live camera.
 *
 * This exists so one recorded take can be scored under several conditions. Running mode, delegate
 * and input resolution can each be varied over the same pixels, which isolates the condition —
 * whereas giving each condition its own capture session confounds it with a different performance
 * of the expression, different lighting and a different day.
 *
 * Use [io.github.kmpfacelink.model.InferenceRunningMode.IMAGE] to detect each frame
 * independently, or `VIDEO` to carry tracking state forward the way the live pipeline does.
 * `LIVE_STREAM` is rejected: it delivers results through a callback on its own schedule and drops
 * frames to keep up, so it cannot answer "what does this exact frame produce".
 *
 * Not thread-safe; drive it from one thread. Close it when done.
 *
 * Live tracking never uses this. It is a measurement tool, which is why it is opt-in rather
 * than stable API surface.
 */
@ExperimentalFaceLinkApi
public interface OfflineFaceInference : Releasable {

    /** Infers one frame. Frames must be supplied in order when running in VIDEO mode. */
    public fun infer(frame: OfflineFrame): OfflineFaceResult

    /**
     * Runs one estimator call, then processes that exact result through the normal SDK pipeline.
     * This is an Android-only measurement path. It is not camera, queue, callback, consumer, or
     * render latency, and it must not be compared to ARKit's callback-side measurements.
     */
    public fun inferMeasured(frame: OfflineFrame): OfflineMeasuredFaceResult {
        throw UnsupportedOperationException("processed offline measurement is unavailable")
    }
}

/**
 * Opens an [OfflineFaceInference] for [config].
 *
 * Android: MediaPipe, honouring `config.performanceConfig.runningMode` and `inferenceDelegate`.
 * iOS: unsupported — ARKit has no way to be handed an arbitrary image, and the library carries no
 * MediaPipe there. Offline re-inference is therefore an Android-side measurement.
 *
 * @throws IllegalArgumentException if the configured running mode is `LIVE_STREAM`
 */
@ExperimentalFaceLinkApi
public expect fun openOfflineFaceInference(
    platformContext: PlatformContext,
    config: FaceTrackerConfig = FaceTrackerConfig(),
): OfflineFaceInference
