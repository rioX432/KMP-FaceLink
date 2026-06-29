package io.github.kmpfacelink.util

/**
 * Shared time-unit conversion constants for the core module.
 *
 * Provides typed variants so call sites avoid redeclaring the same magic value with a
 * different numeric type. Scoped `internal` to the core module on purpose: cross-module
 * sharing of such a trivial value would force feature modules onto the core dependency
 * and widen the public API surface for no real benefit.
 */
internal object TimeConstants {
    /** Milliseconds per second as [Long]. */
    const val MILLIS_PER_SECOND_L: Long = 1000L

    /** Milliseconds per second as [Float]. */
    const val MILLIS_PER_SECOND_F: Float = 1000f

    /** Milliseconds per second as [Double]. */
    const val MILLIS_PER_SECOND_D: Double = 1000.0
}
