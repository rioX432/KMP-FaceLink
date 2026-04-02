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

### Current Implementation

- **File**: `kmp-facelink/src/iosMain/kotlin/io/github/kmpfacelink/internal/VisionHandTracker.kt`
- Uses `VNDetectHumanHandPoseRequest` with `revision = 1UL` (pinned)
- Maps 21 joints via `VisionHandLandmarkMapper.kt`
- Gesture classification via `GestureClassifier` uses joint positions for pose recognition

### Risk Assessment

| Area | Risk | Reason |
|------|------|--------|
| Landmark mapping | **Low** | Joint names unchanged; mapping code doesn't need changes |
| Gesture classification | **Medium** | `GestureClassifier` uses relative joint positions — shifted coordinates could affect thresholds |
| Revision pinning | **Currently safe** | We pin to `revision = 1UL`, so existing behavior is preserved on iOS 26 |
| Default revision change | **Watch** | If Apple changes `currentRevision` to 2 on iOS 26, apps not pinning a revision would get different results |

### Why We're Currently Safe

The project already pins `revision = HAND_POSE_REVISION` (= `1UL`) in `VisionHandTracker.kt`
line 76. This means:

- On iOS 26 devices, revision 1 (the old model) will still be used
- Joint locations will remain consistent with iOS 17-18
- No immediate breakage when users upgrade to iOS 26

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

### Phase 1: Verification (when iOS 26 SDK is available)

1. Check `VNDetectHumanHandPoseRequest.currentRevision` on iOS 26 to confirm the new default
2. Check `supportedRevisions` to see if revision 2 is listed
3. Verify that pinning `revision = 1UL` still works on iOS 26 (backward compatibility)
4. Compare joint locations between revision 1 and revision 2 for the same hand pose

### Phase 2: Support New Model (optional, for better accuracy)

1. Add runtime revision selection based on iOS version:
   - iOS 14-25: revision 1 (only option)
   - iOS 26+: revision 2 (new model) or revision 1 (legacy)
2. Re-calibrate `GestureClassifier` thresholds with revision 2 joint positions
3. Expose revision selection in `HandTrackerConfig` so consumers can choose
4. Update smoothing parameters if the new model's output characteristics differ

### Phase 3: Testing

1. Test gesture recognition accuracy on both revisions
2. Compare performance metrics (latency, memory) between revisions
3. Validate on physical iOS 26 devices
4. Ensure the iOS sample app works correctly with both models

## Recommendation

**No immediate action required.** The current revision pinning (`revision = 1UL`) protects
against unexpected behavior changes. When iOS 26 reaches stable release:

1. Test with the pinned revision 1 to confirm no regressions
2. Optionally adopt revision 2 for improved accuracy/performance
3. If adopting revision 2, re-validate gesture classification thresholds

Apple's guidance is clear: if you have trained classifiers (which `GestureClassifier` effectively
is, with hardcoded thresholds), retrain/recalibrate them with the new model.

## References

- [WWDC 2025 Session 272: Read documents using the Vision framework](https://developer.apple.com/videos/play/wwdc2025/272/)
- [Detecting Hand Poses with Vision (Apple Docs)](https://developer.apple.com/documentation/Vision/detecting-hand-poses-with-vision)
- [VNDetectHumanHandPoseRequest (Apple Docs)](https://developer.apple.com/documentation/vision/vndetecthumanhandposerequest)
- [DetectHumanHandPoseRequest (Swift API, Apple Docs)](https://developer.apple.com/documentation/vision/detecthumanhandposerequest)
- [WWDC 2024 Session: Discover Swift enhancements in the Vision framework](https://developer.apple.com/videos/play/wwdc2024/10163/)
