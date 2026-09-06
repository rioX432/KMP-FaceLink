@file:Suppress("LongParameterList", "TooManyFunctions")

package io.github.kmpfacelink.capture

import io.github.kmpfacelink.model.BlendShapeData
import io.github.kmpfacelink.model.FaceLandmark
import java.io.OutputStream

/** Durable controller journal: any unresolved restart becomes terminal and is never resumed. */
internal class AndroidH2ControllerRunJournal(root: java.io.File) : H2ControllerRunJournal {
    private val store = AndroidH2AtomicFixtureRunStore(root)

    override fun openOrRecover(runIdentitySha256: String): H2RunState {
        val state = if (store.hasRunState()) {
            store.loadOrRecover()
        } else {
            store.open(store.prepare(runIdentitySha256))
        }
        require(state.runIdentitySha256 == runIdentitySha256) { "H2 run identity changed across restart" }
        return state
    }

    override fun persist(state: H2RunState) {
        store.persistTransition(state)
    }

    override fun publishTerminalSeal(state: H2RunState, seal: H2PublicSafeSeal): H2RunState =
        store.publishTerminalSeal(state, seal)

    fun readVerifiedSealedRun(runIdentitySha256: String): H2DurableJournalReadback =
        store.readVerifiedSealedRun(runIdentitySha256)
}

/** Public status consumed by the Activity without opening a camera or storage. */
public data class H2AndroidRouteStatus(
    public val physicalExecutionEnabled: Boolean,
    public val modelBundled: Boolean,
    public val humanAndForwardAuthoritiesClosed: Boolean,
) {
    public val executable: Boolean
        get() = physicalExecutionEnabled && modelBundled && humanAndForwardAuthoritiesClosed
}

/** One score-blind output from the Android callback seam. */
public data class H2AndroidJoinedOutput(
    public val frameIndex: Long,
    public val takeElapsedMillis: Long,
    public val sourceMonotonicNanos: Long,
    public val primary: H2RenderedAvatarFrame?,
    public val comparator: H2RenderedAvatarFrame?,
    public val participant: H2RenderedAvatarFrame?,
)

/**
 * Android callback seam for a future authorized H2 camera branch. The current app constructs only
 * [locked], so no camera or storage is opened. A connected instance can be made only inside this
 * module after all separately governed gates have been checked.
 */
public class AndroidH2CallbackRoute internal constructor(
    public val status: H2AndroidRouteStatus,
    private val controller: H2SessionController?,
) {
    init {
        require(status.executable == (controller != null))
    }

    public fun beginReadiness(
        clockMapping: H2ClockMapping,
        primaryAvailable: Boolean,
        comparatorAvailable: Boolean,
    ) {
        connected().beginReadiness(
            clockMapping,
            primaryAvailable.toAvailability(),
            comparatorAvailable.toAvailability(),
        )
    }

    public fun observeReadiness(
        monotonicNanos: Long,
        primaryAvailable: Boolean,
        comparatorAvailable: Boolean,
    ): H2ReadinessPhase = connected().observeReadiness(
        monotonicNanos,
        primaryAvailable.toAvailability(),
        comparatorAvailable.toAvailability(),
    )

    public fun beginTake(blockId: String, clockMapping: H2ClockMapping) {
        connected().beginTake(blockId, clockMapping)
    }

    public fun routeCue(
        name: H2CueSegmentName,
        takeElapsedMillis: Long,
        monotonicNanos: Long,
    ): H2CuePresentation = connected().routeCue(name, takeElapsedMillis, monotonicNanos)

    public fun acknowledgeCuePresentation(
        name: H2CueSegmentName,
        drawnMonotonicNanos: Long,
    ): H2CuePresentationAcknowledgement =
        connected().acknowledgeCuePresentation(name, drawnMonotonicNanos)

    public fun onJoinedCanonicalResult(
        frameIndex: Long,
        takeElapsedMillis: Long,
        monotonicNanos: Long,
        rgba: ByteArray,
        landmarks: List<FaceLandmark>,
        blendshapes: BlendShapeData,
    ): H2AndroidJoinedOutput {
        val result = connected().onJoinedResult(
            H2JoinedCanonicalResult(
                frameIndex,
                takeElapsedMillis,
                monotonicNanos,
                H2RgbaLandmarkFrame(
                    rgba,
                    H2RgbaLandmarkFrame.WIDTH,
                    H2RgbaLandmarkFrame.HEIGHT,
                    landmarks,
                    H2FrameOrientation.FRONT_CAMERA_MIRRORED_RGBA_AND_LANDMARKS,
                ),
                blendshapes,
            ),
        )
        return H2AndroidJoinedOutput(
            result.frameIndex,
            result.takeElapsedMillis,
            result.sourceMonotonicNanos,
            result.primaryAvatar,
            result.comparatorAvatar,
            result.participantPresentedAvatar,
        )
    }

    public fun acknowledgeParticipantPresentation(
        output: H2AndroidJoinedOutput,
        drawnMonotonicNanos: Long,
    ): Boolean = connected().acknowledgeParticipantPresentation(output.frameIndex, drawnMonotonicNanos)

    public fun completeTake() {
        connected().completeTake()
    }

    public fun cameraLost() {
        connected().cameraLost()
    }

    public fun renderLost() {
        connected().renderLost()
    }

    private fun connected(): H2SessionController =
        checkNotNull(controller) { "H2 Android route is physically locked" }

    public companion object {
        /** Current public APK path: truthful model/authority absence and no camera request. */
        public fun locked(): AndroidH2CallbackRoute = AndroidH2CallbackRoute(
            H2AndroidRouteStatus(
                physicalExecutionEnabled = false,
                modelBundled = false,
                humanAndForwardAuthoritiesClosed = false,
            ),
            null,
        )

        internal fun connected(
            status: H2AndroidRouteStatus,
            controller: H2SessionController,
        ): AndroidH2CallbackRoute {
            require(status.executable)
            return AndroidH2CallbackRoute(status, controller)
        }
    }
}

