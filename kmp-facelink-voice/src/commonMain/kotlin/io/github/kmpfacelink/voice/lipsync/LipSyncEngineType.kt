package io.github.kmpfacelink.voice.lipsync

/**
 * Available lip sync engine types.
 *
 * Used by [LipSyncEngineFactory] to select the appropriate engine.
 */
public enum class LipSyncEngineType {
    /** Basic phoneme/amplitude-based engine (original). */
    DEFAULT,

    /** Enhanced multi-blendshape amplitude engine with audio feature analysis. */
    ENHANCED_AMPLITUDE,

    /** Neural/ML-based engine (requires platform-specific model). */
    NEURAL,
}
