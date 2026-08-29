package io.github.kmpfacelink.internal.onnx

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import io.github.kmpfacelink.util.FaceLinkLogger
import java.security.MessageDigest

/**
 * Loads the pinned OpenSeeFace FP32 models and verifies their bytes and ONNX contracts.
 *
 * The model resources are obtained by `scripts/setup-openseeface-models.sh`. Both the
 * downloader and this runtime loader enforce SHA-256 so a moved upstream branch cannot
 * silently change the SDK's estimator. Session metadata is checked before camera access.
 */
internal class OnnxModelLoader {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var detectionSession: OrtSession? = null
    private var landmarkSession: OrtSession? = null

    val detection: OrtSession get() = detectionSession ?: error("Detection model not loaded")
    val landmark: OrtSession get() = landmarkSession ?: error("Landmark model not loaded")

    fun loadModels() {
        release()
        val threads = resolveIntraOpThreads()
        logRuntimeEnvironment(threads)

        var loadedDetection: OrtSession? = null
        var loadedLandmark: OrtSession? = null
        try {
            buildSessionOptions(threads).use { options ->
                loadedDetection = loadVerifiedModel(
                    resourcePath = DETECTION_MODEL_PATH,
                    expectedSha256 = DETECTION_MODEL_SHA256,
                    options = options,
                ).also(::validateDetectionContract)
                loadedLandmark = loadVerifiedModel(
                    resourcePath = LANDMARK_MODEL_PATH,
                    expectedSha256 = LANDMARK_MODEL_SHA256,
                    options = options,
                ).also(::validateLandmarkContract)
            }
            detectionSession = loadedDetection
            landmarkSession = loadedLandmark
            FaceLinkLogger.d(TAG, "Pinned OpenSeeFace model contracts verified")
        } catch (expected: Exception) {
            loadedDetection?.close()
            loadedLandmark?.close()
            throw expected
        }
    }

    private fun buildSessionOptions(intraOpThreads: Int): OrtSession.SessionOptions {
        return OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setIntraOpNumThreads(intraOpThreads)
        }
    }

    private fun loadVerifiedModel(
        resourcePath: String,
        expectedSha256: String,
        options: OrtSession.SessionOptions,
    ): OrtSession {
        val bytes = javaClass.classLoader?.getResourceAsStream(resourcePath)?.use { it.readBytes() }
            ?: error(
                "Model resource not found: $resourcePath. " +
                    "Run scripts/setup-openseeface-models.sh before building the JVM artifact.",
            )
        val actualSha256 = sha256(bytes)
        require(actualSha256 == expectedSha256) {
            "Model digest mismatch for $resourcePath: expected=$expectedSha256 actual=$actualSha256"
        }
        return env.createSession(bytes, options)
    }

    private fun validateDetectionContract(session: OrtSession) {
        requireExactNames("detection inputs", session.inputInfo.keys, setOf(INPUT_NAME))
        requireExactNames("detection outputs", session.outputInfo.keys, setOf(DETECTION_OUTPUT, MAXPOOL_OUTPUT))
        requireFloatTensor(session.inputInfo.getValue(INPUT_NAME).info, DETECTION_INPUT_SHAPE, "detection input")
        requireFloatTensor(
            session.outputInfo.getValue(DETECTION_OUTPUT).info,
            DETECTION_OUTPUT_SHAPE,
            "detection output",
        )
        requireFloatTensor(
            session.outputInfo.getValue(MAXPOOL_OUTPUT).info,
            DETECTION_OUTPUT_SHAPE,
            "detection maxpool output",
        )
    }

    private fun validateLandmarkContract(session: OrtSession) {
        requireExactNames("landmark inputs", session.inputInfo.keys, setOf(INPUT_NAME))
        requireExactNames("landmark outputs", session.outputInfo.keys, setOf(LANDMARK_OUTPUT))
        requireFloatTensor(session.inputInfo.getValue(INPUT_NAME).info, LANDMARK_INPUT_SHAPE, "landmark input")
        requireFloatTensor(
            session.outputInfo.getValue(LANDMARK_OUTPUT).info,
            LANDMARK_OUTPUT_SHAPE,
            "landmark output",
        )
    }

    private fun requireExactNames(label: String, actual: Set<String>, expected: Set<String>) {
        require(actual == expected) { "Unexpected $label: expected=$expected actual=$actual" }
    }

    private fun requireFloatTensor(info: Any, expectedShape: LongArray, label: String) {
        val tensorInfo = info as? TensorInfo
            ?: throw IllegalArgumentException("$label is not a tensor: ${info::class.qualifiedName}")
        require(tensorInfo.type == OnnxJavaType.FLOAT) {
            "$label must be FLOAT, got ${tensorInfo.type}"
        }
        require(tensorInfo.shape.contentEquals(expectedShape)) {
            "$label shape mismatch: expected=${expectedShape.contentToString()} " +
                "actual=${tensorInfo.shape.contentToString()}"
        }
    }

    fun release() {
        detectionSession?.close()
        landmarkSession?.close()
        detectionSession = null
        landmarkSession = null
    }

    private fun logRuntimeEnvironment(intraOpThreads: Int) {
        val osArch = System.getProperty("os.arch") ?: "unknown"
        val osName = System.getProperty("os.name") ?: "unknown"
        FaceLinkLogger.d(
            TAG,
            "ONNX Runtime initialized: os=$osName arch=$osArch threads=$intraOpThreads, pinned FP32 models",
        )
    }

    internal companion object {
        private const val TAG = "OnnxModelLoader"

        internal const val DETECTION_MODEL_PATH = "models/openseeface/mnv3_detection_opt.onnx"
        internal const val LANDMARK_MODEL_PATH = "models/openseeface/lm_model3_opt.onnx"
        internal const val DETECTION_MODEL_SHA256 =
            "0e8e4806766d85ab067a52c7af0dcb59eb7f9dfe580b44f20a8e6ab712d89809"
        internal const val LANDMARK_MODEL_SHA256 =
            "5aa0ab2594acaf2e1d3916a8de592cffeb1f1a7a7f778cad2f37948bff6549d1"

        private const val INPUT_NAME = "input"
        private const val DETECTION_OUTPUT = "output"
        private const val MAXPOOL_OUTPUT = "maxpool"
        private const val LANDMARK_OUTPUT = "output"

        private val DETECTION_INPUT_SHAPE = longArrayOf(-1, 3, 224, 224)
        private val DETECTION_OUTPUT_SHAPE = longArrayOf(-1, 2, 56, 56)
        private val LANDMARK_INPUT_SHAPE = longArrayOf(-1, 3, 224, 224)
        private val LANDMARK_OUTPUT_SHAPE = longArrayOf(-1, 198, 28, 28)

        private const val MAX_INTRA_OP_THREADS = 4
        private const val MIN_INTRA_OP_THREADS = 2

        internal fun isArm64Host(): Boolean {
            val osArch = System.getProperty("os.arch") ?: return false
            return osArch.equals("aarch64", ignoreCase = true) ||
                osArch.equals("arm64", ignoreCase = true)
        }

        internal fun resolveIntraOpThreads(): Int {
            val available = Runtime.getRuntime().availableProcessors()
            return available.coerceIn(MIN_INTRA_OP_THREADS, MAX_INTRA_OP_THREADS)
        }

        internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
