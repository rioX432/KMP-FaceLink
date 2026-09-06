@file:Suppress("LargeClass", "LongParameterList", "TooManyFunctions", "ThrowsCount")

package io.github.kmpfacelink.capture

import io.github.kmpfacelink.model.BlendShapeData

/** One joined result: image, landmarks, estimator values, frame identity, and one monotonic clock. */
internal data class H2JoinedCanonicalResult(
    val frameIndex: Long,
    val takeElapsedMillis: Long,
    val monotonicNanos: Long,
    val image: H2RgbaLandmarkFrame,
    val estimator: BlendShapeData,
) {
    init {
        require(frameIndex >= 0)
        require(takeElapsedMillis >= 0)
        require(monotonicNanos >= 0)
    }
}

internal data class H2RetainedControllerFrame(
    val frameIndex: Long,
    val takeElapsedMillis: Long,
    val sourceMonotonicNanos: Long,
    val cameraRgba: ByteArray,
    val participantActionRgba: ByteArray,
    val primaryAvatar: H2RenderedAvatarFrame?,
    val comparatorAvatar: H2RenderedAvatarFrame?,
    val participantPresentedAvatar: H2RenderedAvatarFrame?,
    val visibleEvents: List<H2VisibleOutputEvent>,
    val controllerEvents: List<H2ControllerEvent>,
) {
    init {
        require(frameIndex >= 0)
        require(takeElapsedMillis >= 0)
        require(sourceMonotonicNanos >= 0)
        require(visibleEvents.size <= 2)
        require(primaryAvatar == null || primaryAvatar.role == H2CandidateRole.PRIMARY)
        require(comparatorAvatar == null || comparatorAvatar.role == H2CandidateRole.COMPARATOR)
        require(participantPresentedAvatar == null || participantPresentedAvatar === primaryAvatar)
    }
}

internal enum class H2ControllerEventKind {
    READINESS_OBSERVATION,
    BLOCK_SCHEDULE_OPENED,
    CLOCK_MAPPING,
    FRAME_JOINED,
    CALIBRATION_ONLY,
    CANDIDATE_MISSING_OR_NOT_READY,
    ROUTE_AVAILABLE,
    PARTICIPANT_ROUTE_SELECTED,
    COMPOSE_DRAW_ACKNOWLEDGED,
    CUE_ROUTE_ACTIVATED,
    CUE_PRESENTATION_ACKNOWLEDGED,
    TERMINAL_FAILURE,
}

/** Score-free event envelope binding readiness, route, render, cue, frame, and clock evidence. */
internal data class H2ControllerEvent(
    val sequence: Long,
    val blockId: String,
    val kind: H2ControllerEventKind,
    val monotonicNanos: Long,
    val runIdentitySha256: String,
    val frameIndex: Long? = null,
    val takeElapsedMillis: Long? = null,
    val primaryAvailability: H2ProviderRouteAvailability? = null,
    val comparatorAvailability: H2ProviderRouteAvailability? = null,
    val participantVisibility: H2SurfaceVisibilityMode? = null,
    val cueSegment: H2CueSegmentName? = null,
    val clockMapping: H2ClockMapping? = null,
    val failureCode: H2FailureCode? = null,
) {
    init {
        require(sequence >= 0 && monotonicNanos >= 0)
        require(runIdentitySha256.matches(Regex("[0-9a-f]{64}")))
        require(blockId in H2ConfirmationContract.blocks.map(H2ConfirmationBlock::id))
        require(
            frameIndex != null || takeElapsedMillis == null ||
                kind == H2ControllerEventKind.CUE_ROUTE_ACTIVATED ||
                kind == H2ControllerEventKind.CUE_PRESENTATION_ACKNOWLEDGED,
        )
        require(frameIndex == null || frameIndex >= 0)
        require(takeElapsedMillis == null || takeElapsedMillis >= 0)
        require((kind == H2ControllerEventKind.TERMINAL_FAILURE) == (failureCode != null))
        if (kind == H2ControllerEventKind.READINESS_OBSERVATION) {
            require(primaryAvailability != null && comparatorAvailability != null)
        }
        val isCueEvent = kind == H2ControllerEventKind.CUE_ROUTE_ACTIVATED ||
            kind == H2ControllerEventKind.CUE_PRESENTATION_ACKNOWLEDGED
        require(isCueEvent == (cueSegment != null))
        require((kind == H2ControllerEventKind.CLOCK_MAPPING) == (clockMapping != null))
    }
}

