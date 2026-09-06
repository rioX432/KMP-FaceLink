@file:OptIn(io.github.kmpfacelink.ExperimentalFaceLinkApi::class)

package io.github.kmpfacelink.capture

import io.github.kmpfacelink.ExperimentalFaceLinkApi
import io.github.kmpfacelink.research.C1FrozenContourCandidate
import io.github.kmpfacelink.research.C1StandaloneFrameRole
import kotlin.math.roundToInt

/** Converts the recorded opening-neutral span into the exact standalone C1 frame-role plane. */
@ExperimentalFaceLinkApi
public object C1StandaloneFrameRoleAdapter {
    public const val BURN_IN_MS: Int = 1_500

    public fun openingNeutralSpan(manifest: TakeManifest): TakeSpan = manifest.spans.firstOrNull {
        it.cue == OPENING_NEUTRAL_CUE
    } ?: throw IllegalArgumentException("take manifest has no opening NEUTRAL span")

    /**
     * Mirrors `scripts/plan2/geometry.py::initial_neutral_mask`: trim by scripted duration and
     * admitted sequence count, include the resulting lower bound and the recorded last sequence.
     */
    public fun role(span: TakeSpan, analysisFrameSeq: Int): C1StandaloneFrameRole {
        require(span.cue == OPENING_NEUTRAL_CUE) { "span must be the opening NEUTRAL cue" }
        val durationMs = span.endMs - span.startMs
        val frameCount = span.lastFrameSeq - span.firstFrameSeq + 1
        require(durationMs > 0L) { "opening NEUTRAL duration must be positive" }
        require(frameCount > 0) { "opening NEUTRAL sequence range must be non-empty" }
        val trimmedFrames = (BURN_IN_MS.toDouble() * frameCount / durationMs).roundToInt()
        val calibrationFirstSeq = span.firstFrameSeq + trimmedFrames
        return when {
            analysisFrameSeq < calibrationFirstSeq -> C1StandaloneFrameRole.BEFORE_CALIBRATION
            analysisFrameSeq <= span.lastFrameSeq -> C1StandaloneFrameRole.OPENING_NEUTRAL_CALIBRATION
            else -> C1StandaloneFrameRole.EVALUATION
        }
    }

    public fun featurePlanes(
        span: TakeSpan,
        analysisFrameSeq: Int,
    ): Map<String, List<Float>> = mapOf(
        C1FrozenContourCandidate.FRAME_ROLE_PLANE_KEY to listOf(role(span, analysisFrameSeq).encodedValue),
    )

    private const val OPENING_NEUTRAL_CUE: String = "NEUTRAL"
}
