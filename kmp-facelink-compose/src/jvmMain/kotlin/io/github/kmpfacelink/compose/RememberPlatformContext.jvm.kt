package io.github.kmpfacelink.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.kmpfacelink.api.PlatformContext

@Composable
internal actual fun rememberPlatformContext(): PlatformContext {
    return remember { PlatformContext() }
}