private data class H2PendingPresentation(
    val frameIndex: Long,
    val sourceTakeElapsedMillis: Long,
)

internal data class H2ControllerPublication(
    val blockId: String,
    val attempt: Int,
    val streamManifestSha256: String,
)

internal interface H2ControllerStore {
    fun beginBlock(blockId: String, attempt: Int)
    fun appendEvent(event: H2ControllerEvent)
    fun append(frame: H2RetainedControllerFrame)
    fun publishBlock(): H2ControllerPublication
    fun terminateBlock(code: H2FailureCode)
}

internal class H2ControllerStorageFailure(
    val failureCode: H2FailureCode,
    cause: Throwable,
) : IllegalStateException("H2 controller storage failure: $failureCode", cause)

internal interface H2ControllerRunJournal {
    fun openOrRecover(runIdentitySha256: String): H2RunState
    fun persist(state: H2RunState)
    fun publishTerminalSeal(state: H2RunState, seal: H2PublicSafeSeal): H2RunState
}

private object H2EphemeralRunJournal : H2ControllerRunJournal {
    override fun openOrRecover(runIdentitySha256: String): H2RunState =
        openPreparedH2Run(H2RunState(runIdentitySha256), true, true)

    override fun persist(state: H2RunState) = Unit

    override fun publishTerminalSeal(state: H2RunState, seal: H2PublicSafeSeal): H2RunState =
        sealH2Run(state, seal)
}

internal fun interface H2ControllerRenderer {
    fun render(
        route: H2AvatarRouteState,
        clock: H2ClockMapping,
        eventSequence: Long,
        blockId: String,
        monotonicNanos: Long,
    ): H2RenderedAvatarFrame?
}

/**
 * Default-off H2 run controller. It never owns a camera and can only accept already joined,
 * canonical mirrored results from the Android callback seam. Both candidate surfaces are always
 * retained together when available; participant visibility can expose primary-only or neither.
 */
