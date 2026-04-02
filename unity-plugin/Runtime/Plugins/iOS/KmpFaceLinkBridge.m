// Copyright (c) KMP-FaceLink contributors. Licensed under Apache-2.0.

// C bridge between Unity (DllImport) and KMP-FaceLink iOS (Kotlin/Native).
//
// Architecture:
//   Unity C# -> [DllImport("__Internal")] -> This bridge -> KMP-FaceLink XCFramework (K/N ObjC)
//
// The bridge creates KMP-FaceLink tracker instances via the K/N-generated ObjC API,
// uses FlowCollectors helpers (from iosMain/unity/) to collect Flow emissions
// on background threads, and buffers the latest frame data for thread-safe
// polling by Unity's main thread.
//
// K/N ObjC naming convention (framework baseName = "KMPFaceLink"):
//   Kotlin class FaceTracker      -> ObjC protocol KMPFaceLinkFaceTracker
//   Top-level fun createFaceTracker -> ObjC class KMPFaceLinkFaceTrackerFactory_iosKt
//   Kotlin data class HeadTransform -> ObjC class KMPFaceLinkHeadTransform
//   Kotlin enum BlendShape          -> ObjC class KMPFaceLinkBlendShape
//
// Build integration:
//   1. Add KMPFaceLink.xcframework to the Unity Xcode project
//   2. This .m file is compiled into the Unity executable (static linking)
//   3. Unity calls these functions via P/Invoke

#import "KmpFaceLinkBridge.h"
#import <KMPFaceLink/KMPFaceLink.h>
#import <os/lock.h>

// -- Internal state --

static id<KMPFaceLinkFaceTracker> _faceTracker = nil;
static id<KMPFaceLinkHandTracker> _handTracker = nil;
static id<KMPFaceLinkCancellable> _faceCancellable = nil;
static id<KMPFaceLinkCancellable> _handCancellable = nil;

static bool _initialized = false;
static bool _tracking = false;

// Face data buffer (protected by _faceLock)
static os_unfair_lock _faceLock = OS_UNFAIR_LOCK_INIT;
static float _blendShapes[52];
static float _headTransform[6]; // pitch, yaw, roll, posX, posY, posZ
static int64_t _faceTimestamp = 0;

// Hand data buffer (protected by _handLock)
static os_unfair_lock _handLock = OS_UNFAIR_LOCK_INIT;
static int _handCount = 0;
static float _handLandmarks[2][63]; // 2 hands * 21 joints * 3 coords
static int _handedness[2];          // 0=left, 1=right, 2=unknown
static int64_t _handTimestamp = 0;

// -- Helpers --

static void copyBlendShapesToBuffer(NSDictionary<KMPFaceLinkBlendShape *, NSNumber *> *blendShapeMap) {
    if (blendShapeMap == nil) return;
    for (KMPFaceLinkBlendShape *shape in blendShapeMap) {
        NSNumber *value = blendShapeMap[shape];
        int32_t idx = shape.ordinal;
        if (idx >= 0 && idx < 52) {
            _blendShapes[idx] = value ? value.floatValue : 0.0f;
        }
    }
}

static void copyHeadTransformToBuffer(KMPFaceLinkHeadTransform *ht) {
    if (ht == nil) {
        memset(_headTransform, 0, sizeof(_headTransform));
        return;
    }
    _headTransform[0] = ht.pitch;
    _headTransform[1] = ht.yaw;
    _headTransform[2] = ht.roll;
    _headTransform[3] = ht.positionX;
    _headTransform[4] = ht.positionY;
    _headTransform[5] = ht.positionZ;
}

static void copyHandLandmarksToBuffer(NSArray<KMPFaceLinkHandLandmarkPoint *> *landmarks, int handIdx) {
    if (landmarks == nil || handIdx < 0 || handIdx >= 2) return;
    for (KMPFaceLinkHandLandmarkPoint *lm in landmarks) {
        int32_t jointIdx = lm.joint.ordinal;
        if (jointIdx >= 0 && jointIdx < 21) {
            int base = jointIdx * 3;
            _handLandmarks[handIdx][base] = lm.x;
            _handLandmarks[handIdx][base + 1] = lm.y;
            _handLandmarks[handIdx][base + 2] = lm.z;
        }
    }
}

// -- Public C API --

bool KmpFaceLink_Initialize(bool enableFace, bool enableHands) {
    if (_initialized) return true;

    KMPFaceLinkPlatformContext *context = [[KMPFaceLinkPlatformContext alloc] init];

    if (enableFace) {
        // Use default config for simplicity
        KMPFaceLinkFaceTrackerConfig *config = [[KMPFaceLinkFaceTrackerConfig alloc] init];
        _faceTracker = [KMPFaceLinkFaceTrackerFactory_iosKt
            createFaceTrackerPlatformContext:context config:config];
    }

    if (enableHands) {
        KMPFaceLinkHandTrackerConfig *config = [[KMPFaceLinkHandTrackerConfig alloc] init];
        _handTracker = [KMPFaceLinkHandTrackerFactory_iosKt
            createHandTrackerPlatformContext:context config:config];
    }

    memset(_blendShapes, 0, sizeof(_blendShapes));
    memset(_headTransform, 0, sizeof(_headTransform));
    memset(_handLandmarks, 0, sizeof(_handLandmarks));

    _initialized = true;
    return true;
}

