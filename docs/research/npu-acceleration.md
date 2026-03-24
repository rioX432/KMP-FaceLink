# NPU Acceleration via LiteRT QNN Accelerator

**Issue:** #303
**Status:** Research complete
**Date:** 2026-03-24

## Executive Summary

This document evaluates NPU (Neural Processing Unit) acceleration for face and hand tracking inference on Android via the LiteRT QNN Accelerator (Qualcomm) and LiteRT NeuroPilot Accelerator (MediaTek). NPU offloading can deliver up to 30-60x speedup over CPU and 10x over GPU for quantized vision models, while consuming significantly less power — critical for VTuber streaming sessions lasting hours.

**Key finding:** MediaPipe's FaceLandmarker and HandLandmarker use `.task` model bundles built on TFLite/LiteRT. The LiteRT QNN delegate can accelerate these models on Qualcomm NPUs with minimal code changes — the primary integration point is swapping the `Delegate.GPU` for a QNN `CompiledModel` path when the hardware supports it. However, MediaPipe's high-level Tasks API does not yet expose NPU delegate selection directly; a lower-level LiteRT Interpreter approach or a custom MediaPipe graph is required for full NPU delegation.

---

## 1. Current Architecture

### 1.1 Android Tracking Pipeline

The current `MediaPipeFaceTracker` (and `MediaPipeHandTracker`, `MediaPipeBodyTracker`) use MediaPipe's Tasks API with GPU delegate and CPU fallback:

```
CameraX → ImageProxy → BitmapPool → MediaPipe FaceLandmarker (GPU/CPU) → BlendShapes + Landmarks
```

Key code path in `MediaPipeFaceTracker.initFaceLandmarker()`:
```kotlin
faceLandmarker = try {
    createLandmarker(Delegate.GPU)
} catch (e: RuntimeException) {
    createLandmarker(Delegate.CPU)
}
```

The `Delegate` enum from MediaPipe Tasks currently supports only `CPU` and `GPU`. There is no `NPU` option in the MediaPipe Tasks API as of v0.10.32.

### 1.2 Power Management

`PowerModeManager` already manages frame rate based on `PowerMode` (HIGH/BALANCED/LOW_POWER), thermal state, idle detection, and battery level. NPU acceleration would complement this by reducing per-frame inference cost, allowing:
- Higher frame rates at the same power budget
- Same frame rates at dramatically lower power consumption
- A new NPU-aware mode in `PowerModeManager` for `LOW_POWER` that leverages NPU efficiency

---

## 2. LiteRT QNN Accelerator (Qualcomm)

### 2.1 Overview

The LiteRT Qualcomm AI Engine Direct (QNN) Accelerator is the successor to the TFLite QNN delegate. Developed jointly by Google and Qualcomm, it provides direct access to Qualcomm's Hexagon NPU via LiteRT's Compiled Model API.

### 2.2 Key Capabilities

| Feature | Details |
|---|---|
| Op coverage | 90 LiteRT ops supported, 64/72 benchmark models fully delegated to NPU |
| Compilation | AOT (ahead-of-time) and on-device compilation |
| Quantization | INT8 quantized models required for HTP (Hexagon Tensor Processor) acceleration |
| API | LiteRT `CompiledModel` API with `Accelerator.create()` |
| Fallback | Automatic fallback to GPU → CPU when NPU delegation fails |
| Distribution | Maven Central: `com.qualcomm.qti:qnn-runtime` + `qnn-litert-delegate` |

### 2.3 Android Integration Example

```kotlin
// Maven dependencies
// com.qualcomm.qti:qnn-runtime:2.34.0
// com.qualcomm.qti:qnn-litert-delegate:2.34.0

val npuAccelerator = Accelerator.create(
    context,
    Accelerator.Config.Builder()
        .setTarget(Accelerator.Target.NPU)
        .build()
)

val gpuAccelerator = Accelerator.create(
    context,
    Accelerator.Config.Builder()
        .setTarget(Accelerator.Target.GPU)
        .build()
)

// CompiledModel with NPU primary, GPU fallback
val model = CompiledModel.create(
    context,
    modelPath,
    CompiledModel.Options.Builder()
        .addAccelerator(npuAccelerator)  // Primary
        .addAccelerator(gpuAccelerator)  // Fallback
        .build()
)

// Pre-allocate buffers
val inputBuffer = model.createInputBuffers()
val outputBuffer = model.createOutputBuffers()

// Run inference
model.run(inputBuffer, outputBuffer)
```

