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

// Binary protocol constants. Face wire format v2; see FaceLinkModule.kt for the layout.
static const int BLEND_SHAPE_COUNT = 52;
static const int HEAD_TRANSFORM_FLOATS = 6;
static const uint32_t FACE_WIRE_MAGIC = 0x4B4E4C46; // "FLNK"
static const uint16_t FACE_WIRE_VERSION = 2;
static const int FACE_HEADER_BYTES = 20;
static const int FACE_BUFFER_SIZE =
    FACE_HEADER_BYTES + (BLEND_SHAPE_COUNT + HEAD_TRANSFORM_FLOATS) * sizeof(float) + 1;
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

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(getLatestFaceDataV2) {
    // Check if there is face data available (timestamp > 0)
    int64_t timestamp = KmpFaceLink_GetLatestFaceTimestamp();
    if (timestamp == 0) {
        return nil;
    }

    uint8_t buffer[FACE_BUFFER_SIZE];

    // Header: magic, version, channel count, schema fingerprint, observed mask.
    // The mask is what keeps a 0-filled slot for a shape the tracker never produced from
    // reaching JavaScript as a measured value.
    uint32_t maskLow = 0;
    uint32_t maskHigh = 0;
    KmpFaceLink_GetLatestBlendShapeMask(&maskLow, &maskHigh);

    uint32_t magic = FACE_WIRE_MAGIC;
    uint16_t version = FACE_WIRE_VERSION;
    uint16_t channels = (uint16_t)BLEND_SHAPE_COUNT;
    int32_t fingerprint = KmpFaceLink_GetBlendShapeSchemaFingerprint();
    memcpy(buffer + 0, &magic, sizeof(magic));
    memcpy(buffer + 4, &version, sizeof(version));
    memcpy(buffer + 6, &channels, sizeof(channels));
    memcpy(buffer + 8, &fingerprint, sizeof(fingerprint));
    memcpy(buffer + 12, &maskLow, sizeof(maskLow));
    memcpy(buffer + 16, &maskHigh, sizeof(maskHigh));

    float *floatBuffer = (float *)(buffer + FACE_HEADER_BYTES);

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
