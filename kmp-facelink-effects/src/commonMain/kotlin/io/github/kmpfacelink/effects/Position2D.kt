package io.github.kmpfacelink.effects

/**
 * A 2D position with normalized coordinates (0.0–1.0).
 *
 * @property x Horizontal position, 0.0 (left) to 1.0 (right)
 * @property y Vertical position, 0.0 (top) to 1.0 (bottom)
 */
public data class Position2D(
    val x: Float,
    val y: Float,
)
