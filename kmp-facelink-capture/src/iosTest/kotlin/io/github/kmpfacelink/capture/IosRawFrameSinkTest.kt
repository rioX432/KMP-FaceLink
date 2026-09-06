package io.github.kmpfacelink.capture

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class IosRawFrameSinkTest {
    @Test
    fun `frames round trip byte for byte with the Android layout`() {
        val directory = iosTestDirectory("roundtrip")
        val sequences = listOf(0, 1, 4, 7)
        val frames = sequences.map(::iosTestFrame)
        val sink = openRawFrameSink(directory, IOS_TEST_WIDTH, IOS_TEST_HEIGHT)
        frames.forEach(sink::write)
        sink.close()

        val payload = readIosTestFile(joinCapturePath(directory, RawFrameFormat.FRAMES_FILE))
        assertContentEquals(frames.flatMap { it.copyPixels().asIterable() }.toByteArray(), payload)
        val index = readIosTestFile(joinCapturePath(directory, RawFrameFormat.INDEX_FILE))
        frames.forEachIndexed { position, frame ->
            val offset = position * RawFrameFormat.INDEX_RECORD_BYTES
            assertEquals(frame.analysisFrameSeq, index.intLittleEndian(offset))
            assertEquals(frame.phase.code, index.intLittleEndian(offset + 4))
            assertEquals(frame.captureTimestampNs, index.longLittleEndian(offset + 8))
            assertEquals(frame.inferenceTimestampMs, index.longLittleEndian(offset + 16))
            assertEquals(
                position.toLong() * frame.pixelByteCount,
                index.longLittleEndian(offset + 24),
            )
        }
        val manifest = readIosManifest(directory)
        assertEquals(RawFrameFormat.SCHEMA_VERSION, manifest.schemaVersion)
        assertEquals(RawFrameFormat.PIXEL_FORMAT, manifest.pixelFormat)
        assertEquals(IOS_TEST_WIDTH, manifest.width)
        assertEquals(IOS_TEST_HEIGHT, manifest.height)
        assertEquals(frames.size, manifest.frameCount)
        assertEquals(false, manifest.compressed)
        removeCapturePath(directory)
    }

    @Test
    fun `producer buffer reuse cannot repaint an accepted frame`() {
        val directory = iosTestDirectory("buffer-reuse")
        val original = iosTestPixels(42)
        val reused = original.copyOf()
        val frame = RawFrame(
            IOS_TEST_WIDTH,
            IOS_TEST_HEIGHT,
            0,
            0L,
            0L,
            CapturePhase.ACTED,
            reused,
        )
        val sink = openRawFrameSink(directory, IOS_TEST_WIDTH, IOS_TEST_HEIGHT)
        sink.write(frame)
        kotlin.random.Random(999).nextBytes(reused)
        sink.close()

        assertContentEquals(
            original,
            readIosTestFile(joinCapturePath(directory, RawFrameFormat.FRAMES_FILE)),
        )
        removeCapturePath(directory)
    }

    @Test
    fun `inference records use the same fixed little endian strides`() {
        val directory = iosTestDirectory("inference")
        val landmarks = FloatArray(InferenceRecordFormat.LANDMARK_FLOATS) { it + 0.25f }
        val blendshapes = FloatArray(InferenceRecordFormat.BLENDSHAPE_COUNT) { it / 100f }
        val transform = FloatArray(InferenceRecordFormat.TRANSFORM_FLOATS) { it.toFloat() }
        val sink = openInferenceRecordSink(directory)
        sink.write(
            InferenceRecord(
                analysisFrameSeq = 9,
                captureTimestampNs = 12_345L,
                resultElapsedNs = 67_890L,
                phase = CapturePhase.NEUTRAL,
                hasFace = true,
                inferenceFailed = false,
                landmarks = landmarks,
                blendshapes = blendshapes,
                transform = transform,
            ),
        )
        sink.close()

        val index = readIosTestFile(joinCapturePath(directory, InferenceRecordFormat.INDEX_FILE))
        assertEquals(InferenceRecordFormat.INDEX_RECORD_BYTES, index.size)
        assertEquals(9, index.intLittleEndian(0))
        assertEquals(CapturePhase.NEUTRAL.code, index.intLittleEndian(4))
        assertEquals(InferenceRecordFormat.FLAG_HAS_FACE, index.intLittleEndian(8))
        assertEquals(0, index.intLittleEndian(12))
        assertEquals(12_345L, index.longLittleEndian(16))
        assertEquals(67_890L, index.longLittleEndian(24))
        assertEquals(
            InferenceRecordFormat.LANDMARK_FLOATS * Float.SIZE_BYTES,
            readIosTestFile(joinCapturePath(directory, InferenceRecordFormat.LANDMARKS_FILE)).size,
        )
        assertEquals(
            landmarks.first().toRawBits(),
            readIosTestFile(joinCapturePath(directory, InferenceRecordFormat.LANDMARKS_FILE)).intLittleEndian(0),
        )
        assertEquals(
            blendshapes.first().toRawBits(),
            readIosTestFile(joinCapturePath(directory, InferenceRecordFormat.BLENDSHAPES_FILE)).intLittleEndian(0),
        )
        assertEquals(
            transform.first().toRawBits(),
            readIosTestFile(joinCapturePath(directory, InferenceRecordFormat.TRANSFORM_FILE)).intLittleEndian(0),
        )
        assertEquals(
            listOf(
                InferenceRecordFormat.INDEX_FILE,
                InferenceRecordFormat.LANDMARKS_FILE,
                InferenceRecordFormat.BLENDSHAPES_FILE,
                InferenceRecordFormat.TRANSFORM_FILE,
            ).map { sha256OfCaptureFile(joinCapturePath(directory, it)) },
            sink.hashes(),
        )
        removeCapturePath(directory)
    }

    @Test
    fun `unusable inference records preserve every fixed stride with zeroes`() {
        val directory = iosTestDirectory("inference-zeroes")
        val sink = openInferenceRecordSink(directory)
        sink.write(
            InferenceRecord(
                analysisFrameSeq = 0,
                captureTimestampNs = 0L,
                resultElapsedNs = 1L,
                phase = CapturePhase.ACTED,
                hasFace = false,
                inferenceFailed = false,
                landmarks = null,
                blendshapes = null,
                transform = null,
            ),
        )
        sink.close()

        listOf(
            InferenceRecordFormat.LANDMARKS_FILE to InferenceRecordFormat.LANDMARK_FLOATS,
            InferenceRecordFormat.BLENDSHAPES_FILE to InferenceRecordFormat.BLENDSHAPE_COUNT,
            InferenceRecordFormat.TRANSFORM_FILE to InferenceRecordFormat.TRANSFORM_FLOATS,
        ).forEach { (name, floats) ->
            assertContentEquals(
                ByteArray(floats * Float.SIZE_BYTES),
                readIosTestFile(joinCapturePath(directory, name)),
            )
        }
        removeCapturePath(directory)
    }
}
