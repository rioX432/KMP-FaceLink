package io.github.kmpfacelink.capture

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AndroidParticipantQualificationManagerTest {
    @Test
    fun activeAttemptRestoresAsTerminalInsteadOfSilentlyResuming() {
        withFixture { root, takes, run ->
            val manager = manager(root, takes, run)
            manager.beginAttempt(qualificationSlotKeys().first())

            val restored = manager(root, takes, run)

            assertEquals(ParticipantQualificationPhase.TERMINATED, restored.state.value.phase)
            assertEquals(INTERRUPTION_CODE, restored.state.value.terminalCode)
        }
    }

    @Test
    fun oneEligibleReplacementPersistsAcrossRestart() {
        withFixture { root, takes, run ->
            val slot = qualificationSlotKeys().first()
            val first = manager(root, takes, run)
            first.beginAttempt(slot)
            assertEquals(
                ParticipantQualificationFailureDecision.RETRY_ONCE,
                first.failAttempt(slot, CaptureFailureCategory.STREAM_WRITE.code),
            )

            val restored = manager(root, takes, run)
            restored.beginAttempt(slot)
            assertEquals(
                ParticipantQualificationFailureDecision.TERMINATED,
                restored.failAttempt(slot, CaptureFailureCategory.INTEGRITY.code),
            )
            assertEquals(2, restored.state.value.failures.last().captureAttempt)
        }
    }

    @Test
    fun completeExact12SealsAndStreamMutationFailsClosed() = runBlocking {
        withFixture { root, takes, run ->
            createPublishedTakes(takes, run)
            val manager = manager(root, takes, run)
            val firstTake = takes.listFiles()!!.sortedBy(File::getName).first().name
            manager.recordReferencePreflight(firstTake)
            val seal = manager.seal()

            assertEquals(ParticipantQualificationPhase.SEALED, manager.state.value.phase)
            assertEquals(12, seal.slots.size)
            assertFailsWith<IllegalStateException> { manager.beginAttempt(qualificationSlotKeys().first()) }

            val restored = manager(root, takes, run)
            assertEquals(seal, restored.state.value.seal)
        }

        withFixture { root, takes, run ->
            createPublishedTakes(takes, run)
            val manager = manager(root, takes, run)
            val firstTake = takes.listFiles()!!.sortedBy(File::getName).first().name
            manager.recordReferencePreflight(firstTake)
            File(File(takes.listFiles()!!.first(), AndroidCaptureSession.FRAMES_DIR), RawFrameFormat.INDEX_FILE)
                .appendBytes(byteArrayOf(1))

            assertFailsWith<IllegalArgumentException> { manager.seal() }
            assertEquals(ParticipantQualificationPhase.TERMINATED, manager.state.value.phase)
        }
    }

    private fun createPublishedTakes(takes: File, run: StudyRunIdentity) {
        val condition = CaptureCondition(
            nativeResolution = "240x320",
            runningMode = "LIVE_STREAM",
            delegate = "CPU",
            numFaces = 1,
            minDetectionConfidence = 0.5f,
            minTrackingConfidence = 0.5f,
            minPresenceConfidence = 0.5f,
            mediapipeVersion = "fixture",
            modelSha256 = "7".repeat(64),
            mirrored = true,
            participantPreview = ParticipantPreview.NATIVE_CAMERA_WITH_GUIDE,
            layer = MeasurementLayer.DEVICE_REALTIME,
            provenance = SourceProvenance(
                repo = "KMP-FaceLink",
                branch = "fixture",
                gitSha = "1".repeat(40),
                commitTimeIso = "2026-08-30T00:00:00Z",
                dirty = false,
            ),
        )
        studyTakeSlots(CaptureStudyProtocol.learnedPatchSessionC12().scenarios).forEachIndexed {
                index,
                slot,
            ->
            val takeId = "%02d_%s_%d".format(index, slot.scenario.id, slot.attempt)
            val directory = File(takes, takeId).apply { mkdirs() }
            val frames = File(directory, AndroidCaptureSession.FRAMES_DIR).apply { mkdirs() }
            val streams = listOf(
                File(directory, InferenceRecordFormat.INDEX_FILE) to byteArrayOf(1, index.toByte()),
                File(directory, InferenceRecordFormat.LANDMARKS_FILE) to byteArrayOf(2, index.toByte()),
                File(directory, InferenceRecordFormat.BLENDSHAPES_FILE) to byteArrayOf(3, index.toByte()),
                File(directory, InferenceRecordFormat.TRANSFORM_FILE) to byteArrayOf(4, index.toByte()),
                File(frames, RawFrameFormat.FRAMES_FILE) to ByteArray(CaptureStorageReceipt.CANONICAL_BYTES) {
                    index.toByte()
                },
                File(frames, RawFrameFormat.INDEX_FILE) to ByteArray(RawFrameFormat.INDEX_RECORD_BYTES) {
                    index.toByte()
                },
            )
            streams.forEach { (file, bytes) -> file.writeBytes(bytes) }
            val hashes = streams.map { (_, bytes) -> sha256(bytes) }
            val frameManifest = RawFrameManifest(
                schemaVersion = RawFrameFormat.SCHEMA_VERSION,
                pixelFormat = RawFrameFormat.PIXEL_FORMAT,
                bytesPerPixel = RawFrameFormat.BYTES_PER_PIXEL,
                width = CaptureStorageReceipt.CANONICAL_WIDTH,
                height = CaptureStorageReceipt.CANONICAL_HEIGHT,
                frameCount = 1,
                bytesPerFrame = CaptureStorageReceipt.CANONICAL_BYTES,
                indexRecordBytes = RawFrameFormat.INDEX_RECORD_BYTES,
                compressed = false,
                payloadSha256 = hashes[4],
                indexSha256 = hashes[5],
                phaseCodes = capturePhaseCodes(),
            )
            File(frames, RawFrameFormat.FRAMES_META_FILE).writeText(rawFrameJson.encodeToString(frameManifest))
            val takeManifest = TakeManifest(
                schemaVersion = TakeManifest.SCHEMA_VERSION,
                takeId = takeId,
                scenarioId = slot.scenario.id,
                attempt = slot.attempt,
                startedAtIso = "2026-08-30T00:00:00Z",
                condition = condition,
                analysisFramesAdmitted = 1,
                inferenceResults = 1,
                noFaceResults = 0,
                inferenceFailedResults = 0,
                inferenceErrors = 0,
                framesStored = 1,
                softValidationPassed = true,
                softValidationReason = "fixture",
                medianIod = 0.2f,
                outOfDistanceBandFraction = 0f,
                spans = listOf(TakeSpan("fixture", CapturePhase.NEUTRAL.code, 0, 1, 0, 0)),
                blendshapeNames = listOf("cheekPuff"),
                resultIndexSha256 = hashes[0],
                landmarksSha256 = hashes[1],
                blendshapesSha256 = hashes[2],
                transformSha256 = hashes[3],
                captureWorkflow = CaptureWorkflow.GUIDED_GATE_AUTOSTART_V1,
                storageReceipt = CaptureStorageReceipt(
                    profile = CaptureStorageProfile.CANONICAL_RGBA_240X320_V1,
                    pixelSemantics = CapturePixelSemantics.EXACT_ANDROID_INFERENCE_INPUT,
                    sourceWidth = CaptureStorageReceipt.CANONICAL_WIDTH,
                    sourceHeight = CaptureStorageReceipt.CANONICAL_HEIGHT,
                    storedWidth = CaptureStorageReceipt.CANONICAL_WIDTH,
                    storedHeight = CaptureStorageReceipt.CANONICAL_HEIGHT,
                    orientationPolicy = "fixture",
                    mirrorPolicy = "fixture",
                    canonicalizationAlgorithm = "fixture",
                ),
                studyRun = run,
            )
            File(directory, InferenceRecordFormat.TAKE_MANIFEST_FILE)
                .writeText(rawFrameJson.encodeToString(takeManifest))
        }
    }

    private fun manager(root: File, takes: File, run: StudyRunIdentity) =
        AndroidParticipantQualificationManager(
            root = root,
            takesRoot = takes,
            studyRun = run,
            evidence = evidence(),
            nowIso = { "2026-08-30T00:00:01Z" },
        )

    private inline fun withFixture(block: (File, File, StudyRunIdentity) -> Unit) {
        val root = Files.createTempDirectory("facelink-qualification-").toFile()
        try {
            val takes = File(root, "takes").apply { mkdirs() }
            block(root, takes, run())
        } finally {
            root.deleteRecursively()
        }
    }

    private fun run() = StudyRunIdentity(
        schemaVersion = StudyRunIdentity.SCHEMA_VERSION,
        runId = "qualification-local-1",
        protocolId = CaptureStudyProtocol.LEARNED_PATCH_SESSION_C_ID,
        storageProfile = CaptureStorageProfile.CANONICAL_RGBA_240X320_V1,
        createdAtIso = "2026-08-30T00:00:00Z",
    )

    private fun evidence() = ParticipantQualificationEvidence(
        reviewPacketSha256 = "1".repeat(64),
        sourceManifestSha256 = "2".repeat(64),
        sourceAuthorizationSha256 = "3".repeat(64),
        installedApkSha256 = "4".repeat(64),
        cheekModelSha256 = "5".repeat(64),
        mouthControlModelSha256 = "6".repeat(64),
        mediaPipeTaskSha256 = "7".repeat(64),
        deviceContractSha256 = "8".repeat(64),
    )

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
