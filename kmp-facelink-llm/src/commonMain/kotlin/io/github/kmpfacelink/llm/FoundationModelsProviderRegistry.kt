package io.github.kmpfacelink.llm

import kotlin.concurrent.Volatile

/**
 * Global registry for the [FoundationModelsProvider] implementation.
 *
 * On iOS 26+, the Swift side registers a provider at app startup:
 * ```swift
 * FoundationModelsProviderRegistry.shared.register(provider: MyProvider())
 * ```
 *
 * This registry pattern allows the KMP module to remain platform-agnostic
 * while the Swift-only Foundation Models framework is bridged in from iOS.
 */
public object FoundationModelsProviderRegistry {

    @Volatile
    private var provider: FoundationModelsProvider? = null

    /**
     * Registers a [FoundationModelsProvider] implementation.
     *
     * Must be called before creating any [LlmConfig.FoundationModels] client.
     * Typically called once at app startup on iOS 26+.
     *
     * @param provider The Foundation Models bridge implementation
     */
    public fun register(provider: FoundationModelsProvider) {
        this.provider = provider
    }

    /**
     * Returns the registered provider, or null if none is registered.
     */
    public fun get(): FoundationModelsProvider? = provider

    /**
     * Whether a provider is registered and reports itself as available.
     */
    public fun isAvailable(): Boolean = provider?.isAvailable() == true

    /**
     * Clears the registered provider. Primarily for testing.
     */
    public fun clear() {
        provider = null
    }
}
