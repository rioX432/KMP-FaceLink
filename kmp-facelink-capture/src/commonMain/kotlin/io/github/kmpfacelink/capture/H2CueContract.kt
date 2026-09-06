@file:Suppress("MagicNumber")

package io.github.kmpfacelink.capture

/** Exact score-free cue segment names frozen by the H2 V2 protocol. */
public enum class H2CueSegmentName {
    READINESS_AVAILABILITY,
    TAKE_CALIBRATION_NEUTRAL,
    READY_PREPARE,
    ACTION_HOLD_1,
    RELEASE_1,
    ACTION_HOLD_2,
    RELEASE_2,
    ORDINARY_USE_OBSERVATION,
    NATURAL_SPEECH,
}

public data class H2CueSegment(
    public val name: H2CueSegmentName,
    public val startMillis: Long,
    public val endMillis: Long,
) {
    init {
        require(startMillis >= 0)
        require(endMillis > startMillis)
    }
}

public data class H2CuePresentation(
    public val blockId: String,
    public val segment: H2CueSegment,
    public val takeElapsedMillis: Long,
    public val monotonicNanos: Long,
)

public data class H2CuePresentationAcknowledgement(
    public val blockId: String,
    public val segment: H2CueSegmentName,
    public val scheduledStartMillis: Long,
    public val drawnMonotonicNanos: Long,
)

/** Immutable per-block schedules. A presentation is evidence only when the UI invokes the API. */
public object H2CueContract {
    public fun segmentsFor(blockId: String): List<H2CueSegment> {
        val block = H2ConfirmationContract.blocks.single { it.id == blockId }
        val end = block.totalDurationSeconds * 1_000L
        return when {
            block.kind == H2BlockKind.STARTUP_READINESS -> listOf(
                H2CueSegment(H2CueSegmentName.READINESS_AVAILABILITY, 0, end),
            )
            block.id == "B10_NATURAL_SPEECH" -> listOf(
                H2CueSegment(H2CueSegmentName.TAKE_CALIBRATION_NEUTRAL, 0, 3_000),
                H2CueSegment(H2CueSegmentName.NATURAL_SPEECH, 3_000, end),
            )
            block.kind == H2BlockKind.ORDINARY_USE -> listOf(
                H2CueSegment(H2CueSegmentName.TAKE_CALIBRATION_NEUTRAL, 0, 3_000),
                H2CueSegment(H2CueSegmentName.ORDINARY_USE_OBSERVATION, 3_000, end),
            )
            block.totalDurationSeconds == 13 -> CONTROL_SEGMENTS
            block.totalDurationSeconds == 21 -> POSITIVE_SEGMENTS
            else -> error("H2 cue schedule is missing for ${block.id}")
        }
    }

    private val CONTROL_SEGMENTS: List<H2CueSegment> = listOf(
        H2CueSegment(H2CueSegmentName.TAKE_CALIBRATION_NEUTRAL, 0, 3_000),
        H2CueSegment(H2CueSegmentName.READY_PREPARE, 3_000, 5_000),
        H2CueSegment(H2CueSegmentName.ACTION_HOLD_1, 5_000, 9_000),
        H2CueSegment(H2CueSegmentName.RELEASE_1, 9_000, 13_000),
    )

    private val POSITIVE_SEGMENTS: List<H2CueSegment> = listOf(
        H2CueSegment(H2CueSegmentName.TAKE_CALIBRATION_NEUTRAL, 0, 3_000),
        H2CueSegment(H2CueSegmentName.READY_PREPARE, 3_000, 5_000),
        H2CueSegment(H2CueSegmentName.ACTION_HOLD_1, 5_000, 9_000),
        H2CueSegment(H2CueSegmentName.RELEASE_1, 9_000, 13_000),
        H2CueSegment(H2CueSegmentName.ACTION_HOLD_2, 13_000, 17_000),
        H2CueSegment(H2CueSegmentName.RELEASE_2, 17_000, 21_000),
    )
}

internal class H2CueTracker(
    private val blockId: String,
    private val originMonotonicNanos: Long,
) {
    private val expected = H2CueContract.segmentsFor(blockId)
    private var nextIndex = 0
    private var routed: H2CuePresentation? = null

    val complete: Boolean
        get() = nextIndex == expected.size

    val next: H2CueSegment?
        get() = expected.getOrNull(nextIndex)

    fun route(
        name: H2CueSegmentName,
        takeElapsedMillis: Long,
        monotonicNanos: Long,
    ): H2CuePresentation {
        check(routed == null) { "previous H2 cue route has no UI presentation acknowledgement" }
        val segment = checkNotNull(next) { "all H2 cue segments were already acknowledged" }
        require(segment.name == name) { "H2 cue segment is out of order" }
        require(takeElapsedMillis == segment.startMillis) { "H2 cue was not presented at its exact boundary" }
        require(monotonicNanos == originMonotonicNanos + takeElapsedMillis * 1_000_000L) {
            "H2 cue clock does not match the take clock"
        }
        return H2CuePresentation(blockId, segment, takeElapsedMillis, monotonicNanos).also { routed = it }
    }

    fun acknowledge(name: H2CueSegmentName, drawnMonotonicNanos: Long): H2CuePresentationAcknowledgement {
        val presentation = checkNotNull(routed) { "H2 cue was not routed to the UI" }
        require(presentation.segment.name == name) { "H2 cue acknowledgement does not match its route" }
        require(drawnMonotonicNanos >= presentation.monotonicNanos) { "H2 cue was acknowledged before routing" }
        require(drawnMonotonicNanos <= originMonotonicNanos + presentation.segment.endMillis * 1_000_000L) {
            "H2 cue acknowledgement is after the segment boundary"
        }
        routed = null
        nextIndex++
        return H2CuePresentationAcknowledgement(
            blockId,
            name,
            presentation.segment.startMillis,
            drawnMonotonicNanos,
        )
    }
}