### 2.4 Supported Qualcomm Chipsets

| Chipset | NPU TOPS | QNN Support | Devices (examples) |
|---|---|---|---|
| Snapdragon 8 Elite (Gen 5) | 80 TOPS | Full | Samsung S25 Ultra, OnePlus 13 |
| Snapdragon 8 Gen 3 | 45 TOPS | Full | Samsung S24 Ultra, Pixel 9 Pro |
| Snapdragon 8 Gen 2 | 35 TOPS | Full | Samsung S23 Ultra, Pixel 8 Pro |
| Snapdragon 8+ Gen 1 | 27 TOPS | Full | Samsung S22+, OnePlus 10T |
| Snapdragon 8 Gen 1 | 27 TOPS | Full | Samsung S22, Pixel 7 (Tensor G2) |
| Snapdragon 7+ Gen 3 | ~20 TOPS | Partial | Samsung A56, mid-range 2025 |
| Snapdragon 7 Gen 3 | ~13 TOPS | Partial | Mid-range devices |

Note: Devices must have the Qualcomm QNN runtime libraries available. Pixel devices use Google Tensor SoCs (not Qualcomm NPU), so QNN does not apply to them.

---

## 3. LiteRT NeuroPilot Accelerator (MediaTek)

### 3.1 Overview

Google and MediaTek jointly developed the LiteRT NeuroPilot Accelerator, replacing the older TFLite NeuroPilot delegate. It provides direct integration with MediaTek's NeuroPilot compiler and APU (AI Processing Unit).

### 3.2 Supported MediaTek Chipsets

| Chipset | NPU Capability | LiteRT Support | Devices (examples) |
|---|---|---|---|
| Dimensity 9400 | Flagship APU | Full | Vivo X200 Pro, Oppo Find X8 |
| Dimensity 9300 | Flagship APU | Full | Vivo X100 Pro |
| Dimensity 9200 | High-end APU | Full | Vivo X90 Pro |
| Dimensity 9000 | High-end APU | Full | Oppo Reno 8 Pro |
| Dimensity 8300 | Mid-range APU | Full | Redmi Note 13 Pro+ |
| Dimensity 7300 | Entry APU | Supported | Mid-range 2024-2025 |
| Dimensity 9500 | Next-gen APU | Expected | 2026 flagships |

### 3.3 Market Coverage

Qualcomm and MediaTek together power ~85% of Android smartphones. Adding NPU acceleration for both platforms provides broad coverage of 2023+ flagship and 2024+ mid-range devices.

---

## 4. Benchmark Data

### 4.1 NPU vs GPU vs CPU: General Vision Models

Source: Google AI Edge Portal, LiteRT QNN benchmarks on Snapdragon 8 Elite Gen 5.

| Metric | CPU | GPU | NPU | NPU Speedup vs CPU |
|---|---|---|---|---|
| Models under 5ms | 13/72 | ~30/72 | 56/72 | Up to 100x |
| Typical vision model latency | 10-50ms | 2-15ms | 0.5-5ms | 10-100x |
| MobileNet v2 (classification) | ~8ms | ~3ms | ~0.8ms | 10x |
| EfficientDet (detection) | ~35ms | ~12ms | ~3ms | 12x |

### 4.2 Face/Hand Tracking Model Benchmarks

Based on Hackster.io benchmarks accelerating MediaPipe models with Qualcomm QNN, and Google AI Edge data:

| Model | CPU (ms) | GPU (ms) | NPU (ms) | NPU Speedup |
|---|---|---|---|---|
| Hand Landmarks Full (unquantized CPU vs quantized NPU) | 47.4 | ~8-12 | 1.0 | **47x** |
| Face Detection Short Range | ~12 | ~4 | ~1.2 | **10x** |
| Face Landmarks V2 (estimated) | ~15-25 | ~5-8 | ~1.5-3 | **10-17x** |
| Hand Landmarks Lite | ~20 | ~5 | ~0.8 | **25x** |
| Pose Landmarks Full | ~30-50 | ~10-15 | ~3-5 | **10x** |

