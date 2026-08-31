package io.github.kmpfacelink.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant

/** Android-local, score-blind lifecycle for the one frozen 12-slot qualification run. */
internal class AndroidParticipantQualificationManager(
    private val root: File,
    private val takesRoot: File,
    private val studyRun: StudyRunIdentity,
    private val evidence: ParticipantQualificationEvidence,
    private val nowIso: () -> String = { Instant.now().toString() },
) : ParticipantQualificationController {

    private val stateFile = File(root, PARTICIPANT_QUALIFICATION_STATE_FILE)
    private val _state: MutableStateFlow<ParticipantQualificationRunState>
    override val state: StateFlow<ParticipantQualificationRunState>
        get() = _state.asStateFlow()

    init {
        val restored = if (stateFile.exists()) {
            decodeState(stateFile.readText())
        } else {
            ParticipantQualificationRunState(studyRun = studyRun, evidence = evidence)
        }
        require(restored.studyRun.isExactMatch(studyRun)) { "qualification run identity mismatch" }
        require(restored.evidence == evidence) { "qualification evidence mismatch" }
        val recovered = terminateInterruptedParticipantQualification(restored)
        _state = MutableStateFlow(recovered)
        if (!stateFile.exists() || recovered != restored) writeState(recovered)
    }

    @Synchronized
    fun beginAttempt(slotKey: String) {
        update(beginParticipantQualificationAttempt(_state.value, slotKey))
    }

    @Synchronized
    fun completeAttempt(slotKey: String) {
        update(completeParticipantQualificationAttempt(_state.value, slotKey))
    }

    @Synchronized
    fun failAttempt(slotKey: String, code: String): ParticipantQualificationFailureDecision {
        val (next, decision) = failParticipantQualificationAttempt(_state.value, slotKey, code)
        update(next)
        return decision
    }

    @Synchronized
    fun requireOpen() {
        check(_state.value.phase == ParticipantQualificationPhase.OPEN) {
            "qualification run is not open"
        }
    }

    @Synchronized
    fun recordReferencePreflight(takeId: String) {
        val current = _state.value
        check(current.phase == ParticipantQualificationPhase.OPEN) { "qualification run is not open" }
        check(current.activeSlotKey == null) { "qualification attempt is still active" }
        val firstSlot = studyTakeSlots(
            CaptureStudyProtocol.learnedPatchSessionC12().scenarios,
        ).first()
        val record = requireNotNull(
            acceptedStudyTake(readAndroidStudyTakeRecords(takesRoot, studyRun), firstSlot),
        ) { "qualification reference take is not published" }
        require(record.takeId == takeId) { "qualification preflight used another take" }
        val manifest = File(File(takesRoot, takeId), InferenceRecordFormat.TAKE_MANIFEST_FILE)
        val receipt = ParticipantQualificationReferencePreflight(
            takeId = takeId,
            takeManifestSha256 = sha256(manifest.readBytes()),
        )
        val previous = current.referencePreflight
        require(previous == null || previous == receipt) {
            "qualification reference preflight cannot be replaced"
        }
        if (previous == null) update(current.copy(referencePreflight = receipt))
    }

    @Synchronized
    fun terminate(code: String) {
        val current = _state.value
        if (current.phase != ParticipantQualificationPhase.OPEN) return
        update(
            current.copy(
                phase = ParticipantQualificationPhase.TERMINATED,
                activeSlotKey = null,
                activeCaptureAttempt = null,
                terminalCode = code,
            ),
        )
    }

    override suspend fun seal(): ParticipantQualificationSeal {
        val current = _state.value
        check(current.phase == ParticipantQualificationPhase.OPEN) { "qualification run is not open" }
        check(current.activeSlotKey == null) { "qualification attempt is still active" }
        val seal = try {
            buildSeal()
        } catch (failure: Throwable) {
            terminate(CaptureFailureCategory.INTEGRITY.code)
            throw failure
        }
        update(
            current.copy(
                phase = ParticipantQualificationPhase.SEALED,
                seal = seal,
            ),
        )
        return seal
    }

    private fun buildSeal(): ParticipantQualificationSeal {
        val records = readAndroidStudyTakeRecords(takesRoot, studyRun)
        val slots = studyTakeSlots(CaptureStudyProtocol.learnedPatchSessionC12().scenarios)
        require(records.size == QUALIFICATION_SLOT_COUNT) { "qualification run is incomplete" }
        val sealedSlots = slots.map { slot ->
            val record = requireNotNull(acceptedStudyTake(records, slot)) {
                "missing qualification slot ${slot.key}"
            }
            sealSlot(slot, record)
        }
        val referencePreflight = requireNotNull(_state.value.referencePreflight) {
            "qualification reference preflight is missing"
        }
        require(referencePreflight.takeId == sealedSlots.first().takeId) {
            "qualification reference preflight is not the first neutral take"
        }
        require(referencePreflight.takeManifestSha256 == sealedSlots.first().takeManifestSha256) {
            "qualification reference take changed after preflight"
        }
        val manifestBytes = sealedSlots.joinToString("\n") { slot ->
            listOf(
                slot.slotKey,
                slot.takeManifestSha256,
                slot.frameManifestSha256,
                slot.streamSetSha256,
            ).joinToString(":")
        }.encodeToByteArray()
        val conditionBytes = slots.joinToString("\n") { slot ->
            val record = requireNotNull(acceptedStudyTake(records, slot))
            val manifestFile = File(File(takesRoot, record.takeId), InferenceRecordFormat.TAKE_MANIFEST_FILE)
            rawFrameJson.encodeToString(
                rawFrameJson.decodeFromString<TakeManifest>(manifestFile.readText()).condition,
            )
        }.encodeToByteArray()
        return ParticipantQualificationSeal(
            schemaVersion = ParticipantQualificationSeal.SCHEMA_VERSION,
            runId = studyRun.runId,
            protocolId = studyRun.protocolId,
            storageProfile = studyRun.storageProfile,
            sealedAtIso = nowIso(),
            evidence = evidence,
            captureConditionsSha256 = sha256(conditionBytes),
            orderedInputSha256 = sha256(manifestBytes),
            referencePreflight = referencePreflight,
            slots = sealedSlots,
        )
    }

    private fun sealSlot(
        slot: StudyTakeSlot,
        record: StudyTakeRecord,
    ): ParticipantQualificationSealedSlot {
        val directory = File(takesRoot, record.takeId)
        require(directory.isDirectory) { "published qualification take is missing" }
        val takeManifestFile = File(directory, InferenceRecordFormat.TAKE_MANIFEST_FILE)
        val frameManifestFile = File(
            File(directory, AndroidCaptureSession.FRAMES_DIR),
            RawFrameFormat.FRAMES_META_FILE,
        )
        val takeBytes = takeManifestFile.readBytes()
        val frameBytes = frameManifestFile.readBytes()
        val takeManifest = rawFrameJson.decodeFromString<TakeManifest>(takeBytes.decodeToString())
        val frameManifest = rawFrameJson.decodeFromString<RawFrameManifest>(frameBytes.decodeToString())
        require(takeManifest.studyRun.isExactMatch(studyRun)) { "sealed take belongs to another run" }
        require(takeManifest.scenarioId == slot.scenario.id && takeManifest.attempt == slot.attempt) {
            "sealed take does not match its registered slot"
        }
        require(takeManifest.storageReceipt.profile == CaptureStorageProfile.CANONICAL_RGBA_240X320_V1) {
            "qualification take is not canonical RGBA"
        }
        require(takeManifest.condition.modelSha256 == evidence.mediaPipeTaskSha256) {
            "qualification take used another MediaPipe task"
        }
        require(
            takeManifest.analysisFramesAdmitted > 0 &&
                takeManifest.inferenceResults > 0 &&
                takeManifest.framesStored > 0,
        ) { "qualification take has no scoreable records" }
        require(takeManifest.framesStored == frameManifest.frameCount) { "frame count mismatch" }
        require(
            frameManifest.schemaVersion == RawFrameFormat.SCHEMA_VERSION &&
                frameManifest.pixelFormat == RawFrameFormat.PIXEL_FORMAT &&
                frameManifest.bytesPerPixel == RawFrameFormat.BYTES_PER_PIXEL &&
                frameManifest.width == CaptureStorageReceipt.CANONICAL_WIDTH &&
                frameManifest.height == CaptureStorageReceipt.CANONICAL_HEIGHT &&
                frameManifest.bytesPerFrame == CaptureStorageReceipt.CANONICAL_BYTES &&
                frameManifest.indexRecordBytes == RawFrameFormat.INDEX_RECORD_BYTES &&
                !frameManifest.compressed &&
                frameManifest.phaseCodes == capturePhaseCodes(),
        ) { "qualification frame manifest is not canonical" }
        val streamHashes = listOf(
            takeManifest.resultIndexSha256,
            takeManifest.landmarksSha256,
            takeManifest.blendshapesSha256,
            takeManifest.transformSha256,
            frameManifest.payloadSha256,
            frameManifest.indexSha256,
        )
        require(streamHashes.all(String::isLowercaseSha256)) { "qualification stream hash is invalid" }
        val actualStreamFiles = listOf(
            File(directory, InferenceRecordFormat.INDEX_FILE),
            File(directory, InferenceRecordFormat.LANDMARKS_FILE),
            File(directory, InferenceRecordFormat.BLENDSHAPES_FILE),
            File(directory, InferenceRecordFormat.TRANSFORM_FILE),
            File(File(directory, AndroidCaptureSession.FRAMES_DIR), RawFrameFormat.FRAMES_FILE),
            File(File(directory, AndroidCaptureSession.FRAMES_DIR), RawFrameFormat.INDEX_FILE),
        )
        require(
            actualStreamFiles[4].length() ==
                frameManifest.frameCount.toLong() * frameManifest.bytesPerFrame &&
                actualStreamFiles[5].length() ==
                frameManifest.frameCount.toLong() * frameManifest.indexRecordBytes,
        ) { "qualification frame stream length mismatch" }
        val actualStreamHashes = actualStreamFiles.map { stream ->
            require(stream.isFile) { "qualification stream is missing" }
            sha256(stream.readBytes())
        }
        require(actualStreamHashes == streamHashes) { "qualification stream hash mismatch" }
        return ParticipantQualificationSealedSlot(
            slotKey = slot.key,
            takeId = record.takeId,
            takeManifestSha256 = sha256(takeBytes),
            frameManifestSha256 = sha256(frameBytes),
            streamSetSha256 = sha256(actualStreamHashes.joinToString("\n").encodeToByteArray()),
        )
    }

    private fun update(next: ParticipantQualificationRunState) {
        writeState(next)
        _state.value = next
    }

    private fun writeState(state: ParticipantQualificationRunState) {
        check(root.isDirectory || root.mkdirs()) { "qualification root is unavailable" }
        val staging = File(root, "$PARTICIPANT_QUALIFICATION_STATE_FILE.partial")
        FileOutputStream(staging).use { stream ->
            stream.write(rawFrameJson.encodeToString(state).encodeToByteArray())
            stream.fd.sync()
        }
        Files.move(
            staging.toPath(),
            stateFile.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    private fun decodeState(text: String): ParticipantQualificationRunState =
        rawFrameJson.decodeFromString(text)

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte.toInt() and BYTE_MASK) }

    private companion object {
        const val BYTE_MASK: Int = 0xff
    }
}