private fun Boolean.toAvailability(): H2ProviderRouteAvailability =
    if (this) H2ProviderRouteAvailability.AVAILABLE else H2ProviderRouteAvailability.UNAVAILABLE

/** Writes the controller's five framed byte streams through the atomic streaming manager. */
internal class AndroidH2ControllerStore(private val manager: AndroidH2StreamingCaptureManager) : H2ControllerStore {
    private var writer: H2StreamingBlockWriter? = null
    private var activeBlockId: String? = null
    private var activeAttempt: Int? = null
    private val streams = linkedMapOf<String, OutputStream>()

    override fun beginBlock(blockId: String, attempt: Int) {
        mapStorageFailure(H2FailureCode.BEGIN_ATTEMPT_PRECONDITION_FAILURE) {
            check(writer == null)
            writer = manager.beginBlock(blockId, attempt)
            activeBlockId = blockId
            activeAttempt = attempt
            H2StreamingBlockWriter.REQUIRED_STREAMS.forEach { name ->
                streams[name] = checkNotNull(writer).open(name)
            }
        }
    }

    override fun appendEvent(event: H2ControllerEvent) {
        mapStorageFailure(H2FailureCode.UNPUBLISHED_EVENT_LOG_WRITE_FAILURE) {
            checkNotNull(streams[EVENT_STREAM]).write(event.jsonLine())
        }
    }

    override fun append(frame: H2RetainedControllerFrame) {
        val eventLines = frame.controllerEvents.map { it.sequence to it.jsonLine() } +
            frame.visibleEvents.map {
                it.sequence to it.jsonLine(
                    frame.frameIndex,
                    frame.takeElapsedMillis,
                    frame.controllerEvents.first().runIdentitySha256,
                )
            }
        mapStorageFailure(H2FailureCode.UNPUBLISHED_EVENT_LOG_WRITE_FAILURE) {
            eventLines.sortedBy(Pair<Long, ByteArray>::first).forEach { (_, line) ->
                checkNotNull(streams[EVENT_STREAM]).write(line)
            }
        }
        mapStorageFailure(H2FailureCode.UNPUBLISHED_CAMERA_STREAM_WRITE_FAILURE) {
            writeFramed("camera.rgba", frame.frameIndex, frame.takeElapsedMillis, frame.cameraRgba)
        }
        mapStorageFailure(H2FailureCode.UNPUBLISHED_OUTPUT_STREAM_WRITE_FAILURE) {
            writeFramed(
                "participant-action.rgba",
                frame.frameIndex,
                frame.takeElapsedMillis,
                frame.participantActionRgba,
            )
            writeFramed("avatar-a.rgba", frame.frameIndex, frame.takeElapsedMillis, frame.primaryAvatar?.rgba)
            writeFramed("avatar-b.rgba", frame.frameIndex, frame.takeElapsedMillis, frame.comparatorAvatar?.rgba)
        }
    }

