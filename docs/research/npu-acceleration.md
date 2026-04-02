# NPU Acceleration via LiteRT Unified API

**Issue:** #468 (supersedes #303)
**Status:** Research complete (updated)
**Date:** 2026-04-02
**Previous research:** 2026-03-24

## Executive Summary

This document evaluates NPU (Neural Processing Unit) acceleration for face/hand/body tracking inference on Android. It updates the original research (#303) with significant new findings from Q1 2026.

**Key updates since last research:**

1. **MediaPipe v0.10.32 added NPU delegate configuration** (commit 6a19743, Jan 2026). The `BaseOptions` Java class now includes `NpuOptions` under `DelegateOptions`, alongside `CpuOptions` and `GpuOptions`. However, the NPU delegate is not yet validated in the switch logic and the public documentation still only lists CPU/GPU delegates.

2. **LiteRT GPU and NPU acceleration graduated to production** (Jan 28, 2026). The CompiledModel API with Qualcomm QNN and MediaTek NeuroPilot accelerators is now production-ready (though the repo still labels itself "version two alpha"). GA release expected at Google I/O May 2026.

3. **MediaPipe Tasks Vision Android artifact gap**: The latest Maven release for `com.google.mediapipe:tasks-vision` is 0.10.21 (Jan 2025). Versions 0.10.22-0.10.24 only included GenAI Tasks, not vision. Versions 0.10.32+ exist as GitHub releases and npm packages but their Maven availability for Android is unconfirmed. Our project uses 0.10.33 (likely from Google's Maven repo, not Maven Central).

4. **The fundamental constraint remains**: MediaPipe Tasks Vision manages its own inference pipeline internally. Even with `NpuOptions` added to the source, the integration appears incomplete -- the NPU case is missing from the delegate validation switch statement, and no public documentation or sample code exists for NPU delegate usage with FaceLandmarker/HandLandmarker.

**Bottom line:** NPU acceleration for MediaPipe Tasks Vision tracking is approaching but not yet usable. The recommended strategy is a two-track approach: (A) monitor MediaPipe for complete NPU delegate support (low effort, arrival likely mid-2026), and (B) prepare the architecture for direct LiteRT NPU inference as a fallback path (medium effort, available now).

---

## 1. What Changed Since Last Research

### 1.1 MediaPipe v0.10.32: NPU Delegate Configuration (Jan 2026)

MediaPipe v0.10.32 release notes explicitly state: "Allow users to configure NPU delegate" (Android). The source code at `mediapipe/tasks/java/com/google/mediapipe/tasks/core/BaseOptions.java` now includes:

- `BaseOptions.DelegateOptions.NpuOptions` class with a `dispatchLibraryDirectory` parameter
- The `NpuOptions` requires a path to the vendor dispatch library (QNN or NeuroPilot runtime)

However, critical gaps remain:
- The delegate validation switch statement only handles `CPU` and `GPU` cases -- `NPU` is absent
- No public documentation on how to use NPU with FaceLandmarker, HandLandmarker, or PoseLandmarker
- No sample code or integration guide exists
- The Android setup guide at `ai.google.dev` still lists only CPU and GPU as accepted delegate values

**Assessment:** This is infrastructure work in progress. Google is building the plumbing for NPU delegation in MediaPipe Tasks, but it is not yet functional for vision tasks.

### 1.2 LiteRT Production Graduation (Jan 2026)

On January 28, 2026, Google announced that LiteRT's GPU (MLDrift engine) and NPU acceleration capabilities have graduated from preview to production:

- **GPU**: 1.4x average performance improvement over TFLite delegate
- **NPU**: Up to 100x speedup over CPU on supported Snapdragon NPUs
- **Unified API**: One `CompiledModel` API routes workloads to GPU/NPU/CPU based on detected hardware
- **Zero-copy buffers**: Reduced memory management overhead
- **Qualcomm QNN**: Production-ready for Snapdragon 8 Gen 1 through 8 Elite
- **MediaTek NeuroPilot**: Production-ready for Dimensity 7300+
- **Google Tensor**: Experimental access (requires sign-up)

The LiteRT CompiledModel API supports both AOT (ahead-of-time) and on-device JIT compilation modes. GA release is expected at Google I/O in May 2026.

### 1.3 MediaPipe GitHub Issue #5744: NPU Support Roadmap

A Google team member confirmed NPU delegate support was delivered in v0.10.32. However, clarification revealed this is Android-only with no timeline for cross-platform expansion. The issue remained open through March 2026, suggesting the feature is not considered complete.

### 1.4 Competitor Landscape

- **InspireFace**: Open-source face recognition SDK supporting NPU via RKNN (Rockchip only), CUDA/TensorRT (NVIDIA). Not applicable to mobile Qualcomm/MediaTek.
- **Banuba**: Commercial face tracking SDK with GPU acceleration, no documented NPU support.
- **No competitor has shipped NPU-accelerated face tracking** on Qualcomm/MediaTek mobile devices as of April 2026. This remains a frontier opportunity.

---

## 2. Current Architecture (Unchanged)

The current `MediaPipeFaceTracker` uses MediaPipe's Tasks API with GPU/CPU fallback:

```
CameraX -> ImageProxy -> BitmapPool -> MediaPipe FaceLandmarker (GPU/CPU) -> BlendShapes + Landmarks
```

The `Delegate` enum supports only `CPU` and `GPU`. See [litert-2x-mediapipe-compatibility.md](litert-2x-mediapipe-compatibility.md) for the full compatibility analysis.

---

## 3. Updated Integration Strategy

### 3.1 Track A: MediaPipe Native NPU Delegate (Recommended Primary Track)

**Status:** Infrastructure added in v0.10.32, not yet functional
**Effort:** Low (when ready)
**Timeline estimate:** Mid-2026 (post Google I/O, aligned with LiteRT GA)

When MediaPipe completes NPU delegate integration:

```kotlin
// Expected API (not yet available)
val npuOptions = BaseOptions.DelegateOptions.NpuOptions.builder()
    .setDispatchLibraryDirectory(context.applicationInfo.nativeLibraryDir)
    .build()

val baseOptions = BaseOptions.builder()
    .setModelAssetPath("face_landmarker_v2.task")
    .setDelegateOptions(npuOptions)
    .build()
```

**Action items:**
1. Pin watch on MediaPipe releases for NPU delegate documentation
2. When available, extend `DelegateResolver` with NPU option
3. Update fallback chain: NPU -> GPU -> CPU

### 3.2 Track B: Direct LiteRT CompiledModel Pipeline (Fallback Track)

**Status:** API production-ready (LiteRT), requires significant integration work
**Effort:** High
**Timeline:** Available now, 4-8 weeks implementation

Bypass MediaPipe Tasks entirely and use LiteRT CompiledModel with NPU acceleration:

```kotlin
val npuAccelerator = Accelerator.create(
    context,
    Accelerator.Config.Builder()
        .setTarget(Accelerator.Target.NPU)
        .build()
)

val model = CompiledModel.create(
    context,
    "models/face_landmarker_v2.tflite",
    CompiledModel.Options.Builder()
        .addAccelerator(npuAccelerator)
        .build()
)
```

This requires:
- Extracting `.tflite` models from `.task` bundles
- Reimplementing MediaPipe preprocessing (image normalization, tensor layout)
- Reimplementing postprocessing (landmark extraction, blend shape calculation)
- Accuracy validation against MediaPipe baseline (RMSE < 0.02)

### 3.3 Decision Framework

| Condition | Action |
|---|---|
| MediaPipe ships functional NPU delegate (2026 H1) | Adopt Track A immediately |
| No MediaPipe NPU by end of 2026 H1 | Begin Track B implementation |
| Customer demands NPU before MediaPipe support | Begin Track B immediately |
| LiteRT GA at Google I/O (May 2026) confirms MediaPipe NPU | Wait for Track A |

---

## 4. LiteRT NPU Technical Details

### 4.1 Supported Accelerators

| Vendor | Accelerator | Compilation | Status |
|---|---|---|---|
| Qualcomm QNN | AI Engine Direct (v69-v81) | AOT + JIT | Production |
| MediaTek NeuroPilot | APU Compiler | AOT + JIT | Production |
| Google Tensor | Edge TPU | Experimental | Private preview |
| Samsung Exynos | NPU | Not supported | No LiteRT delegate |

### 4.2 Compilation Modes

**AOT (Ahead-of-Time):**
- Best for known target SoCs (e.g., shipping an app targeting Snapdragon 8 Elite)
- Reduces initialization costs and memory usage
- Requires pre-compilation per target chipset
- Can be distributed via Google Play AI Packs

**JIT (On-Device):**
- Platform-agnostic distribution
- Higher first-run latency (compilation on device)
- No preparation needed
- Better for broad device coverage

**Recommendation:** Use JIT compilation for the tracking SDK since we need broad device compatibility. AOT can be offered as an optimization for known flagship targets.

### 4.3 Quantization Requirements for NPU

| Format | Qualcomm HTP | MediaTek APU | Notes |
|---|---|---|---|
| INT8 (post-training) | Required | Required | Best NPU performance, slight accuracy loss |
| FP16 | Supported (slower) | Supported (slower) | Fallback if INT8 accuracy is insufficient |
| INT4 | Supported (Gen 3+) | Limited | GenAI models primarily |
| FP32 | CPU fallback only | CPU fallback only | No NPU acceleration |

MediaPipe's face landmark model is FP32 by default. For NPU acceleration via Track B, INT8 quantization is required. This adds a quantization validation step to ensure blend shape accuracy is maintained.

---

## 5. Benchmark Data (Unchanged from Previous Research)

### 5.1 Face/Hand Tracking Model Estimates

| Model | CPU (ms) | GPU (ms) | NPU (ms) | NPU Speedup |
|---|---|---|---|---|
| Hand Landmarks Full | 47.4 | ~8-12 | 1.0 | **47x** |
| Face Detection Short Range | ~12 | ~4 | ~1.2 | **10x** |
| Face Landmarks V2 (est.) | ~15-25 | ~5-8 | ~1.5-3 | **10-17x** |
| Pose Landmarks Full | ~30-50 | ~10-15 | ~3-5 | **10x** |

Source: Hackster.io benchmarks on Snapdragon 8 Elite with QNN delegate.

### 5.2 Power Efficiency (Primary Value Proposition)

| Delegate | Est. Power Draw | Battery per Hour | Session Duration |
|---|---|---|---|
| CPU | ~1.5-2.5W | ~30-40% | ~2.5-3.5h |
| GPU | ~0.8-1.5W | ~15-25% | ~4-6h |
| NPU | ~0.3-0.8W | ~5-12% | **~8-15+h** |

For VTuber streaming sessions lasting hours, NPU's power efficiency is the primary value -- not raw speed.

---

## 6. Device Compatibility

### 6.1 NPU-Capable Devices

| Tier | Chipsets | NPU TOPS | Coverage |
|---|---|---|---|
| Flagship 2025+ | SD 8 Elite, Dimensity 9400/9500 | 45-80 | ~10% |
| Flagship 2024 | SD 8 Gen 3, Dimensity 9300 | 35-45 | ~15% |
| Flagship 2023 | SD 8 Gen 2, Dimensity 9200 | 27-35 | ~15% |
| High-mid 2024+ | SD 7+ Gen 3, Dimensity 8300 | 13-20 | ~15% |
| Mid-range 2024+ | SD 7 Gen 3, Dimensity 7300 | 5-13 | ~10% |

**~65% of 2023+ Android devices** have some NPU capability.

### 6.2 Devices Without LiteRT NPU Support

| Category | Fallback |
|---|---|
| Google Pixel (Tensor G2-G4) | GPU (Tensor NPU experimental, no public delegate) |
| Samsung Exynos | GPU (no LiteRT delegate) |
| Older Qualcomm (pre-8 Gen 1) | GPU/CPU |
| Budget chipsets (Helio, Unisoc) | CPU only |

---

## 7. Recommended Next Steps

### 7.1 Immediate (No Code Changes)

1. **Monitor MediaPipe releases** for functional NPU delegate documentation and samples
2. **Monitor LiteRT GA** expected at Google I/O May 2026
3. **Track MediaPipe GitHub issue #5744** for NPU roadmap updates

### 7.2 Phase 1: NPU Capability Detection + Config (Low Effort)

Same as previously documented:
- Add `NpuCapabilityDetector` for QNN/NeuroPilot runtime detection
- Add `InferenceDelegate` enum (`AUTO`, `CPU`, `GPU`, `NPU`) to tracker configs
- Update `PowerModeManager` to recommend NPU in `LOW_POWER`/`BALANCED` modes
- No actual NPU inference yet

### 7.3 Phase 2: Adopt MediaPipe NPU Delegate OR Begin LiteRT Pipeline

**If MediaPipe NPU delegate ships (Track A):**
- Update `DelegateResolver` to include NPU option
- Add `NpuOptions` configuration with dispatch library path
- Test on Snapdragon 8 Gen 3+ and Dimensity 9300+ devices
- Validate blend shape accuracy vs GPU baseline

**If MediaPipe NPU delegate does not ship by mid-2026 (Track B):**
- Extract `.tflite` models from `.task` bundles
- Implement `NpuFaceTracker` using LiteRT `CompiledModel` with QNN
- Replicate preprocessing/postprocessing pipeline
- Benchmark and validate accuracy

### 7.4 Phase 3: Full NPU Pipeline + Power Optimization

- Complete NPU-accelerated face/hand/body landmark pipeline
- Integrate with `PowerModeManager` for NPU-aware power modes
- Optional `kmp-facelink-npu` Gradle module for QNN/NeuroPilot dependencies
- Benchmark harness in sample app (CPU vs GPU vs NPU side-by-side)

---

## 8. Risk Assessment (Updated)

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| MediaPipe NPU delegate remains incomplete through 2026 | Medium | Medium | Track B (direct LiteRT) as fallback |
| MediaPipe Tasks Vision Maven releases lag behind GitHub | High | Low | Use Google Maven repo or build from source |
| INT8 quantization reduces tracking precision | Medium | Medium | Validate RMSE < 0.02; fall back to FP16 if needed |
| NPU warmup latency delays first frame | Low | Low | Pre-warm during `start()` before camera binding |
| QNN/NeuroPilot API differences across vendors | Medium | Medium | Abstract behind `NpuAccelerator` interface |
| MediaPipe adds native NPU support, making Track B redundant | Medium | Low | Positive outcome -- migrate to native support |
| Google Tensor NPU remains experimental | High | Low | GPU fallback for Pixel devices |

---

## 9. Conclusion

The landscape has shifted significantly since the initial research:

1. **MediaPipe is actively building NPU delegate support** (v0.10.32 added `NpuOptions`), but it is not yet functional for vision tasks. Google I/O May 2026 is the likely milestone for completion.

2. **LiteRT's CompiledModel API with NPU is production-ready** for direct model inference, making Track B (bypass MediaPipe) a viable option if MediaPipe native support is delayed.

3. **No competitor has shipped NPU-accelerated face tracking** on mobile Qualcomm/MediaTek devices, maintaining our frontier opportunity.

4. **The primary value of NPU is power efficiency** (3-5x better than GPU), critical for long VTuber streaming sessions.

**Recommended action:** Wait for Google I/O May 2026 for clarity on MediaPipe NPU delegate timeline. In parallel, implement Phase 1 (NPU detection + config) which is low-effort and benefits both tracks. Defer Track B implementation unless customer demand requires it earlier.

---

## References

- [MediaPipe v0.10.32 Release Notes](https://github.com/google-ai-edge/mediapipe/releases/tag/v0.10.32)
- [MediaPipe NPU Support Roadmap (Issue #5744)](https://github.com/google-ai-edge/mediapipe/issues/5744)
- [MediaPipe NPU Support (Issue #5249)](https://github.com/google/mediapipe/issues/5249)
- [LiteRT NPU Acceleration](https://ai.google.dev/edge/litert/next/npu)
- [LiteRT GPU and NPU Graduation (Jan 2026)](https://www.aicerts.ai/news/google-litert-graduates-gpu-and-npu-acceleration/)
- [LiteRT QNN Accelerator (Qualcomm)](https://developers.googleblog.com/unlocking-peak-performance-on-qualcomm-npu-with-litert/)
- [LiteRT NeuroPilot Accelerator (MediaTek)](https://developers.googleblog.com/mediatek-npu-and-litert-powering-the-next-generation-of-on-device-ai/)
- [Qualcomm NPU with LiteRT](https://ai.google.dev/edge/litert/next/qualcomm)
- [MediaTek NPU with LiteRT](https://ai.google.dev/edge/litert/next/mediatek)
- [MediaPipe Tasks Vision Maven Gap (Issue #5978)](https://github.com/google-ai-edge/mediapipe/issues/5978)
- [TensorFlow 2.21 + LiteRT Announcement (Mar 2026)](https://www.marktechpost.com/2026/03/06/google-launches-tensorflow-2-21-and-litert-faster-gpu-performance-new-npu-acceleration-and-seamless-pytorch-edge-deployment-upgrades/)
- [Delegate QNN NPU in MediaPipe Graph (Forum)](https://discuss.ai.google.dev/t/delegate-calculator-nodes-to-qnn-npu-within-a-mediapipe-graph/86584)
- [Accelerating MediaPipe Models with Qualcomm (Hackster.io)](https://www.hackster.io/AlbertaBeef/accelerating-the-mediapipe-models-with-qualcomm-3247ed)
- [InspireFace (NPU face SDK competitor)](https://github.com/HyperInspire/InspireFace)
- [LiteRT 2.x MediaPipe Compatibility](litert-2x-mediapipe-compatibility.md)
