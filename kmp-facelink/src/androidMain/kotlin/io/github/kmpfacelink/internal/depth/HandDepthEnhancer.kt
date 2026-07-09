package io.github.kmpfacelink.internal.depth

import android.content.Context
import android.graphics.Bitmap
import io.github.kmpfacelink.model.HandLandmarkPoint
import io.github.kmpfacelink.util.DepthAwareHandLifter
import io.github.kmpfacelink.util.DepthMap
import io.github.kmpfacelink.util.FaceLinkLogger

/**
 * Bridges the Android-only [MonocularDepthEstimator] (#898) to the platform-agnostic
 * [DepthAwareHandLifter] (#899).
 *
 * Per frame the tracker calls [estimateDepth] once to run depth inference on the frame bitmap, then
 * [enhance] once per detected hand to rewrite landmark z from the shared depth map.
 *
 * [estimateDepth] **blocks** the calling thread until inference completes (the estimator serializes
 * work on its own executor), so it must run off the main thread — it is called from the MediaPipe
 * result callback thread. Every failure degrades gracefully to "no depth this frame": a missing or
 * unavailable model, or a [release] that races an in-flight estimate, returns null instead of
 * crashing tracking.
 */
internal class HandDepthEnhancer(
    private val estimator: MonocularDepthEstimator,
    private val lifter: DepthAwareHandLifter = DepthAwareHandLifter(),
) {

    /**
     * Runs depth inference on [bitmap] and wraps the result as a [DepthMap], or returns null when
     * inference fails (e.g. the estimator was released concurrently). Blocks until inference
     * completes.
     */
    internal fun estimateDepth(bitmap: Bitmap): DepthMap? = try {
        DepthMap(
            width = estimator.outputWidth,
            height = estimator.outputHeight,
            values = estimator.estimate(bitmap),
        )
    } catch (e: Exception) {
        FaceLinkLogger.w(TAG, "Depth estimation failed, skipping depth for this frame", e)
        null
    }

    /** Rewrites each landmark's z in [landmarks] from [depthMap]. */
    internal fun enhance(
        landmarks: List<HandLandmarkPoint>,
        depthMap: DepthMap,
    ): List<HandLandmarkPoint> = lifter.enhanceDepth(landmarks, depthMap)

    /** Releases the underlying estimator. Idempotent. */
    internal fun release() {
        estimator.release()
    }

    internal companion object {
        private const val TAG = "HandDepthEnhancer"

        /**
         * Creates a [HandDepthEnhancer], or null when no depth estimator can be created (e.g. the
         * model asset is absent). A null result means depth enhancement is silently unavailable and
         * hand tracking runs unchanged.
         */
        internal fun create(context: Context): HandDepthEnhancer? =
            createMonocularDepthEstimator(context)?.let { HandDepthEnhancer(it) }
    }
}
