package io.github.kmpfacelink.llm.internal

import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

internal actual fun platformModelCacheDir(): String {
    val paths = NSSearchPathForDirectoriesInDomains(
        NSCachesDirectory,
        NSUserDomainMask,
        true,
    )
    val cachesDir = paths.firstOrNull() as? String ?: "/tmp"
    return "$cachesDir/kmp-facelink/models"
}
