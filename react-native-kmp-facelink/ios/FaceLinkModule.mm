// Copyright (c) KMP-FaceLink contributors. Licensed under Apache-2.0.

// React Native Turbo Module for KMP-FaceLink (iOS).
//
// This ObjC++ module wraps the shared C bridge (kmpfacelink_bridge.h) and
// exposes face/hand tracking to JavaScript via the Turbo Module interface.
//
// Data is returned as binary NSData (mapped to ArrayBuffer in JS) to avoid
// serialization overhead. The JS side polls each requestAnimationFrame tick.

#import "FaceLinkModule.h"
#import "kmpfacelink_bridge.h"
#import <React/RCTBridgeModule.h>

// Binary protocol constants
static const int BLEND_SHAPE_COUNT = 52;
static const int HEAD_TRANSFORM_FLOATS = 6;
static const int FACE_BUFFER_SIZE = (BLEND_SHAPE_COUNT + HEAD_TRANSFORM_FLOATS) * sizeof(float) + 1;
static const int LANDMARKS_PER_HAND = 63;
static const int HAND_BLOCK_BYTES = LANDMARKS_PER_HAND * sizeof(float) + sizeof(int32_t);

@implementation FaceLinkModule

RCT_EXPORT_MODULE(FaceLinkModule)

+ (BOOL)requiresMainQueueSetup {
    return NO;
}

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(initialize:(BOOL)enableFace enableHands:(BOOL)enableHands) {
    bool result = KmpFaceLink_Initialize(enableFace, enableHands);
    return @(result);
}

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(start) {
    KmpFaceLink_StartTracking();
    return nil;
}

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(stop) {
    KmpFaceLink_StopTracking();
    return nil;
}

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(dispose) {
    KmpFaceLink_Dispose();
    return nil;
}

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(isTracking) {
    return @(KmpFaceLink_IsTracking());
}

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(getLatestFaceData) {
    // Check if there is face data available (timestamp > 0)
    int64_t timestamp = KmpFaceLink_GetLatestFaceTimestamp();
    if (timestamp == 0) {
        return nil;
    }

    // Allocate buffer: 52 blend shapes + 6 head transform + 1 byte isTracking
    uint8_t buffer[FACE_BUFFER_SIZE];
    float *floatBuffer = (float *)buffer;

    // Copy blend shapes (52 floats)
    KmpFaceLink_GetLatestBlendShapes(floatBuffer, BLEND_SHAPE_COUNT);

    // Copy head transform (6 floats)
    KmpFaceLink_GetLatestHeadTransform(floatBuffer + BLEND_SHAPE_COUNT, HEAD_TRANSFORM_FLOATS);

    // Set isTracking flag
    buffer[FACE_BUFFER_SIZE - 1] = KmpFaceLink_IsTracking() ? 1 : 0;

    return [NSData dataWithBytes:buffer length:FACE_BUFFER_SIZE];
}

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(getLatestHandData) {
    // Check if there is hand data available (timestamp > 0)
    int64_t timestamp = KmpFaceLink_GetLatestHandTimestamp();
    if (timestamp == 0) {
        return nil;
    }

    int handCount = KmpFaceLink_GetLatestHandCount();
    if (handCount <= 0) {
        return nil;
    }
    if (handCount > 2) handCount = 2;

    // Allocate buffer: int32 handCount + (63 floats + int32 handedness) per hand
    int totalSize = sizeof(int32_t) + handCount * HAND_BLOCK_BYTES;
    NSMutableData *data = [NSMutableData dataWithLength:totalSize];
    uint8_t *buffer = (uint8_t *)[data mutableBytes];

    // Write hand count
    int32_t handCountLE = CFSwapInt32HostToLittle((int32_t)handCount);
    memcpy(buffer, &handCountLE, sizeof(int32_t));

    // Write per-hand data
    for (int i = 0; i < handCount; i++) {
        int offset = sizeof(int32_t) + i * HAND_BLOCK_BYTES;
        float *landmarks = (float *)(buffer + offset);

        // Copy 63 landmark floats
        KmpFaceLink_GetHandLandmarks(i, landmarks, LANDMARKS_PER_HAND);

        // Write handedness
        int32_t handedness = CFSwapInt32HostToLittle((int32_t)KmpFaceLink_GetHandedness(i));
        memcpy(buffer + offset + LANDMARKS_PER_HAND * sizeof(float), &handedness, sizeof(int32_t));
    }

    return data;
}

@end
