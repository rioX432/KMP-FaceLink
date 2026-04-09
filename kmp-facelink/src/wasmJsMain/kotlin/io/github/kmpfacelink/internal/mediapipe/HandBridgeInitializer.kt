@file:Suppress("MaxLineLength")

package io.github.kmpfacelink.internal.mediapipe

import kotlin.js.JsAny
import kotlin.js.Promise

/**
 * JS bridge functions for MediaPipe HandLandmarker initialization.
 *
 * Uses Kotlin/Wasm `js()` inline functions to call the MediaPipe JS API
 * loaded from CDN. The host HTML page must include the MediaPipe Tasks Vision
 * script and set `globalThis.__faceLinkVision` before the Wasm module loads.
 */

/**
 * Creates a MediaPipe HandLandmarker with the given options.
 *
 * @param modelUrl URL or path to the hand landmarker model asset
 * @param maxHands Maximum number of hands to detect
 * @param delegate Hardware delegate for inference: "GPU" or "CPU"
 * @return Promise resolving to the HandLandmarker JS object
 */
internal fun createHandLandmarkerJs(
    modelUrl: String,
    maxHands: Int,
    delegate: String,
): Promise<JsAny?> = js("""(async () => {
    const vision = await globalThis.__faceLinkVision;
    if (!vision) throw new Error('MediaPipe Tasks Vision not loaded. Set globalThis.__faceLinkVision to the vision module.');
    const landmarker = await vision.HandLandmarker.createFromOptions(
        vision,
        {
            baseOptions: { modelAssetPath: modelUrl, delegate: delegate },
            runningMode: 'VIDEO',
            numHands: maxHands
        }
    );
    return landmarker;
})()""")

/**
 * Closes the HandLandmarker and releases its resources.
 */
internal fun closeHandLandmarkerJs(landmarker: JsHandLandmarker): Unit =
    js("{ landmarker.close(); }")
