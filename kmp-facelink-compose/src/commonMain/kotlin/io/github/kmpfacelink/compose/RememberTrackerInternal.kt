package io.github.kmpfacelink.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.api.Releasable
import kotlinx.coroutines.launch

@Composable
internal fun <T : Releasable> rememberManagedTracker(
    config: Any,
    autoManageLifecycle: Boolean,
    factory: (PlatformContext) -> T,
    start: suspend (T) -> Unit,
    stop: suspend (T) -> Unit,
): T {
    val platformContext = rememberPlatformContext()
    val tracker = remember(config) { factory(platformContext) }
    val scope = rememberCoroutineScope()

    if (autoManageLifecycle) {
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(tracker, lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> scope.launch { start(tracker) }
                    Lifecycle.Event.ON_STOP -> scope.launch { stop(tracker) }
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
    }

    DisposableEffect(tracker) {
        onDispose { tracker.release() }
    }

    return tracker
}
