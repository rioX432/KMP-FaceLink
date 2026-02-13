package io.github.kmpfacelink.live2d

/**
 * Metadata describing a Live2D Cubism model.
 *
 * @property modelId unique identifier for this model
 * @property name human-readable display name
 * @property modelPath path to the .model3.json file (relative to assets/bundle)
 * @property parameterIds set of Live2D parameter IDs supported by this model (empty = accept all)
 */
public data class Live2DModelInfo(
    val modelId: String,
    val name: String,
    val modelPath: String,
    val parameterIds: Set<String> = emptySet(),
)
