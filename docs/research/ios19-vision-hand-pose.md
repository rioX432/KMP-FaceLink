# iOS 19 (iOS 26) Vision Framework Hand Pose Model Migration

## Summary

At WWDC 2025, Apple announced an updated hand pose detection model in the Vision framework
(Session 272: "Read documents using the Vision framework"). The new model ships with iOS 26
(marketed as iOS 19) and applies to `DetectHumanHandPoseRequest` /
`VNDetectHumanHandPoseRequest`.

### Key Changes

| Aspect | Details |
|--------|---------|
| Joint count | Still 21 joints (unchanged) |
| Joint names | Same joint names as before |
| Joint **locations** | **Changed** — joints are not in the same positions as the previous model |
| Model size | Smaller |
| Accuracy | Improved |
| Memory usage | Reduced |
| Latency | Lower |

The critical takeaway: **joint locations differ between the old and new model**. Although the
API surface (joint names, observation types) remains the same, the actual coordinate positions
returned for each joint have shifted due to the new underlying ML model.

## Impact on KMP-FaceLink

### Implementation Status: **Complete**

- **Tracker**: `VisionHandTracker.kt` — auto-selects revision via `HandPoseRevision` config
- **Config**: `HandTrackerConfig.handPoseRevision` — defaults to `AUTO` (v2 on iOS 26+, v1 otherwise)
- **Mapping**: `VisionHandLandmarkMapper.kt` — unchanged (joint names are the same across revisions)
- **Tracking data**: `HandTrackingData.activeHandPoseRevision` — reports which revision produced the frame
- **Gesture classification**: `GestureClassifier` — uses relative distance ratios, largely revision-invariant

### Risk Assessment

| Area | Risk | Reason |
|------|------|--------|
| Landmark mapping | **Low** | Joint names unchanged; mapping code doesn't need changes |
| Gesture classification | **Low-Medium** | Uses relative distance ratios (revision-invariant). Only `PINCH_DISTANCE_THRESHOLD` uses absolute coords and may need recalibration |
| Runtime revision selection | **Resolved** | `resolveHandPoseRevision()` selects v2 on iOS 26+, v1 on older; logs the selection |
| Consumer awareness | **Resolved** | `HandTrackingData.activeHandPoseRevision` lets consumers adapt to the active model |

## API Compatibility Analysis

### VN-prefixed vs Swift Vision API

Starting from iOS 18 / WWDC 2024, Apple introduced modernized Swift Vision APIs that drop the
`VN` prefix (e.g., `DetectHumanHandPoseRequest` instead of `VNDetectHumanHandPoseRequest`).
These new APIs leverage Swift concurrency (`async`/`await`).

| API | Availability | Status |
|-----|-------------|--------|
| `VNDetectHumanHandPoseRequest` | iOS 14+ | Still available, not formally deprecated |
| `DetectHumanHandPoseRequest` | iOS 18+ | Modern Swift concurrency API |

Since KMP-FaceLink uses Kotlin/Native interop (cinterop), we interact with the Objective-C API
(`VNDetectHumanHandPoseRequest`). The VN-prefixed API remains available and functional.

### Revision History

| Revision | Introduced | Notes |
|----------|-----------|-------|
| 1 | iOS 14 (2020) | Original hand pose model |
| 2 (expected) | iOS 26 (2025) | New smaller, more accurate model with shifted joint locations |

**Note**: As of April 2026, the .NET/MAUI iOS bindings for iOS 26 SDK still only list
`VNDetectHumanHandPoseRequestRevision.One` in the enum. The new revision may be exposed
differently (e.g., through the Swift-native `DetectHumanHandPoseRequest` API) or may not have
been reflected in third-party bindings yet. Verification on actual iOS 26 SDK headers is needed.

## Migration Plan

### Phase 1: Runtime revision selection — **Done**

1. ~~Add runtime revision selection based on iOS version~~ — `resolveHandPoseRevision()` in `VisionHandTracker`
2. ~~Expose revision selection in `HandTrackerConfig`~~ — `HandTrackerConfig.handPoseRevision` (AUTO/V1/V2)
3. ~~Report active revision to consumers~~ — `HandTrackingData.activeHandPoseRevision`

### Phase 2: Verification (on physical iOS 26 device)

1. Confirm `VNDetectHumanHandPoseRequest.currentRevision` is 2 on iOS 26
2. Confirm `supportedRevisions` contains both 1 and 2
3. Verify revision 1 fallback still works on iOS 26
4. Compare joint locations between revision 1 and 2 for the same hand pose
5. Validate `PINCH_DISTANCE_THRESHOLD` accuracy on revision 2

### Phase 3: Testing

1. Test gesture recognition accuracy on both revisions
2. Compare performance metrics (latency, memory) between revisions
3. Validate on physical iOS 26 devices
4. Ensure the iOS sample app works correctly with both models

## Remaining Work

- **Pinch threshold calibration**: `GestureClassifier.PINCH_DISTANCE_THRESHOLD` (0.06f) uses
  absolute normalized coordinates. If revision 2 shifts the coordinate scale, this needs
  recalibration on a physical iOS 26 device.
- **Smoothing parameters**: The new model may have different noise characteristics; smoothing
  config may benefit from revision-specific tuning.

## References

- [WWDC 2025 Session 272: Read documents using the Vision framework](https://developer.apple.com/videos/play/wwdc2025/272/)
- [Detecting Hand Poses with Vision (Apple Docs)](https://developer.apple.com/documentation/Vision/detecting-hand-poses-with-vision)
- [VNDetectHumanHandPoseRequest (Apple Docs)](https://developer.apple.com/documentation/vision/vndetecthumanhandposerequest)
- [DetectHumanHandPoseRequest (Swift API, Apple Docs)](https://developer.apple.com/documentation/vision/detecthumanhandposerequest)
- [WWDC 2024 Session: Discover Swift enhancements in the Vision framework](https://developer.apple.com/videos/play/wwdc2024/10163/)
