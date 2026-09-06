@file:Suppress("MagicNumber", "TooManyFunctions")

package io.github.kmpfacelink.capture

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.FaceLandmark
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class H2SessionControllerTest {
    @Test
    fun fullReadinessThenTwoTakesResetAndRetainBothSurfacesOnOneClock() {
        val store = FakeStore()
        val controller = controller(store, H2SurfaceVisibilityMode.PRIMARY_ONLY)
        completeB01(controller)

        controller.beginTake(H2ConfirmationContract.blocks[1].id, ORIGIN)
        calibrate(controller)
        val first = controller.onJoinedResult(joined(40, 3_001))
        assertTrue(controller.acknowledgeParticipantPresentation(first.frameIndex, first.sourceMonotonicNanos))
        assertEquals(H2CandidateRole.PRIMARY, first.primaryAvatar?.role)
        assertEquals(H2CandidateRole.COMPARATOR, first.comparatorAvatar?.role)
        assertEquals(first.cameraRgba.toList(), first.participantActionRgba.toList())
        assertEquals(first.primaryAvatar, first.participantPresentedAvatar)
        assertEquals(first.primaryAvatar?.clock, first.comparatorAvatar?.clock)
        assertEquals(first.visibleEvents.map { it.monotonicNanos }.distinct().size, 1)
        assertEquals(40, first.frameIndex)
        assertEquals(3_001, first.takeElapsedMillis)
        assertEquals(
            setOf(
                H2ControllerEventKind.FRAME_JOINED,
                H2ControllerEventKind.ROUTE_AVAILABLE,
                H2ControllerEventKind.PARTICIPANT_ROUTE_SELECTED,
            ),
            first.controllerEvents.map(H2ControllerEvent::kind).toSet(),
        )
        observeThroughEnd(controller, 41, 3_251, 63_000)
        controller.completeTake()

        controller.beginTake(H2ConfirmationContract.blocks[2].id, NEXT_ORIGIN)
        assertNull(controller.onJoinedResult(joined(0, 0, NEXT_ORIGIN)).primaryAvatar)
        calibrate(controller, NEXT_ORIGIN, startFrameIndex = 1)
        val second = controller.onJoinedResult(joined(40, 3_001, NEXT_ORIGIN))
        assertTrue(controller.acknowledgeParticipantPresentation(second.frameIndex, second.sourceMonotonicNanos))
        assertTrue(second.primaryAvatar != null && second.comparatorAvatar != null)
        observeThroughEnd(controller, 41, 3_251, 13_000, NEXT_ORIGIN)
        controller.completeTake()
        assertEquals(3, store.published.size)
        assertTrue(store.events.any { it.kind == H2ControllerEventKind.BLOCK_SCHEDULE_OPENED })
        assertTrue(store.events.any { it.kind == H2ControllerEventKind.CUE_ROUTE_ACTIVATED })
        assertTrue(store.events.any { it.kind == H2ControllerEventKind.CUE_PRESENTATION_ACKNOWLEDGED })
        assertTrue(store.events.any { it.kind == H2ControllerEventKind.CLOCK_MAPPING })
        assertEquals(
            listOf(
                "B01_STARTUP_NEUTRAL_READINESS",
                "B02_OPENING_NEUTRAL_READY",
                "B03_JAW_OPEN",
            ),
            store.published.map(H2ControllerPublication::blockId),
        )
    }

    @Test
    fun readinessUnavailableAtLaunchOrDropRegainTerminatesWithRetainedEvidence() {
        val lateStore = FakeStore()
        val late = controller(lateStore, H2SurfaceVisibilityMode.NEITHER)
        late.beginReadiness(
            0,
            H2ProviderRouteAvailability.NOT_READY,
            H2ProviderRouteAvailability.AVAILABLE,
        )
        assertEquals(
            H2ReadinessPhase.WAITING,
            late.observeReadiness(
                5_000_000_000,
                H2ProviderRouteAvailability.AVAILABLE,
                H2ProviderRouteAvailability.AVAILABLE,
            ),
        )
        assertEquals(
            H2ReadinessPhase.TIMED_OUT,
            late.observeReadiness(
                15_000_000_000,
                H2ProviderRouteAvailability.AVAILABLE,
                H2ProviderRouteAvailability.AVAILABLE,
            ),
        )
        assertEquals(H2FailureCode.READINESS_TIMEOUT, late.state.terminalCode)
        assertEquals(3, lateStore.events.count { it.kind == H2ControllerEventKind.READINESS_OBSERVATION })
        assertTrue(lateStore.retained.isEmpty())

        val dropStore = FakeStore()
        val drop = controller(dropStore, H2SurfaceVisibilityMode.NEITHER)
        drop.beginReadiness(
            0,
            H2ProviderRouteAvailability.AVAILABLE,
            H2ProviderRouteAvailability.AVAILABLE,
        )
        drop.observeReadiness(
            7_000_000_000,
            H2ProviderRouteAvailability.UNAVAILABLE,
            H2ProviderRouteAvailability.AVAILABLE,
        )
        assertEquals(
            H2ReadinessPhase.TIMED_OUT,
            drop.observeReadiness(
                15_000_000_000,
                H2ProviderRouteAvailability.AVAILABLE,
                H2ProviderRouteAvailability.AVAILABLE,
            ),
        )
        assertTrue(dropStore.events.any { it.primaryAvailability == H2ProviderRouteAvailability.UNAVAILABLE })
        assertEquals(H2FailureCode.READINESS_TIMEOUT, dropStore.terminated)
    }

    @Test
    fun unavailableIsRetainedAsNotVisibleAndCannotBecomeInactiveDenominator() {
        val store = FakeStore()
        val controller = controller(store, H2SurfaceVisibilityMode.NEITHER, modelAvailable = false)
        completeB01(controller)
        controller.beginTake(H2ConfirmationContract.blocks[1].id, ORIGIN)
        calibrate(controller)
        val missing = controller.onJoinedResult(joined(40, 3_001))
        assertNull(missing.primaryAvatar)
        assertNull(missing.comparatorAvatar)
        assertEquals(missing.cameraRgba.toList(), missing.participantActionRgba.toList())
        assertNull(missing.participantPresentedAvatar)
        assertEquals(
            listOf(H2VisibleOutputState.OUTPUT_NOT_VISIBLE, H2VisibleOutputState.OUTPUT_NOT_VISIBLE),
            missing.visibleEvents.map(H2VisibleOutputEvent::state),
        )
        assertTrue(
            missing.controllerEvents.any {
                it.kind == H2ControllerEventKind.CANDIDATE_MISSING_OR_NOT_READY
            },
        )
        controller.onJoinedResult(joined(41, 63_000))
        assertFailsWith<IllegalStateException> { controller.completeTake() }
        assertEquals(H2FailureCode.OBSERVATION_INCOMPLETE, controller.state.terminalCode)
        assertEquals(H2FailureCode.OBSERVATION_INCOMPLETE, store.terminated)
    }

    @Test
    fun renderLossTerminatesInsteadOfPublishingBlankOrInactiveOutput() {
        val store = FakeStore()
        val unavailableRenderer = H2ControllerRenderer { _, _, _, _, _ -> null }
        val controller = controller(
            store,
            H2SurfaceVisibilityMode.PRIMARY_ONLY,
            primaryRenderer = unavailableRenderer,
        )
        completeB01(controller)
        controller.beginTake(H2ConfirmationContract.blocks[1].id, ORIGIN)
        calibrate(controller)
        assertFailsWith<IllegalStateException> {
            controller.onJoinedResult(joined(40, 3_001))
        }
        assertEquals(H2FailureCode.RENDER_LOSS, controller.state.terminalCode)
        assertEquals(H2FailureCode.RENDER_LOSS, store.terminated)
    }

    @Test
    fun sparsePostCalibrationFramesCannotManufactureTheFullObservableDenominator() {
        val store = FakeStore()
        val controller = controller(store, H2SurfaceVisibilityMode.NEITHER)
        completeB01(controller)
        controller.beginTake(H2ConfirmationContract.blocks[1].id, ORIGIN)
        calibrate(controller)
        controller.onJoinedResult(joined(40, 3_001))
        controller.onJoinedResult(joined(41, 63_000))
        assertFailsWith<IllegalStateException> { controller.completeTake() }
        assertEquals(H2FailureCode.OBSERVATION_INCOMPLETE, controller.state.terminalCode)
    }

    @Test
    fun midWindowGapOverTwoHundredFiftyMillisCannotBeRecovered() {
        val store = FakeStore()
        val controller = controller(store, H2SurfaceVisibilityMode.NEITHER)
        completeB01(controller)
        controller.beginTake(H2ConfirmationContract.blocks[1].id, ORIGIN)
        calibrate(controller)
        controller.onJoinedResult(joined(40, 3_001))
        controller.onJoinedResult(joined(41, 3_252))
        observeThroughEnd(controller, 42, 3_502, 63_000)
        assertFailsWith<IllegalStateException> { controller.completeTake() }
        assertEquals(H2FailureCode.OBSERVATION_INCOMPLETE, controller.state.terminalCode)
    }

    @Test
    fun continuousFramesThatStopBeforeTheExactBlockEndCannotPublish() {
        val store = FakeStore()
        val controller = controller(store, H2SurfaceVisibilityMode.NEITHER)
        completeB01(controller)
        controller.beginTake(H2ConfirmationContract.blocks[1].id, ORIGIN)
        calibrate(controller)
        controller.onJoinedResult(joined(40, 3_001))
        observeThroughEnd(controller, 41, 3_251, 62_999)
        assertFailsWith<IllegalStateException> { controller.completeTake() }
        assertEquals(H2FailureCode.OBSERVATION_INCOMPLETE, controller.state.terminalCode)
    }

    @Test
    fun frameAfterExactBlockEndTerminatesBeforeItCanBeRetained() {
        val store = FakeStore()
        val controller = controller(store, H2SurfaceVisibilityMode.NEITHER)
        completeB01(controller)
        controller.beginTake(H2ConfirmationContract.blocks[1].id, ORIGIN)
        calibrate(controller)
        controller.onJoinedResult(joined(40, 3_001))
        observeThroughEnd(controller, 41, 3_251, 63_000)
        val retainedBeforeMutation = store.retained.size
        assertFailsWith<IllegalArgumentException> {
            controller.onJoinedResult(joined(281, 63_001))
        }
        assertEquals(retainedBeforeMutation, store.retained.size)
        assertEquals(H2FailureCode.OBSERVATION_INCOMPLETE, controller.state.terminalCode)
        assertEquals(H2FailureCode.OBSERVATION_INCOMPLETE, store.terminated)
    }

    @Test
    fun duplicateFrameAndStrictRuntimeTimeFailureBecomeDurableTerminalStates() {
        val clockStore = FakeStore()
        val clockController = controller(clockStore, H2SurfaceVisibilityMode.NEITHER)
        completeB01(clockController)
        clockController.beginTake(H2ConfirmationContract.blocks[1].id, ORIGIN)
        clockController.onJoinedResult(joined(0, 250))
        assertFailsWith<IllegalArgumentException> {
            clockController.onJoinedResult(joined(0, 500))
        }
        assertEquals(H2FailureCode.CLOCK_DISCONTINUITY, clockController.state.terminalCode)
        assertEquals(H2FailureCode.CLOCK_DISCONTINUITY, clockStore.terminated)

        val runtimeStore = FakeStore()
        val runtimeController = controller(runtimeStore, H2SurfaceVisibilityMode.NEITHER)
        completeB01(runtimeController)
        runtimeController.beginTake(H2ConfirmationContract.blocks[1].id, ORIGIN)
        calibrate(runtimeController)
        runtimeController.onJoinedResult(joined(40, 3_001))
        assertFailsWith<IllegalStateException> {
            runtimeController.onJoinedResult(joined(41, 3_001))
        }
        assertEquals(H2FailureCode.RUNTIME_OR_DELEGATE_FALLBACK, runtimeController.state.terminalCode)
        assertEquals(H2FailureCode.RUNTIME_OR_DELEGATE_FALLBACK, runtimeStore.terminated)
    }

    @Test
    fun beginAttemptFailureIsTerminalBecauseNoAttemptEvidenceDirectoryExists() {
        val store = FakeStore()
        val controller = controller(store, H2SurfaceVisibilityMode.NEITHER)
        completeB01(controller)
        store.failBeginCode = H2FailureCode.BEGIN_ATTEMPT_PRECONDITION_FAILURE
        assertFailsWith<H2ControllerStorageFailure> {
            controller.beginTake(H2ConfirmationContract.blocks[1].id, ORIGIN)
        }
        assertEquals(H2RunPhase.TERMINATED, controller.state.phase)
        assertEquals(
            H2FailureCode.BEGIN_ATTEMPT_PRECONDITION_FAILURE,
            controller.state.terminalCode,
        )
        assertEquals(listOf(1), store.beginAttempts.takeLast(1))
    }

    @Test
    fun cameraOutputAndEventWriteFailuresRetainTheirClosedMachineCodes() {
        listOf(
            H2FailureCode.UNPUBLISHED_CAMERA_STREAM_WRITE_FAILURE,
            H2FailureCode.UNPUBLISHED_OUTPUT_STREAM_WRITE_FAILURE,
            H2FailureCode.UNPUBLISHED_EVENT_LOG_WRITE_FAILURE,
        ).forEach { code ->
            val store = FakeStore()
            val controller = controller(store, H2SurfaceVisibilityMode.NEITHER)
            completeB01(controller)
            controller.beginTake(H2ConfirmationContract.blocks[1].id, ORIGIN)
            calibrate(controller)
            store.failAppendCode = code
            assertFailsWith<H2ControllerStorageFailure> {
                controller.onJoinedResult(joined(40, 3_001))
            }
            assertEquals(code, controller.state.failures.single().code)
            assertEquals(H2RunPhase.OPEN, controller.state.phase)
            if (code == H2FailureCode.UNPUBLISHED_EVENT_LOG_WRITE_FAILURE) {
                assertTrue(store.events.none { it.kind == H2ControllerEventKind.TERMINAL_FAILURE })
            } else {
                assertEquals(H2ControllerEventKind.TERMINAL_FAILURE, store.events.last().kind)
                assertEquals(code, store.events.last().failureCode)
            }
        }
    }

    @Test
    fun secondaryTerminalEventWriteFailureMakesPrimaryStorageFailureNonRetryable() {
        listOf(
            H2FailureCode.UNPUBLISHED_CAMERA_STREAM_WRITE_FAILURE,
            H2FailureCode.UNPUBLISHED_OUTPUT_STREAM_WRITE_FAILURE,
        ).forEach { primaryCode ->
            val store = FakeStore()
            val controller = controller(store, H2SurfaceVisibilityMode.NEITHER)
            completeB01(controller)
            controller.beginTake(H2ConfirmationContract.blocks[1].id, ORIGIN)
            calibrate(controller)
            store.failAppendCode = primaryCode
            store.failTerminalEvent = true
            val failure = assertFailsWith<H2ControllerStorageFailure> {
                controller.onJoinedResult(joined(40, 3_001))
            }
            assertEquals(H2FailureCode.POST_PUBLICATION_MUTATION, failure.failureCode)
            assertEquals(H2RunPhase.TERMINATED, controller.state.phase)
            assertEquals(H2FailureCode.POST_PUBLICATION_MUTATION, controller.state.terminalCode)
            assertEquals(H2FailureCode.POST_PUBLICATION_MUTATION, controller.state.failures.single().code)
            assertEquals(H2FailureCode.POST_PUBLICATION_MUTATION, store.terminated)
            assertTrue(store.events.none { it.kind == H2ControllerEventKind.TERMINAL_FAILURE })
            assertFailsWith<IllegalStateException> {
                controller.beginTake(H2ConfirmationContract.blocks[1].id, NEXT_ORIGIN)
            }
        }
    }

    @Test
    fun hashOrLengthFailureDuringPublicationCannotLeaveTheRunActive() {
        val store = FakeStore()
        val controller = controller(store, H2SurfaceVisibilityMode.NEITHER)
        controller.beginReadiness(
            0,
            H2ProviderRouteAvailability.AVAILABLE,
            H2ProviderRouteAvailability.AVAILABLE,
        )
        controller.presentCue(H2CueSegmentName.READINESS_AVAILABILITY, 0, 0)
        observeB01UntilEndpoint(controller)
        store.failPublishCode = H2FailureCode.UNPUBLISHED_HASH_OR_LENGTH_MISMATCH
        assertFailsWith<H2ControllerStorageFailure> {
            controller.observeReadiness(
                15_000_000_000,
                H2ProviderRouteAvailability.AVAILABLE,
                H2ProviderRouteAvailability.AVAILABLE,
            )
        }
        assertEquals(H2RunPhase.OPEN, controller.state.phase)
        assertEquals(H2ConfirmationContract.blocks.first().id, controller.state.replacementUsedBlockId)
        assertEquals(H2FailureCode.UNPUBLISHED_HASH_OR_LENGTH_MISMATCH, controller.state.failures.single().code)
    }

    @Test
    fun cleanupFailureCannotPreventTerminalStatePersistence() {
        val store = FakeStore()
        val controller = controller(store, H2SurfaceVisibilityMode.NEITHER)
        completeB01(controller)
        controller.beginTake(H2ConfirmationContract.blocks[1].id, ORIGIN)
        store.failTerminate = true
        assertEquals(H2FailureDecision.TERMINATED, controller.cameraLost())
        assertEquals(H2RunPhase.TERMINATED, controller.state.phase)
        assertEquals(H2FailureCode.CAMERA_LOSS, controller.state.terminalCode)
    }

    private fun controller(
        store: FakeStore,
        visibility: H2SurfaceVisibilityMode,
        modelAvailable: Boolean = true,
        primaryRenderer: H2ControllerRenderer = defaultRenderer(H2CandidateRole.PRIMARY),
    ): H2SessionController {
        val provider = H2StrictC3bModelProvider(
            EXPECTED,
            H2ModelByteLoader { if (modelAvailable) model().encodeToByteArray() else null },
            H2Sha256 { EXPECTED },
        )
        return H2SessionController(
            RUN_ID,
            H2V2TakeRuntime(H2C3bCandidate(provider), H2C2C1Candidate()),
            store,
            visibility,
            primaryRenderer,
            defaultRenderer(H2CandidateRole.COMPARATOR),
        )
    }

    private fun H2SessionController.beginReadiness(
        originNanos: Long,
        primary: H2ProviderRouteAvailability,
        comparator: H2ProviderRouteAvailability,
    ) = beginReadiness(mapping(originNanos), primary, comparator)

    private fun H2SessionController.beginTake(blockId: String, originNanos: Long) =
        beginTake(blockId, mapping(originNanos))

    private fun H2SessionController.presentCue(
        name: H2CueSegmentName,
        elapsedMillis: Long,
        monotonicNanos: Long,
    ) {
        routeCue(name, elapsedMillis, monotonicNanos)
        acknowledgeCuePresentation(name, monotonicNanos)
    }

    private fun mapping(originNanos: Long): H2ClockMapping = H2ClockMapping(
        H2ClockMapping.MONOTONIC_CLOCK_ID,
        originNanos,
        originNanos,
        originNanos,
        originNanos,
        maxMappingUncertaintyNanos = 0,
    )

    private fun completeB01(controller: H2SessionController) {
        controller.beginReadiness(
            0,
            H2ProviderRouteAvailability.AVAILABLE,
            H2ProviderRouteAvailability.AVAILABLE,
        )
        controller.presentCue(H2CueSegmentName.READINESS_AVAILABILITY, 0, 0)
        observeB01UntilEndpoint(controller)
        assertEquals(
            H2ReadinessPhase.READY,
            controller.observeReadiness(
                15_000L * 1_000_000L,
                H2ProviderRouteAvailability.AVAILABLE,
                H2ProviderRouteAvailability.AVAILABLE,
            ),
        )
    }

    private fun observeB01UntilEndpoint(controller: H2SessionController) {
        for (millis in 250L until 15_000L step 250L) {
            assertEquals(
                H2ReadinessPhase.WAITING,
                controller.observeReadiness(
                    millis * 1_000_000L,
                    H2ProviderRouteAvailability.AVAILABLE,
                    H2ProviderRouteAvailability.AVAILABLE,
                ),
            )
        }
    }

    private fun calibrate(
        controller: H2SessionController,
        origin: Long = ORIGIN,
        startFrameIndex: Long = 0,
    ) {
        val segments = H2CueContract.segmentsFor(checkNotNull(controller.state.activeBlockId))
        controller.presentCue(segments[0].name, segments[0].startMillis, origin)
        var frameIndex = startFrameIndex
        (250L..1_250L step 50L).forEach { elapsed ->
            val retained = controller.onJoinedResult(joined(frameIndex++, elapsed, origin))
            if (retained.participantPresentedAvatar != null) {
                controller.acknowledgeParticipantPresentation(retained.frameIndex, retained.sourceMonotonicNanos)
            }
        }
        (1_501L..2_201L step 50L).forEach { elapsed ->
            controller.onJoinedResult(joined(frameIndex++, elapsed, origin))
        }
        controller.presentCue(segments[1].name, segments[1].startMillis, origin + 3_000_000_000L)
        controller.onJoinedResult(joined(frameIndex, 3_000, origin))
    }

    private fun observeThroughEnd(
        controller: H2SessionController,
        startFrameIndex: Long,
        startElapsed: Long,
        endElapsed: Long,
        origin: Long = ORIGIN,
    ) {
        val remainingCues = H2CueContract.segmentsFor(checkNotNull(controller.state.activeBlockId))
            .drop(2)
            .iterator()
        var nextCue = if (remainingCues.hasNext()) remainingCues.next() else null
        var frameIndex = startFrameIndex
        var elapsed = startElapsed
        while (elapsed < endElapsed) {
            while (nextCue != null && nextCue.startMillis <= elapsed) {
                controller.presentCue(
                    nextCue.name,
                    nextCue.startMillis,
                    origin + nextCue.startMillis * 1_000_000L,
                )
                nextCue = if (remainingCues.hasNext()) remainingCues.next() else null
            }
            val retained = controller.onJoinedResult(joined(frameIndex++, elapsed, origin))
            if (retained.participantPresentedAvatar != null) {
                controller.acknowledgeParticipantPresentation(retained.frameIndex, retained.sourceMonotonicNanos)
            }
            elapsed += 250
        }
        while (nextCue != null && nextCue.startMillis <= endElapsed) {
            controller.presentCue(
                nextCue.name,
                nextCue.startMillis,
                origin + nextCue.startMillis * 1_000_000L,
            )
            nextCue = if (remainingCues.hasNext()) remainingCues.next() else null
        }
        if (elapsed != endElapsed) {
            val retained = controller.onJoinedResult(joined(frameIndex, endElapsed, origin))
            if (retained.participantPresentedAvatar != null) {
                controller.acknowledgeParticipantPresentation(retained.frameIndex, retained.sourceMonotonicNanos)
            }
        } else {
            val retained = controller.onJoinedResult(joined(frameIndex, elapsed, origin))
            if (retained.participantPresentedAvatar != null) {
                controller.acknowledgeParticipantPresentation(retained.frameIndex, retained.sourceMonotonicNanos)
            }
        }
    }

    private fun joined(frameIndex: Long, elapsed: Long, origin: Long = ORIGIN) = H2JoinedCanonicalResult(
        frameIndex,
        elapsed,
        origin + elapsed * 1_000_000,
        image(frameIndex.toInt()),
        neutral(),
    )

    private fun image(index: Int): H2RgbaLandmarkFrame {
        val rgba = ByteArray(240 * 320 * 4)
        for (pixel in 0 until 240 * 320) {
            rgba[pixel * 4] = ((pixel % 240 + index) % 256).toByte()
            rgba[pixel * 4 + 1] = ((pixel / 240 + index * 3) % 256).toByte()
            rgba[pixel * 4 + 2] = ((pixel + index * 7) % 256).toByte()
            rgba[pixel * 4 + 3] = 0xff.toByte()
        }
        val points = face().toMutableList()
        points[33] = point(40f, 44f)
        points[133] = points[33]
        points[362] = point(88f, 44f)
        points[263] = points[362]
        points[1] = point(64f, 72f)
        return H2RgbaLandmarkFrame(
            rgba,
            240,
            320,
            points,
            H2FrameOrientation.FRONT_CAMERA_MIRRORED_RGBA_AND_LANDMARKS,
        )
    }

    private fun face(): List<FaceLandmark> = MutableList(478) { point(120f, 120f) }.also { points ->
        points[10] = point(120f, 30f)
        points[152] = point(120f, 280f)
        points[168] = point(120f, 70f)
        points[6] = point(120f, 100f)
        points[197] = point(115f, 115f)
        points[195] = point(120f, 125f)
        points[61] = point(95f, 190f)
        points[291] = point(145f, 190f)
        points[13] = point(120f, 187f)
        points[14] = point(120f, 193f)
        FACE_OVAL.forEachIndexed { position, landmark ->
            if (landmark != 10 && landmark != 152) {
                val fraction = position.toFloat() / (FACE_OVAL.size - 1)
                val side = if (position < FACE_OVAL.size / 2) 1f else -1f
                points[landmark] = point(120f + side * 55f, 30f + 250f * fraction)
            }
        }
    }

    private fun point(x: Float, y: Float) = FaceLandmark(x / 240f, y / 320f, 0f)

    private fun neutral() = mapOf(
        BlendShape.MOUTH_ROLL_LOWER to 0f,
        BlendShape.MOUTH_ROLL_UPPER to 0f,
        BlendShape.MOUTH_LEFT to 0f,
        BlendShape.MOUTH_RIGHT to 0f,
        BlendShape.MOUTH_FUNNEL to 0f,
        BlendShape.JAW_OPEN to 0f,
        BlendShape.MOUTH_SMILE_LEFT to 0f,
        BlendShape.MOUTH_SMILE_RIGHT to 0f,
    )

    private fun model(): String {
        fun vector(size: Int, value: String) = List(size) { value }.joinToString(prefix = "[", postfix = "]")
        val components = List(16) { vector(384, "0") }.joinToString(prefix = "[", postfix = "]")
        return "{\"schema_version\":1,\"recipe\":\"DK_SESSION_B_LEARNED_CHEEK_PATCH_V1\",\"C\":1," +
            "\"scaler_mean\":${vector(384, "0")},\"scaler_scale\":${vector(384, "1")}," +
            "\"pca_mean\":${vector(384, "0")},\"pca_components\":$components," +
            "\"pca_explained_variance\":${vector(16, "1")}," +
            "\"classifier_intercept\":[0],\"classifier_coef\":[${vector(16, "0")}] }"
    }

    private fun defaultRenderer(role: H2CandidateRole): H2ControllerRenderer {
        val renderer = H2BlindAvatarRenderer(role)
        return H2ControllerRenderer(renderer::render)
    }

    private class FakeStore : H2ControllerStore {
        val published = mutableListOf<H2ControllerPublication>()
        val retained = mutableListOf<H2RetainedControllerFrame>()
        val events = mutableListOf<H2ControllerEvent>()
        var terminated: H2FailureCode? = null
        var failBeginCode: H2FailureCode? = null
        var failAppendCode: H2FailureCode? = null
        var failPublishCode: H2FailureCode? = null
        var failTerminate = false
        var failTerminalEvent = false
        val beginAttempts = mutableListOf<Int>()
        private var blockId: String? = null
        private var attempt: Int? = null

        override fun beginBlock(blockId: String, attempt: Int) {
            beginAttempts += attempt
            failBeginCode?.let { throw H2ControllerStorageFailure(it, IllegalStateException("fault")) }
            this.blockId = blockId
            this.attempt = attempt
        }

        override fun append(frame: H2RetainedControllerFrame) {
            failAppendCode?.let { throw H2ControllerStorageFailure(it, IllegalStateException("fault")) }
            retained += frame
            events += frame.controllerEvents
        }

        override fun appendEvent(event: H2ControllerEvent) {
            if (failTerminalEvent && event.kind == H2ControllerEventKind.TERMINAL_FAILURE) {
                throw H2ControllerStorageFailure(
                    H2FailureCode.UNPUBLISHED_EVENT_LOG_WRITE_FAILURE,
                    IllegalStateException("terminal event fault"),
                )
            }
            events += event
        }

        override fun publishBlock(): H2ControllerPublication = H2ControllerPublication(
            checkNotNull(blockId),
            checkNotNull(attempt),
            RUN_ID,
        ).also {
            failPublishCode?.let { code ->
                throw H2ControllerStorageFailure(code, IllegalStateException("fault"))
            }
            published += it
            blockId = null
            attempt = null
        }

        override fun terminateBlock(code: H2FailureCode) {
            if (failTerminate) throw IllegalStateException("terminate fault")
            terminated = code
            blockId = null
            attempt = null
        }
    }

    companion object {
        private const val EXPECTED = "0000000000000000000000000000000000000000000000000000000000000000"
        private const val RUN_ID = "1111111111111111111111111111111111111111111111111111111111111111"
        private const val ORIGIN = 20_000_000_000L
        private const val NEXT_ORIGIN = 90_000_000_000L
        private val FACE_OVAL = intArrayOf(
            10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365,
            379, 378, 400, 377, 152, 148, 176, 149, 150, 136, 172, 58, 132, 93,
            234, 127, 162, 21, 54, 103, 67, 109,
        )
    }
}
