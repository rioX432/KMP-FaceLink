package io.github.kmpfacelink.internal.onnx

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import io.github.kmpfacelink.util.FaceLinkLogger

/**
 * Loads ONNX model files from classpath resources and creates inference sessions.
 *
 * Models are expected to be bundled as classpath resources under "models/".
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
        logRuntimeEnvironment(threads)

        val options = buildSessionOptions(threads)

        detectionSession = loadFromResource(DETECTION_MODEL_PATH, options)
        landmarkSession = loadFromResource(LANDMARK_MODEL_PATH, options)

        FaceLinkLogger.d(TAG, "ONNX models loaded successfully")
    }

    private fun buildSessionOptions(intraOpThreads: Int): OrtSession.SessionOptions {
        val options = OrtSession.SessionOptions()
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        options.setIntraOpNumThreads(intraOpThreads)
        return options
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
     * Logs detected CPU architecture and intra-op thread count.
     *
     * On ARM64 hosts (Apple Silicon, ARM servers), this confirms that the loaded
     * native library should be using Arm KleidiAI-accelerated MLAS kernels.
     */
    private fun logRuntimeEnvironment(intraOpThreads: Int) {
        val osArch = System.getProperty("os.arch") ?: "unknown"
        val osName = System.getProperty("os.name") ?: "unknown"
        val isArm64 = osArch.equals("aarch64", ignoreCase = true) || osArch.equals("arm64", ignoreCase = true)
        val kleidiaiNote = if (isArm64) {
            "KleidiAI auto-enabled via MLAS (ARM64 detected)"
        } else {
            "KleidiAI not applicable ($osArch)"
        }
        FaceLinkLogger.d(
            TAG,
            "ONNX Runtime initialized: os=$osName arch=$osArch threads=$intraOpThreads — $kleidiaiNote",
        )
    }

    internal companion object {
        private const val TAG = "OnnxModelLoader"
        private const val DETECTION_MODEL_PATH = "models/openseeface/mnv3_detection_opt.onnx"
        private const val LANDMARK_MODEL_PATH = "models/openseeface/lm_model3_opt.onnx"

        /** Maximum intra-op threads — bounded to avoid oversubscription between detection and landmark sessions. */
        private const val MAX_INTRA_OP_THREADS = 4

        /** Minimum intra-op threads — guarantees at least 2 on single-core VMs. */
        private const val MIN_INTRA_OP_THREADS = 2

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
