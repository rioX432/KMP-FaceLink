package io.github.kmpfacelink.actions.emotion

/**
 * Basic emotion categories derived from blend shape analysis.
 *
 * Based on Ekman's six basic emotions plus Neutral.
 */
public enum class Emotion {
    /** Smiling, mouth corners raised, cheek squint. */
    HAPPY,

    /** Frown, inner brow raised, slight eye squint. */
    SAD,

    /** Brow down, mouth press, jaw clench, nose sneer. */
    ANGRY,

    /** Eyes wide, brows raised, jaw open. */
    SURPRISED,

    /** Nose sneer, upper lip raised, mouth frown. */
    DISGUSTED,

    /** Eyes wide, brows raised and drawn together, mouth stretch. */
    FEAR,

    /** Relaxed face, no strong activation of any blend shape group. */
    NEUTRAL,
}
