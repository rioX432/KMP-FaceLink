// Copyright (c) KMP-FaceLink contributors. Licensed under Apache-2.0.

// Stub implementation of the C bridge for KMP-FaceLink iOS integration.
//
// TODO: Replace this stub with real calls to the KMP-FaceLink XCFramework.
// The XCFramework exposes Kotlin classes via Kotlin/Native ObjC interop:
//   - KmpFaceLinkFaceTracker (wraps ARKitFaceTracker)
//   - KmpFaceLinkHandTracker (wraps VisionHandTracker)
//
// The implementation should:
//   1. Import the KmpFaceLink umbrella header from the XCFramework
//   2. Create tracker instances and collect Flow data on a background queue
//   3. Buffer the latest frame in a thread-safe manner
//   4. Return the buffered data when Unity polls

#import "KmpFaceLinkBridge.h"

// Placeholder — tracks whether we've been initialized
static bool _initialized = false;
static bool _tracking = false;

bool KmpFaceLink_Initialize(bool enableFace, bool enableHands) {
    // TODO: Create KMP-FaceLink tracker instances
    // KmpFaceLinkFaceTrackerConfig *config = [[KmpFaceLinkFaceTrackerConfig alloc] init];
    // _faceTracker = [KmpFaceLinkFaceTrackerFactory createWithConfig:config context:nil];
    _initialized = true;
    return true;
}

void KmpFaceLink_StartTracking(void) {
    if (!_initialized) return;
    // TODO: Call [_faceTracker start] and [_handTracker start]
    _tracking = true;
}

void KmpFaceLink_StopTracking(void) {
    if (!_initialized) return;
    // TODO: Call [_faceTracker stop] and [_handTracker stop]
    _tracking = false;
}

void KmpFaceLink_Dispose(void) {
    // TODO: Release tracker instances
    _tracking = false;
    _initialized = false;
}

bool KmpFaceLink_IsTracking(void) {
    return _tracking;
}

int64_t KmpFaceLink_GetLatestFaceTimestamp(void) {
    // TODO: Return buffered face frame timestamp
    return 0;
}

void KmpFaceLink_GetLatestBlendShapes(float* blendShapes, int count) {
    // TODO: Copy buffered blend shapes into the output array
    if (blendShapes == NULL || count <= 0) return;
    for (int i = 0; i < count; i++) {
        blendShapes[i] = 0.0f;
    }
}

void KmpFaceLink_GetLatestHeadTransform(float* transform, int count) {
    // TODO: Copy buffered head transform into the output array
    if (transform == NULL || count < 6) return;
    for (int i = 0; i < count; i++) {
        transform[i] = 0.0f;
    }
}

int64_t KmpFaceLink_GetLatestHandTimestamp(void) {
    // TODO: Return buffered hand frame timestamp
    return 0;
}

int KmpFaceLink_GetLatestHandCount(void) {
    // TODO: Return number of tracked hands from buffer
    return 0;
}

void KmpFaceLink_GetHandLandmarks(int handIndex, float* landmarks, int count) {
    // TODO: Copy buffered hand landmarks into the output array
    if (landmarks == NULL || count <= 0) return;
    for (int i = 0; i < count; i++) {
        landmarks[i] = 0.0f;
    }
}

int KmpFaceLink_GetHandedness(int handIndex) {
    // TODO: Return 0 for left, 1 for right
    return 1;
}
