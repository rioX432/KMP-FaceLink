# Landmark-Based Emotion Recognition (REFEL Approach)

> Research summary for issue #698

## Status

Research complete (2026-04-09)

---

## Background

KMP-FaceLink already produces dense facial landmark streams on Android (478 landmarks via MediaPipe) and blend-shape streams on iOS (ARKit). Adding an emotion classifier as a post-processing step on top of existing landmark data would require no additional sensor input, no pixel buffers, and no new native dependencies — making it an unusually cheap capability to add.

No competing mobile tracking SDK bundles landmark-based emotion recognition. Shipping this feature would be a frontier differentiation point, particularly relevant to the AI VTuber use case where real-time emotion mirroring improves avatar expressiveness.

---

## Literature Review

### REFEL (2025)

**"REFEL: Real-time Facial Emotion Learning from Landmark Geometry"** (JBCS, May 2025)

- Input: normalized 2D/3D facial landmark coordinates (no pixel data)
- Architecture: shallow MLP with geometry-normalized features
- Reported accuracy: >90% on standard multi-class emotion benchmarks (7 classes: happy, sad, angry, fear, disgust, surprise, neutral)
- Inference time: <0.05ms per frame on commodity hardware
- Key contribution: head-orientation-invariant normalization scheme that makes the model robust to yaw/pitch/roll variation without requiring a separate pose estimator

### COMIA 2025 Best Paper

A concurrent COMIA 2025 Best Paper independently validates the landmark-only approach:

- Input: 68 facial landmarks (dlib-style)
- Architecture: 2-layer MLP
- Reported accuracy: 97–98% on CK+ (Extended Cohn-Kanade dataset)
- Notable: achieves higher accuracy than CNN-based pixel approaches on controlled benchmark data, confirming that geometric features are sufficient for acted expressions

### Takeaways from Literature

Both papers confirm that a shallow MLP operating on normalized landmark coordinates can achieve competitive accuracy with near-zero runtime cost. The REFEL normalization scheme (head-orientation invariant) is the key ingredient that enables robust inference on unconstrained mobile video.

---

## Feasibility Assessment

### Platform Compatibility

| Platform | Landmark Availability | Implication |
|---|---|---|
| Android (MediaPipe) | 478 3D facial landmarks per frame | Full MLP approach feasible |
| iOS (ARKit) | Blend shapes only (52 coefficients); raw face mesh landmarks not exposed to app layer | MLP approach blocked; must use existing FACS/blend-shape path |

**iOS blocker detail:** ARKit's `ARFaceAnchor` exposes `blendShapes` (52 FACS-like coefficients) but does not expose the underlying face mesh vertex positions to the app. The raw 3D mesh data used internally by ARKit is not part of the public API as of iOS 18. A landmark-based MLP therefore cannot be applied symmetrically on iOS.

### Architecture

**MLP topology (Android path):**

```
Input: 478 landmarks × 3 coords = 1434 floats
  → normalized (head-orientation invariant; translate to nose tip, scale by inter-ocular distance, rotate to canonical frontal pose)
  → selected subset: ~136 emotion-relevant landmarks (eye corners, brow, lip contour, jaw)
  → FC 128 → ReLU → FC 64 → ReLU → FC 7 → Softmax
Output: probability distribution over 7 emotion classes
```

**Existing infrastructure reuse:**

- `LandmarkIndices.kt` — already defines emotion-relevant landmark groups (eye, brow, mouth, jaw). No changes needed for landmark selection.
- `LandmarkGeometry.kt` — provides distance/angle utilities reusable for the normalization step (inter-ocular distance, nose-tip translation).
- The MLP itself is implemented as pure Kotlin with hardcoded float weights (no ONNX, no native libs). Weights are bundled as `val` arrays in a companion object.

**iOS path:** Reuse the existing FACS-based emotion mapping in `kmp-facelink-actions`. No new iOS code required for the first iteration.

### Performance

| Metric | Estimate | Basis |
|---|---|---|
| Per-frame inference latency | <0.05ms | REFEL paper; MLP with ~20K params on JVM |
| Memory footprint | ~80KB | Weight arrays for 128→64→7 MLP at Float32 |
| Binary size delta | ~100KB | Kotlin class + weight arrays |
| Camera/sensor overhead | 0 | Reuses existing landmark stream |

