package io.github.kmpfacelink.pipeline.benchmark

/**
 * Latency report for a single avatar pipeline run.
 *
 * Captures timing for each stage of the pipeline: LLM first token,
 * first complete sentence, first audio synthesis, first blend shape frame,
 * and total end-to-end processing time.
 *
 * @property firstTokenLatencyMs Time from start to first LLM token
 * @property firstSentenceLatencyMs Time from start to first complete sentence
 * @property firstAudioLatencyMs Time from start to first TTS audio ready
 * @property firstBlendShapeLatencyMs Time from start to first lip sync frame
 * @property totalLatencyMs Total end-to-end processing time
 * @property sentenceCount Number of sentences processed
 * @property totalFrames Total blend shape frames emitted
 */
public data class PipelineLatencyReport(
    public val firstTokenLatencyMs: Long,
    public val firstSentenceLatencyMs: Long,
    public val firstAudioLatencyMs: Long,
    public val firstBlendShapeLatencyMs: Long,
    public val totalLatencyMs: Long,
    public val sentenceCount: Int,
    public val totalFrames: Int,
) {

    /**
     * Serializes this report to a JSON string for CI output.
     *
     * @return JSON representation of the latency report
     */
    public fun toJson(): String = buildString {
        append('{')
        append("\"firstTokenLatencyMs\":$firstTokenLatencyMs,")
        append("\"firstSentenceLatencyMs\":$firstSentenceLatencyMs,")
        append("\"firstAudioLatencyMs\":$firstAudioLatencyMs,")
        append("\"firstBlendShapeLatencyMs\":$firstBlendShapeLatencyMs,")
        append("\"totalLatencyMs\":$totalLatencyMs,")
        append("\"sentenceCount\":$sentenceCount,")
        append("\"totalFrames\":$totalFrames")
        append('}')
    }
}
