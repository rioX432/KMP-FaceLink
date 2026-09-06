package io.github.kmpfacelink.internal.onnx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FaceDetectorTest {

    @Test
    fun acceptsPinnedDetectionTensorShape() {
        val session = testSession()
        try {
            val detector = FaceDetector(session)
            val value = arrayOf(Array(2) { Array(56) { FloatArray(56) } })

            val output = detector.requireOutputShape(value, "output")

            assertEquals(2, output.size)
            assertEquals(56, output.first().size)
        } finally {
            session.close()
        }
    }

    @Test
    fun rejectsSingleChannelDetectionTensor() {
        val session = testSession()
        try {
            val detector = FaceDetector(session)
            val value = arrayOf(Array(1) { Array(56) { FloatArray(56) } })

            val error = assertFailsWith<IllegalArgumentException> {
                detector.requireOutputShape(value, "output")
            }

            assertTrue(error.message.orEmpty().contains("2 channels"))
        } finally {
            session.close()
        }
    }

    private fun testSession() = ai.onnxruntime.OrtEnvironment.getEnvironment().createSession(
        javaClass.classLoader.getResourceAsStream(OnnxModelLoader.DETECTION_MODEL_PATH)?.use { it.readBytes() }
            ?: error("Missing pinned detection model; run scripts/setup-openseeface-models.sh"),
    )
}