**Key observation:** The hand landmarks model achieves the most dramatic speedup (47x) because it is a relatively small model that fits entirely in the NPU's on-chip memory, eliminating DRAM bandwidth bottlenecks.

### 4.3 End-to-End Tracking Pipeline Impact

Current pipeline latency estimate (30fps, single face):

| Stage | Current (GPU) | With NPU | Savings |
|---|---|---|---|
| Camera capture + bitmap conversion | ~3-5ms | ~3-5ms | 0ms (unchanged) |
| MediaPipe inference | ~5-8ms | ~1.5-3ms | **3-6ms** |
| BlendShape mapping + smoothing | ~0.5ms | ~0.5ms | 0ms (unchanged) |
| **Total per frame** | **~8-14ms** | **~5-9ms** | **3-6ms** |
| **Achievable FPS** | 60fps (comfortable) | 60fps (headroom for 120fps) | More thermal/power budget |

At 30fps the savings are less dramatic because inference is not the bottleneck. The real win is **power efficiency** — the NPU achieves the same latency at a fraction of the power, extending battery life significantly during long streaming sessions.

---

## 5. Power Consumption Comparison

### 5.1 Per-Inference Power

| Processor | Active Power (typical) | Efficiency (TOPS/W) |
|---|---|---|
| CPU (big cores) | 3-6W | ~1-2 TOPS/W |
| GPU (Adreno) | 2-4W | ~5-8 TOPS/W |
| NPU (Hexagon) | 0.5-2W | ~15-40 TOPS/W |

### 5.2 Continuous Tracking Session Impact

For a VTuber streaming session running face tracking at 30fps:

| Delegate | Est. Power Draw | Battery Impact (4500mAh) | Session Duration (tracking only) |
|---|---|---|---|
| CPU | ~1.5-2.5W | ~30-40% per hour | ~2.5-3.5 hours |
| GPU | ~0.8-1.5W | ~15-25% per hour | ~4-6 hours |
| NPU | ~0.3-0.8W | ~5-12% per hour | **~8-15+ hours** |

### 5.3 Thermal Impact

NPU inference generates significantly less heat than GPU, which means:
- `ThermalState` stays at `NOMINAL` longer before escalating to `FAIR`/`SERIOUS`
- `PowerModeManager` triggers fewer FPS downgrades
- Users can maintain `PowerMode.HIGH` (60fps) for longer periods
- Sustained performance without thermal throttling during multi-hour streams

---

## 6. Integration Path with MediaPipe and Existing Architecture

### 6.1 Challenge: MediaPipe Tasks API Limitation

The MediaPipe Tasks API (`FaceLandmarker`, `HandLandmarker`, `PoseLandmarker`) currently only exposes `Delegate.CPU` and `Delegate.GPU`. There is no `Delegate.NPU` enum value. This means we cannot simply swap the delegate in `createLandmarker()`.

### 6.2 Integration Options

#### Option A: LiteRT Interpreter with NPU Delegate (Recommended)

Bypass MediaPipe's high-level Tasks API and use LiteRT directly with the QNN delegate for model inference, while keeping MediaPipe's pre/post-processing logic.

**Approach:**
1. Extract the `.tflite` model from MediaPipe's `.task` bundle
2. Load it via LiteRT `CompiledModel` with QNN accelerator
3. Implement pre-processing (image resize, normalization) matching MediaPipe's pipeline
4. Implement post-processing (landmark extraction, blend shape mapping) matching MediaPipe's output format
5. Feed results into the existing `BlendShapeMapper` → smoother → enhancer pipeline

**Pros:** Full NPU acceleration, no dependency on MediaPipe delegate support
**Cons:** Significant implementation effort, must replicate MediaPipe pre/post-processing exactly, risk of subtle accuracy differences

```kotlin
internal class NpuFaceTracker(
    private val platformContext: PlatformContext,
    private val config: FaceTrackerConfig,
) : FaceTracker {

    private var compiledModel: CompiledModel? = null

    private fun initModel() {
        val npuAccelerator = Accelerator.create(
            platformContext.context,
            Accelerator.Config.Builder()
                .setTarget(Accelerator.Target.NPU)
                .build()
        )

        compiledModel = CompiledModel.create(
            platformContext.context,
            "models/face_landmarker_v2.tflite", // extracted from .task bundle
            CompiledModel.Options.Builder()
                .addAccelerator(npuAccelerator)
                .build()
        )
    }

    // Pre/post-processing would need to replicate MediaPipe's pipeline
}
```

