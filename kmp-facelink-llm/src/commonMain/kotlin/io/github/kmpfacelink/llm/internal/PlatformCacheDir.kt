package io.github.kmpfacelink.llm.internal

/**
 * Returns the platform-specific directory path for caching model files.
 */
internal expect fun platformModelCacheDir(): String
