package io.github.kmpfacelink.avatar

/**
 * VRM specification version.
 *
 * VRM 0.x uses PascalCase expression names (e.g., "Joy", "BlinkL", "A").
 * VRM 1.0 uses camelCase expression names (e.g., "happy", "blinkLeft", "aa").
 */
public enum class VrmVersion {
    /** VRM 0.x — BlendShapeProxy with PascalCase names. */
    V0_X,

    /** VRM 1.0 — ExpressionManager with camelCase names. */
    V1_0,
}