#### Option B: Wait for MediaPipe NPU Delegate Support

MediaPipe is actively integrating LiteRT as its inference backend. Future versions may expose NPU delegate selection through the Tasks API.

**Pros:** Zero implementation effort, guaranteed compatibility
**Cons:** Unknown timeline, may never happen for legacy Tasks API

#### Option C: Hybrid — MediaPipe GPU + LiteRT NPU for Specific Models

Use MediaPipe Tasks API with GPU for the primary pipeline, but add an optional LiteRT NPU path for specific sub-models (e.g., the face detection model within the FaceLandmarker).

**Pros:** Incremental approach, lower risk
**Cons:** Limited benefit (only part of the pipeline is accelerated)

#### Option D: Custom MediaPipe Graph with NPU Delegate

Build a custom MediaPipe graph (`.pbtxt`) that uses LiteRT's NPU-enabled `InferenceCalculator` instead of the default `TfLiteInferenceCalculator`.

**Pros:** Stays within MediaPipe ecosystem, full pipeline control
**Cons:** Requires deep MediaPipe graph knowledge, complex build setup, less portable

### 6.3 Recommended Approach: Option A with Phased Rollout

**Phase 1 — NPU Capability Detection + Config (Low effort)**
- Add `NpuCapabilityDetector` to check QNN/NeuroPilot availability at runtime
- Add `InferenceDelegate` enum to config: `AUTO`, `CPU`, `GPU`, `NPU`
- Update `PowerModeManager` to recommend NPU in `LOW_POWER` mode
- No actual NPU inference yet — just detection and config plumbing

**Phase 2 — LiteRT NPU for Face Detection Sub-Model (Medium effort)**
- Extract the face detection `.tflite` from the `.task` bundle
- Run face detection on NPU via LiteRT `CompiledModel`
- Keep face landmark regression on MediaPipe GPU
- This accelerates the most expensive part of the pipeline

**Phase 3 — Full NPU Face/Hand Landmark Pipeline (High effort)**
- Implement complete LiteRT-based face landmark inference with NPU
- Replicate MediaPipe's pre/post-processing
- Validate accuracy against MediaPipe GPU baseline (blend shape RMSE < 0.02)
- Add as an alternative `FaceTracker` implementation (`NpuFaceTracker`)

### 6.4 Architecture Integration

```
FaceTrackerFactory.create(config, context)
    │
    ├── config.inferenceDelegate == NPU && NpuCapabilityDetector.isAvailable()
    │   └── NpuFaceTracker (LiteRT + QNN)
    │       └── CompiledModel → QNN Accelerator → Hexagon NPU
    │
    ├── config.inferenceDelegate == GPU (or NPU unavailable)
    │   └── MediaPipeFaceTracker (current, Delegate.GPU)
    │       └── FaceLandmarker → GPU delegate → Adreno GPU
    │
    └── config.inferenceDelegate == CPU
        └── MediaPipeFaceTracker (current, Delegate.CPU)
            └── FaceLandmarker → XNNPACK → CPU
```

### 6.5 Config Extension

```kotlin
public enum class InferenceDelegate {
    /** Auto-select: NPU > GPU > CPU based on availability */
    AUTO,
    CPU,
    GPU,
    /** Requires Qualcomm QNN or MediaTek NeuroPilot runtime */
    NPU,
}

public data class FaceTrackerConfig(
    // ... existing fields ...
    val inferenceDelegate: InferenceDelegate = InferenceDelegate.AUTO,
)
```

---

## 7. Device Compatibility Matrix

### 7.1 NPU-Capable Devices (QNN + NeuroPilot)

| Tier | Chipsets | NPU Performance | Market Share (est.) | Year |
|---|---|---|---|---|
| Flagship 2025+ | SD 8 Elite, Dimensity 9400 | 45-80 TOPS | ~10% | 2025-2026 |
| Flagship 2024 | SD 8 Gen 3, Dimensity 9300 | 35-45 TOPS | ~15% | 2024-2025 |
| Flagship 2023 | SD 8 Gen 2, Dimensity 9200 | 27-35 TOPS | ~15% | 2023-2024 |
| High-mid 2024+ | SD 7+ Gen 3, Dimensity 8300 | 13-20 TOPS | ~15% | 2024-2025 |
| Mid-range 2024+ | SD 7 Gen 3, Dimensity 7300 | 5-13 TOPS | ~10% | 2024-2025 |

