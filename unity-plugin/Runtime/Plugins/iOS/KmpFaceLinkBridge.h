// Copyright (c) KMP-FaceLink contributors. Licensed under Apache-2.0.

// C bridge between Unity (DllImport) and KMP-FaceLink iOS (Swift/Kotlin Native).
//
// This header declares the extern "C" functions that Unity calls via
// [DllImport("__Internal")]. The implementation (.m/.mm) wraps the
// KMP-FaceLink XCFramework's Objective-C/Swift API.
//
// Build integration:
//   1. Add kmp_facelink.xcframework to the Unity Xcode project
//   2. This .m file is compiled into the Unity executable (static linking)
//   3. Unity calls these functions via P/Invoke

#ifndef KmpFaceLinkBridge_h
#define KmpFaceLinkBridge_h

#include <stdbool.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

bool KmpFaceLink_Initialize(bool enableFace, bool enableHands);
void KmpFaceLink_StartTracking(void);
void KmpFaceLink_StopTracking(void);
void KmpFaceLink_Dispose(void);
bool KmpFaceLink_IsTracking(void);

// Face tracking
int64_t KmpFaceLink_GetLatestFaceTimestamp(void);
void KmpFaceLink_GetLatestBlendShapes(float* blendShapes, int count);
void KmpFaceLink_GetLatestHeadTransform(float* transform, int count);

// Hand tracking
int64_t KmpFaceLink_GetLatestHandTimestamp(void);
int KmpFaceLink_GetLatestHandCount(void);
void KmpFaceLink_GetHandLandmarks(int handIndex, float* landmarks, int count);
int KmpFaceLink_GetHandedness(int handIndex);

#ifdef __cplusplus
}
#endif

#endif /* KmpFaceLinkBridge_h */
