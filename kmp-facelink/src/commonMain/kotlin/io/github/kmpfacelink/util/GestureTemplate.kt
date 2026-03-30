package io.github.kmpfacelink.util

/**
 * A registered custom gesture template.
 *
 * @property name User-defined gesture name
 * @property meanFeature Averaged feature vector computed from training samples
 * @property sampleCount Number of training samples used to create this template
 */
internal data class GestureTemplate(
    val name: String,
    val meanFeature: FloatArray,
    val sampleCount: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GestureTemplate) return false
        return name == other.name &&
            sampleCount == other.sampleCount &&
            meanFeature.contentEquals(other.meanFeature)
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + meanFeature.contentHashCode()
        result = 31 * result + sampleCount
        return result
    }
}