    override fun publishBlock(): H2ControllerPublication {
        return mapStorageFailure(H2FailureCode.UNPUBLISHED_ATOMIC_PUBLICATION_PRECONDITION_FAILURE) {
            val published = manager.publishBlock(checkNotNull(writer))
            check(published.blockId == activeBlockId && published.attempt == activeAttempt)
            clear()
            H2ControllerPublication(published.blockId, published.attempt, published.manifestSha256)
        }
    }

    override fun terminateBlock(code: H2FailureCode) {
        try {
            if (writer != null) manager.terminateActive(code)
        } finally {
            clear()
        }
    }

    private fun writeFramed(name: String, frameIndex: Long, takeElapsedMillis: Long, payload: ByteArray?) {
        val target = checkNotNull(streams[name])
        target.write(longLittleEndian(frameIndex))
        target.write(longLittleEndian(takeElapsedMillis))
        target.write(intLittleEndian(payload?.size ?: ABSENT_LENGTH))
        payload?.let(target::write)
    }

    private fun clear() {
        writer = null
        activeBlockId = null
        activeAttempt = null
        streams.clear()
    }

    private companion object {
        const val ABSENT_LENGTH = -1
        const val EVENT_STREAM = "controller-events.jsonl"
    }
}

private inline fun <T> mapStorageFailure(code: H2FailureCode, block: () -> T): T = try {
    block()
} catch (failure: H2ControllerStorageFailure) {
    throw failure
} catch (cause: Exception) {
    throw H2ControllerStorageFailure(code, cause)
}

@Suppress("UnusedPrivateMember")
private fun H2VisibleOutputEvent.jsonLine(
    frameIndex: Long,
    takeElapsedMillis: Long,
    runIdentitySha256: String,
): ByteArray = buildString {
    append("{\"frame_index\":$frameIndex,\"sequence\":$sequence,\"block_id\":\"$blockId\",")
    append("\"take_elapsed_millis\":$takeElapsedMillis,\"event_kind\":\"VISIBLE_OUTPUT\",")
    append("\"candidate_role\":\"$candidateRole\",\"surface\":\"$surface\",")
    append("\"monotonic_nanos\":$monotonicNanos,\"state\":\"$state\",")
    append("\"run_identity_sha256\":\"$runIdentitySha256\"}\n")
}.encodeToByteArray()

private fun H2ControllerEvent.jsonLine(): ByteArray {
    val fields = mutableListOf(
        "\"sequence\":$sequence",
        "\"block_id\":\"$blockId\"",
        "\"event_kind\":\"$kind\"",
        "\"monotonic_nanos\":$monotonicNanos",
        "\"run_identity_sha256\":\"$runIdentitySha256\"",
    )
    frameIndex?.let { fields += "\"frame_index\":$it" }
    takeElapsedMillis?.let { fields += "\"take_elapsed_millis\":$it" }
    primaryAvailability?.let { fields += "\"primary_availability\":\"$it\"" }
    comparatorAvailability?.let { fields += "\"comparator_availability\":\"$it\"" }
    participantVisibility?.let { fields += "\"participant_visibility\":\"$it\"" }
    cueSegment?.let { fields += "\"cue_segment\":\"$it\"" }
    clockMapping?.let { mapping ->
        fields += "\"clock_id\":\"${mapping.clockId}\""
        fields += "\"camera_origin_nanos\":${mapping.cameraOriginNanos}"
        fields += "\"cue_origin_nanos\":${mapping.cueOriginNanos}"
        fields += "\"action_observer_origin_nanos\":${mapping.actionObserverOriginNanos}"
        fields += "\"visible_output_origin_nanos\":${mapping.visibleOutputOriginNanos}"
        fields += "\"max_mapping_uncertainty_nanos\":${mapping.maxMappingUncertaintyNanos}"
    }
    failureCode?.let { fields += "\"failure_code\":\"$it\"" }
    return (fields.joinToString(prefix = "{", postfix = "}\n")).encodeToByteArray()
}

private fun longLittleEndian(value: Long): ByteArray =
    ByteArray(Long.SIZE_BYTES) { index -> (value ushr (index * Byte.SIZE_BITS)).toByte() }

private fun intLittleEndian(value: Int): ByteArray =
    ByteArray(Int.SIZE_BYTES) { index -> (value ushr (index * Byte.SIZE_BITS)).toByte() }
