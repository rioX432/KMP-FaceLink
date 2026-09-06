package io.github.kmpfacelink.internal.onnx

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LandmarkInferenceTest {

    @Test
    fun acceptsTheOpenSeeFaceRuntimeTensorShape() {
        val output = landmarkOutput()

        val channels = OpenSeeFaceLandmarkDecoder.requireOutputShape(arrayOf(output))

        assertEquals(198, channels.size)
        assertEquals(28, channels.first().size)
        assertEquals(28, channels.first().first().size)
    }

    @Test
    fun rejectsTheFormerTwentySevenByTwentySevenAssumption() {
        val output = Array(198) { Array(27) { FloatArray(27) } }

        val error = assertFailsWith<IllegalArgumentException> {
            OpenSeeFaceLandmarkDecoder.requireOutputShape(arrayOf(output))
        }

        assertTrue(error.message.orEmpty().contains("28 rows"))
    }

    @Test
    fun decodesGroupedChannelsAndReturnsPublicXThenYCoordinates() {
        val output = landmarkOutput()
        output[0][2][3] = 0.9f
        output[66][2][3] = 0.5f
        output[132][2][3] = 0.5f
        val input = LandmarkInput(
            tensor = FloatArray(3 * 224 * 224),
            cropX1 = 10,
            cropY1 = 20,
            cropWidth = 224,
            cropHeight = 224,
        )

        val result = OpenSeeFaceLandmarkDecoder.decode(
            value = arrayOf(output),
            input = input,
            frameWidth = 448,
            frameHeight = 448,
        )

        val expectedX = (10f + 3f * 223f / 27f) / 448f
        val expectedY = (20f + 2f * 223f / 27f) / 448f
        assertClose(expectedX, result.landmarks[0])
        assertClose(expectedY, result.landmarks[1])
        assertClose(0.9f, result.confidences[0])
    }

    private fun landmarkOutput(): Array<Array<FloatArray>> {
        return Array(198) { channel ->
            Array(28) {
                FloatArray(28) {
                    if (channel >= 66) 0.5f else 0f
                }
            }
        }
    }

    private fun assertClose(expected: Float, actual: Float) {
        assertTrue(abs(expected - actual) < 0.000001f, "expected=$expected actual=$actual")
    }
}