internal class H2SessionController(
    private val runIdentitySha256: String,
    private val runtime: H2V2TakeRuntime,
    private val store: H2ControllerStore,
    private val visibility: H2SurfaceVisibilityMode,
    private val primaryRenderer: H2ControllerRenderer = renderer(H2CandidateRole.PRIMARY),
    private val comparatorRenderer: H2ControllerRenderer = renderer(H2CandidateRole.COMPARATOR),
    private val journal: H2ControllerRunJournal = H2EphemeralRunJournal,
) {
    private var runState = journal.openOrRecover(runIdentitySha256)
    private var readiness: H2ReadinessState? = null
    private var epoch: H2TakeEpoch? = null
    private var cueTracker: H2CueTracker? = null
    private var blockOriginNanos: Long? = null
    private var blockClockMapping: H2ClockMapping? = null
    private var lastFrameIndex: Long? = null
    private var lastElapsedMillis: Long? = null
    private var primaryEventCount = 0
    private var comparatorEventCount = 0
    private var unavailableAfterCalibration = false
    private var lastObservableElapsedMillis: Long? = null
    private var observationIncomplete = false
    private var pendingPresentation: H2PendingPresentation? = null
    private var presentationAckIncomplete = false
    private var lastPresentationAckSourceElapsedMillis: Long? = null
    private var nextEventSequence = 0L

    val state: H2RunState
        get() = runState

    fun beginReadiness(
        clockMapping: H2ClockMapping,
        primary: H2ProviderRouteAvailability,
        comparator: H2ProviderRouteAvailability,
    ) {
        require(epoch == null && readiness == null)
        require(visibility != H2SurfaceVisibilityMode.UNDECIDED) {
            "H2 participant visibility choice is unresolved"
        }
        val launchMonotonicNanos = clockMapping.cueOriginNanos
        nextEventSequence = 0
        blockClockMapping = clockMapping
        runState = beginH2Block(
            runState,
            H2ConfirmationContract.blocks.first().id,
            providerPolicyRouteRenderAvailable = false,
        )
        journal.persist(runState)
        beginStoreBlockOrFail()
        readiness = observeH2Readiness(
            H2ReadinessState(launchMonotonicNanos, launchMonotonicNanos),
            launchMonotonicNanos,
            primary,
            comparator,
        )
        cueTracker = H2CueTracker(H2ConfirmationContract.blocks.first().id, launchMonotonicNanos)
        appendStoreEvent(
            controllerEvent(
                H2ConfirmationContract.blocks.first().id,
                H2ControllerEventKind.BLOCK_SCHEDULE_OPENED,
                clockMapping.cameraOriginNanos,
                frameIndex = null,
                takeElapsedMillis = null,
                participantVisibility = visibility,
            ),
        )
        appendStoreEvent(
            controllerEvent(
                H2ConfirmationContract.blocks.first().id,
                H2ControllerEventKind.CLOCK_MAPPING,
                clockMapping.cameraOriginNanos,
                frameIndex = null,
                takeElapsedMillis = null,
                clockMapping = clockMapping,
            ),
        )
        appendReadinessEvidence(checkNotNull(readiness))
    }

    fun observeReadiness(
        monotonicNanos: Long,
        primary: H2ProviderRouteAvailability,
        comparator: H2ProviderRouteAvailability,
    ): H2ReadinessPhase {
        val updated = observeH2Readiness(requireNotNull(readiness), monotonicNanos, primary, comparator)
        readiness = updated
        appendReadinessEvidence(updated)
        if (updated.phase == H2ReadinessPhase.READY) completeReadiness()
        if (updated.phase == H2ReadinessPhase.TIMED_OUT) terminate(H2FailureCode.READINESS_TIMEOUT)
        return updated.phase
    }

    fun beginTake(blockId: String, clockMapping: H2ClockMapping) {
        require(readiness == null && epoch == null)
        require(visibility != H2SurfaceVisibilityMode.UNDECIDED) {
            "H2 participant visibility choice is unresolved"
        }
        runState = beginH2Block(runState, blockId, providerPolicyRouteRenderAvailable = true)
        journal.persist(runState)
        epoch = runtime.begin(blockId)
        nextEventSequence = 0
        blockClockMapping = clockMapping
        val attemptOriginNanos = clockMapping.cameraOriginNanos
        cueTracker = H2CueTracker(blockId, clockMapping.cueOriginNanos)
        blockOriginNanos = attemptOriginNanos
        lastFrameIndex = null
        lastElapsedMillis = null
        primaryEventCount = 0
        comparatorEventCount = 0
        unavailableAfterCalibration = false
        lastObservableElapsedMillis = null
        observationIncomplete = false
        presentationAckIncomplete = false
        lastPresentationAckSourceElapsedMillis = null
        beginStoreBlockOrFail()
        appendStoreEvent(
            controllerEvent(
                blockId,
                H2ControllerEventKind.BLOCK_SCHEDULE_OPENED,
                attemptOriginNanos,
                frameIndex = null,
                takeElapsedMillis = null,
                participantVisibility = visibility,
            ),
        )
        appendStoreEvent(
            controllerEvent(
                blockId,
                H2ControllerEventKind.CLOCK_MAPPING,
                attemptOriginNanos,
                frameIndex = null,
                takeElapsedMillis = null,
                clockMapping = clockMapping,
            ),
        )
    }

    fun routeCue(
        name: H2CueSegmentName,
        takeElapsedMillis: Long,
        monotonicNanos: Long,
    ): H2CuePresentation {
        val presentation = requireNotNull(cueTracker).route(name, takeElapsedMillis, monotonicNanos)
        appendStoreEvent(
            controllerEvent(
                presentation.blockId,
                H2ControllerEventKind.CUE_ROUTE_ACTIVATED,
                monotonicNanos,
                frameIndex = null,
                takeElapsedMillis = takeElapsedMillis,
                cueSegment = name,
            ),
        )
        return presentation
    }

    fun acknowledgeCuePresentation(
        name: H2CueSegmentName,
        drawnMonotonicNanos: Long,
    ): H2CuePresentationAcknowledgement {
        val acknowledgement = requireNotNull(cueTracker).acknowledge(name, drawnMonotonicNanos)
        val origin = requireNotNull(blockClockMapping).cueOriginNanos
        val elapsedMillis = (drawnMonotonicNanos - origin) / NANOS_PER_MILLI
        appendStoreEvent(
            controllerEvent(
                acknowledgement.blockId,
                H2ControllerEventKind.CUE_PRESENTATION_ACKNOWLEDGED,
                drawnMonotonicNanos,
                frameIndex = null,
                takeElapsedMillis = elapsedMillis,
                cueSegment = name,
            ),
        )
        return acknowledgement
    }

    fun onJoinedResult(result: H2JoinedCanonicalResult): H2RetainedControllerFrame {
        val activeEpoch = requireNotNull(epoch) { "no H2 take is active" }
        val origin = requireNotNull(blockOriginNanos)
        validateJoinedIdentityAndClock(result, origin)
        lastFrameIndex = result.frameIndex
        lastElapsedMillis = result.takeElapsedMillis

        val route = observeRuntime(activeEpoch, result)
        if (result.takeElapsedMillis > CALIBRATION_END_MILLIS) {
            trackObservableCoverage(result.takeElapsedMillis, route?.endpointAvailable == true)
        }
        val retained = if (route == null) {
            H2RetainedControllerFrame(
                result.frameIndex,
                result.takeElapsedMillis,
                result.monotonicNanos,
                result.image.rgba,
                result.image.rgba,
                null,
                null,
                null,
                emptyList(),
                listOf(
                    frameEvent(result, H2ControllerEventKind.FRAME_JOINED),
                    frameEvent(result, H2ControllerEventKind.CALIBRATION_ONLY),
                ),
            )
        } else {
            retainRoute(result, route)
        }
        try {
            store.append(retained)
        } catch (failure: H2ControllerStorageFailure) {
            failStore(failure)
        }
        return retained
    }

    private fun validateJoinedIdentityAndClock(result: H2JoinedCanonicalResult, origin: Long) {
        val blockEndMillis = H2ConfirmationContract.blocks[runState.nextBlockIndex]
            .totalDurationSeconds * MILLIS_PER_SECOND
        if (result.takeElapsedMillis > blockEndMillis) {
            terminate(H2FailureCode.OBSERVATION_INCOMPLETE)
            throw IllegalArgumentException("joined H2 frame is after the exact block endpoint")
        }
        if (
            result.monotonicNanos < origin ||
            result.monotonicNanos - origin != result.takeElapsedMillis * NANOS_PER_MILLI
        ) {
            terminate(H2FailureCode.CLOCK_DISCONTINUITY)
            throw IllegalArgumentException("joined H2 frame clock does not match take-relative time")
        }
        if (lastFrameIndex != null && result.frameIndex <= checkNotNull(lastFrameIndex)) {
            terminate(H2FailureCode.CLOCK_DISCONTINUITY)
            throw IllegalArgumentException("joined H2 frame identity is duplicate or reordered")
        }
    }

    private fun observeRuntime(
        activeEpoch: H2TakeEpoch,
        result: H2JoinedCanonicalResult,
    ): H2TakeRouteObservation? = try {
        runtime.observe(
            activeEpoch,
            result.takeElapsedMillis,
            result.image,
            result.estimator,
        )
    } catch (cause: Exception) {
        terminate(H2FailureCode.RUNTIME_OR_DELEGATE_FALLBACK)
        throw IllegalStateException("H2 candidate runtime failed", cause)
    }

    fun completeTake(): H2ControllerPublication {
        val activeEpoch = requireNotNull(epoch)
        val block = H2ConfirmationContract.blocks[runState.nextBlockIndex]
        val elapsed = requireNotNull(lastElapsedMillis)
        val expectedEndMillis = block.totalDurationSeconds * MILLIS_PER_SECOND
        val observedEnd = lastObservableElapsedMillis
        if (cueTracker?.complete != true) {
            terminate(H2FailureCode.INSTRUCTION_DEVIATION)
            error("H2 cue evidence is incomplete")
        }
        val primaryPresentationIncomplete = pendingPresentation != null ||
            presentationAckIncomplete ||
            lastPresentationAckSourceElapsedMillis != expectedEndMillis
        if (visibility == H2SurfaceVisibilityMode.PRIMARY_ONLY && primaryPresentationIncomplete) {
            terminate(H2FailureCode.RENDER_LOSS)
            error("H2 participant presentation acknowledgements are incomplete")
        }
        if (!hasCompleteObservableCoverage(elapsed, observedEnd, expectedEndMillis)) {
            terminate(H2FailureCode.OBSERVATION_INCOMPLETE)
            error("H2 take does not contain continuous available post-calibration observation")
        }
        val publication = publishStoreBlockOrFail()
        val receipt = H2BlockReceipt(
            blockId = requireNotNull(runState.activeBlockId),
            captureAttempt = requireNotNull(runState.activeAttempt),
            observableSeconds = ((expectedEndMillis - CALIBRATION_END_MILLIS) / MILLIS_PER_SECOND).toInt(),
            primaryVisibleEventCount = primaryEventCount,
            comparatorVisibleEventCount = comparatorEventCount,
            retainedStreamManifestSha256 = publication.streamManifestSha256,
            publishedAtomically = true,
        )
        runtime.close(activeEpoch)
        runState = completeH2Block(runState, receipt)
        journal.persist(runState)
        clearActiveTake()
        return publication
    }

    fun cameraLost(): H2FailureDecision = terminate(H2FailureCode.CAMERA_LOSS)

    fun renderLost(): H2FailureDecision = terminate(H2FailureCode.RENDER_LOSS)

    fun terminate(code: H2FailureCode): H2FailureDecision {
        epoch?.let(runtime::close)
        runState.activeBlockId?.let { blockId ->
            runCatching {
                store.appendEvent(
                    controllerEvent(
                        blockId,
                        H2ControllerEventKind.TERMINAL_FAILURE,
                        lastElapsedMillis?.let { requireNotNull(blockOriginNanos) + it * NANOS_PER_MILLI }
                            ?: requireNotNull(readiness).lastMonotonicNanos,
                        frameIndex = lastFrameIndex,
                        takeElapsedMillis = lastElapsedMillis,
                        failureCode = code,
                    ),
                )
            }
        }
        val failed = failH2Block(runState, requireNotNull(runState.activeBlockId), code)
        runState = failed.first
        journal.persist(runState)
        runCatching { store.terminateBlock(code) }
        readiness = null
        clearActiveTake()
        return failed.second
    }

    private fun completeReadiness() {
        if (cueTracker?.complete != true) {
            terminate(H2FailureCode.INSTRUCTION_DEVIATION)
            error("H2 readiness cue evidence is incomplete")
        }
        val blockId = requireNotNull(runState.activeBlockId)
        val publication = publishStoreBlockOrFail()
        runState = completeH2Block(
            runState,
            H2BlockReceipt(
                blockId,
                requireNotNull(runState.activeAttempt),
                H2ConfirmationContract.STARTUP_TOTAL_SECONDS,
                0,
                0,
                publication.streamManifestSha256,
                true,
            ),
        )
        journal.persist(runState)
        readiness = null
        cueTracker = null
        primaryEventCount = 0
        comparatorEventCount = 0
    }

    fun publishTerminalSeal(seal: H2PublicSafeSeal): H2RunState {
        require(readiness == null && epoch == null)
        runState = journal.publishTerminalSeal(runState, seal)
        return runState
    }

    private fun retainRoute(
        result: H2JoinedCanonicalResult,
        route: H2TakeRouteObservation,
    ): H2RetainedControllerFrame {
        val clock = checkNotNull(blockClockMapping)
        if (!route.endpointAvailable) {
            return retainUnavailable(result, route)
        }
        return retainAvailable(result, route, clock)
    }

    private fun retainUnavailable(
        result: H2JoinedCanonicalResult,
        route: H2TakeRouteObservation,
    ): H2RetainedControllerFrame {
        unavailableAfterCalibration = true
        val controllerEvents = listOf(
            frameEvent(result, H2ControllerEventKind.FRAME_JOINED),
            frameEvent(result, H2ControllerEventKind.CANDIDATE_MISSING_OR_NOT_READY),
        )
        val events = unavailableEvents(
            route.epoch.blockId,
            checkNotNull(blockClockMapping).visibleOutputOriginNanos +
                result.takeElapsedMillis * NANOS_PER_MILLI,
        )
        primaryEventCount++
        comparatorEventCount++
        return H2RetainedControllerFrame(
            result.frameIndex,
            result.takeElapsedMillis,
            result.monotonicNanos,
            result.image.rgba,
            result.image.rgba,
            null,
            null,
            null,
            events,
            controllerEvents,
        )
    }

    private fun retainAvailable(
        result: H2JoinedCanonicalResult,
        route: H2TakeRouteObservation,
        clock: H2ClockMapping,
    ): H2RetainedControllerFrame {
        val joinedEvent = frameEvent(result, H2ControllerEventKind.FRAME_JOINED)
        val routeEvent = frameEvent(result, H2ControllerEventKind.ROUTE_AVAILABLE)
        val primary = primaryRenderer.render(
            route.primary.toAvatarRoute(),
            clock,
            nextEventSequence++,
            route.epoch.blockId,
            clock.visibleOutputOriginNanos + result.takeElapsedMillis * NANOS_PER_MILLI,
        ) ?: return failRender(result)
        val comparator = comparatorRenderer.render(
            route.comparator.toAvatarRoute(),
            clock,
            nextEventSequence++,
            route.epoch.blockId,
            clock.visibleOutputOriginNanos + result.takeElapsedMillis * NANOS_PER_MILLI,
        ) ?: return failRender(result)
        primaryEventCount++
        comparatorEventCount++
        val participant = when (visibility) {
            H2SurfaceVisibilityMode.PRIMARY_ONLY -> primary
            H2SurfaceVisibilityMode.NEITHER -> null
            H2SurfaceVisibilityMode.UNDECIDED -> error("unresolved visibility passed beginTake")
        }
        val participantRouteEvent = frameEvent(
            result,
            H2ControllerEventKind.PARTICIPANT_ROUTE_SELECTED,
            participantVisibility = visibility,
        )
        return H2RetainedControllerFrame(
            result.frameIndex,
            result.takeElapsedMillis,
            result.monotonicNanos,
            result.image.rgba,
            result.image.rgba,
            primary,
            comparator,
            participant,
            listOf(primary.event, comparator.event),
            listOf(joinedEvent, routeEvent, participantRouteEvent),
        ).also {
            participant?.let {
                if (pendingPresentation != null) presentationAckIncomplete = true
                pendingPresentation = H2PendingPresentation(result.frameIndex, result.takeElapsedMillis)
            }
        }
    }

    fun acknowledgeParticipantPresentation(frameIndex: Long, drawnMonotonicNanos: Long): Boolean {
        val pending = pendingPresentation ?: return false
        if (pending.frameIndex != frameIndex) return false
        val visibleOrigin = requireNotNull(blockClockMapping).visibleOutputOriginNanos
        require(drawnMonotonicNanos >= visibleOrigin + pending.sourceTakeElapsedMillis * NANOS_PER_MILLI)
        val blockEndMillis = H2ConfirmationContract.blocks[runState.nextBlockIndex]
            .totalDurationSeconds * MILLIS_PER_SECOND
        require(pending.sourceTakeElapsedMillis <= blockEndMillis)
        require(drawnMonotonicNanos <= visibleOrigin + blockEndMillis * NANOS_PER_MILLI) {
            "H2 Compose draw acknowledgement is after the exact block endpoint"
        }
        appendStoreEvent(
            controllerEvent(
                requireNotNull(runState.activeBlockId),
                H2ControllerEventKind.COMPOSE_DRAW_ACKNOWLEDGED,
                drawnMonotonicNanos,
                frameIndex,
                pending.sourceTakeElapsedMillis,
                participantVisibility = H2SurfaceVisibilityMode.PRIMARY_ONLY,
            ),
        )
        lastPresentationAckSourceElapsedMillis = pending.sourceTakeElapsedMillis
        pendingPresentation = null
        return true
    }

    private fun hasCompleteObservableCoverage(
        lastElapsed: Long,
        observedEnd: Long?,
        expectedEnd: Long,
    ): Boolean {
        if (lastElapsed != expectedEnd || observedEnd == null || observedEnd != expectedEnd) return false
        if (unavailableAfterCalibration || observationIncomplete) return false
        return true
    }

    private fun appendReadinessEvidence(state: H2ReadinessState) {
        try {
            store.appendEvent(
                controllerEvent(
                    H2ConfirmationContract.blocks.first().id,
                    H2ControllerEventKind.READINESS_OBSERVATION,
                    state.lastMonotonicNanos,
                    frameIndex = null,
                    takeElapsedMillis = null,
                    primaryAvailability = state.primary,
                    comparatorAvailability = state.comparator,
                ),
            )
        } catch (failure: H2ControllerStorageFailure) {
            failStore(failure)
        }
    }

    private fun beginStoreBlockOrFail() {
        try {
            store.beginBlock(requireNotNull(runState.activeBlockId), requireNotNull(runState.activeAttempt))
        } catch (failure: H2ControllerStorageFailure) {
            failStore(failure)
        }
    }

    private fun appendStoreEvent(event: H2ControllerEvent) {
        try {
            store.appendEvent(event)
        } catch (failure: H2ControllerStorageFailure) {
            failStore(failure)
        }
    }

    private fun publishStoreBlockOrFail(): H2ControllerPublication = try {
        store.publishBlock()
    } catch (failure: H2ControllerStorageFailure) {
        failStore(failure)
    }

    private fun failStore(failure: H2ControllerStorageFailure): Nothing {
        val terminalEventFailure = appendTerminalFailureIfRequired(failure.failureCode)
        val codeBeforeCleanup = if (terminalEventFailure == null) {
            failure.failureCode
        } else {
            H2FailureCode.POST_PUBLICATION_MUTATION
        }
        val cleanupFailure = runCatching { store.terminateBlock(codeBeforeCleanup) }.exceptionOrNull()
        val retainedCode = if (cleanupFailure == null) {
            codeBeforeCleanup
        } else {
            H2FailureCode.POST_PUBLICATION_MUTATION
        }
        val failed = failH2Block(runState, requireNotNull(runState.activeBlockId), retainedCode)
        runState = failed.first
        journal.persist(runState)
        readiness = null
        epoch?.let(runtime::close)
        clearActiveTake()
        val secondaryFailure = cleanupFailure ?: terminalEventFailure
        if (secondaryFailure == null) {
            throw failure
        }
        throw H2ControllerStorageFailure(retainedCode, secondaryFailure)
    }

    /**
     * Camera/output failures require a terminal event while the event stream remains writable.
     * A second event-log failure is not silently ignored: the attempt and run become the
     * non-retryable POST_PUBLICATION_MUTATION disposition. Failures whose defining condition
     * already makes an event append unavailable use their closed failure file and manifest.
     */
    private fun appendTerminalFailureIfRequired(code: H2FailureCode): Throwable? {
        if (code != H2FailureCode.UNPUBLISHED_CAMERA_STREAM_WRITE_FAILURE &&
            code != H2FailureCode.UNPUBLISHED_OUTPUT_STREAM_WRITE_FAILURE
        ) {
            return null
        }
        val blockId = runState.activeBlockId ?: return null
        val monotonicNanos = lastElapsedMillis?.let {
            requireNotNull(blockOriginNanos) + it * NANOS_PER_MILLI
        } ?: readiness?.lastMonotonicNanos ?: blockClockMapping?.cameraOriginNanos ?: return null
        return runCatching {
            store.appendEvent(
                controllerEvent(
                    blockId,
                    H2ControllerEventKind.TERMINAL_FAILURE,
                    monotonicNanos,
                    frameIndex = lastFrameIndex,
                    takeElapsedMillis = lastElapsedMillis,
                    failureCode = code,
                ),
            )
        }.exceptionOrNull()
    }

    private fun trackObservableCoverage(elapsedMillis: Long, endpointAvailable: Boolean) {
        val previous = lastObservableElapsedMillis
        val gap = if (previous == null) {
            elapsedMillis - CALIBRATION_END_MILLIS
        } else {
            elapsedMillis - previous
        }
        if (gap !in 1..H2ConfirmationContract.MAX_CONTINUOUS_GAP_MILLIS.toLong()) {
            observationIncomplete = true
        }
        if (!endpointAvailable) {
            unavailableAfterCalibration = true
            observationIncomplete = true
        } else {
            lastObservableElapsedMillis = elapsedMillis
        }
    }

    private fun frameEvent(
        result: H2JoinedCanonicalResult,
        kind: H2ControllerEventKind,
        participantVisibility: H2SurfaceVisibilityMode? = null,
    ): H2ControllerEvent = controllerEvent(
        requireNotNull(runState.activeBlockId),
        kind,
        result.monotonicNanos,
        result.frameIndex,
        result.takeElapsedMillis,
        participantVisibility = participantVisibility,
    )

    private fun controllerEvent(
        blockId: String,
        kind: H2ControllerEventKind,
        monotonicNanos: Long,
        frameIndex: Long?,
        takeElapsedMillis: Long?,
        primaryAvailability: H2ProviderRouteAvailability? = null,
        comparatorAvailability: H2ProviderRouteAvailability? = null,
        participantVisibility: H2SurfaceVisibilityMode? = null,
        cueSegment: H2CueSegmentName? = null,
        clockMapping: H2ClockMapping? = null,
        failureCode: H2FailureCode? = null,
    ): H2ControllerEvent = H2ControllerEvent(
        nextEventSequence++,
        blockId,
        kind,
        monotonicNanos,
        runIdentitySha256,
        frameIndex,
        takeElapsedMillis,
        primaryAvailability,
        comparatorAvailability,
        participantVisibility,
        cueSegment,
        clockMapping,
        failureCode,
    )

    private fun failRender(result: H2JoinedCanonicalResult): Nothing {
        renderLost()
        error("H2 renderer failed at frame ${result.frameIndex}")
    }

    private fun unavailableEvents(blockId: String, monotonicNanos: Long): List<H2VisibleOutputEvent> =
        H2CandidateRole.entries.map { role ->
            H2VisibleOutputEvent(
                nextEventSequence++,
                blockId,
                role,
                if (role == H2CandidateRole.PRIMARY) {
                    H2VisibleSurface.H2_PRIMARY_C3B_AVATAR_RENDER
                } else {
                    H2VisibleSurface.H2_SECONDARY_C2_PLUS_C1_AVATAR_RENDER
                },
                monotonicNanos,
                H2VisibleOutputState.OUTPUT_NOT_VISIBLE,
            )
        }

    private fun H2PolicyRouteState.toAvatarRoute(): H2AvatarRouteState =
        H2AvatarRouteState(active = active, available = available)

    private fun clearActiveTake() {
        epoch = null
        cueTracker = null
        pendingPresentation = null
        presentationAckIncomplete = false
        lastPresentationAckSourceElapsedMillis = null
        blockOriginNanos = null
        blockClockMapping = null
        lastFrameIndex = null
        lastElapsedMillis = null
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        const val MILLIS_PER_SECOND = 1_000L
        const val CALIBRATION_END_MILLIS = 3_000L

        fun renderer(role: H2CandidateRole): H2ControllerRenderer {
            val renderer = H2BlindAvatarRenderer(role)
            return H2ControllerRenderer(renderer::render)
        }
    }
}
