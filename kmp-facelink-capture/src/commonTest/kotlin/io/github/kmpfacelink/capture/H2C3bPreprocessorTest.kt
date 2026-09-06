package io.github.kmpfacelink.capture

import io.github.kmpfacelink.model.FaceLandmark
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class H2C3bPreprocessorTest {
    @Test
    fun publicPythonGoldenMatchesWithinFrozenTolerance() {
        val preprocessor = H2C3bPreprocessor()
        repeat(15) { index ->
            val state = preprocessor.observeCalibration(frame(index), 1_501L + index * 50L)
            assertEquals(H2FeatureReadiness.CALIBRATING, state.readiness)
        }
        val actual = checkNotNull(preprocessor.observeEvaluation(frame(15), 3_001L).values)
        val expected = floatsFromLittleEndian(base64Decode(GOLDEN_BASE64))
        assertEquals(384, actual.size)
        assertEquals(384, expected.size)
        val maxAbs = actual.indices.maxOf { index -> abs(actual[index] - expected[index]) }
        val maxIndex = actual.indices.maxBy { index -> abs(actual[index] - expected[index]) }
        assertTrue(
            maxAbs <= 1e-5f,
            "public Python golden max_abs=$maxAbs index=$maxIndex " +
                "actual=${actual[maxIndex]} expected=${expected[maxIndex]}",
        )
    }

    @Test
    fun publicPythonGoldenCoversSubpixelScaleRotationAndConstantBorder() {
        val preprocessor = H2C3bPreprocessor()
        repeat(15) { index ->
            preprocessor.observeCalibration(transformedFrame(index), 1_501L + index * 50L)
        }
        val actual = checkNotNull(preprocessor.observeEvaluation(transformedFrame(15), 3_001L).values)
        val expected = floatsFromLittleEndian(base64Decode(TRANSFORMED_GOLDEN_BASE64))
        val maxAbs = actual.indices.maxOf { index -> abs(actual[index] - expected[index]) }
        val maxIndex = actual.indices.maxBy { index -> abs(actual[index] - expected[index]) }
        assertTrue(
            maxAbs <= 1e-5f,
            "transformed public Python golden max_abs=$maxAbs index=$maxIndex " +
                "actual=${actual[maxIndex]} expected=${expected[maxIndex]}",
        )
    }

    @Test
    fun frozenDimensionsAndDegenerateAlignmentFailClosed() {
        assertFailsWith<IllegalArgumentException> {
            H2RgbaLandmarkFrame(
                ByteArray(4),
                1,
                1,
                List(478) { FaceLandmark(0f, 0f) },
                H2FrameOrientation.FRONT_CAMERA_MIRRORED_RGBA_AND_LANDMARKS,
            )
        }
        val preprocessor = H2C3bPreprocessor()
        val degenerate = H2RgbaLandmarkFrame(
            ByteArray(240 * 320 * 4),
            240,
            320,
            List(478) { FaceLandmark(0.5f, 0.5f) },
            H2FrameOrientation.FRONT_CAMERA_MIRRORED_RGBA_AND_LANDMARKS,
        )
        assertEquals(H2FeatureReadiness.REJECTED, preprocessor.observeCalibration(degenerate, 1_501).readiness)
        assertEquals(H2FeatureReadiness.REJECTED, preprocessor.observeCalibration(frame(0), 1_550).readiness)
    }

    @Test
    fun calibrationScopeIsExplicitAndNotConnectedToH2Blocks() {
        val preprocessor = H2C3bPreprocessor()
        repeat(15) { index -> preprocessor.observeCalibration(frame(index), 1_501L + index * 50L) }
        assertEquals(H2FeatureReadiness.READY, preprocessor.observeEvaluation(frame(15), 3_001L).readiness)
        preprocessor.reset()
        assertEquals(H2FeatureReadiness.REJECTED, preprocessor.observeEvaluation(frame(15), 3_001L).readiness)
    }

    @Test
    fun exactCalibrationEndpointsAndAllWindowFramesAreEnforced() {
        val excludedAt1500 = H2C3bPreprocessor()
        excludedAt1500.observeCalibration(frame(0), 1_500L)
        repeat(14) { index -> excludedAt1500.observeCalibration(frame(index + 1), 1_501L + index * 50L) }
        assertEquals(H2FeatureReadiness.REJECTED, excludedAt1500.observeEvaluation(frame(15), 3_001L).readiness)

        val includedAt3000 = H2C3bPreprocessor()
        repeat(14) { index -> includedAt3000.observeCalibration(frame(index), 1_501L + index * 50L) }
        includedAt3000.observeCalibration(frame(14), 3_000L)
        assertEquals(H2FeatureReadiness.CALIBRATING, includedAt3000.observeEvaluation(frame(15), 3_000L).readiness)
        assertEquals(H2FeatureReadiness.READY, includedAt3000.observeEvaluation(frame(15), 3_001L).readiness)

        val firstFifteen = H2C3bPreprocessor()
        val withLaterWindowFrame = H2C3bPreprocessor()
        repeat(15) { index ->
            firstFifteen.observeCalibration(frame(index), 1_501L + index * 50L)
            withLaterWindowFrame.observeCalibration(frame(index), 1_501L + index * 50L)
        }
        withLaterWindowFrame.observeCalibration(frame(15), 3_000L)
        val withoutLater = checkNotNull(firstFifteen.observeEvaluation(frame(15), 3_001L).values)
        val withLater = checkNotNull(withLaterWindowFrame.observeEvaluation(frame(15), 3_001L).values)
        assertTrue(withoutLater != withLater, "the full calibration window must affect the template")
    }

    private fun frame(index: Int): H2RgbaLandmarkFrame {
        val rgba = ByteArray(240 * 320 * 4)
        for (y in 0 until 320) {
            for (x in 0 until 240) {
                val offset = (y * 240 + x) * 4
                rgba[offset] = ((x + index * 7) % 256).toByte()
                rgba[offset + 1] = ((y * 3 + index * 11) % 256).toByte()
                rgba[offset + 2] = ((x + y + index * 13) % 256).toByte()
                rgba[offset + 3] = 0xff.toByte()
            }
        }
        val landmarks = MutableList(478) { FaceLandmark(0f, 0f, 0f) }
        landmarks[33] = FaceLandmark(40f / 240f, 44f / 320f, 0f)
        landmarks[133] = landmarks[33]
        landmarks[362] = FaceLandmark(88f / 240f, 44f / 320f, 0f)
        landmarks[263] = landmarks[362]
        landmarks[1] = FaceLandmark(64f / 240f, 72f / 320f, 0f)
        return H2RgbaLandmarkFrame(
            rgba,
            240,
            320,
            landmarks,
            H2FrameOrientation.FRONT_CAMERA_MIRRORED_RGBA_AND_LANDMARKS,
        )
    }

    private fun transformedFrame(index: Int): H2RgbaLandmarkFrame {
        val rgba = ByteArray(240 * 320 * 4)
        for (y in 0 until 320) {
            for (x in 0 until 240) {
                val offset = (y * 240 + x) * 4
                rgba[offset] = ((x * 3 + y + index * 7) % 256).toByte()
                rgba[offset + 1] = ((x + y * 2 + index * 11) % 256).toByte()
                rgba[offset + 2] = ((x * 2 + y * 5 + index * 13) % 256).toByte()
                rgba[offset + 3] = 0xff.toByte()
            }
        }
        val landmarks = MutableList(478) { FaceLandmark(0f, 0f, 0f) }
        landmarks[33] = FaceLandmark(10.35f / 240f, 15.65f / 320f, 0f)
        landmarks[133] = landmarks[33]
        landmarks[362] = FaceLandmark(55.8f / 240f, 20.2f / 320f, 0f)
        landmarks[263] = landmarks[362]
        landmarks[1] = FaceLandmark(30.45f / 240f, 45.75f / 320f, 0f)
        return H2RgbaLandmarkFrame(
            rgba,
            240,
            320,
            landmarks,
            H2FrameOrientation.FRONT_CAMERA_MIRRORED_RGBA_AND_LANDMARKS,
        )
    }

    private fun floatsFromLittleEndian(bytes: ByteArray): FloatArray = FloatArray(bytes.size / 4) { index ->
        val offset = index * 4
        val bits = (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)
        Float.fromBits(bits)
    }

    private fun base64Decode(value: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val output = ArrayList<Byte>()
        var buffer = 0
        var bits = 0
        value.forEach { char ->
            if (char == '=') return@forEach
            val digit = alphabet.indexOf(char)
            require(digit >= 0)
            buffer = (buffer shl 6) or digit
            bits += 6
            if (bits >= 8) {
                bits -= 8
                output += ((buffer shr bits) and 0xff).toByte()
            }
        }
        return output.toByteArray()
    }

    companion object {
        private const val TRANSFORMED_GOLDEN_BASE64 =
                "37u6vd67ur3eu7q937u6vd67ur26EWA/20msPxqvxT8emtQ/WxrpP/jM7j+w8/4/37u6vd67ur3eu7q937u6vd67ur0RJGQ/jBTVPymc6j+c9PI/wUoAQAF/" +
                "CUCsOwxA37u6vd67ur3eu7q937u6vd67ur0of4o/eTHvP3Qh+T8W8ARAPakLQH8QDkDW4BtA37u6vd67ur3eu7q937u6vd67ur2Wx5g/7aIGQDZ7C0CkqhBA" +
                "JZ8bQDXGFkDHnOY/37u6vd67ur3eu7q937u6vd67ur35KyI/cf4OQAvKE0Bl7h5AYan+P9osgj/M/4O/37u6vd67ur3eu7q937u6vd67ur1JmCY/TpUZQKVO" +
                "BkAD4MU/6cNlvXIDz787vMG/37u6vd67ur3eu7q937u6vd67ur0YhwY/3r/lP/kMgj/2dYK/nsXIv3YGt79ia6W/37u6vd67ur3eu7q937u6vd67ur2nz1o+" +
                "CCIyvf5Cxr9937G/Mwytv3UDlb+1JJK/37u6vd67ur3eu7q937u6vd67ur3eu7q9MB+6v+Tyqb8grZq/sBKNv4L6hb/QXHy/37u6vd67ur3eu7q937u6vd67" +
                "ur3eu7q9Ayafv6gojb/dO5K/xH2Gv/6zbr/0z3e/37u6vd67ur3eu7q937u6vd67ur3eu7q9USyNvza8hb+O63y/7Plwv9jHbL9+d1W/37u6vd67ur3eu7q9" +
                "37u6vd67ur3eu7q9vARAv8gtb7/EKXa/HZFfvzmmdr/EnqG/37u6vd67ur3eu7q937u6vd67ur3eu7q9fGQwv1IAbr/kMHe/1e2Uv0E/mr8N64u/37u6vd67" +
                "ur3eu7q937u6vd67ur3eu7q9BIQyv9eAkL8zvqi/VUmYv2+eir917Y6/37u6vd67ur3eu7q937u6vd67ur3eu7q9uIstv5RNnL8jhIy/uqCJv1AYdr8hYUW/" +
                "37u6vd67ur3eu7q937u6vd67ur3eu7q9PjMDvwg2i7/U3I6/E8BVvxWIP79NzEO/41eTPljnBD96kwA+ius1v+qupL9Ut/6/onsgwJhCM8DJ0k3A/ElowPiR" +
                "LsAfiBPAaBNmPrCEED5YlDM+LzlEvpO4er9gzdS/eewLwMndLMC6YibAftoIwAcACsBPgQTAtTXQPgGZoT6ErLS88CUQv7gMS7/gGqy/w/jzv5xsDsAUmRHA" +
                "xmL6v4JryL9Asbi/rYYFP3X93z4Qw5k+IPkDvqoKk79hc9m/Uo3xv5qtA8B4OvC/UHqpv0XtsL93YZ6/vboVPxLEDz8c3PM+7KDlPfxB/77stbW/ICMcwAFt" +
                "IMD9592/N063v+IEjL++OIq/YooxP8wHGz9y0gs/r065Pn2BNL61416/lpq1vzbEEcA1ZBzAlEu+v3/phr+QomO/9I5APxv2Mj/qBis/2VHhPtpRBz6MU4e+" +
                "Azetv41ojr9KAJ2/gnbIv3mztb9ATH+/FluaP84idj+eUTc/JK0fP2Bcoj4gY7m9hIS1vlIzUb/SmWG/LX1EvwMtkL9ZJZm/MjOgP2h+nD/G8pI/cqI1P4ZVD" +
                "z8p4Jk+yjBFvmjaBr86XgK/wYY0vyuHJr9LwHy/FvKsPyJSpT/19Zw/nXOZP84xXj+tK/o+5JCTPuMEkr3ezfK+SKGxvocUA7+USBi/GJO0P8WUrT8lUKw/y" +
                "Z6hP3bmmT/snI0/uhwKP/ohTz4OiIq5QouRvptMaL6Q6rK+QIG/P9I9uz/pWrE/MiGtP/Ekoz/mX5k/RMyVPxiJOj+ZSNE9+E6APDxBPr6h1Fq+yi/PP1iqv" +
                "z++tL4/I6i1Pxyvqj8YJqg/LhGeP+qjlz9/tYc/8n7APprtgT0jo0u9SAXhP8o82D/2FcQ/Oom/P4pQuj/WU6w/Nb2nP3pCoD8wY5g/kP2SPxyV/D5bX1c9d" +
                "Hn1Pzju4D818+c/RxDPPxAhwD+ys74/WsyyP/t1qD9Dyqc/9uybP0VCkT9OT1Q/Kzr3PzCv+T+6FOs/XhLiP0QP1z8E68M/U0+/P1JSuj9BVKw/OcmlP8Gkm" +
                "T/FTIQ/"

        private const val GOLDEN_BASE64 =
            "3tyuv0nvrr8yk66/mByuv6Jerr+iXq6/3v6nv4w3nb+SQJ2/jTedv8iCnL8IYpy/xP2Yv5IJmb/m1pi/" +
                "qNyYvx72mL92C5m/ZsmTv2C8k79Yr5O/k5yPvxG3i7/onIu/rfCHv1Zog7+CuoO/wq2Dv7F/f7/kHni/" +
                "5B54v/d+d7+NCG6/2ZJbv9NVW78rx1u/TF9Sv7YWTr8KzU2/vKNGvz+wJb9XFyK/XPQiv4zEGL+xC/a+" +
                "b0T0vm9E9L7AYfO+WBcivwRhIr9EPR6/k1/7vsBh875vRPS+Lo7yvhm07r7BD+6+wQ/uvsBy0L6kU2K+" +
                "b0T0vt7W877i6u6+Un3uvvnY7b6AduG+zP12vv7NWb78zVm+kUdXvsk9Or4cKDa+onvrvgI4or5xoJi+" +
                "QoOXvjxdl75EsWe+ZwtEvj4UQ75OBRK+NocVPs2aHD6SwRc+ZPCMvugtTb5fllS+fDFAvtiKxrxAuma8" +
                "WK5/vLgFD7ypJXg+8aanPvYBpj7Sc7Y+iWfavc3y1b1I1NK900MxPu6mpz7wpqc+6JWsPsUc4D4S8+M+" +
                "EvPjPr5m4z7ViRo/QUdAPdF8ST3El2c+ifiwPoj4sD6I+LA+9GENP0CVIz9AlSM/7AgjP7NWKT+FtSo/" +
                "npSGPt4liz7gJYs+4CWLPpFMjD7IEwM/hrUqPwSIKz+FtSo/C1VfPyjndj+BznU/xm3RPqCg1j6ZetY+" +
                "al3VPrUc5D52Ow4/HagNP3U7Dj+4x1Q/nwN7P58Dez8d1ns/MHv9PmGW+z5hlvs+MXv9PoJp/z7SQv4+" +
                "g2n/PhcIPj9DwX0/Q8F9P8HufD/tE5U/lNf5PvY3/D79Xfw+g2n/PoRp/z6Daf8+CkYyPz3BfT87wX0/" +
                "OsF9P7RgkT8KSqQ/Wpb7Pvxd/D6Daf8+gmn/PtJC/j5aciU/vO58PzrBfT86wX0/U2eNPzSQpD8KSqQ/" +
                "XJb7PoNp/z6Daf8+hGn/PlQPHT86vXg/OsF9PzrBfT9F+ok/CkqkP/UmpD/gA6Q/mEnzvx405L9CVeG/" +
                "yIDhv8YR4b+fhd+/UoPfv0BW3788dN2/xi3Zv50f2b91Edm/G4jNv2LQzL8ojci/NjnIv6mOyL82Oci/" +
                "c97Hv0AAyL9SE8i/lvfHv0EAyL9z3se/kZysv66ZrL+hfKy/ctCrv0quq79Krqu/kKKrv4S0qr+tmaq/" +
                "GXKqv3xwqr8cWKq/2Pp9vybjfb+JEn6/75V9v+Qpfb/GTH2/agZ9v7zLe788p3a/bBp2v5Bsdr9fNXa/" +
                "ogZbv7onWr8jgVm/rA9avzzaVr/EWEy/koJMv1sHTL8RG0u/DHxKv2AySr84V0q/7H4iv3hhIb+6/B6/" +
                "SVAfv7v8Hr96LhO/rIHyvmwF8b6Nw/G+luzsvu3u5r4U2ui+JGPCvkQbwr7/bMK+hBy5vizptr57obm+" +
                "1TakvtiOnb7ajp2+W5idvowVWb6MQje9Hcwxvj6PML4CGC2+q+cmviOyHb4ceR2+GHQfvsgY8b1V8D88" +
                "Cmo1PCutOjyAuzM+aaNjPY6Ylz0ek4o9EZaYPdUQ6D3KrYg+/lmMPj5yiT4YtfU+A5P/PgpJ/D4LSfw+" +
                "hR9hPowMYT4SHGo+FBxqPk6BZT6oERQ/IqBNP3YsTj/KuE4/G9MZP6RZAz/45QM/nN3lPoZm5z6VvVs/" +
                "xg+QP+9VkD8ZnJA/hlJsPzofVj85H1Y/u0xVP212UT8goE0/gsmOP2Vujz+K144/UFCeP75Zkz9UqpI/" +
                "FkGSP9+MhD+NAIQ/jQCEPyNRgz9MQ2A/FEKcP5ByuT8ORbo/0za4P0+rnD8jCZQ/JQmUPyYJlD9tKJE/" +
                "BXmQP+9VkD/i6Iw/rtUzP7lXoj+5uLk/ek+5P1Rktz/oVpo/NiyUP0tPlD/jn5M/gkuRPxmckD+w7I8/" +
                "FdUtP72aOj8i4ag/D0W6P49yuT+WKLY/lyWYPyAJlD9fcpQ/uVmTPwR5kD8EeZA/oBAtP9tyLT8O0EE/" +
                "ppSvP6WVuT+5uLk/8mqzP7Cjlj82LJQ/NiyUP6Q2kz8ZnJA/"
    }
}
