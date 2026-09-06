@file:Suppress("LongMethod", "MagicNumber", "StringLiteralDuplication")

package io.github.kmpfacelink.capture

import io.github.kmpfacelink.model.FaceLandmark
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidH2StreamingFailurePathTest {
    @Test
    fun realControllerFailStoreRetainsEventFailureAndStartsOnlyAttemptTwo() = withRoot("h2-v4-controller") { root ->
        val fault = OneShotFault()
        val controller = controller(root, fault)
        beginReadiness(controller)
        fault.arm(H2StreamingFaultPoint.STREAM_WRITE, EVENT_STREAM)
        val failure = assertFailsWith<H2ControllerStorageFailure> {
            controller.observeReadiness(
                ORIGIN + 250_000_000,
                H2ProviderRouteAvailability.AVAILABLE,
                H2ProviderRouteAvailability.AVAILABLE,
            )
        }
        assertEquals(H2FailureCode.UNPUBLISHED_EVENT_LOG_WRITE_FAILURE, failure.failureCode)
        assertEquals(H2RunPhase.OPEN, controller.state.phase)
        assertEquals(1, controller.state.failures.size)
        assertTerminated(
            root.resolve(".B01_STARTUP_NEUTRAL_READINESS.1.terminated"),
            H2FailureCode.UNPUBLISHED_EVENT_LOG_WRITE_FAILURE,
        )
        assertFalse(
            "TERMINAL_FAILURE" in
                root.resolve(".B01_STARTUP_NEUTRAL_READINESS.1.terminated/$EVENT_STREAM").readText(),
        )
        controller.beginReadiness(CLOCK.copy(cameraOriginNanos = ORIGIN + 20_000_000_000), available(), available())
        assertEquals(2, controller.state.activeAttempt)
        controller.cameraLost()
    }

    @Test
    fun realControllerPublishAndHashFailuresRetainPrefixBeforeRetry() {
        val cases = listOf(
            H2StreamingFaultPoint.PUBLISH_FINALIZE to
                H2FailureCode.UNPUBLISHED_ATOMIC_PUBLICATION_PRECONDITION_FAILURE,
            H2StreamingFaultPoint.PUBLISH_MANIFEST_SYNCED to
                H2FailureCode.UNPUBLISHED_ATOMIC_PUBLICATION_PRECONDITION_FAILURE,
            H2StreamingFaultPoint.PUBLISH_ATOMIC_MOVE to
                H2FailureCode.UNPUBLISHED_ATOMIC_PUBLICATION_PRECONDITION_FAILURE,
        )
        cases.forEach { (point, expected) ->
            withRoot("h2-v4-controller-publish") { root ->
                val fault = OneShotFault()
                val controller = controller(root, fault)
                readyThroughEndpointMinusOneStep(controller)
                fault.arm(point)
                val failure = assertFailsWith<H2ControllerStorageFailure> {
                    observeReadiness(controller, 15_000)
                }
                assertEquals(expected, failure.failureCode)
                assertEquals(H2RunPhase.OPEN, controller.state.phase)
                assertTerminated(root.resolve(".B01_STARTUP_NEUTRAL_READINESS.1.terminated"), expected)
                assertFalse(root.resolve(".B01_STARTUP_NEUTRAL_READINESS.1.partial").exists())
                controller.beginReadiness(
                    CLOCK.copy(cameraOriginNanos = ORIGIN + 20_000_000_000),
                    available(),
                    available(),
                )
                assertEquals(2, controller.state.activeAttempt)
                controller.cameraLost()
            }
        }
        withRoot("h2-v4-controller-hash") { root ->
            val controller = controller(root, OneShotFault())
            readyThroughEndpointMinusOneStep(controller)
            FileOutputStream(
                root.resolve(".B01_STARTUP_NEUTRAL_READINESS.1.partial/camera.rgba"),
                true,
            ).use { it.write(7) }
            val failure = assertFailsWith<H2ControllerStorageFailure> {
                observeReadiness(controller, 15_000)
            }
            assertEquals(H2FailureCode.UNPUBLISHED_HASH_OR_LENGTH_MISMATCH, failure.failureCode)
            assertEquals(H2RunPhase.OPEN, controller.state.phase)
            assertTerminated(
                root.resolve(".B01_STARTUP_NEUTRAL_READINESS.1.terminated"),
                H2FailureCode.UNPUBLISHED_HASH_OR_LENGTH_MISMATCH,
            )
        }
    }

    @Test
    fun realControllerTerminationCleanupFailureCannotLeaveRetryOpen() {
        withRoot("h2-v4-controller-cleanup") { root ->
            val fault = OneShotFault()
            val controller = controller(root, fault)
            beginReadiness(controller)
            fault.arm(H2StreamingFaultPoint.STREAM_WRITE, EVENT_STREAM)
            fault.armAdditional(H2StreamingFaultPoint.TERMINATE_ATOMIC_MOVE)
            val failure = assertFailsWith<H2ControllerStorageFailure> {
                observeReadiness(controller, 250)
            }
            assertEquals(H2FailureCode.POST_PUBLICATION_MUTATION, failure.failureCode)
            assertEquals(H2RunPhase.TERMINATED, controller.state.phase)
            assertEquals(H2FailureCode.POST_PUBLICATION_MUTATION, controller.state.terminalCode)
            assertTrue(root.resolve(".B01_STARTUP_NEUTRAL_READINESS.1.partial").isDirectory)
            AndroidH2StreamingCaptureManager(root, BLOCK_LIMIT)
            assertFalse(root.resolve(".B01_STARTUP_NEUTRAL_READINESS.1.partial").exists())
            assertTerminated(
                root.resolve(".B01_STARTUP_NEUTRAL_READINESS.1.terminated"),
                H2FailureCode.RESTART_DURING_ACTIVE_ATTEMPT,
            )
        }
    }

    @Test
    fun primaryStreamFailurePlusTerminalEventFailureIsDurablyNonRetryable() {
        val primaryStreams = listOf(
            "camera.rgba",
            "participant-action.rgba",
            "avatar-a.rgba",
            "avatar-b.rgba",
        )
        primaryStreams.forEach { primaryStream ->
            withRoot("h2-v5-double-fault") { root ->
                val blockRoot = root.resolve("attempts").apply { require(mkdir()) }.canonicalFile
                val journalRoot = root.resolve("journal").apply { require(mkdir()) }.canonicalFile
                val fault = PrimaryThenEventFault(primaryStream)
                val controller = controller(blockRoot, fault, AndroidH2ControllerRunJournal(journalRoot))
                readyThroughEndpointMinusOneStep(controller)
                assertEquals(H2ReadinessPhase.READY, observeReadiness(controller, 15_000))
                val takeClock = clockAt(50_000_000_000L)
                controller.beginTake(H2ConfirmationContract.blocks[1].id, takeClock)

                val failure = assertFailsWith<H2ControllerStorageFailure> {
                    controller.onJoinedResult(joinedCalibration(takeClock))
                }

                assertEquals(H2FailureCode.POST_PUBLICATION_MUTATION, failure.failureCode)
                assertEquals(H2RunPhase.TERMINATED, controller.state.phase)
                assertEquals(H2FailureCode.POST_PUBLICATION_MUTATION, controller.state.terminalCode)
                assertEquals(H2FailureCode.POST_PUBLICATION_MUTATION, controller.state.failures.single().code)
                val terminated = blockRoot.resolve(".B02_OPENING_NEUTRAL_READY.1.terminated")
                assertTerminated(terminated, H2FailureCode.POST_PUBLICATION_MUTATION)
                assertFalse("TERMINAL_FAILURE" in terminated.resolve(EVENT_STREAM).readText())
                assertFalse(blockRoot.resolve(".B02_OPENING_NEUTRAL_READY.1.partial").exists())
                val reopened = AndroidH2ControllerRunJournal(journalRoot).openOrRecover(RUN_ID)
                assertEquals(H2RunPhase.TERMINATED, reopened.phase)
                assertEquals(H2FailureCode.POST_PUBLICATION_MUTATION, reopened.terminalCode)
                assertEquals(controller.state.failures, reopened.failures)
                assertFailsWith<IllegalArgumentException> {
                    AndroidH2FinalizedRunVerifier(blockRoot).verifyAndBuildCaptureSeal(
                        controller.state,
                        unusableSealInputs(),
                        "2026-09-03T00:00:00Z",
                    )
                }
                assertFailsWith<IllegalStateException> {
                    controller.beginTake(H2ConfirmationContract.blocks[1].id, clockAt(80_000_000_000L))
                }
            }
        }
    }

    @Test
    fun realStoreStreamFailuresRetainExactPrefixAndAllowAttemptTwo() {
        val cases = listOf(
            "camera.rgba" to H2FailureCode.UNPUBLISHED_CAMERA_STREAM_WRITE_FAILURE,
            "participant-action.rgba" to H2FailureCode.UNPUBLISHED_OUTPUT_STREAM_WRITE_FAILURE,
            "avatar-a.rgba" to H2FailureCode.UNPUBLISHED_OUTPUT_STREAM_WRITE_FAILURE,
            "avatar-b.rgba" to H2FailureCode.UNPUBLISHED_OUTPUT_STREAM_WRITE_FAILURE,
            EVENT_STREAM to H2FailureCode.UNPUBLISHED_EVENT_LOG_WRITE_FAILURE,
        )
        cases.forEach { (logicalName, expectedCode) ->
            withRoot("h2-v4-stream") { root ->
                val fault = OneShotFault()
                val store = AndroidH2ControllerStore(
                    AndroidH2StreamingCaptureManager(root, BLOCK_LIMIT, faultInjection = fault),
                )
                store.beginBlock(BLOCK_ID, 1)
                appendPrefix(store)
                fault.arm(H2StreamingFaultPoint.STREAM_WRITE, logicalName)
                val failure = assertFailsWith<H2ControllerStorageFailure> { store.append(frame()) }
                assertEquals(expectedCode, failure.failureCode)
                if (expectedCode != H2FailureCode.UNPUBLISHED_EVENT_LOG_WRITE_FAILURE) {
                    store.appendEvent(terminalEvent(expectedCode, 5))
                }
                store.terminateBlock(expectedCode)

                val terminated = root.resolve(".$BLOCK_ID.1.terminated")
                assertTerminated(terminated, expectedCode)
                assertFalse(root.resolve(".$BLOCK_ID.1.partial").exists())
                val events = terminated.resolve(EVENT_STREAM).readText()
                val sequences = Regex("\\\"sequence\\\":(\\d+)").findAll(events)
                    .map { it.groupValues[1].toLong() }
                    .toList()
                val expectedSequences = if (logicalName == EVENT_STREAM) listOf(0L, 1L) else (0L..5L).toList()
                assertEquals(expectedSequences, sequences)
                assertEquals(
                    expectedCode != H2FailureCode.UNPUBLISHED_EVENT_LOG_WRITE_FAILURE,
                    "TERMINAL_FAILURE" in events,
                )

                store.beginBlock(BLOCK_ID, 2)
                store.terminateBlock(H2FailureCode.PARTICIPANT_STOP)
                assertTrue(root.resolve(".$BLOCK_ID.2.terminated").isDirectory)
            }
        }
    }

    @Test
    fun realWriterClassifiesHashMismatchAndRetainsRetryableAttempt() = withRoot("h2-v4-hash") { root ->
        val store = AndroidH2ControllerStore(AndroidH2StreamingCaptureManager(root, BLOCK_LIMIT))
        store.beginBlock(BLOCK_ID, 1)
        appendPrefix(store)
        FileOutputStream(root.resolve(".$BLOCK_ID.1.partial/camera.rgba"), true).use { it.write(7) }
        val failure = assertFailsWith<H2ControllerStorageFailure> { store.publishBlock() }
        assertEquals(H2FailureCode.UNPUBLISHED_HASH_OR_LENGTH_MISMATCH, failure.failureCode)
        assertTerminated(
            root.resolve(".$BLOCK_ID.1.terminated"),
            H2FailureCode.UNPUBLISHED_HASH_OR_LENGTH_MISMATCH,
        )
        assertFalse(root.resolve(".$BLOCK_ID.1.partial").exists())
        store.terminateBlock(failure.failureCode)
        store.beginBlock(BLOCK_ID, 2)
        store.terminateBlock(H2FailureCode.PARTICIPANT_STOP)
    }

    @Test
    fun unpublishedPublishFaultsBecomeTerminatedAndAttemptTwoNeverCollides() {
        listOf(
            H2StreamingFaultPoint.PUBLISH_FINALIZE,
            H2StreamingFaultPoint.PUBLISH_MANIFEST_WRITE,
            H2StreamingFaultPoint.PUBLISH_MANIFEST_SYNCED,
            H2StreamingFaultPoint.PUBLISH_ATOMIC_MOVE,
        ).forEach { point ->
            withRoot("h2-v4-publish") { root ->
                val fault = OneShotFault()
                val store = AndroidH2ControllerStore(
                    AndroidH2StreamingCaptureManager(root, BLOCK_LIMIT, faultInjection = fault),
                )
                store.beginBlock(BLOCK_ID, 1)
                appendPrefix(store)
                fault.arm(point)
                val failure = assertFailsWith<H2ControllerStorageFailure> { store.publishBlock() }
                assertEquals(H2FailureCode.UNPUBLISHED_ATOMIC_PUBLICATION_PRECONDITION_FAILURE, failure.failureCode)
                assertTerminated(
                    root.resolve(".$BLOCK_ID.1.terminated"),
                    H2FailureCode.UNPUBLISHED_ATOMIC_PUBLICATION_PRECONDITION_FAILURE,
                )
                assertFalse(root.resolve(".$BLOCK_ID.1.partial").exists())
                store.terminateBlock(failure.failureCode)
                store.beginBlock(BLOCK_ID, 2)
                store.terminateBlock(H2FailureCode.PARTICIPANT_STOP)
            }
        }
    }

    @Test
    fun destinationCollisionAndPostMoveFailureAreNonRetryableAndLeaveNoActiveWriter() {
        withRoot("h2-v4-collision") { root ->
            val store = AndroidH2ControllerStore(AndroidH2StreamingCaptureManager(root, BLOCK_LIMIT))
            store.beginBlock(BLOCK_ID, 1)
            appendPrefix(store)
            assertTrue(root.resolve("$BLOCK_ID.1").mkdir())
            val failure = assertFailsWith<H2ControllerStorageFailure> { store.publishBlock() }
            assertEquals(H2FailureCode.POST_PUBLICATION_MUTATION, failure.failureCode)
            assertFalse(root.resolve(".$BLOCK_ID.1.partial").exists())
            assertTerminated(root.resolve(".$BLOCK_ID.1.terminated"), H2FailureCode.POST_PUBLICATION_MUTATION)
            store.terminateBlock(failure.failureCode)
            store.beginBlock(BLOCK_ID, 2)
            store.terminateBlock(H2FailureCode.PARTICIPANT_STOP)
        }
        withRoot("h2-v4-post-move") { root ->
            val fault = OneShotFault()
            val store = AndroidH2ControllerStore(
                AndroidH2StreamingCaptureManager(root, BLOCK_LIMIT, faultInjection = fault),
            )
            store.beginBlock(BLOCK_ID, 1)
            appendPrefix(store)
            fault.arm(H2StreamingFaultPoint.PUBLISH_MOVED)
            val failure = assertFailsWith<H2ControllerStorageFailure> { store.publishBlock() }
            assertEquals(H2FailureCode.POST_PUBLICATION_MUTATION, failure.failureCode)
            assertTrue(root.resolve("$BLOCK_ID.1").isDirectory)
            assertFalse(root.resolve(".$BLOCK_ID.1.partial").exists())
            store.terminateBlock(failure.failureCode)
            store.beginBlock(BLOCK_ID, 2)
            store.terminateBlock(H2FailureCode.PARTICIPANT_STOP)
        }
    }

    @Test
    fun terminationFailureCannotRemainRetryOpenAndRestartRecoversThePartial() = withRoot("h2-v4-cleanup") { root ->
        val fault = OneShotFault()
        val manager = AndroidH2StreamingCaptureManager(root, BLOCK_LIMIT, faultInjection = fault)
        val store = AndroidH2ControllerStore(manager)
        store.beginBlock(BLOCK_ID, 1)
        appendPrefix(store)
        fault.arm(H2StreamingFaultPoint.TERMINATE_ATOMIC_MOVE)
        assertFailsWith<IOException> {
            store.terminateBlock(H2FailureCode.UNPUBLISHED_CAMERA_STREAM_WRITE_FAILURE)
        }
        assertTrue(root.resolve(".$BLOCK_ID.1.partial").isDirectory)
        AndroidH2StreamingCaptureManager(root, BLOCK_LIMIT)
        assertFalse(root.resolve(".$BLOCK_ID.1.partial").exists())
        assertTerminated(
            root.resolve(".$BLOCK_ID.1.terminated"),
            H2FailureCode.RESTART_DURING_ACTIVE_ATTEMPT,
        )
    }

    private fun appendPrefix(store: AndroidH2ControllerStore) {
        store.appendEvent(
            H2ControllerEvent(
                0,
                BLOCK_ID,
                H2ControllerEventKind.BLOCK_SCHEDULE_OPENED,
                ORIGIN,
                RUN_ID,
                participantVisibility = H2SurfaceVisibilityMode.NEITHER,
            ),
        )
        store.appendEvent(
            H2ControllerEvent(
                1,
                BLOCK_ID,
                H2ControllerEventKind.CLOCK_MAPPING,
                ORIGIN,
                RUN_ID,
                clockMapping = CLOCK,
            ),
        )
    }

    private fun controller(
        root: File,
        fault: H2StreamingFaultInjection,
        journal: H2ControllerRunJournal? = null,
    ): H2SessionController {
        val expected = "0".repeat(64)
        val provider = H2StrictC3bModelProvider(
            expected,
            H2ModelByteLoader { null },
            H2Sha256 { expected },
        )
        val runtime = H2V2TakeRuntime(H2C3bCandidate(provider), H2C2C1Candidate())
        val store = AndroidH2ControllerStore(
            AndroidH2StreamingCaptureManager(root, BLOCK_LIMIT, faultInjection = fault),
        )
        return if (journal == null) {
            H2SessionController(
                RUN_ID,
                runtime,
                store,
                H2SurfaceVisibilityMode.NEITHER,
            )
        } else {
            H2SessionController(
                RUN_ID,
                runtime,
                store,
                H2SurfaceVisibilityMode.NEITHER,
                journal = journal,
            )
        }
    }

    private fun beginReadiness(controller: H2SessionController) {
        controller.beginReadiness(CLOCK, available(), available())
        controller.routeCue(H2CueSegmentName.READINESS_AVAILABILITY, 0, ORIGIN)
        controller.acknowledgeCuePresentation(H2CueSegmentName.READINESS_AVAILABILITY, ORIGIN)
    }

    private fun readyThroughEndpointMinusOneStep(controller: H2SessionController) {
        beginReadiness(controller)
        for (elapsedMillis in 250L until 15_000L step 250L) observeReadiness(controller, elapsedMillis)
    }

    private fun observeReadiness(controller: H2SessionController, elapsedMillis: Long): H2ReadinessPhase =
        controller.observeReadiness(
            ORIGIN + elapsedMillis * 1_000_000,
            available(),
            available(),
        )

    private fun joinedCalibration(clock: H2ClockMapping): H2JoinedCanonicalResult = H2JoinedCanonicalResult(
        frameIndex = 0,
        takeElapsedMillis = 0,
        monotonicNanos = clock.cameraOriginNanos,
        image = H2RgbaLandmarkFrame(
            ByteArray(H2RgbaLandmarkFrame.WIDTH * H2RgbaLandmarkFrame.HEIGHT * H2RgbaLandmarkFrame.RGBA_CHANNELS),
            H2RgbaLandmarkFrame.WIDTH,
            H2RgbaLandmarkFrame.HEIGHT,
            List(H2RgbaLandmarkFrame.LANDMARK_COUNT) { FaceLandmark(0.5f, 0.5f, 0f) },
            H2FrameOrientation.FRONT_CAMERA_MIRRORED_RGBA_AND_LANDMARKS,
        ),
        estimator = emptyMap(),
    )

    private fun clockAt(origin: Long): H2ClockMapping = H2ClockMapping(
        H2ClockMapping.MONOTONIC_CLOCK_ID,
        origin,
        origin,
        origin,
        origin,
    )

    private fun unusableSealInputs(): H2CaptureSealInputBytes = H2CaptureSealInputBytes(
        ByteArray(0),
        ByteArray(0),
        ByteArray(0),
        ByteArray(0),
        "0".repeat(40),
        ByteArray(0),
        ByteArray(0),
        ByteArray(0),
        ByteArray(0),
    )

    private fun available(): H2ProviderRouteAvailability = H2ProviderRouteAvailability.AVAILABLE

    private fun frame(): H2RetainedControllerFrame {
        val primary = checkNotNull(
            H2BlindAvatarRenderer(H2CandidateRole.PRIMARY).render(
                H2AvatarRouteState(false, true),
                CLOCK,
                3,
                BLOCK_ID,
                ORIGIN + 3_001_000_000,
            ),
        )
        val comparator = checkNotNull(
            H2BlindAvatarRenderer(H2CandidateRole.COMPARATOR).render(
                H2AvatarRouteState(true, true),
                CLOCK,
                4,
                BLOCK_ID,
                ORIGIN + 3_001_000_000,
            ),
        )
        return H2RetainedControllerFrame(
            7,
            3_001,
            ORIGIN + 3_001_000_000,
            byteArrayOf(1, 2, 3),
            byteArrayOf(1, 2, 3),
            primary,
            comparator,
            null,
            listOf(primary.event, comparator.event),
            listOf(
                H2ControllerEvent(
                    2,
                    BLOCK_ID,
                    H2ControllerEventKind.FRAME_JOINED,
                    ORIGIN + 3_001_000_000,
                    RUN_ID,
                    7,
                    3_001,
                ),
            ),
        )
    }

    private fun terminalEvent(code: H2FailureCode, sequence: Long): H2ControllerEvent = H2ControllerEvent(
        sequence,
        BLOCK_ID,
        H2ControllerEventKind.TERMINAL_FAILURE,
        ORIGIN + 3_001_000_000,
        RUN_ID,
        7,
        3_001,
        failureCode = code,
    )

    private fun assertTerminated(directory: File, code: H2FailureCode) {
        assertTrue(directory.isDirectory)
        assertEquals(code.name, directory.resolve(H2StreamingBlockWriter.FAILURE_CODE_FILE).readText().trim())
        val records = directory.resolve(H2StreamingBlockWriter.TERMINATED_STREAM_MANIFEST)
            .readLines()
            .filter(String::isNotEmpty)
        assertEquals(H2StreamingBlockWriter.REQUIRED_STREAMS.size, records.size)
        records.forEach { record ->
            val fields = record.split('\t')
            val file = directory.resolve(fields[0])
            assertEquals(fields[1].toLong(), file.length())
            assertEquals(fields[2], sha256(file.readBytes()))
        }
        assertEquals(
            H2StreamingBlockWriter.REQUIRED_STREAMS +
                H2StreamingBlockWriter.TERMINATED_STREAM_MANIFEST + H2StreamingBlockWriter.FAILURE_CODE_FILE,
            directory.listFiles().orEmpty().map { it.name }.toSet(),
        )
    }

    private inline fun withRoot(prefix: String, block: (File) -> Unit) {
        val root = Files.createTempDirectory(prefix).toFile().canonicalFile
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }

    private class OneShotFault : H2StreamingFaultInjection {
        private val pending = mutableListOf<Pair<H2StreamingFaultPoint, String?>>()

        fun arm(point: H2StreamingFaultPoint, logicalName: String? = null) {
            pending.clear()
            pending += point to logicalName
        }

        fun armAdditional(point: H2StreamingFaultPoint, logicalName: String? = null) {
            pending += point to logicalName
        }

        override fun before(point: H2StreamingFaultPoint, logicalName: String?) {
            val index = pending.indexOfFirst { (expectedPoint, expectedName) ->
                expectedPoint == point && (expectedName == null || expectedName == logicalName)
            }
            if (index >= 0) {
                pending.removeAt(index)
                throw IOException("synthetic public H2 storage fault")
            }
        }
    }

    private class PrimaryThenEventFault(private val primaryStream: String) : H2StreamingFaultInjection {
        private var primaryFailed = false

        override fun before(point: H2StreamingFaultPoint, logicalName: String?) {
            if (point != H2StreamingFaultPoint.STREAM_WRITE) return
            if (!primaryFailed && logicalName == primaryStream) {
                primaryFailed = true
                throw IOException("synthetic public H2 primary storage fault")
            }
            if (primaryFailed && logicalName == EVENT_STREAM) {
                throw IOException("synthetic public H2 terminal-event storage fault")
            }
        }
    }

    private companion object {
        const val BLOCK_ID = "B03_JAW_OPEN"
        const val BLOCK_LIMIT = 2_000_000L
        const val ORIGIN = 20_000_000_000L
        const val EVENT_STREAM = "controller-events.jsonl"
        const val RUN_ID = "1111111111111111111111111111111111111111111111111111111111111111"
        val CLOCK = H2ClockMapping(
            H2ClockMapping.MONOTONIC_CLOCK_ID,
            ORIGIN,
            ORIGIN,
            ORIGIN,
            ORIGIN,
        )

        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
