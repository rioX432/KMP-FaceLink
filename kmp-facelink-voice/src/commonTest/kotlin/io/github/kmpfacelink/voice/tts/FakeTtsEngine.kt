package io.github.kmpfacelink.voice.tts

import io.github.kmpfacelink.voice.audio.AudioData
import io.github.kmpfacelink.voice.audio.AudioFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val FAKE_DURATION_MS = 500L

/**
 * Fake [TtsEngine] for testing.
 */
class FakeTtsEngine : TtsEngine {

    private val _state = MutableStateFlow<TtsState>(TtsState.Idle)
    override val state: StateFlow<TtsState> = _state.asStateFlow()

    /** Result to return from [synthesize]. */
    var resultToReturn: TtsResult = TtsResult(
        audio = AudioData(
            bytes = ByteArray(16000), // 500ms at 16kHz 16-bit
            format = AudioFormat(),
            durationMs = FAKE_DURATION_MS,
        ),
        phonemeEvents = emptyList(),
        durationMs = FAKE_DURATION_MS,
    )

    /** Last text passed to [synthesize]. */
    var lastSynthesizedText: String? = null

    /** Number of times [synthesize] was called. */
    var synthesizeCount: Int = 0

    override suspend fun synthesize(text: String): TtsResult {
        lastSynthesizedText = text
        synthesizeCount++
        _state.value = TtsState.Synthesizing
        _state.value = TtsState.Idle
        return resultToReturn
    }

    override fun release() {
        _state.value = TtsState.Idle
    }
}
