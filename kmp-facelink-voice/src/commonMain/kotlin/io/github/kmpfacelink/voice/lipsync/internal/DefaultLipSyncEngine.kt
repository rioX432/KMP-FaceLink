package io.github.kmpfacelink.voice.lipsync.internal

import io.github.kmpfacelink.voice.audio.AudioData
import io.github.kmpfacelink.voice.lipsync.LipSyncConfig
import io.github.kmpfacelink.voice.lipsync.LipSyncEngine
import io.github.kmpfacelink.voice.lipsync.LipSyncFrame
import io.github.kmpfacelink.voice.tts.PhonemeEvent
import kotlinx.coroutines.flow.Flow

/**
 * Default [LipSyncEngine] implementation.
 *
 * Delegates to [PhonemeAnimator] for phoneme-based animation
 * and [AmplitudeAnimator] for amplitude-based fallback.
 */
internal class DefaultLipSyncEngine : LipSyncEngine {

    override fun animateFromPhonemes(
        phonemeEvents: List<PhonemeEvent>,
        config: LipSyncConfig,
    ): Flow<LipSyncFrame> = PhonemeAnimator.animate(phonemeEvents, config)

    override fun animateFromAudio(
        audio: AudioData,
        config: LipSyncConfig,
    ): Flow<LipSyncFrame> = AmplitudeAnimator.animate(audio, config)
}
