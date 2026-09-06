@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@file:Suppress(
    // The receipt is the only type, while the file intentionally owns its complete local verifier.
    "MatchingDeclarationName",
    // Any filesystem/decoding failure must be redacted to one integrity category.
    "TooGenericExceptionCaught",
    "InstanceOfCheckForException",
)

package io.github.kmpfacelink.capture

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString

/** Public-safe receipt for the first-neutral iOS storage gate. */
internal data class IosCoefficientOnlyPreflightReceipt(
    val takeId: String,
    val availableBytes: Long,
)

/**
 * Verifies the published first-neutral take before the participant can continue to slot two.
 *
 * This reads private streams locally but returns only structural facts. It does not score an
 * expression and does not expose a coefficient, transform, pixel, or per-frame timestamp.
 */
internal suspend fun IosCaptureSession.verifyCoefficientOnlyReference(
    takeId: String,
): IosCoefficientOnlyPreflightReceipt = withContext(Dispatchers.Default) {
    try {
        check(studyTakeRecords.value.any { it.takeId == takeId && it.studyRun == studyRun }) {
            "published take $takeId does not belong to the active study run"
        }
        val directory = joinCapturePath(takesDirectory, takeId)
        val availableBytes = captureAvailableBytes(directory)
        requireMinimumIosCaptureCapacity(availableBytes)
        val manifest = readIosTakeManifest(directory)
        require(manifest.takeId == takeId) { "take directory and manifest identity differ" }
        require(manifest.scenarioId == CaptureScenario.NEUTRAL.id) {
            "the coefficient-only preflight requires the first neutral take"
        }
        require(manifest.studyRun == studyRun) { "take manifest belongs to a different study run" }

        val observation = inspectIosCoefficientOnlyTake(directory, manifest)
        require(CapturePreflightPolicy.passesCoefficientOnly(observation)) {
            "coefficient-only storage or result-stream contract failed"
        }
        IosCoefficientOnlyPreflightReceipt(takeId, availableBytes)
    } catch (failure: Throwable) {
        if (failure is Error || failure is CaptureFailureException) throw failure
        throw captureFailure(CaptureFailureCategory.INTEGRITY, failure)
    }
}

internal fun requireMinimumIosCaptureCapacity(availableBytes: Long) {
    if (availableBytes < MINIMUM_IOS_CAPTURE_AVAILABLE_BYTES) {
        throw captureFailure(CaptureFailureCategory.CAPACITY)
    }
}

internal fun inspectIosCoefficientOnlyTake(
    directory: String,
    manifest: TakeManifest,
): CoefficientOnlyPreflightObservation {
    manifest.storageReceipt.requireSchema4Consistency(manifest.framesStored)
    val indexPath = joinCapturePath(directory, InferenceRecordFormat.INDEX_FILE)
    val index = readCaptureBytes(indexPath)
    require(index.size % InferenceRecordFormat.INDEX_RECORD_BYTES == 0) {
        "$indexPath ends in a partial result record"
    }
    val records = index.size / InferenceRecordFormat.INDEX_RECORD_BYTES
    requireResultStreamLength(
        directory,
        InferenceRecordFormat.LANDMARKS_FILE,
        records,
        InferenceRecordFormat.LANDMARK_FLOATS,
    )
    requireResultStreamLength(
        directory,
        InferenceRecordFormat.BLENDSHAPES_FILE,
        records,
        InferenceRecordFormat.BLENDSHAPE_COUNT,
    )
    requireResultStreamLength(
        directory,
        InferenceRecordFormat.TRANSFORM_FILE,
        records,
        InferenceRecordFormat.TRANSFORM_FLOATS,
    )

    val ordered = resultIndexIsStrictlyIncreasing(index, records)
    val expectedHashes = listOf(
        manifest.resultIndexSha256,
        manifest.landmarksSha256,
        manifest.blendshapesSha256,
        manifest.transformSha256,
    )
    val actualHashes = RESULT_STREAM_FILES.map { name ->
        sha256OfCaptureFile(joinCapturePath(directory, name))
    }
    val forbiddenPixelArtifactExists = FORBIDDEN_PIXEL_ARTIFACTS.any { name ->
        capturePathExists(joinCapturePath(directory, name))
    }
    return CoefficientOnlyPreflightObservation(
        schemaVersion = manifest.schemaVersion,
        profile = manifest.storageReceipt.profile,
        pixelSemantics = manifest.storageReceipt.pixelSemantics,
        framesStored = manifest.framesStored,
        framesDirectoryExists = forbiddenPixelArtifactExists,
        analysisFramesAdmitted = manifest.analysisFramesAdmitted,
        manifestInferenceResults = manifest.inferenceResults,
        resultRecords = records,
        sequenceAndTimestampStrictlyIncreasing = ordered,
        resultStreamHashesMatch = actualHashes == expectedHashes,
    )
}

