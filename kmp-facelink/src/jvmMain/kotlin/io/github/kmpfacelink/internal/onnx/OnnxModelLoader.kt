package io.github.kmpfacelink.internal.onnx

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import io.github.kmpfacelink.util.FaceLinkLogger

/**
 * Loads ONNX model files from classpath resources and creates inference sessions.
 *
 * Models are expected to be bundled as classpath resources under "models/".
 */
internal class OnnxModelLoader {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var detectionSession: OrtSession? = null
    private var landmarkSession: OrtSession? = null

    val detection: OrtSession get() = detectionSession ?: error("Detection model not loaded")
    val landmark: OrtSession get() = landmarkSession ?: error("Landmark model not loaded")

    fun loadModels() {
        val options = OrtSession.SessionOptions()
        options.setIntraOpNumThreads(INTRA_OP_THREADS)

        detectionSession = loadFromResource(DETECTION_MODEL_PATH, options)
        landmarkSession = loadFromResource(LANDMARK_MODEL_PATH, options)

        FaceLinkLogger.d(TAG, "ONNX models loaded successfully")
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

    companion object {
        private const val TAG = "OnnxModelLoader"
        private const val DETECTION_MODEL_PATH = "models/openseeface/mnv3_detection_opt.onnx"
        private const val LANDMARK_MODEL_PATH = "models/openseeface/lm_model3_opt.onnx"
        private const val INTRA_OP_THREADS = 2
    }
}
