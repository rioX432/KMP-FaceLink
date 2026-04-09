package io.github.kmpfacelink.internal.onnx

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import io.github.kmpfacelink.util.FaceLinkLogger

/**
 * Loads ONNX model files from classpath resources and creates inference sessions.
 *
 * Models are expected to be bundled as classpath resources under "models/".
 *
 * On ARM64 hosts, INT8 dynamic-quantized model variants are preferred when available.
 * These models unlock KleidiAI IGEMM / MatMulNBits acceleration (ONNX Runtime v1.22+)
 * for ~2x speedup over FP32 SGEMM. Falls back to FP32 models on x86_64 or when
 * INT8 variants are not present.
 *
 * Session options are tuned for ARM64 hosts to take advantage of ONNX Runtime's
 * MLAS backend, which auto-enables Arm KleidiAI on ARM64 since ONNX Runtime v1.22
 * (SGEMM / MatMulNBits / SME2 convolution acceleration). No explicit execution
 * provider is required — KleidiAI is wired into the default CPU EP.
 */
internal class OnnxModelLoader {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var detectionSession: OrtSession? = null
    private var landmarkSession: OrtSession? = null

    val detection: OrtSession get() = detectionSession ?: error("Detection model not loaded")
    val landmark: OrtSession get() = landmarkSession ?: error("Landmark model not loaded")

    fun loadModels() {
        val threads = resolveIntraOpThreads()
        val arm64 = isArm64Host()
        logRuntimeEnvironment(threads, arm64)

        val options = buildSessionOptions(threads)

        detectionSession = loadModelWithFallback(
            preferredPath = DETECTION_MODEL_INT8_PATH,
            fallbackPath = DETECTION_MODEL_PATH,
            preferQuantized = arm64,
            options = options,
        )
        landmarkSession = loadModelWithFallback(
            preferredPath = LANDMARK_MODEL_INT8_PATH,
            fallbackPath = LANDMARK_MODEL_PATH,
            preferQuantized = arm64,
            options = options,
        )

        FaceLinkLogger.d(TAG, "ONNX models loaded successfully")
    }

    private fun buildSessionOptions(intraOpThreads: Int): OrtSession.SessionOptions {
        val options = OrtSession.SessionOptions()
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        options.setIntraOpNumThreads(intraOpThreads)
        return options
    }

    /**
     * Loads the preferred (quantized) model on ARM64, falling back to the
     * FP32 variant if the quantized resource is unavailable.
     */
    private fun loadModelWithFallback(
        preferredPath: String,
        fallbackPath: String,
        preferQuantized: Boolean,
        options: OrtSession.SessionOptions,
    ): OrtSession {
        if (preferQuantized) {
            val quantized = tryLoadFromResource(preferredPath, options)
            if (quantized != null) {
                FaceLinkLogger.d(TAG, "Loaded INT8 model: $preferredPath")
                return quantized
            }
            FaceLinkLogger.d(TAG, "INT8 model not found ($preferredPath), falling back to FP32")
        }
        return loadFromResource(fallbackPath, options)
    }

    private fun tryLoadFromResource(
        resourcePath: String,
        options: OrtSession.SessionOptions,
    ): OrtSession? {
        val stream = javaClass.classLoader?.getResourceAsStream(resourcePath) ?: return null
        val bytes = stream.use { it.readBytes() }
        return env.createSession(bytes, options)
    }

    private fun loadFromResource(resourcePath: String, options: OrtSession.SessionOptions): OrtSession {
        val stream = javaClass.classLoader?.getResourceAsStream(resourcePath)
            ?: error("Model resource not found: $resourcePath")
        val bytes = stream.use { it.readBytes() }
        return env.createSession(bytes, options)
    }

    fun release() {
        detectionSession?.close()
        landmarkSession?.close()
        detectionSession = null
        landmarkSession = null
        FaceLinkLogger.d(TAG, "ONNX sessions released")
    }

    /**
     * Logs detected CPU architecture, model variant, and intra-op thread count.
     */
    private fun logRuntimeEnvironment(intraOpThreads: Int, arm64: Boolean) {
        val osArch = System.getProperty("os.arch") ?: "unknown"
        val osName = System.getProperty("os.name") ?: "unknown"
        val kleidiaiNote = if (arm64) {
            "KleidiAI auto-enabled via MLAS (ARM64 detected), preferring INT8 models"
        } else {
            "KleidiAI not applicable ($osArch), using FP32 models"
        }
        FaceLinkLogger.d(
            TAG,
            "ONNX Runtime initialized: os=$osName arch=$osArch threads=$intraOpThreads — $kleidiaiNote",
        )
    }

    internal companion object {
        private const val TAG = "OnnxModelLoader"

        // FP32 model paths (fallback / x86_64)
        private const val DETECTION_MODEL_PATH = "models/openseeface/mnv3_detection_opt.onnx"
        private const val LANDMARK_MODEL_PATH = "models/openseeface/lm_model3_opt.onnx"

        // INT8 dynamic-quantized model paths (preferred on ARM64)
        private const val DETECTION_MODEL_INT8_PATH = "models/openseeface/mnv3_detection_opt_int8.onnx"
        private const val LANDMARK_MODEL_INT8_PATH = "models/openseeface/lm_model3_opt_int8.onnx"

        /** Maximum intra-op threads — bounded to avoid oversubscription between detection and landmark sessions. */
        private const val MAX_INTRA_OP_THREADS = 4

        /** Minimum intra-op threads — guarantees at least 2 on single-core VMs. */
        private const val MIN_INTRA_OP_THREADS = 2

        /**
         * Returns true when the host CPU is ARM64 (Apple Silicon, ARM servers, WoA).
         *
         * Visible for testing.
         */
        internal fun isArm64Host(): Boolean {
            val osArch = System.getProperty("os.arch") ?: return false
            return osArch.equals("aarch64", ignoreCase = true) ||
                osArch.equals("arm64", ignoreCase = true)
        }

        /**
         * Resolves intra-op thread count based on available CPU cores.
         *
         * Clamps to [[MIN_INTRA_OP_THREADS], [MAX_INTRA_OP_THREADS]] so that a single
         * session never monopolises all cores — the tracking loop runs detection and
         * landmark inference sequentially, so extra threads past ~4 yield no benefit
         * on typical mobile/desktop ARM CPUs (e.g. Apple M-series performance clusters).
         *
         * Visible for testing.
         */
        internal fun resolveIntraOpThreads(): Int {
            val available = Runtime.getRuntime().availableProcessors()
            return available.coerceIn(MIN_INTRA_OP_THREADS, MAX_INTRA_OP_THREADS)
        }
    }
}
