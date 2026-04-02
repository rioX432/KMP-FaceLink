# LiteRT 2.x CompiledModel API: MediaPipe Tasks Vision Compatibility

**Issue:** #467
**Status:** Research complete
**Date:** 2026-04-02

## Executive Summary

This document evaluates migrating Android inference from MediaPipe's TFLite delegate to Google's LiteRT 2.x CompiledModel API. The investigation concludes that **direct migration is not feasible** because MediaPipe Tasks Vision manages its own TFLite/LiteRT runtime internally. The CompiledModel API (`com.google.ai.edge.litert:litert:2.1.0`) is designed for standalone model inference, not for plugging into MediaPipe's high-level Tasks API.

**Key finding:** MediaPipe Tasks Vision (FaceLandmarker, HandLandmarker, PoseLandmarker) encapsulates model loading, preprocessing, inference, and postprocessing in an opaque pipeline. The only delegate control exposed is `BaseOptions.setDelegate(Delegate.GPU | Delegate.CPU)`. LiteRT 2.x CompiledModel API cannot replace or augment this internal runtime without bypassing the entire MediaPipe Tasks API.

**Action taken:** Updated MediaPipe from 0.10.32 to 0.10.34 (latest stable), which includes ongoing internal LiteRT integration improvements from Google.

---

## 1. Architecture Analysis

### 1.1 MediaPipe Tasks Vision Pipeline

MediaPipe Tasks Vision operates as a self-contained pipeline:

```
.task model bundle → MediaPipe Graph → TfLiteInferenceCalculator → Internal TFLite/LiteRT Runtime
                                                                    └── Delegate: GPU (OpenGL) or CPU (XNNPACK)
```

The `FaceLandmarker.createFromOptions()` API provides:
- Model loading from `.task` asset bundles
- Image preprocessing (resize, normalization, color conversion)
- Internal TFLite interpreter with GPU/CPU delegate
- Postprocessing (landmark extraction, blend shape mapping, transformation matrices)
- Result delivery via callback or synchronous API

**Users have no access to the internal TFLite interpreter.** The only delegate control is `BaseOptions.builder().setDelegate(Delegate.GPU)`.

### 1.2 LiteRT 2.x CompiledModel API

LiteRT 2.x (`com.google.ai.edge.litert:litert:2.1.0`) provides a standalone inference runtime:

```kotlin
val model = CompiledModel.create(
    context.assets,
    "model.tflite",
    CompiledModel.Options(Accelerator.GPU),
)
val inputBuffers = model.createInputBuffers()
val outputBuffers = model.createOutputBuffers()
model.run(inputBuffers, outputBuffers)
```

This API is designed for direct model inference where the caller manages:
- Model file loading
- Input tensor preparation (preprocessing)
- Inference execution
- Output tensor interpretation (postprocessing)

### 1.3 Incompatibility

The two APIs operate at different abstraction levels and cannot be combined:

| Aspect | MediaPipe Tasks | LiteRT 2.x CompiledModel |
|---|---|---|
| Abstraction | High-level (task-oriented) | Low-level (tensor-oriented) |
| Model format | `.task` bundle (graph + models) | Raw `.tflite` files |
| Preprocessing | Handled internally | Caller responsibility |
| Delegate control | `Delegate.GPU` / `Delegate.CPU` enum | `Accelerator.GPU` / `Accelerator.CPU` / `Accelerator.NPU` |
| Postprocessing | Handled internally (landmarks, blend shapes) | Caller responsibility |
| NPU support | Not exposed | Supported via vendor accelerators |

To use LiteRT 2.x CompiledModel for face tracking, we would need to:
1. Extract the raw `.tflite` model from the `.task` bundle
2. Reimplement all MediaPipe preprocessing (image normalization, tensor layout)
3. Reimplement all postprocessing (landmark extraction, blend shape calculation, head transform)
4. Maintain accuracy parity with MediaPipe's reference implementation

This is the approach documented in `npu-acceleration.md` (Option A) and would be a separate, high-effort project.

---

## 2. MediaPipe Internal LiteRT Status

### 2.1 Current State (v0.10.34, April 2026)

MediaPipe Tasks Vision v0.10.33 continues to use its internal TFLite runtime. Google has been gradually migrating MediaPipe's internals to use LiteRT (the renamed TFLite), but this is transparent to the Tasks API consumer:

