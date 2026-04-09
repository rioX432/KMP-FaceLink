@file:Suppress("MaxLineLength")

package io.github.kmpfacelink.internal.mediapipe

import kotlin.js.JsAny
import kotlin.js.Promise

/**
 * JS bridge functions for MediaPipe PoseLandmarker initialization.
 *
 * Uses Kotlin/Wasm `js()` inline functions to call the MediaPipe JS API
 * loaded from CDN. The host HTML page must include the MediaPipe Tasks Vision
 * script and set `globalThis.__faceLinkVision` before the Wasm module loads.
 */

/**
 * Creates a MediaPipe PoseLandmarker with the given options.
 *
 * @param modelUrl URL or path to the pose landmarker model asset
 * @param maxBodies Maximum number of bodies to detect
 * @param delegate Hardware delegate for inference: "GPU" or "CPU"
 * @return Promise resolving to the PoseLandmarker JS object
 */
internal fun createPoseLandmarkerJs(
    modelUrl: String,
    maxBodies: Int,
    delegate: String,
): Promise<JsAny?> = js("""(async () => {
    const vision = await globalThis.__faceLinkVision;
    if (!vision) throw new Error('MediaPipe Tasks Vision not loaded. Set globalThis.__faceLinkVision to the vision module.');
    const landmarker = await vision.PoseLandmarker.createFromOptions(
        vision,
        {
            baseOptions: { modelAssetPath: modelUrl, delegate: delegate },
            runningMode: 'VIDEO',
            numPoses: maxBodies
        }
    );
    return landmarker;
})()""")

/**
 * Closes the PoseLandmarker and releases its resources.
 */
internal fun closePoseLandmarkerJs(landmarker: JsPoseLandmarker): Unit =
    js("{ landmarker.close(); }")
