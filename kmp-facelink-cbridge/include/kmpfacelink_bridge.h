// Copyright (c) KMP-FaceLink contributors. Licensed under Apache-2.0.

// Shared C bridge for KMP-FaceLink.
//
// Provides a stable C API for non-KMP consumers (Unity, React Native, Flutter).
// The bridge creates KMP-FaceLink tracker instances, collects Flow emissions on
// background threads, and buffers the latest frame data for thread-safe polling.
//
// Consumers:
//   - Unity: DllImport("__Internal") -> this bridge (iOS), JNI -> Kotlin facade (Android)
//   - React Native: ObjC++ Turbo Module -> this bridge (iOS), JNI -> Kotlin facade (Android)
//   - Flutter: dart:ffi -> this bridge (both platforms)
//
// Thread safety:
//   All getters are safe to call from any thread. Data is protected by os_unfair_lock (iOS).

#ifndef kmpfacelink_bridge_h
#define kmpfacelink_bridge_h

#include <stdbool.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// -- Lifecycle --

bool KmpFaceLink_Initialize(bool enableFace, bool enableHands);
void KmpFaceLink_StartTracking(void);
void KmpFaceLink_StopTracking(void);
void KmpFaceLink_Dispose(void);
bool KmpFaceLink_IsTracking(void);

// -- Face tracking --

// Returns the timestamp (monotonic ms) of the latest face frame, or 0 if none.
int64_t KmpFaceLink_GetLatestFaceTimestamp(void);

// Copies up to `count` blend shape floats (max 52) into the caller's buffer.
//
// LEGACY ordering: indexed by KMP-FaceLink's BlendShape.ordinal (anatomical groups:
// eyes, jaw, mouth, brow, cheek, nose, tongue). The channel ORDER is a stable binary
// contract — kept unchanged. Channels the tracker did not produce are 0-filled, and that
// zero is indistinguishable from a measured rest value, and this ABI carries no way to
// tell them apart. Trackers differ in what they produce — the desktop estimator emits 39
// of 52, and MediaPipe has no tongueOut channel — so a consumer of this array reads
// shapes it was never given. Closing that gap needs an added ABI entry point and is not
// done. Prefer
// KmpFaceLink_GetLatestBlendShapesLiveLink for new consumers.
void KmpFaceLink_GetLatestBlendShapes(float* blendShapes, int count);




// Copies up to `count` blend shape floats (max 52) into the caller's buffer, in
// canonical ARKit Live Link Face wire order (== Apple ARFaceAnchor.BlendShapeLocation
// declaration order == Unreal ELiveLinkFaceBlendShape). Channel 0 = eyeBlinkLeft ...
// channel 51 = tongueOut. Mapped BY NAME, never by ordinal. Channels the tracker does
// not produce (e.g. tongueOut on Android/MediaPipe) are 0-filled.
// This is the ordering avatar-core and other Live Link consumers expect.
void KmpFaceLink_GetLatestBlendShapesLiveLink(float* blendShapes, int count);

// Copies 6 head transform floats into the buffer. `count` must be >= 6.
// Layout: [pitch, yaw, roll, posX, posY, posZ].
//   - pitch/yaw/roll: Euler angles in DEGREES (ZYX order), right-handed.
//   - posX/posY/posZ: translation in METERS, right-handed camera/world space
//     (+X right, +Y up, -Z forward), origin = tracker session origin.
// Neutral / identity pose = all six values 0. Degrees->radians and any axis remap
// are the consumer adapter's responsibility. See README.md "Head transform contract".
void KmpFaceLink_GetLatestHeadTransform(float* transform, int count);

// -- Hand tracking --

// Returns the timestamp (monotonic ms) of the latest hand frame, or 0 if none.
int64_t KmpFaceLink_GetLatestHandTimestamp(void);

// Returns the number of currently tracked hands (0-2).
int KmpFaceLink_GetLatestHandCount(void);

// Copies up to `count` landmark floats (max 63 = 21 joints * 3 coords) for the given hand.
void KmpFaceLink_GetHandLandmarks(int handIndex, float* landmarks, int count);

// Returns handedness for the given hand index: 0=left, 1=right, 2=unknown.
int KmpFaceLink_GetHandedness(int handIndex);

#ifdef __cplusplus
}
#endif

#endif /* kmpfacelink_bridge_h */
