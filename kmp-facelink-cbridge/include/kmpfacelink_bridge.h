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
// zero is indistinguishable from a measured rest value: call
// KmpFaceLink_GetLatestBlendShapeMask to learn which channels carry a measurement.
// Trackers differ here — the desktop estimator produces well under 52 — so a consumer
// that ignores the mask will read shapes it was never given. Prefer
// KmpFaceLink_GetLatestBlendShapesLiveLink for new consumers.
void KmpFaceLink_GetLatestBlendShapes(float* blendShapes, int count);

// Writes the bit set of channels that carry a measurement in the most recent frame, for
// the ordering used by KmpFaceLink_GetLatestBlendShapes. Bit i corresponds to channel i;
// `low` receives channels 0-31 and `high` channels 32-51. A cleared bit means the tracker
// did not produce that channel and the float in its slot is padding, not a value.
void KmpFaceLink_GetLatestBlendShapeMask(uint32_t* low, uint32_t* high);

// The same bit set for the ordering used by KmpFaceLink_GetLatestBlendShapesLiveLink.
// The two orderings differ, so the masks are not interchangeable.
void KmpFaceLink_GetLatestBlendShapeMaskLiveLink(uint32_t* low, uint32_t* high);

// FNV-1a over the blend shape names in ordinal order, as computed by the Kotlin
// BlendShapeWireSchema. A consumer that keeps its own copy of the channel order compares
// this against a fingerprint over its own table: they diverge the moment a shape is added,
// renamed or reordered on one side, which is otherwise undetectable because every channel
// still decodes to a plausible float.
int32_t KmpFaceLink_GetBlendShapeSchemaFingerprint(void);

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
