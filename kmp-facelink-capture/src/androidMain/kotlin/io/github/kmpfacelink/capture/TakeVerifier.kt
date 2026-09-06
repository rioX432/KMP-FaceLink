package io.github.kmpfacelink.capture

import io.github.kmpfacelink.api.OfflineFaceResult
import io.github.kmpfacelink.api.OfflineFrame
import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.api.openOfflineFaceInference
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.InferenceRunningMode
import io.github.kmpfacelink.model.PerformanceConfig
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.abs

/**
 * Re-infers a recorded take on the device that recorded it, and reports how the replay compares to
 * what the live pipeline produced.
 *
 * **This is the check that decides whether "record once, vary the condition afterwards" holds.** If
 * the stored pixels are not what inference actually saw — a stale buffer, the wrong mirroring, a
 * frame joined to the wrong result — the replay does not merely differ by a little, it produces a
 * different face or no face at all.
 *
 * What it cannot be is an equality test, and reading it as one would be wrong. The live take ran in
 * LIVE_STREAM, which skips the face detector while it can track from the previous frame
 * (`face_landmarker_graph.cc`: *"While in stream mode, skip face detector graph when we successfully
 * track the faces from the last frame"*), so replaying the same pixels in IMAGE mode — where every
 * frame is detected from scratch — is a different computation over the same input. VIDEO mode
 * carries tracking state forward like the live path does, so it should sit closer. Both are reported
 * for exactly that reason: the gap between them is the running mode, and the gap that is left over
 * is everything else.
 */
public class TakeVerifier(private val platformContext: PlatformContext) {

    /**
     * @property replayedFrames stored frames that were re-inferred
     * @property joined frames that had a live result to compare against. Fewer than
     *   [replayedFrames] whenever LIVE_STREAM declined to run some of them, which is expected
     * @property faceAgreement fraction of joined frames where replay and live agree on whether
     *   there was a face. A low number here means the stored pixels are wrong, not that the modes
     *   differ
     * @property maxBlendshapeDelta largest absolute difference in any of the 52 coefficients
     * @property medianBlendshapeDelta typical difference, which the running mode explains
     * @property maxLandmarkDelta largest absolute difference in any landmark coordinate, normalised
     */
    public data class Report(
        val takeId: String,
        val mode: String,
        val storedFrames: Int,
        val liveResults: Int,
        val replayedFrames: Int,
        val joined: Int,
        val faceAgreement: Float,
        val maxBlendshapeDelta: Float,
        val medianBlendshapeDelta: Float,
        val worstBlendshape: String,
        val maxLandmarkDelta: Float,
        val medianLandmarkDelta: Float,
    ) {
        public fun summary(): String = buildString {
            append(takeId)
            append(" [")
            append(mode)
            append("] stored=")
            append(storedFrames)
            append(" live=")
            append(liveResults)
            append(" joined=")
            append(joined)
            append(" faceAgreement=")
            append(percent(faceAgreement))
            append(" blendshape max=")
            append(format(maxBlendshapeDelta))
            append(" (")
            append(worstBlendshape)
            append(") median=")
            append(format(medianBlendshapeDelta))
            append(" landmark max=")
            append(format(maxLandmarkDelta))
        }
    }

    /**
     * Replays [takeDir] in [mode].
     *
     * The frame payload is read with its hashes checked, so a report can never be produced from
     * altered pixels.
     */
    public fun verify(takeDir: File, mode: InferenceRunningMode): Report {
        val manifest = Json.decodeFromString<TakeManifest>(
            File(takeDir, InferenceRecordFormat.TAKE_MANIFEST_FILE).readText(),
        )
        val frames = RawFrameReader(
            File(takeDir, AndroidCaptureSession.FRAMES_DIR).absolutePath,
            storageReceipt = manifest.storageReceipt,
        )
        val results = InferenceRecordReader(takeDir.absolutePath)
        val comparison = Comparison(results)

        val inference = openOfflineFaceInference(
            platformContext,
            FaceTrackerConfig(performanceConfig = PerformanceConfig(runningMode = mode)),
        )
        try {
            frames.frames().forEach { frame ->
                val replay = inference.infer(
                    OfflineFrame(
                        width = frame.width,
                        height = frame.height,
                        rgba = frame.copyPixels(),
                        timestampMs = frame.inferenceTimestampMs,
                        mirroredBeforeInference = manifest.condition.mirrored,
                    ),
                )
                comparison.add(frame.analysisFrameSeq, replay)
            }
        } finally {
            inference.release()
        }
        return comparison.report(manifest, mode, frames.frameCount)
    }