void KmpFaceLink_StartTracking(void) {
    if (!_initialized) return;

    // Start face tracker and collect Flow via FlowCollectors helper
    if (_faceTracker != nil) {
        [_faceTracker startWithCompletionHandler:^(NSError * _Nullable error) {
            if (error) {
                NSLog(@"[KmpFaceLink] Face tracker start failed: %@", error);
                return;
            }

            // Collect face tracking data Flow using the iosMain helper
            _faceCancellable = [KMPFaceLinkFlowCollectorsKt
                collectFaceTrackingFlowFlow:[_faceTracker trackingData]
                callback:^KMPFaceLinkStdlibUnit *(KMPFaceLinkFaceTrackingData *data) {
                    os_unfair_lock_lock(&_faceLock);
                    copyBlendShapesToBuffer(data.blendShapes);
                    copyHeadTransformToBuffer(data.headTransform);
                    _faceTimestamp = data.timestampMs;
                    os_unfair_lock_unlock(&_faceLock);
                    return nil;
                }];
        }];
    }

    // Start hand tracker and collect Flow
    if (_handTracker != nil) {
        [_handTracker startWithCompletionHandler:^(NSError * _Nullable error) {
            if (error) {
                NSLog(@"[KmpFaceLink] Hand tracker start failed: %@", error);
                return;
            }

            _handCancellable = [KMPFaceLinkFlowCollectorsKt
                collectHandTrackingFlowFlow:[_handTracker trackingData]
                callback:^KMPFaceLinkStdlibUnit *(KMPFaceLinkHandTrackingData *data) {
                    os_unfair_lock_lock(&_handLock);
                    NSArray<KMPFaceLinkTrackedHand *> *hands = data.hands;
                    _handCount = (int)hands.count;
                    _handTimestamp = data.timestampMs;
                    for (int i = 0; i < _handCount && i < 2; i++) {
                        KMPFaceLinkTrackedHand *hand = hands[i];
                        _handedness[i] = hand.handedness.ordinal;
                        copyHandLandmarksToBuffer(hand.landmarks, i);
                    }
                    os_unfair_lock_unlock(&_handLock);
                    return nil;
                }];
        }];
    }

    _tracking = true;
}

void KmpFaceLink_StopTracking(void) {
    if (!_initialized) return;

    // Cancel Flow collection
    if (_faceCancellable != nil) {
        [_faceCancellable cancel];
        _faceCancellable = nil;
    }
    if (_handCancellable != nil) {
        [_handCancellable cancel];
        _handCancellable = nil;
    }

    // Stop trackers
    if (_faceTracker != nil) {
        [_faceTracker stopWithCompletionHandler:^(NSError * _Nullable error) {
            if (error) NSLog(@"[KmpFaceLink] Face tracker stop error: %@", error);
        }];
    }
    if (_handTracker != nil) {
        [_handTracker stopWithCompletionHandler:^(NSError * _Nullable error) {
            if (error) NSLog(@"[KmpFaceLink] Hand tracker stop error: %@", error);
        }];
    }

    _tracking = false;
}

void KmpFaceLink_Dispose(void) {
    KmpFaceLink_StopTracking();

    if (_faceTracker != nil) {
        [_faceTracker release_];
        _faceTracker = nil;
    }
    if (_handTracker != nil) {
        [_handTracker release_];
        _handTracker = nil;
    }
    _initialized = false;
}

bool KmpFaceLink_IsTracking(void) {
    return _tracking;
}

// -- Face data getters --

int64_t KmpFaceLink_GetLatestFaceTimestamp(void) {
    os_unfair_lock_lock(&_faceLock);
    int64_t ts = _faceTimestamp;
    os_unfair_lock_unlock(&_faceLock);
    return ts;
}

void KmpFaceLink_GetLatestBlendShapes(float* blendShapes, int count) {
    if (blendShapes == NULL || count <= 0) return;
    int copyCount = count < 52 ? count : 52;
    os_unfair_lock_lock(&_faceLock);
    memcpy(blendShapes, _blendShapes, copyCount * sizeof(float));
    os_unfair_lock_unlock(&_faceLock);
}

void KmpFaceLink_GetLatestHeadTransform(float* transform, int count) {
    if (transform == NULL || count < 6) return;
    os_unfair_lock_lock(&_faceLock);
    memcpy(transform, _headTransform, 6 * sizeof(float));
    os_unfair_lock_unlock(&_faceLock);
}

// -- Hand data getters --

int64_t KmpFaceLink_GetLatestHandTimestamp(void) {
    os_unfair_lock_lock(&_handLock);
    int64_t ts = _handTimestamp;
    os_unfair_lock_unlock(&_handLock);
    return ts;
}

int KmpFaceLink_GetLatestHandCount(void) {
    os_unfair_lock_lock(&_handLock);
    int count = _handCount;
    os_unfair_lock_unlock(&_handLock);
    return count;
}

void KmpFaceLink_GetHandLandmarks(int handIndex, float* landmarks, int count) {
    if (landmarks == NULL || count <= 0 || handIndex < 0 || handIndex >= 2) return;
    int copyCount = count < 63 ? count : 63;
    os_unfair_lock_lock(&_handLock);
    memcpy(landmarks, _handLandmarks[handIndex], copyCount * sizeof(float));
    os_unfair_lock_unlock(&_handLock);
}

int KmpFaceLink_GetHandedness(int handIndex) {
    if (handIndex < 0 || handIndex >= 2) return 1;
    os_unfair_lock_lock(&_handLock);
    int result = _handedness[handIndex];
    os_unfair_lock_unlock(&_handLock);
    return result;
}
