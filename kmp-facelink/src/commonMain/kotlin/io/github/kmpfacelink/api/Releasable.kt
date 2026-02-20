package io.github.kmpfacelink.api

/**
 * Common lifecycle interface for resource-holding components.
 *
 * All modules that hold system resources (trackers, renderers, clients,
 * engines) should implement this interface. Call [release] when the
 * component is no longer needed to free underlying resources.
 *
 * After [release] is called, the component should not be used again.
 */
public fun interface Releasable {
    /** Releases all resources held by this component. */
    public fun release()
}