- The `com.google.mediapipe:tasks-vision` Maven artifact bundles its own TFLite runtime
- The `Delegate` enum has not been extended (still only `CPU` and `GPU`)
- No `Delegate.NPU` or LiteRT-specific options have been added
- The v0.10.33 release notes show no LiteRT CompiledModel integration for Tasks API

### 2.2 Google's Roadmap

Based on public information:
- Google positions LiteRT as the runtime layer, MediaPipe Tasks as the solution layer
- LiteRT-LM (for LLMs) has its own API separate from MediaPipe Tasks
- MediaPipe Tasks may eventually adopt LiteRT's new delegate system internally, but this would be transparent to the API consumer
- NPU delegation for MediaPipe Tasks is not yet available through the public API

---

## 3. What We Updated

### 3.1 MediaPipe Version Bump (0.10.32 -> 0.10.33)

Updated to the latest stable Android release which includes:
- Full-range face detection model support
- Holistic Landmarker C API
- Framework and calculator improvements
- Bug fixes and stability improvements

This ensures we benefit from any internal runtime improvements Google has made, even though the API surface is unchanged.

### 3.2 No Code Changes Required

The existing `DelegateResolver` and `FaceLandmarkerFactory` patterns remain correct:
- `DelegateResolver.createWithFallback()` tries GPU then falls back to CPU
- This is the optimal strategy given MediaPipe's current delegate support
- When/if MediaPipe adds NPU delegate support, the `DelegateResolver` can be extended

---

## 4. Future Paths for Hardware Acceleration

### 4.1 Path A: Wait for MediaPipe NPU Delegate (Low effort, unknown timeline)

Google may add `Delegate.NPU` or an equivalent to the MediaPipe Tasks API. When this happens:
- Add the new value to `DelegateResolver.resolve()` for the `NNAPI` preference
- Update the fallback chain: NPU -> GPU -> CPU
- No architectural changes needed

### 4.2 Path B: Direct LiteRT CompiledModel Pipeline (High effort, available now)

As documented in `npu-acceleration.md` (Option A), bypass MediaPipe Tasks entirely:
- Extract `.tflite` models from `.task` bundles
- Use `CompiledModel.create()` with `Accelerator.NPU` / `Accelerator.GPU`
- Reimplement preprocessing and postprocessing
- Validate accuracy against MediaPipe baseline
- This would unlock NPU acceleration on Qualcomm and MediaTek chipsets

This is tracked separately and requires significant investment (see `npu-acceleration.md` Phase 2-3).

### 4.3 Path C: LiteRT 2.x for LLM Module (Already planned)

LiteRT 2.x CompiledModel API is directly applicable to the `kmp-facelink-llm` module for on-device LLM inference. This is a separate concern from tracking inference and is documented in `litert-lm.md`.

---

## 5. Conclusion

The LiteRT 2.x CompiledModel API is not compatible with MediaPipe Tasks Vision for tracking inference. MediaPipe manages its own runtime internally, and the only migration path is to wait for Google to expose new delegate options or to bypass MediaPipe entirely (high effort, tracked in NPU acceleration research).

The actionable outcome is:
1. **Updated MediaPipe to 0.10.34** to capture latest improvements
2. **No delegate API changes** -- the current GPU/CPU fallback pattern is optimal
3. **Future NPU work** remains tracked in `npu-acceleration.md` as a separate initiative

---

## References

- [LiteRT CompiledModel Kotlin API](https://ai.google.dev/edge/litert/next/android_kotlin)
- [LiteRT GPU Acceleration](https://ai.google.dev/edge/litert/next/gpu)
- [LiteRT Migration Guide](https://ai.google.dev/edge/litert/migration)
- [MediaPipe Tasks Vision](https://ai.google.dev/edge/mediapipe/solutions/tasks)
- [MediaPipe v0.10.33 Release Notes](https://github.com/google-ai-edge/mediapipe/releases/tag/v0.10.33)
- [LiteRT Maven (com.google.ai.edge.litert:litert)](https://mvnrepository.com/artifact/com.google.ai.edge.litert/litert)
- [NPU Acceleration Research](npu-acceleration.md)
- [LiteRT-LM Research](litert-lm.md)
