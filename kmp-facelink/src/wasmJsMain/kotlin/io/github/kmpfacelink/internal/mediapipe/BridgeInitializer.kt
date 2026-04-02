@file:Suppress("MaxLineLength")

package io.github.kmpfacelink.internal.mediapipe

import kotlin.js.JsAny
import kotlin.js.Promise

/**
 * JS bridge functions for MediaPipe Tasks Vision initialization.
 *
 * Uses Kotlin/Wasm `js()` inline functions to call the MediaPipe JS API
 * loaded from CDN. The host HTML page must include the MediaPipe Tasks Vision
 * script before the Wasm module loads.
 */

/**
 * Initializes the MediaPipe vision fileset and creates a FaceLandmarker.
 *
 * Loads `@mediapipe/tasks-vision` from CDN, then calls
 * `FaceLandmarker.createFromOptions` with VIDEO running mode and blend shapes enabled.
 *
 * @param modelUrl URL or path to the face landmarker model asset
 * @param maxFaces Maximum number of faces to detect
 * @return Promise resolving to the FaceLandmarker JS object
 */
internal fun createFaceLandmarkerJs(
    modelUrl: String,
    maxFaces: Int,
): Promise<JsAny?> = js("""(async () => {
    const vision = await globalThis.__faceLinkVision;
    if (!vision) throw new Error('MediaPipe Tasks Vision not loaded. Set globalThis.__faceLinkVision to the vision module.');
    const landmarker = await vision.FaceLandmarker.createFromOptions(
        vision,
        {
            baseOptions: { modelAssetPath: modelUrl },
            runningMode: 'VIDEO',
            numFaces: maxFaces,
            outputFaceBlendshapes: true,
            outputFacialTransformationMatrixes: true
        }
    );
    return landmarker;
})()""")

/**
 * Calls `navigator.mediaDevices.getUserMedia({ video: true })` to get a camera stream.
 *
 * @return Promise resolving to a MediaStream
 */
internal fun getUserMediaJs(): Promise<JsAny?> =
    js("navigator.mediaDevices.getUserMedia({ video: { facingMode: 'user', width: { ideal: 640 }, height: { ideal: 480 } } })")

/**
 * Creates an HTMLVideoElement, attaches the media stream, and starts playback.
 *
 * @param stream MediaStream from getUserMedia
 * @return Promise that resolves to the video element once it is playing
 */
internal fun createVideoElementJs(stream: JsMediaStream): Promise<JsAny?> = js("""(async () => {
    const video = document.createElement('video');
    video.setAttribute('playsinline', '');
    video.setAttribute('autoplay', '');
    video.srcObject = stream;
    video.style.display = 'none';
    document.body.appendChild(video);
    await video.play();
    return video;
})()""")

/**
 * Stops all tracks on the given MediaStream.
 */
internal fun stopMediaStreamJs(stream: JsMediaStream): Unit =
    js("{ stream.getTracks().forEach(t => t.stop()); }")

/**
 * Removes the video element from the DOM.
 */
internal fun removeVideoElementJs(video: JsVideoElement): Unit =
    js("{ if (video && video.parentNode) video.parentNode.removeChild(video); }")

/**
 * Closes the FaceLandmarker and releases its resources.
 */
internal fun closeFaceLandmarkerJs(landmarker: JsFaceLandmarker): Unit =
    js("{ landmarker.close(); }")