    /**
     * Accumulates one replay against the live results, joined by sequence.
     *
     * Joining by position instead would pair a frame with whichever result happened to be next, and
     * in LIVE_STREAM the two streams are different lengths by design.
     */
    private class Comparison(private val results: InferenceRecordReader) {

        private val liveBySeq: Map<Int, Int> =
            results.headers.withIndex().associate { (index, header) ->
                header.analysisFrameSeq to index
            }
        private val blendshapeDeltas = ArrayList<Float>()
        private val landmarkDeltas = ArrayList<Float>()
        private var replayed = 0
        private var joined = 0
        private var faceAgreed = 0
        private var worstBlendshape = -1
        private var maxBlendshapeDelta = 0f
        private var maxLandmarkDelta = 0f

        fun add(seq: Int, replay: OfflineFaceResult) {
            replayed++
            val liveIndex = liveBySeq[seq] ?: return
            joined++
            if (results.headers[liveIndex].hasFace == replay.hasFace) faceAgreed++
            compareBlendshapes(results.blendshapesAt(liveIndex), replay.blendshapes)
            compareLandmarks(results.landmarksAt(liveIndex), replay.landmarks)
        }

        private fun compareBlendshapes(live: FloatArray?, replay: FloatArray?) {
            if (live == null || replay == null) return
            live.indices.forEach { i ->
                val delta = abs(live[i] - replay[i])
                blendshapeDeltas.add(delta)
                if (delta > maxBlendshapeDelta) {
                    maxBlendshapeDelta = delta
                    worstBlendshape = i
                }
            }
        }

        private fun compareLandmarks(live: FloatArray?, replay: FloatArray?) {
            if (live == null || replay == null) return
            live.indices.forEach { i ->
                val delta = abs(live[i] - replay[i])
                landmarkDeltas.add(delta)
                if (delta > maxLandmarkDelta) maxLandmarkDelta = delta
            }
        }

        fun report(manifest: TakeManifest, mode: InferenceRunningMode, storedFrames: Int): Report =
            Report(
                takeId = manifest.takeId,
                mode = mode.name,
                storedFrames = storedFrames,
                liveResults = results.headers.size,
                replayedFrames = replayed,
                joined = joined,
                faceAgreement = if (joined == 0) 0f else faceAgreed.toFloat() / joined,
                maxBlendshapeDelta = maxBlendshapeDelta,
                medianBlendshapeDelta = median(blendshapeDeltas),
                worstBlendshape = manifest.blendshapeNames.getOrElse(worstBlendshape) { "n/a" },
                maxLandmarkDelta = maxLandmarkDelta,
                medianLandmarkDelta = median(landmarkDeltas),
            )
    }
}

private fun median(values: List<Float>): Float =
    if (values.isEmpty()) 0f else values.sorted()[values.size / 2]

private const val PERCENT = 100
private const val MICRO = 1_000_000
private const val MICRO_DIGITS = 6

private fun percent(value: Float): String = "${(value * PERCENT).toInt()}%"

private fun format(value: Float): String {
    // Enough digits to tell a floor from a small difference: the coefficients this study cares about
    // live at 1e-5, and rounding them to two places would print every one of them as zero.
    val scaled = (value * MICRO).toLong()
    return "${scaled / MICRO}.${(scaled % MICRO).toString().padStart(MICRO_DIGITS, '0')}"
}
