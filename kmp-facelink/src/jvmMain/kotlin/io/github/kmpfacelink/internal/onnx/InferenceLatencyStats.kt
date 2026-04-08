package io.github.kmpfacelink.internal.onnx

import io.github.kmpfacelink.util.FaceLinkLogger
import java.util.Locale

/**
 * Exponential-moving-average latency tracker for the ONNX tracking loop.
 *
 * Records detection, landmark, and inference-pipeline end-to-end latencies in
 * nanoseconds and emits a periodic debug log every [LOG_INTERVAL_FRAMES]
 * recorded frames so operators can verify that ARM64 hosts are benefitting
 * from the KleidiAI-accelerated MLAS kernels compiled into ONNX Runtime.
 *
 * Not thread-safe — intended for single-coroutine use inside
 * [runTrackingLoop]. Do not share across coroutines without external
 * synchronisation.
 */
internal class InferenceLatencyStats {
    private var frameCount = 0
    private var detectEmaMs = 0.0
    private var landmarkEmaMs = 0.0
    private var inferenceEmaMs = 0.0

    /**
     * Records one sample of per-stage latency (nanoseconds).
     *
     * @param detectNs Time spent in the face detection ONNX session.
     * @param landmarkNs Time spent in landmark inference + post-processing
     *   (blend-shape mapping, head pose, gaze). Only meaningful when a face
     *   was detected on this frame.
     * @param inferenceNs End-to-end inference pipeline time covering
     *   preprocess → detect → landmark → post-processing. Excludes camera
     *   grab and tracking data emission to avoid stalls polluting the EMA.
     */
    fun record(detectNs: Long, landmarkNs: Long, inferenceNs: Long) {
        val detectMs = detectNs / NANOS_PER_MS
        val landmarkMs = landmarkNs / NANOS_PER_MS
        val inferenceMs = inferenceNs / NANOS_PER_MS

        if (frameCount == 0) {
            detectEmaMs = detectMs
            landmarkEmaMs = landmarkMs
            inferenceEmaMs = inferenceMs
        } else {
            detectEmaMs = updateEma(detectEmaMs, detectMs)
            landmarkEmaMs = updateEma(landmarkEmaMs, landmarkMs)
            inferenceEmaMs = updateEma(inferenceEmaMs, inferenceMs)
        }
        frameCount++

        if (frameCount % LOG_INTERVAL_FRAMES == 0) {
            FaceLinkLogger.d(
                TAG,
                "Inference latency (EMA, frame=$frameCount): " +
                    "detect=${format(detectEmaMs)}ms " +
                    "landmark=${format(landmarkEmaMs)}ms " +
                    "inference=${format(inferenceEmaMs)}ms",
            )
        }
    }

    /** Visible for testing. */
    internal fun detectEmaMs(): Double = detectEmaMs

    /** Visible for testing. */
    internal fun landmarkEmaMs(): Double = landmarkEmaMs

    /** Visible for testing. */
    internal fun inferenceEmaMs(): Double = inferenceEmaMs

    private fun updateEma(previous: Double, sample: Double): Double =
        EMA_ALPHA * sample + (1 - EMA_ALPHA) * previous

    private fun format(value: Double): String = String.format(Locale.ROOT, "%.2f", value)

    internal companion object {
        private const val TAG = "InferenceLatencyStats"
        private const val NANOS_PER_MS = 1_000_000.0
        private const val EMA_ALPHA = 0.1
        private const val LOG_INTERVAL_FRAMES = 30
    }
}
