package io.github.kmpfacelink.pipeline.benchmark

import kotlin.time.TimeSource

/**
 * Tracks latency for each stage of the avatar pipeline.
 *
 * Uses [TimeSource.Monotonic] for accurate, monotonic timing that is not
 * affected by system clock adjustments.
 *
 * Usage:
 * 1. Call [onProcessingStart] when the pipeline begins processing
 * 2. Call stage callbacks as each milestone is reached
 * 3. Call [onComplete] when all output has been emitted
 * 4. Call [report] to get the final latency report
 */
public class PipelineLatencyTracker {

    private val timeSource: TimeSource.Monotonic = TimeSource.Monotonic
    private var startMark: TimeSource.Monotonic.ValueTimeMark? = null
    private var firstTokenMark: TimeSource.Monotonic.ValueTimeMark? = null
    private var firstSentenceMark: TimeSource.Monotonic.ValueTimeMark? = null
    private var firstAudioMark: TimeSource.Monotonic.ValueTimeMark? = null
    private var firstBlendShapeMark: TimeSource.Monotonic.ValueTimeMark? = null
    private var endMark: TimeSource.Monotonic.ValueTimeMark? = null
    private var sentenceCount: Int = 0
    private var totalFrames: Int = 0

    /** Records the start of pipeline processing. */
    public fun onProcessingStart() {
        startMark = timeSource.markNow()
    }

    /** Records the first LLM token arrival. */
    public fun onFirstToken() {
        if (firstTokenMark == null) {
            firstTokenMark = timeSource.markNow()
        }
    }

    /** Records the first complete sentence and increments the sentence count. */
    public fun onFirstSentence() {
        sentenceCount++
        if (firstSentenceMark == null) {
            firstSentenceMark = timeSource.markNow()
        }
    }

    /** Records the first TTS audio ready event. */
    public fun onFirstAudio() {
        if (firstAudioMark == null) {
            firstAudioMark = timeSource.markNow()
        }
    }

    /** Records a blend shape frame and captures the first blend shape timestamp. */
    public fun onFirstBlendShape() {
        totalFrames++
        if (firstBlendShapeMark == null) {
            firstBlendShapeMark = timeSource.markNow()
        }
    }

    /** Records the end of pipeline processing. */
    public fun onComplete() {
        endMark = timeSource.markNow()
    }

    /**
     * Builds a [PipelineLatencyReport] from the recorded timestamps.
     *
     * @return Latency report with all measured stage timings
     * @throws IllegalStateException if [onProcessingStart] was not called
     */
    public fun report(): PipelineLatencyReport {
        val start = requireNotNull(startMark) { "onProcessingStart() was not called" }
        val end = endMark ?: timeSource.markNow()

        return PipelineLatencyReport(
            firstTokenLatencyMs = elapsedFrom(start, firstTokenMark),
            firstSentenceLatencyMs = elapsedFrom(start, firstSentenceMark),
            firstAudioLatencyMs = elapsedFrom(start, firstAudioMark),
            firstBlendShapeLatencyMs = elapsedFrom(start, firstBlendShapeMark),
            totalLatencyMs = (end - start).inWholeMilliseconds,
            sentenceCount = sentenceCount,
            totalFrames = totalFrames,
        )
    }

    private fun elapsedFrom(
        start: TimeSource.Monotonic.ValueTimeMark,
        mark: TimeSource.Monotonic.ValueTimeMark?,
    ): Long = mark?.let { (it - start).inWholeMilliseconds } ?: -1L
}
