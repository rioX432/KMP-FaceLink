# Monocular Depth Estimation for Android (LiteRT/TFLite)

> Research summary for issue #898 (part of epic #892)

Last updated: 2026-06-28

## Status

Research complete — **conditional GO**, pending on-device latency validation (PoC).
Scope of this document: model survey, candidate selection, license screening, and
integration design. **No prototype code and no on-device benchmark numbers are
included** — the 30 ms decision criterion (#898/#892) requires real-device
measurement on a Snapdragon 7 Gen 1+ class device, which must be done in the PoC
phase before committing to full implementation.

---

## Background & Goal

iOS gets real depth from TrueDepth/LiDAR (ARKit face 3D, `VNDetectHumanBodyPose3DRequest`).
Android has no depth sensor and relies on 2D→3D heuristics. Epic #892 aims to close this gap
with a software monocular depth model feeding the tracking pipeline:

- **#897 (done):** `PoseLiftingStrategy` wired into `MediaPipeBodyTracker`
  (`onPoseLandmarkerResult` → `poseLiftingStrategy.estimateDepth(landmarks)`).
- **#898 (this doc):** select a depth model + integration design.
- **#899 (next):** use depth output to correct hand-tracking Z.

**Hard requirement:** model must run via **LiteRT (TFLite)**, not ONNX — the Android
source set has no ONNX runtime (only the JVM/desktop `OnnxFaceTracker` does).

---

## Critical Caveat on All Latency Numbers

Almost every hard mobile latency figure publicly available comes from **Qualcomm AI Hub**,
measured on the Qualcomm **NPU via the QNN/HTP runtime** — *not* via the generic LiteRT
**GPU delegate** or NNAPI that a vanilla Android app would use. These are best-case
Snapdragon-NPU figures and **do not transfer directly** to a LiteRT GPU-delegate app on
arbitrary devices.

> Source: https://huggingface.co/qualcomm/Depth-Anything-V2 , https://huggingface.co/qualcomm/Midas-V2

**Generic LiteRT GPU/NNAPI latency for these models on non-Qualcomm SoCs is UNVERIFIED**
and is exactly what the PoC must measure.

---

## Candidate Comparison

| Model | Params | TFLite/LiteRT export | Reported latency (NPU, see caveat) | File size | Output | License | Commercial? |
|---|---|---|---|---|---|---|---|
| **Depth Anything V2 – Small (ViT-S)** | 24.7M | ✅ Official (Qualcomm AI Hub) | float 38.9 ms (SD 8 Gen 3 NPU); w8a16 12.5 ms (SD 8 Elite Gen 5 NPU) | 94.3 MB float (quantizable) | Relative (affine-invariant) | **Apache-2.0** | ✅ **Yes** (see dataset caveat) |
| DA V2 – Base/Large/Giant | 97M / 335M / 1.3B | ❌ no standard mobile TFLite | — (too heavy for real-time mobile) | large | Relative | **CC-BY-NC-4.0** | ❌ No |
| **MiDaS v3.1 ("Midas-V2" small on AI Hub)** | 16.6M | ✅ Official (Qualcomm AI Hub) | w8a8 0.74 ms (SD 8 Gen 3 NPU), 256×256 | 63.2 MB float / 16.9 MB w8a8 | Relative | **MIT** | ✅ **Yes** |
| Depth Anything V1 (ViT-S) | ~25M | ⚠️ community ONNX only (manual TFLite conv.) | n/a | n/a | Relative | Apache-2.0 (verify per ckpt) | ✅ (baseline only) |
| Apple Depth Pro | ~504M (UNVERIFIED) | ❌ no mobile export | 0.3 s @ 2.25 MP on desktop GPU | ~1.9 GB class | **Metric** | apple-amlr | ❌ No |
| FastDepth (2019) | MobileNet-based | ⚠️ author release, no maintained TFLite | 27 fps Jetson TX2 CPU | small | Metric, **NYU indoor only** | BSD-style | ⚠️ poor generalization |
| **MediaPipe Tasks depth API** | — | **DOES NOT EXIST** | — | — | — | — | — |

> DA-V2 license split: https://github.com/DepthAnything/Depth-Anything-V2/issues/162 ·
> MiDaS MIT: https://community.intel.com/t5/Blogs/Tech-Innovation/Artificial-Intelligence-AI/Intel-Labs-Releases-Models-for-Computer-Vision-Depth-Estimation/post/1468771 ·
> MediaPipe Tasks list (no depth): https://developers.google.com/edge/mediapipe/solutions/tasks

---

## Key Findings

### 1. License is the primary filter — it eliminates most models

- **Depth Anything V2 license is split by variant.** **Small = Apache-2.0 (commercial OK)**;
  **Base/Large/Giant = CC-BY-NC-4.0 (non-commercial only)**. Only the Small variant is usable
  in a commercial SDK.
  - https://huggingface.co/depth-anything/Depth-Anything-V2-Small (license: `apache-2.0`)
  - https://github.com/DepthAnything/Depth-Anything-V2/issues/162
  - ⚠️ **Open dataset-license question even for Small** — flag for legal review before shipping:
    https://github.com/DepthAnything/Depth-Anything-V2/issues/320
- **MiDaS = MIT** — safest license, no caveats.
- **Apple Depth Pro = apple-amlr (non-commercial)** and **DA-V2 Base+ = CC-BY-NC** → both
  excluded regardless of quality.

### 2. MediaPipe provides no depth task — depth must be a custom LiteRT model

The current MediaPipe Tasks vision list (object detection, segmentation, gesture, hand/face/pose
landmark, holistic, image gen…) contains **no depth estimation task**. Depth must be run as a
**custom LiteRT model outside the Tasks API**.
> https://developers.google.com/edge/mediapipe/solutions/tasks

### 3. Official mobile TFLite exists only via Qualcomm AI Hub

Both viable models (DA-V2 Small, MiDaS "Midas-V2") have **official TFLite exports on
Qualcomm AI Hub**. The community Android repo (shubham0204) runs DA-V1/V2 but via **ONNX, not
TFLite** — not usable for our androidMain constraint.
> https://huggingface.co/qualcomm/Depth-Anything-V2 · https://huggingface.co/qualcomm/Midas-V2 ·
> https://github.com/shubham0204/Depth-Anything-Android

### 4. GPU contention against HandLandmarker is the main engineering risk

- LiteRT GPU delegate must be used **on the same thread that initialized the interpreter**;
  HandLandmarker (GPU, live-stream) + a depth model (GPU) **serialize on one GPU queue** within
  the ~33 ms/frame budget at 30 fps.
  > https://github.com/google/mediapipe/issues/2286 · https://ai.google.dev/edge/litert/android/gpu
- There is a real **GPU↔CPU memory-copy cost** per delegate boundary crossing — avoid per-frame
  round trips.
  > https://ai.google.dev/edge/litert/performance/delegates
- **Mitigation:** run HandLandmarker on GPU at full camera rate; run depth at a **reduced rate /
  lower resolution (256×256) on a separate thread**, ideally on a different compute unit
  (NPU/CPU) to avoid stalling hand tracking. (Engineering recommendation synthesized from the
  cited delegate/thread facts — not a single benchmark.)

---

## Recommendation

**Primary candidate: Depth Anything V2 Small (Apache-2.0), TFLite from Qualcomm AI Hub.**
Best accuracy among commercially-licensed mobile-ready models; official TFLite; quantizable
(94.3 MB float → ~24 MB w8a16 class) to meet the <100 MB budget.

**Fallback: MiDaS "Midas-V2" (MIT).** Use if (a) DA-V2 Small's generic-GPU latency exceeds the
30 ms budget, or (b) legal rejects the DA-V2 dataset caveat. Smaller/faster, lower accuracy,
zero license risk.

**Quantization is likely mandatory** to hit latency + memory on mid-range devices — plan to
evaluate w8a16/w8a8 in the PoC, not just float.

---

## Integration Design (grounded in current code)

### Architectural finding: the existing `PoseLiftingStrategy` interface is insufficient

`PoseLiftingStrategy.estimateDepth(landmarks: List<BodyLandmarkPoint>)`
(`commonMain/.../util/PoseLiftingStrategy.kt`) takes **only landmarks — no image**. A depth
**model** needs the camera frame to produce a depth map. So the depth path **cannot** reuse the
existing landmark-only lifting interface.

**Proposed shape:**

1. **New androidMain abstraction** — `MonocularDepthEstimator` producing a depth map from the
   frame, e.g.:
   ```
   internal interface MonocularDepthEstimator {
       fun estimate(bitmap: Bitmap): DepthMap   // dense, low-res
       fun release()
   }
   // LiteRT-backed impl: LiteRtDepthEstimator(GPU delegate, owning thread)
   ```
2. **Sample depth at landmark pixel positions** (cheaper than dense fusion) to correct
   `BodyLandmarkPoint.z` / `HandLandmarkPoint.z`. Full dense-map fusion is a later option;
   per-landmark sampling is the latency-friendly default (one of #898's open tradeoffs).
3. **Config:** add an opt-in flag (depth disabled by default → no regression when off, per
   #892 acceptance criteria). Adding a `LiftingStrategy.DEPTH_MODEL` enum value is tempting for
   consistency with `Body3DConfig`, but note (1): the model path needs a different (image-aware)
   injection point than the current enum-driven landmark lifters, so a separate config field is
   cleaner than overloading the enum.

### Frame lifecycle constraint

Bitmaps flow as **RGBA_8888 with pooling/recycling** (`CameraPipelineHelper`,
`returnBitmapAfterResult` / `imageConverter.returnBitmap`). Depth inference must **consume the
bitmap before it is recycled, or take a copy** — and must run **decoupled from** the MediaPipe
async callback cadence so depth backpressure never blocks hand/body tracking.

### Pipeline (target)

```
CameraX Frame → Bitmap (RGBA_8888, pooled)
  ├── MediaPipe {Hand,Pose}Landmarker.detectAsync()   [existing, GPU, full rate]
  └── MonocularDepthEstimator.estimate(bitmap)         [new, reduced rate/res, separate thread]
        → DepthMap → sample at landmark pixels
        → correct landmark.z  (body lifting / hand Z #899)
```

---

## Go / No-Go for PoC (decision criteria from #898/#892)

**Proceed to full implementation if the PoC shows:**
- [ ] Selected model < 30 ms on Snapdragon 7 Gen 1 via **LiteRT GPU delegate** (or NPU if
      reliably available) — **this is the unverified number that gates the epic**.
- [ ] Depth provides meaningful differentiation at hand/body landmark positions.
- [ ] Total memory overhead < 100 MB (likely requires quantization).
- [ ] No hand-tracking FPS regression with depth running in parallel.

**Abort if:** all models exceed 50 ms on the target device class, or depth values are too noisy
to improve tracking accuracy.

---

## Open Questions / UNVERIFIED

- Generic **LiteRT GPU/NNAPI latency** for DA-V2 Small & MiDaS on non-Qualcomm mid-range SoCs
  (the gating metric — PoC must measure).
- DA-V2 Small **training-dataset license risk** (open upstream question #320) — needs legal sign-off.
- Apple Depth Pro exact parameter count (~504M from a secondary blog, not the paper).

---

## References

- Depth Anything V2: https://github.com/DepthAnything/Depth-Anything-V2 ·
  Small (Apache-2.0): https://huggingface.co/depth-anything/Depth-Anything-V2-Small ·
  TFLite: https://huggingface.co/qualcomm/Depth-Anything-V2
- MiDaS v3.1: https://arxiv.org/abs/2307.14460 ·
  TFLite (Midas-V2): https://huggingface.co/qualcomm/Midas-V2 ·
  MIT statement: https://community.intel.com/t5/Blogs/Tech-Innovation/Artificial-Intelligence-AI/Intel-Labs-Releases-Models-for-Computer-Vision-Depth-Estimation/post/1468771
- MediaPipe Tasks (no depth): https://developers.google.com/edge/mediapipe/solutions/tasks
- LiteRT GPU delegate: https://ai.google.dev/edge/litert/android/gpu ·
  delegate overhead: https://ai.google.dev/edge/litert/performance/delegates
- MediaPipe GPU thread affinity: https://github.com/google/mediapipe/issues/2286
- Community Android (ONNX) reference: https://github.com/shubham0204/Depth-Anything-Android
- Existing code: `PoseLiftingStrategy.kt`, `Body3DConfig.kt`, `MediaPipeBodyTracker.kt`,
  `CameraPipelineHelper.kt`, `SharedCameraSession.kt`
