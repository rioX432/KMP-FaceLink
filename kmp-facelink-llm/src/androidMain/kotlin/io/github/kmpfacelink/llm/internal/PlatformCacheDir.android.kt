package io.github.kmpfacelink.llm.internal

private const val MODEL_CACHE_SUBDIR = "kmp-facelink/models"

/**
 * Returns a temporary directory-based cache path on Android.
 *
 * Uses `java.io.tmpdir` as a safer default than a hardcoded `/data/local/tmp` path.
 * A future enhancement should accept an Android Context to use `context.cacheDir`
 * for proper app-scoped storage.
 */
internal actual fun platformModelCacheDir(): String {
    val tmpDir = System.getProperty("java.io.tmpdir") ?: "/tmp"
    return "$tmpDir/$MODEL_CACHE_SUBDIR"
}