**Total addressable market:** ~65% of 2023+ Android devices have some level of NPU capability.

### 7.2 Devices Without NPU Support

| Category | Chipsets | Fallback |
|---|---|---|
| Google Pixel (Tensor) | Tensor G2, G3, G4 | GPU (Tensor has Edge TPU but no LiteRT delegate) |
| Samsung Exynos | Exynos 2400, 2500 | GPU (Exynos NPU exists but no LiteRT delegate yet) |
| Older Qualcomm | SD 800 series pre-8 Gen 1 | GPU/CPU |
| Budget chipsets | Helio, Unisoc | CPU only |

### 7.3 Runtime Detection

```kotlin
internal object NpuCapabilityDetector {
    fun isQnnAvailable(context: Context): Boolean {
        // Check if QNN runtime libraries exist on device
        return try {
            System.loadLibrary("QnnHtp")
            true
        } catch (e: UnsatisfiedLinkError) {
            false
        }
    }

    fun isNeuroPilotAvailable(context: Context): Boolean {
        // Check MediaTek NeuroPilot availability
        return try {
            System.loadLibrary("neuron_runtime")
            true
        } catch (e: UnsatisfiedLinkError) {
            false
        }
    }

    fun bestDelegate(context: Context): InferenceDelegate {
        return when {
            isQnnAvailable(context) -> InferenceDelegate.NPU
            isNeuroPilotAvailable(context) -> InferenceDelegate.NPU
            else -> InferenceDelegate.GPU
        }
    }
}
```

---

## 8. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| MediaPipe `.task` model extraction breaks across versions | Medium | High | Pin MediaPipe version, validate model hash on extraction |
| Pre/post-processing mismatch causes accuracy degradation | Medium | High | Automated accuracy tests comparing NPU vs GPU blend shape output |
| QNN runtime not available on target device | Low | Low | Graceful fallback to GPU → CPU (existing pattern) |
| INT8 quantization reduces tracking precision | Medium | Medium | Benchmark blend shape RMSE; accept if < 0.02 |
| NPU warmup latency delays first frame | Low | Low | Pre-warm NPU during `start()` before camera binding |
| MediaTek NeuroPilot API differs from QNN | Medium | Medium | Abstract behind `NpuAccelerator` interface |
| MediaPipe adds native NPU support, making our impl redundant | Medium | Low | Actually a positive outcome — migrate to native support |
| Increased APK size from QNN/NeuroPilot libraries | Low | Medium | Optional dependency via Gradle feature variant |

---

## 9. PowerModeManager NPU Integration

### 9.1 NPU-Aware Power Modes

When NPU is available, `PowerModeManager` can offer a better power-performance tradeoff:

| PowerMode | Current (GPU) | With NPU Available |
|---|---|---|
| HIGH | 60fps, GPU inference | 60fps, NPU inference (lower power, cooler) |
| BALANCED | 30fps, GPU inference | 30fps, NPU inference (significant power saving) |
| LOW_POWER | 15fps, GPU inference | 30fps, NPU inference (same power as current LOW_POWER GPU) |

**Key insight:** NPU at 30fps consumes roughly the same power as GPU at 15fps. This means `LOW_POWER` mode can maintain higher quality (30fps) without increasing power consumption by switching to NPU.

### 9.2 Proposed Changes

```kotlin
internal class PowerModeManager(
    private val powerMode: PowerMode,
    private val thermalMonitor: ThermalMonitor,
    private val batteryMonitor: BatteryMonitor,
    private val npuAvailable: Boolean = false,  // NEW
    // ...
) {
    val recommendedDelegate: InferenceDelegate
        get() = when {
            !npuAvailable -> InferenceDelegate.GPU
            effectiveMode == PowerMode.LOW_POWER -> InferenceDelegate.NPU
            effectiveMode == PowerMode.BALANCED -> InferenceDelegate.NPU
            thermalMonitor.thermalState.value >= ThermalState.FAIR -> InferenceDelegate.NPU
            else -> InferenceDelegate.GPU  // HIGH mode, cool device: GPU is fine
        }
}
```