private fun readIosTakeManifest(directory: String): TakeManifest =
    rawFrameJson.decodeFromString(
        readCaptureText(joinCapturePath(directory, InferenceRecordFormat.TAKE_MANIFEST_FILE)),
    )

private fun requireResultStreamLength(
    directory: String,
    name: String,
    records: Int,
    floatsPerRecord: Int,
) {
    val path = joinCapturePath(directory, name)
    val expected = records.toLong() * floatsPerRecord * Float.SIZE_BYTES
    require(captureFileSize(path) == expected) {
        "$path does not contain $records complete aligned result records"
    }
}

@Suppress("ComplexCondition") // All four fields jointly define one aligned result index record.
private fun resultIndexIsStrictlyIncreasing(index: ByteArray, records: Int): Boolean {
    var lastSequence = -1
    var lastCaptureTimestamp = -1L
    var lastResultTimestamp = -1L
    repeat(records) { record ->
        val offset = record * InferenceRecordFormat.INDEX_RECORD_BYTES
        val sequence = index.intLittleEndian(offset)
        val phaseCode = index.intLittleEndian(offset + Int.SIZE_BYTES)
        val captureTimestamp = index.longLittleEndian(offset + INDEX_CAPTURE_TIMESTAMP_OFFSET)
        val resultTimestamp = index.longLittleEndian(offset + INDEX_RESULT_TIMESTAMP_OFFSET)
        if (
            CapturePhase.ofCode(phaseCode) == null ||
            sequence <= lastSequence ||
            captureTimestamp <= lastCaptureTimestamp ||
            resultTimestamp <= lastResultTimestamp
        ) {
            return false
        }
        lastSequence = sequence
        lastCaptureTimestamp = captureTimestamp
        lastResultTimestamp = resultTimestamp
    }
    return true
}

@Suppress("MagicNumber") // Fixed four-byte little-endian wire offsets, not tunable values.
private fun ByteArray.intLittleEndian(offset: Int): Int =
    (this[offset].toInt() and BYTE_MASK) or
        ((this[offset + 1].toInt() and BYTE_MASK) shl Byte.SIZE_BITS) or
        ((this[offset + 2].toInt() and BYTE_MASK) shl (2 * Byte.SIZE_BITS)) or
        ((this[offset + 3].toInt() and BYTE_MASK) shl (3 * Byte.SIZE_BITS))

private fun ByteArray.longLittleEndian(offset: Int): Long {
    var value = 0L
    repeat(Long.SIZE_BYTES) { index ->
        value = value or ((this[offset + index].toLong() and BYTE_MASK.toLong()) shl (index * Byte.SIZE_BITS))
    }
    return value
}

internal const val MINIMUM_IOS_CAPTURE_AVAILABLE_BYTES: Long = 1L shl 30
private const val BYTE_MASK = 0xff
private const val INDEX_CAPTURE_TIMESTAMP_OFFSET = 16
private const val INDEX_RESULT_TIMESTAMP_OFFSET = 24
private const val IOS_FRAMES_DIRECTORY = "frames"
private val RESULT_STREAM_FILES = listOf(
    InferenceRecordFormat.INDEX_FILE,
    InferenceRecordFormat.LANDMARKS_FILE,
    InferenceRecordFormat.BLENDSHAPES_FILE,
    InferenceRecordFormat.TRANSFORM_FILE,
)
private val FORBIDDEN_PIXEL_ARTIFACTS = listOf(
    IOS_FRAMES_DIRECTORY,
    RawFrameFormat.FRAMES_FILE,
    RawFrameFormat.INDEX_FILE,
    RawFrameFormat.FRAMES_META_FILE,
)
