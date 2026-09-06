@file:Suppress(
    "ArgumentListWrapping",
    "ForEachOnRange",
    "Indentation",
    "LargeClass",
    "LongMethod",
    "LongParameterList",
    "MagicNumber",
    "MaxLineLength",
    "MaximumLineLength",
    "MultiLineIfElse",
    "StringLiteralDuplication",
    "TooManyFunctions",
    "Wrapping",
)

package io.github.kmpfacelink.capture

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.FaceLandmark
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AndroidH2FinalizedRunVerifierTest {
    @Test
    fun actualControllerStoreWriterProducesExactTwentyTwoBlocksRetryAndSeal() {
        val root = Files.createTempDirectory("h2-controller-full-run").toFile().canonicalFile
        try {
            val blockRoot = root.resolve("attempts").apply { require(mkdir()) }.canonicalFile
            val journalRoot = root.resolve("journal").apply { require(mkdir()) }.canonicalFile
            val identity = "c".repeat(64)
            val fault = ArmedEventWriteFault()
            val journal = AndroidH2ControllerRunJournal(journalRoot)
            val controller = fullRunController(blockRoot, identity, fault, journal)
            driveReadiness(controller, fullRunClock(0, 1))
            H2ConfirmationContract.blocks.drop(1).forEachIndexed { index, block ->
                val blockIndex = index + 1
                if (block.id == "B03_JAW_OPEN") {
                    val failedClock = fullRunClock(blockIndex, 1)
                    controller.beginTake(block.id, failedClock)
                    presentCue(controller, H2CueContract.segmentsFor(block.id).first(), failedClock)
                    fault.armed = true
                    assertFailsWith<H2ControllerStorageFailure> {
                        controller.onJoinedResult(fullRunResult(0, 0, failedClock))
                    }
                    assertEquals(H2FailureCode.UNPUBLISHED_EVENT_LOG_WRITE_FAILURE, controller.state.failures.single().code)
                    assertEquals(H2FailureDecision.RETRY_ONCE, controller.state.let {
                        if (it.phase == H2RunPhase.OPEN && it.replacementUsedBlockId == block.id) {
                            H2FailureDecision.RETRY_ONCE
                        } else {
                            H2FailureDecision.TERMINATED
                        }
                    })
                }
                driveTake(controller, block, fullRunClock(blockIndex, controller.state.let {
                    if (it.replacementUsedBlockId == block.id) 2 else 1
                }))
            }

            val state = controller.state
            assertEquals(H2ConfirmationContract.EXPECTED_BLOCK_COUNT, state.receipts.size)
            assertEquals(1, state.failures.size)
            assertEquals(H2ConfirmationContract.EXPECTED_BLOCK_COUNT + 1, blockRoot.listFiles().orEmpty().size)
            assertEquals(2, journalRoot.listFiles().orEmpty().size)
            assertEquals(setOf("attempts", "journal"), root.listFiles().orEmpty().map(File::getName).toSet())
            val seal = AndroidH2FinalizedRunVerifier(blockRoot).verifyAndBuildCaptureSeal(
                state,
                inputs(identity),
                "2026-09-03T00:00:00Z",
            )
            assertEquals(H2PublicSafeSeal.SCORE_FREE_RETAINED_EVIDENCE_DISPOSITION, seal.disposition)
            assertEquals(false, seal.scoreOpened)
            assertEquals(state.failures, seal.failureLedger)
            assertEquals(H2RunPhase.SEALED, controller.publishTerminalSeal(seal).phase)
            assertEquals(H2ConfirmationContract.EXPECTED_BLOCK_COUNT + 1, blockRoot.listFiles().orEmpty().size)
            assertEquals(4, journalRoot.listFiles().orEmpty().size)
            assertEquals(
                setOf(
                    "h2-exclusive-run.guard",
                    "h2-run-state-v1.json",
                    "h2-terminal-seal-v1.json",
                    "h2-terminal-seal-v1.sha256",
                ),
                journalRoot.listFiles().orEmpty().map(File::getName).toSet(),
            )
            val readback = journal.readVerifiedSealedRun(identity)
            assertEquals(H2RunPhase.SEALED, readback.state.phase)
            assertEquals(identity, readback.state.runIdentitySha256)
            assertEquals(state.receipts, readback.state.receipts)
            assertEquals(state.failures, readback.state.failures)
            assertEquals(seal.runIdentitySha256, readback.seal.runIdentitySha256)
            assertEquals(seal.orderedBlockReceiptsSha256, readback.seal.orderedBlockReceiptsSha256)
            assertEquals(seal.controllerEventStreamsSha256, readback.seal.controllerEventStreamsSha256)
            assertEquals(seal.clockMappingReceiptSha256, readback.seal.clockMappingReceiptSha256)
            assertEquals(seal.retainedStreamManifestsSha256, readback.seal.retainedStreamManifestsSha256)
            assertEquals(
                journalRoot.resolve("h2-terminal-seal-v1.sha256").readText().trim(),
                readback.terminalSealSha256,
            )
            assertEquals(
                H2RunPhase.SEALED,
                AndroidH2ControllerRunJournal(journalRoot).openOrRecover(identity).phase,
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun exactTwentyTwoBlockBundleBuildsOnlyScoreFreeCaptureSeal() = withBundle { bundle ->
        val seal = bundle.verify()
        assertEquals(H2PublicSafeSeal.SCORE_FREE_RETAINED_EVIDENCE_DISPOSITION, seal.disposition)
        assertEquals(false, seal.scoreOpened)
        assertEquals(22, seal.blockCount)
        assertEquals(bundle.state.failures, seal.failureLedger)
    }

    @Test
    fun primaryOnlyRequiresOneComposeDrawProxyAcknowledgementPerObservableFrame() =
        withBundle(visibility = H2SurfaceVisibilityMode.PRIMARY_ONLY) { bundle ->
            bundle.verify()
            bundle.mutateEvents("B03_JAW_OPEN.1") { bytes ->
                bytes.decodeToString().lineSequence()
                    .filterNot { "\"event_kind\":\"COMPOSE_DRAW_ACKNOWLEDGED\"" in it }
                    .joinToString("\n", postfix = "\n").encodeToByteArray()
            }
            assertFailsWith<IllegalArgumentException> { bundle.verify() }
        }

    @Test
    fun eventSequenceSparseObservationAndStreamFrameMutationsFailAfterManifestRebinding() {
        withBundle { bundle ->
            bundle.mutateEvents("B01_STARTUP_NEUTRAL_READINESS.1") { bytes ->
                bytes.decodeToString().replaceFirst("\"sequence\":1", "\"sequence\":9").encodeToByteArray()
            }
            assertFailsWith<IllegalArgumentException> { bundle.verify() }
        }
        withBundle { bundle ->
            bundle.mutateEvents("B01_STARTUP_NEUTRAL_READINESS.1") { bytes ->
                bytes.decodeToString().lineSequence().filterNot { "\"monotonic_nanos\":1250010000" in it }
                    .joinToString("\n", postfix = "\n")
                    .encodeToByteArray()
            }
            assertFailsWith<IllegalArgumentException> { bundle.verify() }
        }
        withBundle { bundle ->
            val file = bundle.root.resolve("B03_JAW_OPEN.1/avatar-a.rgba")
            file.writeBytes(file.readBytes().copyOfRange(20, file.length().toInt()))
            bundle.rebindManifest("B03_JAW_OPEN.1")
            assertFailsWith<IllegalArgumentException> { bundle.verify() }
        }
    }

    @Test
    fun unrelatedAuthorityBytesAndWrongBuildConfigIdentityFailClosed() {
        withBundle { bundle ->
            val wrong = bundle.inputs.copy(devicePreflight = "{}".encodeToByteArray())
            assertFailsWith<Exception> { bundle.verify(wrong) }
            val wrongConfig = bundle.inputs.copy(
                buildConfig = bundle.inputs.buildConfig.decodeToString()
                    .replace("H2_PRIMARY_MODEL_BUNDLED = true", "H2_PRIMARY_MODEL_BUNDLED = false")
                    .encodeToByteArray(),
            )
            assertFailsWith<IllegalArgumentException> { bundle.verify(wrongConfig) }
        }
    }

    @Test
    fun oneTerminatedEligibleAttemptIsRequiredAndCorrelatedBeforeAttemptTwo() = withBundle(retryBlock = "B03_JAW_OPEN") {
        bundle ->
        val seal = bundle.verify()
        assertEquals(1, seal.failureLedger.size)
        assertEquals(2, bundle.state.receipts.single { it.blockId == "B03_JAW_OPEN" }.captureAttempt)
        bundle.root.resolve(".B03_JAW_OPEN.1.terminated").deleteRecursively()
        assertFailsWith<IllegalArgumentException> { bundle.verify() }
    }

    @Test
    fun eventLogWriteFailureMayLackTerminalEventButCodesMustStillMatch() {
        withBundle(retryBlock = "B03_JAW_OPEN") { bundle ->
            val clock = clock(2)
            val identity = "a".repeat(64)
            bundle.mutateTerminatedEvents("B03_JAW_OPEN") {
                buildString {
                    append(event(0, "B03_JAW_OPEN", "CLOCK_MAPPING", clock.cameraOriginNanos, identity,
                        clock = clock))
                    append(event(1, "B03_JAW_OPEN", "FRAME_JOINED", clock.cameraOriginNanos + 100 * MILLION,
                        identity, elapsed = 100, frame = 7))
                    append(event(2, "B03_JAW_OPEN", "CANDIDATE_MISSING_OR_NOT_READY",
                        clock.cameraOriginNanos + 100 * MILLION, identity, elapsed = 100, frame = 7))
                    append(event(3, "B03_JAW_OPEN", "TERMINAL_FAILURE",
                        clock.cameraOriginNanos + 100 * MILLION, identity, elapsed = 100, frame = 7,
                        failure = H2FailureCode.UNPUBLISHED_EVENT_LOG_WRITE_FAILURE.name))
                }.encodeToByteArray()
            }
            bundle.verify()
        }
        withBundle(retryBlock = "B03_JAW_OPEN", omitRetryTerminalEvent = true) { bundle ->
            bundle.verify()
        }
        withBundle(retryBlock = "B03_JAW_OPEN", omitRetryTerminalEvent = true) { bundle ->
            bundle.root.resolve(".B03_JAW_OPEN.1.terminated/failure-code.txt").writeText("CAMERA_LOSS\n")
            assertFailsWith<IllegalArgumentException> { bundle.verify() }
        }
        withBundle(retryBlock = "B03_JAW_OPEN") { bundle ->
            bundle.mutateTerminatedEvents("B03_JAW_OPEN") { bytes ->
                bytes.decodeToString().replace("UNPUBLISHED_EVENT_LOG_WRITE_FAILURE", "CAMERA_LOSS")
                    .encodeToByteArray()
            }
            assertFailsWith<IllegalArgumentException> { bundle.verify() }
        }
        withBundle(retryBlock = "B03_JAW_OPEN") { bundle ->
            bundle.mutateTerminatedEvents("B03_JAW_OPEN") { bytes ->
                bytes.decodeToString().replaceFirst(
                    "\"failure_code\":\"UNPUBLISHED_EVENT_LOG_WRITE_FAILURE\"",
                    "\"frame_index\":7,\"failure_code\":\"UNPUBLISHED_EVENT_LOG_WRITE_FAILURE\"",
                ).encodeToByteArray()
            }
            assertFailsWith<IllegalArgumentException> { bundle.verify() }
        }
    }

    @Test
    fun allFiveRetryCodesUseActualWriterArtifactsAndOnlyTheirValidTerminalSchemaSeals() {
        val terminalMayBeAbsent = setOf(
            H2FailureCode.UNPUBLISHED_EVENT_LOG_WRITE_FAILURE,
            H2FailureCode.UNPUBLISHED_HASH_OR_LENGTH_MISMATCH,
            H2FailureCode.UNPUBLISHED_ATOMIC_PUBLICATION_PRECONDITION_FAILURE,
        )
        H2FailureCode.entries.filter(H2FailureCode::retryEligibleBeforePublication).forEach { code ->
            withBundle(
                retryBlock = "B03_JAW_OPEN",
                omitRetryTerminalEvent = code in terminalMayBeAbsent,
                retryCode = code,
            ) { bundle ->
                val seal = bundle.verify()
                assertEquals(code, seal.failureLedger.single().code)
                val attempt = bundle.root.resolve(".B03_JAW_OPEN.1.terminated")
                assertEquals(code.name, attempt.resolve("failure-code.txt").readText().trim())
                assertEquals(false, attempt.resolve(".B03_JAW_OPEN.1.partial").exists())
            }
            withBundle(
                retryBlock = "B03_JAW_OPEN",
                omitRetryTerminalEvent = code in terminalMayBeAbsent,
                retryCode = code,
            ) { bundle ->
                bundle.root.resolve(".B03_JAW_OPEN.1.terminated/failure-code.txt")
                    .writeText("CAMERA_LOSS\n")
                assertFailsWith<IllegalArgumentException> { bundle.verify() }
            }
            withBundle(
                retryBlock = "B03_JAW_OPEN",
                omitRetryTerminalEvent = code in terminalMayBeAbsent,
                retryCode = code,
            ) { bundle ->
                bundle.root.resolve(".B03_JAW_OPEN.1.partial").mkdir()
                assertFailsWith<IllegalArgumentException> { bundle.verify() }
            }
        }
        listOf(
            H2FailureCode.UNPUBLISHED_CAMERA_STREAM_WRITE_FAILURE,
            H2FailureCode.UNPUBLISHED_OUTPUT_STREAM_WRITE_FAILURE,
        ).forEach { code ->
            withBundle(
                retryBlock = "B03_JAW_OPEN",
                omitRetryTerminalEvent = true,
                retryCode = code,
            ) { bundle ->
                assertFailsWith<IllegalArgumentException> { bundle.verify() }
            }
        }
    }

    @Test
    fun finalizedInventoryCueClockAndEndpointMutationsFailClosed() {
        withBundle { bundle ->
            bundle.root.resolve("EXTRA.1").mkdir()
            assertFailsWith<IllegalArgumentException> { bundle.verify() }
        }
        withBundle { bundle ->
            bundle.root.resolve("B22_CLOSING_NEUTRAL_READY.1").deleteRecursively()
            assertFailsWith<IllegalArgumentException> { bundle.verify() }
        }
        withBundle { bundle ->
            bundle.mutateEvents("B03_JAW_OPEN.1") { bytes ->
                bytes.decodeToString().replaceFirst("\"cue_segment\":\"ACTION_HOLD_1\"",
                    "\"cue_segment\":\"RELEASE_1\"").encodeToByteArray()
            }
            assertFailsWith<IllegalArgumentException> { bundle.verify() }
        }
        withBundle { bundle ->
            bundle.mutateEvents("B03_JAW_OPEN.1") { bytes ->
                bytes.decodeToString().replaceFirst("\"visible_output_origin_nanos\":",
                    "\"visible_output_origin_nanos\":9").encodeToByteArray()
            }
            assertFailsWith<Exception> { bundle.verify() }
        }
        withBundle { bundle ->
            bundle.mutateEvents("B03_JAW_OPEN.1") { bytes ->
                bytes.decodeToString().replace("\"take_elapsed_millis\":13000",
                    "\"take_elapsed_millis\":12999").encodeToByteArray()
            }
            assertFailsWith<IllegalArgumentException> { bundle.verify() }
        }
    }

    @Test
    fun calibrationRoleVisibilityReceiptAndManifestMutationsFailClosed() {
        withBundle { bundle ->
            bundle.mutateEvents("B03_JAW_OPEN.1") { bytes ->
                bytes.decodeToString().lineSequence().filterNot {
                    "\"take_elapsed_millis\":1250" in it &&
                        ("\"event_kind\":\"FRAME_JOINED\"" in it || "\"event_kind\":\"CALIBRATION_ONLY\"" in it)
                }.joinToString("\n", postfix = "\n").encodeToByteArray()
            }
            assertFailsWith<IllegalArgumentException> { bundle.verify() }
        }
        withBundle { bundle ->
            bundle.mutateEvents("B03_JAW_OPEN.1") { bytes ->
                bytes.decodeToString().replaceFirst(
                    "\"candidate_role\":\"PRIMARY\",\"surface\":\"H2_PRIMARY_C3B_AVATAR_RENDER\"",
                    "\"candidate_role\":\"PRIMARY\",\"surface\":\"H2_SECONDARY_C2_PLUS_C1_AVATAR_RENDER\"",
                ).encodeToByteArray()
            }
            assertFailsWith<IllegalArgumentException> { bundle.verify() }
        }
        withBundle { bundle ->
            bundle.mutateEvents("B03_JAW_OPEN.1") { bytes ->
                bytes.decodeToString().replaceFirst("\"participant_visibility\":\"NEITHER\"",
                    "\"participant_visibility\":\"UNDECIDED\"").encodeToByteArray()
            }
            assertFailsWith<IllegalArgumentException> { bundle.verify() }
        }
        withBundle { bundle ->
            bundle.state = bundle.state.copy(receipts = bundle.state.receipts.map {
                if (it.blockId == "B03_JAW_OPEN") it.copy(primaryVisibleEventCount = it.primaryVisibleEventCount + 1) else it
            })
            assertFailsWith<IllegalArgumentException> { bundle.verify() }
        }
        withBundle { bundle ->
            bundle.state = bundle.state.copy(receipts = bundle.state.receipts.map {
                if (it.blockId == "B03_JAW_OPEN") it.copy(retainedStreamManifestSha256 = "f".repeat(64)) else it
            })
            assertFailsWith<IllegalArgumentException> { bundle.verify() }
        }
    }

    @Test
    fun unknownKeysUnknownKindsAndOrphanFrameEventsFailClosed() {
        withBundle { bundle ->
            bundle.mutateEvents("B03_JAW_OPEN.1") { bytes ->
                bytes.decodeToString().replaceFirst("\"event_kind\":\"FRAME_JOINED\"",
                    "\"event_kind\":\"FAKE\"").encodeToByteArray()
            }
            assertFailsWith<IllegalArgumentException> { bundle.verify() }
        }
        withBundle { bundle ->
            bundle.mutateEvents("B03_JAW_OPEN.1") { bytes ->
                bytes.decodeToString().replaceFirst("}\n", ",\"score\":0.9}\n").encodeToByteArray()
            }
            assertFailsWith<IllegalArgumentException> { bundle.verify() }
        }
        withBundle { bundle ->
            bundle.mutateEvents("B03_JAW_OPEN.1") { bytes ->
                bytes.decodeToString().replaceFirst(
                    Regex("(\\\"event_kind\\\":\\\"CALIBRATION_ONLY\\\"[^}]*\\\"frame_index\\\":)0"),
                    "$1" + "999",
                ).encodeToByteArray()
            }
            assertFailsWith<IllegalArgumentException> { bundle.verify() }
        }
    }

    @Test
    fun avatarPixelsAndVisibleStateMustRemainDeterministicallyBound() {
        withBundle { bundle ->
            val file = bundle.root.resolve("B03_JAW_OPEN.1/avatar-a.rgba")
            val bytes = file.readBytes()
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
            file.writeBytes(bytes)
            bundle.rebindManifest("B03_JAW_OPEN.1")
            assertFailsWith<IllegalArgumentException> { bundle.verify() }
        }
        withBundle { bundle ->
            bundle.mutateEvents("B03_JAW_OPEN.1") { bytes ->
                bytes.decodeToString().replaceFirst("\"state\":\"VISIBLE_OFF\"", "\"state\":\"VISIBLE_ON\"")
                    .encodeToByteArray()
            }
            assertFailsWith<IllegalArgumentException> { bundle.verify() }
        }
    }

    private fun withBundle(
        retryBlock: String? = null,
        visibility: H2SurfaceVisibilityMode = H2SurfaceVisibilityMode.NEITHER,
        omitRetryTerminalEvent: Boolean = false,
        retryCode: H2FailureCode = H2FailureCode.UNPUBLISHED_EVENT_LOG_WRITE_FAILURE,
        block: (SyntheticBundle) -> Unit,
    ) {
        val root = Files.createTempDirectory("h2-finalized-verifier").toFile()
        try {
            block(buildBundle(root, retryBlock, visibility, omitRetryTerminalEvent, retryCode))
        } finally {
            root.deleteRecursively()
        }
    }

    private fun buildBundle(
        root: File,
        retryBlock: String?,
        visibility: H2SurfaceVisibilityMode,
        omitRetryTerminalEvent: Boolean,
        retryCode: H2FailureCode,
    ): SyntheticBundle {
        val identity = "a".repeat(64)
        var state = openPreparedH2Run(H2RunState(identity), true, true)
        H2ConfirmationContract.blocks.forEachIndexed { blockIndex, contract ->
            state = beginH2Block(state, contract.id, providerPolicyRouteRenderAvailable = true)
            if (contract.id == retryBlock) {
                writeTerminated(root, contract, identity, blockIndex, omitRetryTerminalEvent, retryCode)
                state = failH2Block(
                    state,
                    contract.id,
                    retryCode,
                ).first
                state = beginH2Block(state, contract.id, true)
            }
            val evidence = writePublished(
                root, contract, checkNotNull(state.activeAttempt), identity, blockIndex, visibility,
            )
            state = completeH2Block(
                state,
                H2BlockReceipt(
                    contract.id,
                    checkNotNull(state.activeAttempt),
                    contract.minimumObservableSeconds,
                    evidence.visibleCount,
                    evidence.visibleCount,
                    evidence.manifestSha256,
                    true,
                ),
            )
        }
        val inputs = inputs(identity)
        return SyntheticBundle(root, state, inputs)
    }

    private fun writePublished(
        root: File,
        block: H2ConfirmationBlock,
        attempt: Int,
        identity: String,
        blockIndex: Int,
        visibility: H2SurfaceVisibilityMode,
    ): PublishedEvidence {
        val eventBytes: ByteArray
        val headers: List<Header>
        val visibleCount: Int
        if (block.kind == H2BlockKind.STARTUP_READINESS) {
            eventBytes = readinessEvents(block, identity, blockIndex, visibility)
            headers = emptyList()
            visibleCount = 0
        } else {
            val generated = takeEvents(block, identity, blockIndex, visibility)
            eventBytes = generated.first
            headers = generated.second
            visibleCount = headers.count { it.elapsed > 3_000 }
        }
        val streams = linkedMapOf(
            "camera.rgba" to framed(headers) { byteArrayOf((it.frame and 0xff).toByte()) },
            "participant-action.rgba" to framed(headers) { byteArrayOf((it.frame and 0xff).toByte()) },
            "avatar-a.rgba" to framed(headers) {
                avatarPayload(it, H2CandidateRole.PRIMARY, block.id, clock(blockIndex))
            },
            "avatar-b.rgba" to framed(headers) {
                avatarPayload(it, H2CandidateRole.COMPARATOR, block.id, clock(blockIndex))
            },
            "controller-events.jsonl" to eventBytes,
        )
        val manager = AndroidH2StreamingCaptureManager(root.canonicalFile, 50_000_000)
        val writer = manager.beginBlock(block.id, attempt)
        streams.forEach { (name, bytes) -> writer.open(name).use { it.write(bytes) } }
        val published = manager.publishBlock(writer)
        return PublishedEvidence(published.manifestSha256, visibleCount)
    }

    private fun fullRunController(
        root: File,
        identity: String,
        fault: ArmedEventWriteFault,
        journal: H2ControllerRunJournal,
    ): H2SessionController {
        val expected = "0".repeat(64)
        val provider = H2StrictC3bModelProvider(
            expected,
            H2ModelByteLoader { fullRunModel().encodeToByteArray() },
            H2Sha256 { expected },
        )
        return H2SessionController(
            identity,
            H2V2TakeRuntime(H2C3bCandidate(provider), H2C2C1Candidate()),
            AndroidH2ControllerStore(
                AndroidH2StreamingCaptureManager(
                    root,
                    maxBlockBytes = 400_000_000L,
                    faultInjection = fault,
                ),
            ),
            H2SurfaceVisibilityMode.NEITHER,
            journal = journal,
        )
    }

    private fun driveReadiness(controller: H2SessionController, clock: H2ClockMapping) {
        controller.beginReadiness(clock, available(), available())
        presentCue(controller, H2CueContract.segmentsFor("B01_STARTUP_NEUTRAL_READINESS").single(), clock)
        for (elapsed in 250L..15_000L step 250L) {
            controller.observeReadiness(
                clock.cueOriginNanos + elapsed * MILLION,
                available(),
                available(),
            )
        }
    }

    private fun driveTake(
        controller: H2SessionController,
        block: H2ConfirmationBlock,
        clock: H2ClockMapping,
    ) {
        controller.beginTake(block.id, clock)
        val calibration = ((0L..200L step 200L) + (250L..1_250L step 50L) +
            (1_500L..3_000L step 100L)).distinct()
        val end = block.totalDurationSeconds * 1_000L
        val observable = mutableListOf(3_001L).also { values ->
            while (values.last() + 250 < end) values += values.last() + 250
            if (values.last() != end) values += end
        }
        val frameTimes = calibration + observable
        val cues = H2CueContract.segmentsFor(block.id).associateBy(H2CueSegment::startMillis)
        val timeline = (frameTimes + cues.keys).distinct().sorted()
        var frameIndex = 0L
        timeline.forEach { elapsed ->
            cues[elapsed]?.let { presentCue(controller, it, clock) }
            if (elapsed in frameTimes) {
                controller.onJoinedResult(fullRunResult(frameIndex++, elapsed, clock))
            }
        }
        controller.completeTake()
    }

    private fun presentCue(
        controller: H2SessionController,
        cue: H2CueSegment,
        clock: H2ClockMapping,
    ) {
        val monotonic = clock.cueOriginNanos + cue.startMillis * MILLION
        controller.routeCue(cue.name, cue.startMillis, monotonic)
        controller.acknowledgeCuePresentation(cue.name, monotonic)
    }

    private fun fullRunResult(
        frameIndex: Long,
        elapsed: Long,
        clock: H2ClockMapping,
    ): H2JoinedCanonicalResult = H2JoinedCanonicalResult(
        frameIndex,
        elapsed,
        clock.cameraOriginNanos + elapsed * MILLION,
        FULL_RUN_IMAGE,
        FULL_RUN_NEUTRAL,
    )

    private fun fullRunClock(blockIndex: Int, attempt: Int): H2ClockMapping {
        val origin = 10_000_000_000L + blockIndex * 1_000_000_000_000L + attempt * 100_000_000_000L
        return H2ClockMapping(H2ClockMapping.MONOTONIC_CLOCK_ID, origin, origin, origin, origin, 0)
    }

    private fun available(): H2ProviderRouteAvailability = H2ProviderRouteAvailability.AVAILABLE

    private class ArmedEventWriteFault : H2StreamingFaultInjection {
        var armed: Boolean = false

        override fun before(point: H2StreamingFaultPoint, logicalName: String?) {
            if (armed && point == H2StreamingFaultPoint.STREAM_WRITE && logicalName == "controller-events.jsonl") {
                armed = false
                throw IOException("synthetic public H2 full-run retry")
            }
        }
    }

    private fun writeTerminated(
        root: File,
        block: H2ConfirmationBlock,
        identity: String,
        blockIndex: Int,
        omitTerminalEvent: Boolean,
        failureCode: H2FailureCode,
    ) {
        val store = AndroidH2ControllerStore(AndroidH2StreamingCaptureManager(root.canonicalFile, 1_000_000))
        store.beginBlock(block.id, 1)
        val clock = clock(blockIndex)
        store.appendEvent(controllerEvent(0, block.id, identity, clock))
        if (!omitTerminalEvent) store.appendEvent(terminalEvent(1, block.id, identity, clock, failureCode))
        store.terminateBlock(failureCode)
    }

    private fun controllerEvent(
        sequence: Long,
        blockId: String,
        identity: String,
        clock: H2ClockMapping,
    ): H2ControllerEvent = H2ControllerEvent(
        sequence,
        blockId,
        H2ControllerEventKind.CLOCK_MAPPING,
        clock.cameraOriginNanos,
        identity,
        clockMapping = clock,
    )

    private fun terminalEvent(
        sequence: Long,
        blockId: String,
        identity: String,
        clock: H2ClockMapping,
        failureCode: H2FailureCode,
    ): H2ControllerEvent = H2ControllerEvent(
        sequence,
        blockId,
        H2ControllerEventKind.TERMINAL_FAILURE,
        clock.cameraOriginNanos,
        identity,
        failureCode = failureCode,
    )

    private fun readinessEvents(
        block: H2ConfirmationBlock,
        identity: String,
        blockIndex: Int,
        visibility: H2SurfaceVisibilityMode,
    ): ByteArray {
        val clock = clock(blockIndex)
        var sequence = 0L
        return buildString {
            append(event(sequence++, block.id, "BLOCK_SCHEDULE_OPENED", clock.cameraOriginNanos, identity,
                visibility = visibility.name))
            append(event(sequence++, block.id, "CLOCK_MAPPING", clock.cameraOriginNanos, identity, clock = clock))
            append(event(sequence++, block.id, "READINESS_OBSERVATION", clock.cueOriginNanos,
                identity, primary = "AVAILABLE", comparator = "AVAILABLE"))
            append(event(sequence++, block.id, "CUE_ROUTE_ACTIVATED", clock.cueOriginNanos, identity,
                elapsed = 0, cue = "READINESS_AVAILABILITY"))
            append(event(sequence++, block.id, "CUE_PRESENTATION_ACKNOWLEDGED", clock.cueOriginNanos + 1, identity,
                elapsed = 0, cue = "READINESS_AVAILABILITY"))
            (250L..15_000L step 250L).forEach { elapsed ->
                append(event(sequence++, block.id, "READINESS_OBSERVATION", clock.cueOriginNanos + elapsed * MILLION,
                    identity, primary = "AVAILABLE", comparator = "AVAILABLE"))
            }
        }.encodeToByteArray()
    }

    private fun takeEvents(
        block: H2ConfirmationBlock,
        identity: String,
        blockIndex: Int,
        visibility: H2SurfaceVisibilityMode,
    ): Pair<ByteArray, List<Header>> {
        val clock = clock(blockIndex)
        val end = block.totalDurationSeconds * 1_000L
        val calibration = ((0L..200L step 200L) + (250L..1_250L step 50L) +
            (1_500L..3_000L step 100L)).distinct()
        val observable = mutableListOf(3_001L).also { values ->
            while (values.last() + 250 < end) values += values.last() + 250
            if (values.last() != end) values += end
        }
        val headers = (calibration + observable).mapIndexed { index, elapsed -> Header(index.toLong(), elapsed) }
        val cues = H2CueContract.segmentsFor(block.id).associateBy(H2CueSegment::startMillis)
        val timeline = (headers.map(Header::elapsed) + cues.keys).distinct().sorted()
        var sequence = 0L
        val body = buildString {
            append(event(sequence++, block.id, "BLOCK_SCHEDULE_OPENED", clock.cameraOriginNanos, identity,
                visibility = visibility.name))
            append(event(sequence++, block.id, "CLOCK_MAPPING", clock.cameraOriginNanos, identity, clock = clock))
            timeline.forEach { elapsed ->
                cues[elapsed]?.let { cue ->
                    append(event(sequence++, block.id, "CUE_ROUTE_ACTIVATED", clock.cueOriginNanos + elapsed * MILLION,
                        identity, elapsed, cue = cue.name.name))
                    append(event(sequence++, block.id, "CUE_PRESENTATION_ACKNOWLEDGED",
                        clock.cueOriginNanos + elapsed * MILLION + 1, identity, elapsed, cue = cue.name.name))
                }
                headers.singleOrNull { it.elapsed == elapsed }?.let { header ->
                    append(event(sequence++, block.id, "FRAME_JOINED", clock.cameraOriginNanos + elapsed * MILLION,
                        identity, elapsed, header.frame))
                    if (elapsed > 3_000) {
                        append(event(sequence++, block.id, "ROUTE_AVAILABLE", clock.cameraOriginNanos + elapsed * MILLION,
                            identity, elapsed, header.frame))
                        append(visible(sequence++, block.id, header, clock, identity, "PRIMARY",
                            "H2_PRIMARY_C3B_AVATAR_RENDER"))
                        append(visible(sequence++, block.id, header, clock, identity, "COMPARATOR",
                            "H2_SECONDARY_C2_PLUS_C1_AVATAR_RENDER"))
                        append(event(sequence++, block.id, "PARTICIPANT_ROUTE_SELECTED",
                            clock.cameraOriginNanos + elapsed * MILLION, identity, elapsed, header.frame,
                            visibility = visibility.name))
                        if (visibility == H2SurfaceVisibilityMode.PRIMARY_ONLY) {
                            val drawNanos = clock.visibleOutputOriginNanos + elapsed * MILLION
                            append(event(sequence++, block.id, "COMPOSE_DRAW_ACKNOWLEDGED", drawNanos,
                                identity, elapsed, header.frame, visibility = visibility.name))
                        }
                    } else {
                        append(event(sequence++, block.id, "CALIBRATION_ONLY",
                            clock.cameraOriginNanos + elapsed * MILLION, identity, elapsed, header.frame))
                    }
                }
            }
        }.encodeToByteArray()
        return body to headers
    }

    private fun event(
        sequence: Long,
        block: String,
        kind: String,
        nanos: Long,
        identity: String,
        elapsed: Long? = null,
        frame: Long? = null,
        cue: String? = null,
        primary: String? = null,
        comparator: String? = null,
        failure: String? = null,
        clock: H2ClockMapping? = null,
        visibility: String? = null,
    ): String = buildString {
        append("{\"sequence\":$sequence,\"block_id\":\"$block\",\"event_kind\":\"$kind\",")
        append("\"monotonic_nanos\":$nanos,\"run_identity_sha256\":\"$identity\"")
        elapsed?.let { append(",\"take_elapsed_millis\":$it") }
        frame?.let { append(",\"frame_index\":$it") }
        cue?.let { append(",\"cue_segment\":\"$it\"") }
        primary?.let { append(",\"primary_availability\":\"$it\"") }
        comparator?.let { append(",\"comparator_availability\":\"$it\"") }
        failure?.let { append(",\"failure_code\":\"$it\"") }
        visibility?.let { append(",\"participant_visibility\":\"$it\"") }
        clock?.let {
            append(",\"clock_id\":\"${it.clockId}\",\"camera_origin_nanos\":${it.cameraOriginNanos}")
            append(",\"cue_origin_nanos\":${it.cueOriginNanos},\"action_observer_origin_nanos\":${it.actionObserverOriginNanos}")
            append(",\"visible_output_origin_nanos\":${it.visibleOutputOriginNanos}")
            append(",\"max_mapping_uncertainty_nanos\":${it.maxMappingUncertaintyNanos}")
        }
        append("}\n")
    }

    private fun visible(
        sequence: Long,
        block: String,
        header: Header,
        clock: H2ClockMapping,
        identity: String,
        role: String,
        surface: String,
    ): String = "{\"frame_index\":${header.frame},\"sequence\":$sequence,\"block_id\":\"$block\"," +
        "\"take_elapsed_millis\":${header.elapsed},\"event_kind\":\"VISIBLE_OUTPUT\"," +
        "\"candidate_role\":\"$role\",\"surface\":\"$surface\"," +
        "\"monotonic_nanos\":${clock.visibleOutputOriginNanos + header.elapsed * MILLION}," +
        "\"state\":\"VISIBLE_OFF\",\"run_identity_sha256\":\"$identity\"}\n"

    private fun avatarPayload(
        header: Header,
        role: H2CandidateRole,
        blockId: String,
        clock: H2ClockMapping,
    ): ByteArray? = if (header.elapsed <= 3_000) {
        null
    } else {
        checkNotNull(
            H2BlindAvatarRenderer(role).render(
                H2AvatarRouteState(active = false, available = true),
                clock,
                0,
                blockId,
                clock.visibleOutputOriginNanos + header.elapsed * MILLION,
            ),
        ).rgba
    }

    private fun framed(headers: List<Header>, payload: (Header) -> ByteArray?): ByteArray =
        ByteArrayOutputStream().use { output ->
            headers.forEach { header ->
                output.write(longLe(header.frame))
                output.write(longLe(header.elapsed))
                val retained = payload(header)
                if (retained != null) {
                    output.write(intLe(retained.size)); output.write(retained)
                } else output.write(intLe(-1))
            }
            output.toByteArray()
        }

    private fun inputs(identity: String): H2CaptureSealInputBytes {
        val patch = "v3-overlay\n".encodeToByteArray()
        val base = "1".repeat(40)
        val source = """{"protocol_sha256":"${H2ConfirmationContract.PROTOCOL_PREREG_SHA256}","overlay_sha256":"${sha256(patch)}","source_base":{"commit_sha":"$base"}}""".encodeToByteArray()
        val apk = "reviewed-apk".encodeToByteArray()
        val config = buildConfig(sha256(source), sha256(patch), base)
        val binary = chainJson(source, patch, base, config, apk)
        val device = """{"protocol_sha256":"${H2ConfirmationContract.PROTOCOL_PREREG_SHA256}","binary_receipt_sha256":"${sha256(binary)}","apk_sha256":"${sha256(apk)}","build_config_sha256":"${sha256(config)}"}""".encodeToByteArray()
        val privacy = """{"protocol_sha256":"${H2ConfirmationContract.PROTOCOL_PREREG_SHA256}","binary_receipt_sha256":"${sha256(binary)}","device_preflight_sha256":"${sha256(device)}"}""".encodeToByteArray()
        val capture = """{"protocol_sha256":"${H2ConfirmationContract.PROTOCOL_PREREG_SHA256}","binary_receipt_sha256":"${sha256(binary)}","device_preflight_sha256":"${sha256(device)}","privacy_authority_sha256":"${sha256(privacy)}","run_identity_sha256":"$identity"}""".encodeToByteArray()
        return H2CaptureSealInputBytes(source, patch, apk, config, base, binary, device, privacy, capture)
    }

    private fun buildConfig(source: String, patch: String, base: String): ByteArray = """
        public static final String H2_PROTOCOL_SHA256 = "${H2ConfirmationContract.PROTOCOL_PREREG_SHA256}";
        public static final String H2_SOURCE_MANIFEST_SHA256 = "$source";
        public static final String H2_SOURCE_PATCH_SHA256 = "$patch";
        public static final String H2_BASE_COMMIT_SHA = "$base";
        public static final boolean H2_BUILD_INPUT_MATCHES_MANIFEST = true;
        public static final boolean H2_PHYSICAL_EXECUTION_ENABLED = true;
        public static final boolean H2_PRIMARY_MODEL_BUNDLED = true;
    """.trimIndent().encodeToByteArray()

    private fun chainJson(source: ByteArray, patch: ByteArray, base: String, config: ByteArray, apk: ByteArray): ByteArray =
        """{"protocol_sha256":"${H2ConfirmationContract.PROTOCOL_PREREG_SHA256}","source_manifest_sha256":"${sha256(source)}","source_patch_sha256":"${sha256(patch)}","base_commit_sha":"$base","build_config_sha256":"${sha256(config)}","apk_sha256":"${sha256(apk)}"}""".encodeToByteArray()

    private fun clock(index: Int): H2ClockMapping {
        val origin = 1_000_000_000L + index * 1_000_000_000_000L
        return H2ClockMapping(H2ClockMapping.MONOTONIC_CLOCK_ID, origin, origin + 10_000, origin + 20_000, origin + 30_000, 1_000)
    }

    private data class Header(val frame: Long, val elapsed: Long)
    private data class PublishedEvidence(val manifestSha256: String, val visibleCount: Int)

    private data class SyntheticBundle(
        val root: File,
        var state: H2RunState,
        val inputs: H2CaptureSealInputBytes,
    ) {
        fun verify(customInputs: H2CaptureSealInputBytes = inputs): H2PublicSafeSeal =
            AndroidH2FinalizedRunVerifier(root.canonicalFile)
                .verifyAndBuildCaptureSeal(state, customInputs, "2026-09-03T00:00:00Z")

        fun mutateEvents(directoryName: String, mutate: (ByteArray) -> ByteArray) {
            val file = root.resolve("$directoryName/controller-events.jsonl")
            file.writeBytes(mutate(file.readBytes()))
            rebindManifest(directoryName)
        }

        fun rebindManifest(directoryName: String) {
            val directory = root.resolve(directoryName)
            val streams = linkedMapOf<String, ByteArray>()
            H2StreamingBlockWriter.REQUIRED_STREAMS.forEach { name -> streams[name] = directory.resolve(name).readBytes() }
            val bytes = streams.entries.joinToString("") { "${it.key}\t${it.value.size}\t${sha256(it.value)}\n" }.encodeToByteArray()
            directory.resolve("streams.sha256").writeBytes(bytes)
            val blockId = directoryName.substringBeforeLast('.')
            state = state.copy(receipts = state.receipts.map {
                if (it.blockId == blockId) it.copy(retainedStreamManifestSha256 = sha256(bytes)) else it
            })
        }

        fun mutateTerminatedEvents(blockId: String, mutate: (ByteArray) -> ByteArray) {
            val directory = root.resolve(".$blockId.1.terminated")
            val eventFile = directory.resolve("controller-events.jsonl")
            eventFile.writeBytes(mutate(eventFile.readBytes()))
            val streams = linkedMapOf<String, ByteArray>()
            H2StreamingBlockWriter.REQUIRED_STREAMS.forEach { name ->
                directory.resolve(name).takeIf(File::isFile)?.let { streams[name] = it.readBytes() }
            }
            val bytes = streams.entries.joinToString("") {
                "${it.key}\t${it.value.size}\t${sha256(it.value)}\n"
            }.encodeToByteArray()
            directory.resolve("terminated-streams.sha256").writeBytes(bytes)
        }
    }

    private companion object {
        const val MILLION = 1_000_000L
        val FULL_RUN_IMAGE = fullRunImage()
        val FULL_RUN_NEUTRAL = mapOf(
            BlendShape.MOUTH_ROLL_LOWER to 0f,
            BlendShape.MOUTH_ROLL_UPPER to 0f,
            BlendShape.MOUTH_LEFT to 0f,
            BlendShape.MOUTH_RIGHT to 0f,
            BlendShape.MOUTH_FUNNEL to 0f,
            BlendShape.JAW_OPEN to 0f,
            BlendShape.MOUTH_SMILE_LEFT to 0f,
            BlendShape.MOUTH_SMILE_RIGHT to 0f,
        )

        fun fullRunImage(): H2RgbaLandmarkFrame {
            val rgba = ByteArray(H2RgbaLandmarkFrame.WIDTH * H2RgbaLandmarkFrame.HEIGHT * 4)
            for (pixel in 0 until H2RgbaLandmarkFrame.WIDTH * H2RgbaLandmarkFrame.HEIGHT) {
                rgba[pixel * 4] = (pixel % 251).toByte()
                rgba[pixel * 4 + 1] = ((pixel / H2RgbaLandmarkFrame.WIDTH) % 251).toByte()
                rgba[pixel * 4 + 2] = ((pixel * 3) % 251).toByte()
                rgba[pixel * 4 + 3] = 0xff.toByte()
            }
            val points = MutableList(H2RgbaLandmarkFrame.LANDMARK_COUNT) { point(120f, 120f) }
            points[10] = point(120f, 30f)
            points[152] = point(120f, 280f)
            points[168] = point(120f, 70f)
            points[6] = point(120f, 100f)
            points[197] = point(115f, 115f)
            points[195] = point(120f, 125f)
            points[33] = point(40f, 44f)
            points[133] = points[33]
            points[362] = point(88f, 44f)
            points[263] = points[362]
            points[1] = point(64f, 72f)
            points[61] = point(95f, 190f)
            points[291] = point(145f, 190f)
            points[13] = point(120f, 187f)
            points[14] = point(120f, 193f)
            intArrayOf(
                10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365,
                379, 378, 400, 377, 152, 148, 176, 149, 150, 136, 172, 58, 132, 93,
                234, 127, 162, 21, 54, 103, 67, 109,
            ).forEachIndexed { position, landmark ->
                if (landmark != 10 && landmark != 152) {
                    val fraction = position.toFloat() / 35f
                    val side = if (position < 18) 1f else -1f
                    points[landmark] = point(120f + side * 55f, 30f + 250f * fraction)
                }
            }
            return H2RgbaLandmarkFrame(
                rgba,
                H2RgbaLandmarkFrame.WIDTH,
                H2RgbaLandmarkFrame.HEIGHT,
                points,
                H2FrameOrientation.FRONT_CAMERA_MIRRORED_RGBA_AND_LANDMARKS,
            )
        }

        fun point(x: Float, y: Float): FaceLandmark = FaceLandmark(
            x / H2RgbaLandmarkFrame.WIDTH,
            y / H2RgbaLandmarkFrame.HEIGHT,
            0f,
        )

        fun fullRunModel(): String {
            fun vector(size: Int, value: String) = List(size) { value }
                .joinToString(prefix = "[", postfix = "]")
            val components = List(16) { vector(384, "0") }
                .joinToString(prefix = "[", postfix = "]")
            return "{\"schema_version\":1,\"recipe\":\"DK_SESSION_B_LEARNED_CHEEK_PATCH_V1\",\"C\":1," +
                "\"scaler_mean\":${vector(384, "0")},\"scaler_scale\":${vector(384, "1")}," +
                "\"pca_mean\":${vector(384, "0")},\"pca_components\":$components," +
                "\"pca_explained_variance\":${vector(16, "1")}," +
                "\"classifier_intercept\":[0],\"classifier_coef\":[${vector(16, "0")}] }"
        }

        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        fun longLe(value: Long): ByteArray = ByteArray(8) { (value ushr (it * 8)).toByte() }
        fun intLe(value: Int): ByteArray = ByteArray(4) { (value ushr (it * 8)).toByte() }
    }
}
