package io.github.kmpfacelink.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.kmpfacelink.api.PlatformContext

@Composable
internal actual fun rememberPlatformContext(): PlatformContext {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    return remember { PlatformContext(context, lifecycleOwner) }
}
