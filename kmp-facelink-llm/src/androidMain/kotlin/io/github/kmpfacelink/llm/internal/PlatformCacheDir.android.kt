package io.github.kmpfacelink.llm.internal

private const val ANDROID_MODEL_CACHE_PATH = "/data/local/tmp/kmp-facelink/models"

/**
 * Returns a hardcoded cache path on Android.
 * Actual Context-based resolution will be done in the Android inference engine PR.
 */
internal actual fun platformModelCacheDir(): String = ANDROID_MODEL_CACHE_PATH
