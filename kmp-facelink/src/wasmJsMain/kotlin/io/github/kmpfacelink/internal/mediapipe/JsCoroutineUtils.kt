package io.github.kmpfacelink.internal.mediapipe

import kotlinx.coroutines.await
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.js.JsAny
import kotlin.js.Promise

/**
 * Awaits a JS Promise from Kotlin/Wasm coroutine context.
 *
 * Uses kotlinx-coroutines' built-in [Promise.await] which provides
 * cancellation support and proper error propagation.
 *
 * @param T The expected return type
 * @param promise The JS Promise to await
 * @return The resolved value cast to T
 */
@Suppress("UNCHECKED_CAST")
internal suspend fun <T> awaitJsPromise(promise: Promise<JsAny?>): T =
    promise.await() as T

/**
 * Suspends until the next browser animation frame via `requestAnimationFrame`.
 *
 * This aligns tracking frame processing with the browser's render cycle,
 * preventing unnecessary CPU usage and ensuring smooth frame pacing.
 */
internal suspend fun awaitAnimationFrame(): Unit =
    suspendCoroutine { continuation ->
        requestAnimationFrameJs { continuation.resume(Unit) }
    }

/**
 * Wraps `window.requestAnimationFrame` for use from Kotlin/Wasm.
 */
private fun requestAnimationFrameJs(callback: () -> Unit): Unit =
    js("{ window.requestAnimationFrame(() => callback()); }")
