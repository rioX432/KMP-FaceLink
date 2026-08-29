@file:OptIn(ExperimentalForeignApi::class)

package io.github.kmpfacelink.capture

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.serialization.decodeFromString
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy
import kotlin.random.Random

internal const val IOS_TEST_WIDTH = 24
internal const val IOS_TEST_HEIGHT = 32

/** Deterministic noise only; biometric images and landmarks never belong in fixtures. */
internal fun iosTestPixels(seed: Int): ByteArray =
    Random(seed).nextBytes(IOS_TEST_WIDTH * IOS_TEST_HEIGHT * RawFrameFormat.BYTES_PER_PIXEL)

internal fun iosTestFrame(
    sequence: Int,
    seed: Int = sequence,
    phase: CapturePhase = CapturePhase.ACTED,
): RawFrame = RawFrame(
    width = IOS_TEST_WIDTH,
    height = IOS_TEST_HEIGHT,
    analysisFrameSeq = sequence,
    captureTimestampNs = sequence * 1_000_000L,
    inferenceTimestampMs = sequence.toLong(),
    phase = phase,
    pixels = iosTestPixels(seed),
)

internal fun iosTestInferenceRecord(
    sequence: Int,
    captureNs: Long = sequence * 1_000_000L,
    resultNs: Long = sequence * 1_000_000L + 1L,
    phase: CapturePhase = CapturePhase.ACTED,
): InferenceRecord = InferenceRecord(
    analysisFrameSeq = sequence,
    captureTimestampNs = captureNs,
    resultElapsedNs = resultNs,
    phase = phase,
    hasFace = true,
    inferenceFailed = false,
    landmarks = FloatArray(InferenceRecordFormat.LANDMARK_FLOATS) { it * 1e-4f },
    blendshapes = FloatArray(InferenceRecordFormat.BLENDSHAPE_COUNT) { it * 1e-2f },
    transform = FloatArray(InferenceRecordFormat.TRANSFORM_FLOATS) { it.toFloat() },
)

internal fun iosTestDirectory(name: String): String = ensureCaptureDirectory(
    joinCapturePath(NSTemporaryDirectory(), "facelink-$name-${NSUUID.UUID().UUIDString}"),
)

internal fun readIosTestFile(path: String): ByteArray {
    val data = checkNotNull(NSData.dataWithContentsOfFile(path)) { "could not read $path" }
    val bytes = ByteArray(data.length.toInt())
    if (bytes.isNotEmpty()) {
        bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), data.bytes, data.length) }
    }
    return bytes
}

internal fun writeIosTestFile(path: String, bytes: ByteArray) {
    val output = NativeOutput.create(path)
    try {
        output.write(bytes)
        output.closeAndSync()
    } catch (t: Throwable) {
        output.closeAfterFailure()
        throw t
    }
}

internal fun readIosManifest(directory: String): RawFrameManifest = rawFrameJson.decodeFromString(
    readIosTestFile(joinCapturePath(directory, RawFrameFormat.FRAMES_META_FILE)).decodeToString(),
)

internal fun ByteArray.intLittleEndian(offset: Int): Int =
    (this[offset].toInt() and BYTE_MASK) or
        ((this[offset + 1].toInt() and BYTE_MASK) shl 8) or
        ((this[offset + 2].toInt() and BYTE_MASK) shl 16) or
        ((this[offset + 3].toInt() and BYTE_MASK) shl 24)

internal fun ByteArray.longLittleEndian(offset: Int): Long {
    var value = 0L
    repeat(Long.SIZE_BYTES) { index ->
        value = value or ((this[offset + index].toLong() and BYTE_MASK.toLong()) shl (index * Byte.SIZE_BITS))
    }
    return value
}

private const val BYTE_MASK = 0xFF
