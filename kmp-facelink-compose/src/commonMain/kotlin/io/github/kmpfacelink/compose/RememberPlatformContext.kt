package io.github.kmpfacelink.compose

import androidx.compose.runtime.Composable
import io.github.kmpfacelink.api.PlatformContext

@Composable
internal expect fun rememberPlatformContext(): PlatformContext
