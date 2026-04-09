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
void KmpFaceLink_GetLatestBlendShapes(float* blendShapes, int count);

// Copies 6 head transform floats (pitch, yaw, roll, posX, posY, posZ) into the buffer.
// `count` must be >= 6.
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
