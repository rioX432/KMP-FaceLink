# Ultra-Lightweight Hand Gesture Model Research

> Research summary for issue #699

## Status

Research complete (2026-04-09)

---

## Background

KMP-FaceLink targets Wasm and low-end Android devices as future platforms. On these targets, full MediaPipe hand landmark detection is too heavy to run at interactive frame rates. A July 2025 arXiv paper (arXiv:2507.10055) proposes an ultra-lightweight gesture classifier — 1,103 parameters, 7 KB quantized — that runs on top of pre-computed hand landmarks. The hypothesis was that this model could serve as a drop-in fallback classifier to enable gesture recognition on constrained platforms where full MediaPipe is unavailable.

---

## Paper Analysis

### Architecture

| Layer | Input → Output | Activation |
|---|---|---|
| FC-1 | 42 → 20 | ReLU |
| FC-2 | 20 → 10 | ReLU |
| FC-3 | 10 → N classes | Softmax |

- **Input**: 42 features (21 × 2D landmarks, x/y only — no z depth)
- **Parameter count**: 1,103
- **Model size after TFLite int8 quantization**: ~7 KB
- **Training target**: 8 robot-control gestures
- **Reported accuracy**: 93.5% on the 8-class task

### Accuracy Claims

The 93.5% figure is on 8 gestures from a single collection session with limited demographic diversity. No cross-session or cross-user generalization metrics are reported.

### Limitations and Quality Concerns

- **Small dataset**: number of training samples, data collection conditions, and train/test split methodology are not fully described
- **Inconsistent architecture description**: the paper's text and supplementary materials have discrepancies in the layer widths
- **No latency benchmarks**: no inference time measurements are provided for any target platform (CPU, Wasm, mobile)
- **Limited demographic coverage**: gesture recognition accuracy is known to vary significantly across hand shapes and demographics; this is not evaluated
- **No ablation study**: it is unclear how much accuracy degrades when z-depth is omitted vs. included
- **Narrow gesture set**: 8 robot-control gestures do not cover common VTuber/avatar control gestures (e.g., open/closed hand, peace, thumbs up)

---

## Compatibility Assessment

KMP-FaceLink's existing data models align well with the paper's input format:

| Paper Requirement | KMP-FaceLink Counterpart | Status |
|---|---|---|
| 21 MediaPipe hand landmarks | `HandJoint` (indices 0–20, matching MediaPipe) | **Direct match** |
| Wrist-centered normalization | `LandmarkFeatureExtractor.extract()` — translates wrist to origin, scales by max distance | **Compatible** (paper uses x/y 2D; extractor uses x/y/z 3D — trivial to project) |
| Float input vector | `LandmarkFeatureExtractor.FEATURE_DIMENSION = 63` (21 × 3) | **Superset** — paper uses 42 (21 × 2) |
| TFLite inference | Android: TFLite already used for MediaPipe models; iOS: LiteRT via CocoaPods | **Compatible** |

The paper's 42-element 2D input vector is a strict subset of `LandmarkFeatureExtractor`'s 63-element 3D output. Projection is straightforward: discard z coordinates.

---

## Value Analysis

### Why Incremental Value Is Low

The core issue is **which bottleneck this model actually addresses**:

| Stage | Compute Cost | Addressed by Paper Model |
|---|---|---|
| **Hand landmark detection** (21-joint localization from raw pixels) | **High** — CNN/graph model, ~3–15 ms on mid-range Android | **No** |
| **Gesture classification** (label assignment from 21 landmarks) | **Low** — template matching or small MLP, < 0.5 ms | **Yes** |

On Wasm and low-end devices, the bottleneck is landmark detection, not classification. Replacing the classification step with a 7 KB MLP provides negligible end-to-end latency improvement because the expensive step remains unchanged.

### What Would Actually Help

To enable gesture recognition on Wasm/low-end targets, the landmark detection model itself needs to be replaced with something lighter. Options in priority order:

1. **Lightweight landmark detector** (e.g., MoveNet-style hand model, or ONNX-exported MediaPipe graph) that runs in Wasm
2. **WebNN / ONNX Runtime Web** for hardware-accelerated Wasm inference (see `webnn-browser-npu.md`)
3. The paper's MLP pattern — useful only as the classification backend once (1) or (2) is solved

---

## Recommended Approach

Rather than porting this specific model (small dataset, quality concerns), extract the **architectural pattern** it demonstrates: a tiny pure-Kotlin MLP that accepts a normalized landmark vector and outputs a gesture label.

### `NeuralNetGestureStrategy` (Pure Kotlin)

- Implement a simple `FloatArray`-based dense layer forward pass in `commonMain` — no ML runtime dependency
- Weights are loaded from a bundled byte array or a resource file at runtime
- Input: 42 floats (2D landmarks from `LandmarkFeatureExtractor`, z discarded) or 63 floats (3D)
- Integrate as an optional `GestureStrategy` alongside the existing template-matching strategy

```
commonMain/
  gesture/
    GestureStrategy.kt          // sealed interface (already exists or can be added)
    NeuralNetGestureStrategy.kt // new: pure-Kotlin MLP forward pass
    GestureWeights.kt           // new: weight storage / loader
```

This approach:
- Has zero dependency on TFLite/ONNX runtime — works on all KMP targets including Wasm
- Allows weights trained on a larger, more diverse dataset to be swapped without code changes
- Provides a testable, auditable classification layer

---

## Implementation Plan

If this is prioritized (P3), suggested phases:

### Phase 1 — Foundation (1.5 days)
- Add `GestureStrategy` sealed interface to `commonMain`
- Implement `NeuralNetGestureStrategy` with configurable layer widths
- Unit tests with known weight matrices and synthetic inputs

### Phase 2 — Weight Pipeline (1.5 days)
- Collect or curate a gesture dataset covering target use cases (VTuber avatar: open/close, peace, thumbs up, fist, point, etc.)
- Train a 42→20→10→N MLP (can reuse the paper's architecture)
- Export weights as a compact binary format; add to `commonMain` resources

### Phase 3 — Integration & Benchmarks (1.5 days)
- Wire `NeuralNetGestureStrategy` into the existing gesture pipeline
- Benchmark accuracy vs. template matching on a held-out test set
- Verify on Wasm target (once Wasm target is unblocked by landmark detection work)

**Total estimate**: ~4.5 days

---

## Priority Assessment

**Downgraded from P2 to P3.**

Rationale:

1. **Wrong bottleneck**: the paper's model improves classification, not detection. Wasm/low-end feasibility depends on solving landmark detection first.
2. **Paper quality**: insufficient evidence of generalization; reimplementing with a curated dataset is necessary regardless.
3. **Pattern extraction is low-risk but not urgent**: the `NeuralNetGestureStrategy` pattern is straightforward to add once the gesture pipeline design stabilizes.

**Prerequisite**: Wasm target milestone (blocked on lightweight landmark detection). Revisit this issue after WebNN/ONNX-Web investigation (`webnn-browser-npu.md`) concludes.

---

## References

- arXiv:2507.10055 — "Ultra-Lightweight Hand Gesture Recognition for Robot Control" (July 2025)
- `docs/research/webnn-browser-npu.md` — WebNN and ONNX Runtime Web for Wasm acceleration
- `kmp-facelink/src/commonMain/kotlin/io/github/kmpfacelink/model/HandJoint.kt`
- `kmp-facelink/src/commonMain/kotlin/io/github/kmpfacelink/util/LandmarkFeatureExtractor.kt`
- [MediaPipe Hand Landmarker — landmark topology](https://developers.google.com/mediapipe/solutions/vision/hand_landmarker)
