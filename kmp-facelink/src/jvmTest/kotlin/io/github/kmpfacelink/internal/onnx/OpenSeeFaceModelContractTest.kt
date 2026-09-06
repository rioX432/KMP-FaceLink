package io.github.kmpfacelink.internal.onnx

import kotlin.test.Test
import kotlin.test.assertEquals

class OpenSeeFaceModelContractTest {

    @Test
    fun pinnedModelsLoadAndExecuteWithTheSupportedDecoders() {
        val loader = OnnxModelLoader()
        try {
            loader.loadModels()

            val detection = FaceDetector(loader.detection).detect(
                rgbData = FloatArray(3 * 224 * 224),
                frameWidth = 224,
                frameHeight = 224,
            )
            assertEquals(0, detection.size)

            val landmarkInput = LandmarkInput(
                tensor = FloatArray(3 * 224 * 224),
                cropX1 = 0,
                cropY1 = 0,
                cropWidth = 224,
                cropHeight = 224,
            )
            val result = LandmarkInference(loader.landmark).infer(landmarkInput, 224, 224)
            assertEquals(66 * 2, result?.landmarks?.size)
            assertEquals(66, result?.confidences?.size)
        } finally {
            loader.release()
        }
    }

    @Test
    fun pinnedResourceDigestsMatchRuntimeConstants() {
        assertResourceDigest(
            OnnxModelLoader.DETECTION_MODEL_PATH,
            OnnxModelLoader.DETECTION_MODEL_SHA256,
        )
        assertResourceDigest(
            OnnxModelLoader.LANDMARK_MODEL_PATH,
            OnnxModelLoader.LANDMARK_MODEL_SHA256,
        )
    }

    private fun assertResourceDigest(path: String, expected: String) {
        val bytes = javaClass.classLoader.getResourceAsStream(path)?.use { it.readBytes() }
            ?: error("Missing $path; run scripts/setup-openseeface-models.sh")
        assertEquals(expected, OnnxModelLoader.sha256(bytes))
    }
}
