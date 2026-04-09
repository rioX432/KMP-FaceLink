package io.github.kmpfacelink.internal.mediapipe

import kotlin.js.JsAny

/**
 * External JS types representing MediaPipe Tasks Vision API objects.
 *
 * These are opaque handles to JavaScript objects returned by the bridge functions.
 * Property access is done through dedicated accessor functions to avoid Kotlin/Wasm
 * interop limitations with complex JS object graphs.
 */

/** Opaque handle to a JS FaceLandmarker instance. */
internal external interface JsFaceLandmarker : JsAny

/** Opaque handle to a JS MediaStream from getUserMedia. */
internal external interface JsMediaStream : JsAny

/** Opaque handle to a JS HTMLVideoElement. */
internal external interface JsVideoElement : JsAny

/** Opaque handle to a FaceLandmarkerResult from detectForVideo. */
internal external interface JsFaceLandmarkerResult : JsAny

/** Opaque handle to a single blend shape category. */
internal external interface JsCategory : JsAny

/** Opaque handle to a single normalized landmark. */
internal external interface JsNormalizedLandmark : JsAny

/** Opaque handle to a JS HandLandmarker instance. */
internal external interface JsHandLandmarker : JsAny

/** Opaque handle to a HandLandmarkerResult from detectForVideo. */
internal external interface JsHandLandmarkerResult : JsAny

/** Opaque handle to a JS PoseLandmarker instance. */
internal external interface JsPoseLandmarker : JsAny

/** Opaque handle to a PoseLandmarkerResult from detectForVideo. */
internal external interface JsPoseLandmarkerResult : JsAny