---

## 10. Recommendation and Next Steps

### 10.1 Verdict

**Proceed with phased implementation.** NPU acceleration is a strong fit for KMP-FaceLink's VTuber streaming use case where sessions last hours and power/thermal efficiency is critical. The 5-15x power reduction and elimination of thermal throttling directly improve user experience.

### 10.2 Actionable Next Steps

1. **Create issue: Add `InferenceDelegate` enum and `NpuCapabilityDetector`** — Runtime NPU detection for Qualcomm QNN and MediaTek NeuroPilot. Add `inferenceDelegate` field to `FaceTrackerConfig`, `HandTrackerConfig`, and `BodyTrackerConfig`. No functional changes yet.

2. **Create issue: Extract `.tflite` models from MediaPipe `.task` bundles** — Build a utility to extract and validate the TFLite sub-models from MediaPipe task bundles, enabling direct LiteRT inference. Required for any NPU work.

3. **Create issue: Implement `NpuFaceTracker` (Phase 2 — detection only)** — Use LiteRT `CompiledModel` with QNN for the face detection sub-model, keeping landmark regression on MediaPipe GPU. Benchmark latency and power savings on Snapdragon 8 Gen 3+ devices.

4. **Create issue: Full NPU face landmark pipeline (Phase 3)** — Complete `NpuFaceTracker` with NPU-accelerated landmark regression and blend shape extraction. Validate accuracy (RMSE < 0.02 vs MediaPipe GPU baseline).

5. **Create issue: Update `PowerModeManager` for NPU-aware power modes** — Integrate `recommendedDelegate` logic so `LOW_POWER` and `BALANCED` modes automatically prefer NPU when available. Thermal escalation triggers NPU switch in `HIGH` mode.

6. **Create issue: NPU benchmark harness** — Extend sample app with delegate comparison screen showing real-time FPS, latency, power draw, and thermal state for CPU/GPU/NPU side by side.

7. **Create issue: Optional QNN/NeuroPilot Gradle module** — Separate `kmp-facelink-npu` module containing QNN and NeuroPilot dependencies, discoverable at runtime. Keeps the core module lightweight.

---

## References

- [LiteRT QNN Accelerator — Google Developers Blog](https://developers.googleblog.com/unlocking-peak-performance-on-qualcomm-npu-with-litert/)
- [Utilizing Qualcomm NPUs with LiteRT — Google AI Edge](https://ai.google.dev/edge/litert/android/npu/qualcomm)
- [Qualcomm NPU (AI Engine Direct) with LiteRT — Google AI Edge](https://ai.google.dev/edge/litert/next/qualcomm)
- [NPU Acceleration with LiteRT — Google AI Edge](https://ai.google.dev/edge/litert/next/npu)
- [MediaTek NPU and LiteRT — Google Developers Blog](https://developers.googleblog.com/mediatek-npu-and-litert-powering-the-next-generation-of-on-device-ai/)
- [MediaTek NPU (NeuroPilot) with LiteRT — Google AI Edge](https://ai.google.dev/edge/litert/next/mediatek)
- [Accelerating MediaPipe Models with Qualcomm — Hackster.io](https://www.hackster.io/AlbertaBeef/accelerating-the-mediapipe-models-with-qualcomm-3247ed)
- [Google AI Edge Portal](https://ai.google.dev/edge/ai-edge-portal)
- [LiteRT GitHub Repository](https://github.com/google-ai-edge/LiteRT)
- [Google LiteRT Accelerator — InfoQ](https://www.infoq.com/news/2025/11/litert-snapdragon-accelerator/)
- [Fast On-device LLM Inference with NPUs — ASPLOS 2025](https://xumengwei.github.io/files/ASPLOS25-NPU.pdf)
- [Hardware Optimization on Android for AI Models — arXiv](https://arxiv.org/html/2511.13453v1)
- [MediaPipe Face Landmarker Guide — Google AI Edge](https://ai.google.dev/edge/mediapipe/solutions/vision/face_landmarker)
- [QNN Hardware Acceleration on Android — Edge Impulse](https://docs.edgeimpulse.com/tutorials/topics/android/qnn-acceleration)
- [MediaTek NeuroPilot](https://neuropilot.mediatek.com/)