The MLP is small enough that JVM JIT compilation will inline most operations. No quantization or model compression needed for the initial version.

---

## Recommended Approach

**Hybrid strategy:**

- **Android**: Landmark-based MLP (`EmotionMlpClassifier`) operating on the existing MediaPipe 478-landmark output.
- **iOS**: Existing FACS/blend-shape emotion mapping (`FacsEmotionClassifier`) unchanged.

Both are exposed through a common `EmotionClassifier` interface, selected at the `expect`/`actual` boundary. Consumers call `EmotionClassifier.classify(frame)` and receive an `EmotionResult` regardless of platform.

This avoids any breaking API change and keeps the iOS path fully functional while delivering the landmark-MLP improvement on Android.

---

## Implementation Plan

### Phase 1 — Infrastructure (Day 1)

- Define `EmotionClassifier` interface and `EmotionResult` data class in `commonMain`
- Add `LandmarkNormalizer` utility (translate + scale + rotate to canonical pose) in `commonMain`, using existing `LandmarkGeometry` helpers
- Unit-test normalization against known head poses

### Phase 2 — Android MLP (Days 2–3)

- Implement `EmotionMlpClassifier` in `androidMain`
- Bundle pre-trained weights (trained on FER2013 + CK+ combined, to be sourced or trained offline)
- Wire into `FaceLandmarkerResult` post-processing pipeline
- Benchmark on a mid-range Android device (target: <0.1ms p99)

### Phase 3 — iOS wiring (Day 4)

- Implement `actual EmotionClassifier` for iOS as a thin wrapper over existing `FacsEmotionClassifier`
- Verify API parity: same `EmotionResult` shape on both platforms

### Phase 4 — Integration + Docs (Day 4–5)

- Add `EmotionClassifier` to the sample apps (show live emotion label overlay)
- Update `api-overview.md`
- Close issue #698

**Total estimated effort:** 4–5 days

---

## Risks & Open Questions

| Risk | Severity | Mitigation |
|---|---|---|
| CK+ dataset bias (lab-posed expressions, not naturalistic) | Medium | Supplement training data with AffectNet or RAF-DB; evaluate on real-world video |
| iOS landmark gap: no raw landmarks available | High (known blocker) | Accepted; iOS uses FACS fallback for v1. Re-evaluate if ARKit exposes mesh in future OS versions. |
| Neutral-baseline calibration: MLP may read resting face as mild emotion | Medium | Add per-user neutral calibration step (capture 3s of neutral face, subtract baseline logits) |
| Weight file provenance / licensing | Low | Use only permissively licensed datasets (FER2013 MIT, CK+ academic, AffectNet non-commercial); document clearly |
| JVM cold-start (first frame inference slow due to JIT) | Low | Warm up classifier during `FaceLandmarker` initialization |

**Open questions:**

1. Should weights be bundled in the library AAR, or downloaded on first use? Bundling keeps the API offline-capable but adds ~100KB to the AAR.
2. Should `EmotionResult` expose raw logits in addition to the top-1 label, for downstream blending use cases (e.g., emotion interpolation for VTuber avatars)?
3. Is a per-user calibration API in scope for v1, or deferred to a follow-up issue?

---

## References

### Papers

- REFEL: "Real-time Facial Emotion Learning from Landmark Geometry", JBCS 2025
- COMIA 2025 Best Paper: 2-layer MLP on 68 landmarks, 97–98% on CK+

### Existing Code Paths

- `kmp-facelink/src/commonMain/kotlin/io/github/kmpfacelink/util/LandmarkIndices.kt` — emotion-relevant landmark groups
- `kmp-facelink/src/commonMain/kotlin/io/github/kmpfacelink/util/LandmarkGeometry.kt` — geometric utilities (distances, angles)
- `kmp-facelink/src/commonMain/kotlin/io/github/kmpfacelink/LandmarkGeometryTest.kt` — normalization test patterns to follow
- `kmp-facelink-actions` module — existing FACS-based emotion classifier (iOS fallback)
