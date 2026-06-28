package io.github.kmpfacelink.internal.depth

import android.content.Context
import android.graphics.Bitmap
import io.github.kmpfacelink.util.FaceLinkLogger

/**
 * Produces a dense relative-depth map from a single RGB camera frame.
 *
 * This is the Android-only software depth path (#898). It is intentionally a standalone
 * component: it does **not** touch any existing MediaPipe tracker or its GPU delegate. Depth
 * estimation is opt-in — when disabled, no LiteRT runtime is initialized and existing trackers
 * are completely unaffected.
 *
 * Implementations own a dedicated worker thread because the LiteRT GPU delegate must be created
 * and invoked on the same thread (thread-affinity requirement), which also isolates depth work
 * from MediaPipe's own threads.
 */
internal interface MonocularDepthEstimator {

    /** Width of the depth map returned by [estimate]. */
    val outputWidth: Int

    /** Height of the depth map returned by [estimate]. */
    val outputHeight: Int

    /**
     * Runs depth inference on [bitmap].
     *
     * **Blocks the calling thread** until inference completes on the worker thread — call from a
     * background coroutine/worker, never the main thread. Must **not** be called concurrently with
     * [release]; doing so may throw once the worker executor is shut down.
     *
     * @return a row-major depth map of length [outputWidth] * [outputHeight]. Values are
     *   relative (affine-invariant) — only their ordering is meaningful, not absolute distance.
     */
    fun estimate(bitmap: Bitmap): FloatArray

    /** Releases the interpreter, GPU delegate, and worker thread. Idempotent. */
    fun release()
}

/** Per-channel normalization (subtract [mean], divide by [std]) applied before inference. */
internal data class ChannelNorm(val mean: Float, val std: Float)

/** RGB normalization parameters. Model-dependent — confirm against the chosen model's metadata. */
internal data class DepthNormalization(
    val r: ChannelNorm,
    val g: ChannelNorm,
    val b: ChannelNorm,
) {
    internal companion object {
        /** ImageNet normalization — used by Depth Anything V2. */
        val IMAGENET: DepthNormalization = DepthNormalization(
            r = ChannelNorm(IMAGENET_R_MEAN, IMAGENET_R_STD),
            g = ChannelNorm(IMAGENET_G_MEAN, IMAGENET_G_STD),
            b = ChannelNorm(IMAGENET_B_MEAN, IMAGENET_B_STD),
        )

        /** Identity normalization (raw [0,1] pixels). */
        val UNIT: DepthNormalization = DepthNormalization(
            r = ChannelNorm(0f, 1f),
            g = ChannelNorm(0f, 1f),
            b = ChannelNorm(0f, 1f),
        )
    }
}

/**
 * Compute unit the depth model runs on.
 *
 * Defaults to [GPU] (best throughput for the depth model). The dedicated worker thread keeps depth
 * inference off MediaPipe's threads, but both still share one physical GPU — if GPU contention with
 * MediaPipe degrades tracking, switch to [CPU] to keep the GPU dedicated to MediaPipe.
 */
internal enum class DepthDelegate { GPU, CPU }

/**
 * Configuration for the monocular depth estimator.
 *
 * Defaults target **Depth Anything V2 Small** (the recommended model from the #898 research:
 * Apache-2.0, ImageNet normalization, 518x518 input). The input/output tensor layout is assumed
 * NHWC; this and the normalization constants MUST be validated against the actual `.tflite`
 * metadata during on-device benchmarking before relying on the output.
 */
internal data class DepthEstimatorConfig(
    val modelAssetPath: String = DEFAULT_MODEL_ASSET_PATH,
    val inputSize: Int = DEFAULT_INPUT_SIZE,
    val delegate: DepthDelegate = DepthDelegate.GPU,
    val cpuThreads: Int = DEFAULT_CPU_THREADS,
    val normalization: DepthNormalization = DepthNormalization.IMAGENET,
) {
    init {
        require(inputSize > 0) { "inputSize must be positive, was $inputSize" }
        require(cpuThreads > 0) { "cpuThreads must be positive, was $cpuThreads" }
    }

    internal companion object {
        const val DEFAULT_MODEL_ASSET_PATH: String = "models/depth_anything_v2_small.tflite"
        const val DEFAULT_INPUT_SIZE: Int = 518
        const val DEFAULT_CPU_THREADS: Int = 4
    }
}

/**
 * Creates a [MonocularDepthEstimator], or returns `null` if the model cannot be loaded
 * (e.g. the asset is absent). Returning `null` lets callers degrade gracefully — depth is
 * always opt-in, so a missing model must never crash tracking.
 */
internal fun createMonocularDepthEstimator(
    context: Context,
    config: DepthEstimatorConfig = DepthEstimatorConfig(),
): MonocularDepthEstimator? = try {
    LiteRtDepthEstimator.create(context, config)
} catch (e: Exception) {
    FaceLinkLogger.w("MonocularDepthEstimator", "Depth model unavailable, depth disabled", e)
    null
}

/** ImageNet normalization constants (RGB), shared by Depth Anything V2 preprocessing. */
private const val IMAGENET_R_MEAN = 0.485f
private const val IMAGENET_G_MEAN = 0.456f
private const val IMAGENET_B_MEAN = 0.406f
private const val IMAGENET_R_STD = 0.229f
private const val IMAGENET_G_STD = 0.224f
private const val IMAGENET_B_STD = 0.225f
