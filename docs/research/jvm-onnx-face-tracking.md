# JVM Desktop Face Tracking via ONNX Runtime

**Issue:** #478
**Status:** Research complete
**Date:** 2026-04-02

## Executive Summary

This document evaluates adding real face tracking to the JVM/Desktop target using ONNX Runtime for model inference and JavaCV for camera access. The current `jvmMain` source set contains no-op tracker stubs that return `TrackingState.ERROR` when started.

**Key finding:** A functional JVM face tracker is feasible using ONNX Runtime (1.23.2+) for inference, pre-converted ONNX face landmark models (OpenSeeFace or MediaPipe-derived), and JavaCV (1.5.13) for webcam capture. The main engineering effort is landmark-to-blend-shape mapping to produce ARKit-compatible output consistent with the Android/iOS trackers. Estimated effort is 8-12 days.

---

## 1. ONNX Runtime Java API

### Overview

ONNX Runtime provides official Java bindings published to Maven Central under `com.microsoft.onnxruntime:onnxruntime`. The API supports loading `.onnx` models, running inference with configurable execution providers, and retrieving output tensors.

### Latest Versions

| Artifact | Version | Notes |
|---|---|---|
| `onnxruntime` (CPU) | 1.23.2 | Stable, Maven Central |
| `onnxruntime_gpu` (CUDA) | 1.24.2 | Requires CUDA + cuDNN |

### Maven Dependency

```kotlin
// build.gradle.kts (jvmMain)
implementation("com.microsoft.onnxruntime:onnxruntime:1.23.2")
```

### Key API Surface

```kotlin
// Load model
val env = OrtEnvironment.getEnvironment()
val session = env.createSession("model.onnx", OrtSession.SessionOptions())

// Run inference
val inputTensor = OnnxTensor.createTensor(env, floatArrayOf(...))
val results = session.run(mapOf("input" to inputTensor))
val output = results[0].value as Array<FloatArray>
```

### GPU Support

GPU acceleration is available via execution providers configured on `SessionOptions`:

| Provider | Platform | Requirements |
|---|---|---|
| CUDA | Linux, Windows | CUDA 11.8/12.x + cuDNN 8.x |
| DirectML | Windows | DirectX 12 GPU |
| CoreML | macOS | macOS 12+ (via ONNX Runtime CoreML EP) |
| CPU | All | Default, no extra dependencies |

For initial implementation, CPU inference is sufficient. OpenSeeFace models run at 30-60 FPS on CPU with ONNX Runtime, which meets the project's 30 FPS performance target.

### Alternative: KInference

[KInference](https://github.com/JetBrains-Research/kinference) (v0.2.27) by JetBrains Research is a pure-Kotlin ONNX inference library with KMP support. It uses ONNX Runtime as its JVM backend. While it provides a Kotlin-idiomatic API, the extra abstraction layer adds complexity without clear benefit for this use case. **Recommendation:** Use ONNX Runtime Java API directly for simplicity and performance.

### Alternative: KotlinDL

[KotlinDL](https://github.com/Kotlin/kotlindl) by JetBrains is a high-level deep learning API with ONNX support. It is JVM-only (not KMP) and adds significant dependency weight. **Recommendation:** Not suitable; too heavyweight for running a single inference model.

---

## 2. Model Availability

### Option A: OpenSeeFace Models (Recommended)

[OpenSeeFace](https://github.com/emilianavt/OpenSeeFace) provides pre-trained ONNX face landmark models optimized for real-time CPU inference.

| Property | Value |
|---|---|
| License | BSD 2-Clause |
| Architecture | MobileNetV3-based |
| Landmark count | 66 (modified iBUG 68) |
| Models available | 4 variants (speed vs quality trade-off) |
| Performance | 30-60 FPS on CPU via ONNX Runtime |
| Output | 66 (x, y, confidence) landmarks + head pose (rotation, translation) |
| Robustness | Works in low light, high noise, low resolution, wide head pose range |

**Pros:**
- Ready-to-use ONNX models, no conversion needed
- BSD license is compatible with this project
- Proven in VTuber ecosystem (used by VSeeFace, Warudo)
- Optimized for CPU inference (no GPU required)
- Head pose estimation included (PnP-based)

**Cons:**
- 66 landmarks (not 468/478 like MediaPipe) -- less detail
- No native blend shape output -- must compute ARKit-compatible blend shapes from landmarks
- Landmark layout differs slightly from iBUG 68 (quasi-3D contours instead of outline)

**Blend shape derivation:** Eye blink, mouth open/close, jaw, and eyebrow movements can be computed from the 66 landmark positions using geometric ratios (e.g., eye aspect ratio for blink, mouth height/width ratio for jaw open). This is the same approach used by VSeeFace and similar tools. The 52 ARKit blend shapes cannot all be derived from 66 landmarks -- a realistic subset of ~20-30 blend shapes is achievable.

### Option B: MediaPipe Face Landmark (ONNX-converted)

MediaPipe's Face Landmarker provides 468/478 landmarks and 52 ARKit-compatible blend shapes. However, the model is distributed as `.task` (TFLite) format, not ONNX.

**Conversion path:**
1. Extract `.tflite` from `.task` file (rename to `.tar.gz` and extract)
2. Convert via `tf2onnx`: `python -m tf2onnx.convert --tflite model.tflite --output model.onnx --opset 11`
3. The blend shape model (`face_blendshapes.tflite`) requires separate conversion

**Availability of pre-converted models:**
- [PINTO Model Zoo #032](https://github.com/PINTO0309/PINTO_model_zoo/tree/main/032_FaceMesh) -- FaceMesh ONNX
- [PINTO Model Zoo #282](https://github.com/PINTO0309/PINTO_model_zoo/tree/main/282_face_landmark_with_attention) -- Face Landmark with Attention ONNX
- [Qualcomm MediaPipe Face Detection](https://huggingface.co/qualcomm/MediaPipe-Face-Detection) -- includes `MediaPipeFaceLandmarkDetector.onnx`

**Pros:**
- 468 landmarks provide full detail
- ARKit-compatible blend shapes if blend shape model is also converted
- Consistent with Android tracker's MediaPipe output

**Cons:**
- TFLite-to-ONNX conversion can be fragile (users report errors in MediaPipe issues #3206, #3238)
- Blend shape model conversion is not well-documented
- Multi-model pipeline (face detection + landmarks + blend shapes) adds complexity
- No official ONNX distribution from Google

### Recommendation

**Start with OpenSeeFace** for Phase 1. It provides production-ready ONNX models, proven VTuber ecosystem usage, and sufficient quality for avatar animation. Plan MediaPipe ONNX conversion as a Phase 2 enhancement for users who need full 468-landmark and 52-blend-shape fidelity.

---

## 3. Camera Access

### JavaCV (Recommended)

[JavaCV](https://github.com/bytedeco/javacv) (v1.5.13, released Feb 2026) provides Java/Kotlin bindings to OpenCV, FFmpeg, and other native libraries.

| Property | Value |
|---|---|
| Latest version | 1.5.13 |
| Maven artifact | `org.bytedeco:javacv-platform:1.5.13` |
| Camera API | `OpenCVFrameGrabber` or `FFmpegFrameGrabber` |
| Platforms | Windows, macOS, Linux |
| Frame format | `Frame` (convertible to `BufferedImage`, OpenCV `Mat`, etc.) |

```kotlin
// Camera capture with JavaCV
val grabber = OpenCVFrameGrabber(0) // device index 0 = default webcam
grabber.start()

val frame: Frame = grabber.grab() // grab a frame
// Convert to float array for ONNX input preprocessing

grabber.stop()
grabber.release()
```

**Dependency size:** `javacv-platform` bundles native libraries for all platforms (~200MB total). Use platform-specific classifiers to reduce size:

```kotlin
// Only include current platform's natives
implementation("org.bytedeco:javacv:1.5.13")
implementation("org.bytedeco:opencv:4.10.0-1.5.13:$currentPlatform")
```

### Alternative: Java AWT Robot / JMF

- `java.awt.Robot` can capture screen but not webcam
- Java Media Framework (JMF) is abandoned (last update 2003)
- **Not viable** for webcam capture

### Alternative: Webcam Capture API (sarxos)

[webcam-capture](https://github.com/sarxos/webcam-capture) is a lightweight Java library for webcam access. However, it is largely unmaintained (last meaningful update 2018) and has fewer features than JavaCV. **Not recommended.**

### Recommendation

**Use JavaCV** with `OpenCVFrameGrabber`. It is actively maintained, cross-platform, and provides the frame conversion utilities needed for ONNX model preprocessing.

---

## 4. Architecture Proposal

### Module Structure

The JVM face tracker should be implemented within the existing `jvmMain` source set of `kmp-facelink`, replacing the no-op stubs. No new module is needed.

```
kmp-facelink/src/jvmMain/kotlin/io/github/kmpfacelink/
  ├── api/
  │   └── FaceTrackerFactory.jvm.kt          # Updated: returns OnnxFaceTracker
  ├── internal/
  │   ├── NoOpFaceTracker.kt                  # Keep as fallback
  │   ├── onnx/
  │   │   ├── OnnxFaceTracker.kt              # Main tracker implementation
  │   │   ├── OnnxModelLoader.kt              # Model loading and session management
  │   │   ├── LandmarkToBlendShapeMapper.kt   # 66 landmarks -> ARKit blend shapes
  │   │   ├── FaceDetector.kt                 # Face detection preprocessing
  │   │   └── HeadPoseEstimator.kt            # PnP head pose from landmarks
  │   └── camera/
  │       └── JavaCvCameraManager.kt          # JavaCV webcam capture loop
  └── util/
      └── (existing utility files)
```

### Data Flow

```
Camera (JavaCV)
  → Frame (BGR Mat)
  → Preprocess (resize, normalize, convert to float tensor)
  → Face Detection ONNX Model
  → Crop face region
  → Face Landmark ONNX Model
  → 66 landmarks (x, y, confidence)
  → LandmarkToBlendShapeMapper
  → ~20-30 ARKit-compatible blend shapes
  → HeadPoseEstimator (PnP)
  → Head rotation + translation
  → FaceTrackingData (emitted via Flow)
```

### Key Design Decisions

1. **Model bundling:** Ship ONNX model files as resources in the JAR. OpenSeeFace models are small (~5-10MB total for all variants).

2. **Threading:** Run camera capture and inference on a dedicated coroutine dispatcher (`Dispatchers.IO` or a custom single-threaded executor) to avoid blocking the main thread.

3. **Blend shape mapping:** Implement geometric computation from 66 landmarks to ARKit blend shapes. Start with the most impactful subset:
   - Eyes: `eyeBlinkLeft`, `eyeBlinkRight`, `eyeWideLeft`, `eyeWideRight`
   - Mouth: `jawOpen`, `mouthSmileLeft`, `mouthSmileRight`, `mouthFunnel`, `mouthPucker`
   - Brows: `browDownLeft`, `browDownRight`, `browInnerUp`, `browOuterUpLeft`, `browOuterUpRight`
   - Cheeks: `cheekPuff` (approximated)

4. **FaceTrackerConfig integration:** Respect existing config options (target FPS, smoothing, etc.). Add JVM-specific config for camera device index and model quality selection.

5. **Graceful degradation:** If ONNX Runtime or JavaCV native libraries are not available at runtime, fall back to `NoOpFaceTracker` with a descriptive error message instead of crashing.

### Dependencies to Add (jvmMain only)

```kotlin
// In kmp-facelink/build.gradle.kts, jvm target dependencies
jvmMainImplementation("com.microsoft.onnxruntime:onnxruntime:1.23.2")
jvmMainImplementation("org.bytedeco:javacv:1.5.13")
jvmMainImplementation("org.bytedeco:opencv:4.10.0-1.5.13")
// Platform-specific native classifiers added per OS
```

---

## 5. Estimated Effort

| Component | Effort | Complexity |
|---|---|---|
| JavaCV camera capture (`JavaCvCameraManager`) | 1-2 days | Low |
| ONNX model loading and inference pipeline | 2-3 days | Medium |
| Face detection preprocessing | 1-2 days | Medium |
| Landmark-to-blend-shape mapper (geometric) | 2-3 days | High |
| Head pose estimation (PnP) | 1 day | Medium |
| Integration with `FaceTracker` interface + config | 1-2 days | Low |
| Testing and tuning (accuracy, FPS, smoothing) | 2-3 days | Medium |
| **Total** | **10-16 days** | **Medium-High** |

### Phased Approach

**Phase 1 (MVP, ~8 days):** OpenSeeFace model, basic landmark detection, ~15 core blend shapes, head pose, 30 FPS target on CPU.

**Phase 2 (Enhancement, ~5 days):** Full 52 blend shape mapping with improved accuracy, MediaPipe ONNX model option, GPU acceleration via CUDA/DirectML execution providers.

**Phase 3 (Optional, ~3 days):** Hand tracking via ONNX (MediaPipe Hand Landmark model conversion), replacing `NoOpHandTracker`.

---

## 6. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| JavaCV native library size bloats JAR | Certain | Medium | Use platform-specific classifiers; document minimum dependencies |
| Blend shape quality from 66 landmarks is insufficient | Medium | High | Start with proven VSeeFace-style mappings; upgrade to MediaPipe ONNX in Phase 2 |
| ONNX Runtime version conflicts with other JVM dependencies | Low | Medium | Shade or isolate via classloader if needed |
| Camera access fails on certain OS/hardware | Low | Medium | Graceful fallback to NoOpFaceTracker; support multiple grabber backends |
| OpenSeeFace model accuracy degrades at extreme angles | Low | Low | Document limitations; MediaPipe model handles wider range |
| Cross-platform native library packaging (macOS ARM vs x86) | Medium | Medium | JavaCV handles this via classifiers; test on both architectures |

---

## 7. Existing Kotlin/JVM Face Tracking Libraries

No production-ready Kotlin/JVM face tracking library exists. The closest options are:

| Project | Status | Notes |
|---|---|---|
| KInference | Active (v0.2.27) | ONNX inference only, no face-specific logic |
| KotlinDL | Maintenance mode | ONNX support, but heavyweight and JVM-only |
| OpenSeeFace | Active | Python-only; models reusable via ONNX Runtime |
| FaceONNX | Active | C#/.NET only; demonstrates the ONNX face tracking pattern |

The implementation will be novel: there is no existing Kotlin library to wrap. The closest reference implementation is OpenSeeFace's Python code and FaceONNX's C# code.

---

## 8. Recommendation

**Proceed with implementation using OpenSeeFace ONNX models + ONNX Runtime Java API + JavaCV.**

This combination provides:
- Proven models with BSD license and VTuber ecosystem validation
- Mature Java API for inference (ONNX Runtime 1.23.2)
- Reliable cross-platform camera access (JavaCV 1.5.13)
- Path to GPU acceleration without changing the model or inference code
- Consistency with the project's existing architecture (replace no-op stubs in `jvmMain`)

### Actionable Next Steps

1. **Create issue: JVM camera capture with JavaCV** -- Implement `JavaCvCameraManager` with frame grabbing loop
2. **Create issue: ONNX inference pipeline** -- Load OpenSeeFace models, run face detection + landmark inference
3. **Create issue: Landmark-to-blend-shape mapper** -- Geometric computation of ARKit blend shapes from 66 landmarks
4. **Create issue: Wire OnnxFaceTracker into FaceTrackerFactory.jvm.kt** -- Replace NoOpFaceTracker, handle graceful fallback
5. **Create issue: JVM face tracker testing and tuning** -- FPS benchmarks, blend shape accuracy, smoothing filter integration

---

## References

- [ONNX Runtime Java - Get Started](https://onnxruntime.ai/docs/get-started/with-java.html)
- [ONNX Runtime Releases](https://github.com/microsoft/onnxruntime/releases)
- [ONNX Runtime Execution Providers](https://onnxruntime.ai/docs/execution-providers/)
- [OpenSeeFace GitHub](https://github.com/emilianavt/OpenSeeFace)
- [OpenSeeFace - Warudo Docs](https://docs.warudo.app/docs/mocap/openseeface)
- [JavaCV GitHub](https://github.com/bytedeco/javacv)
- [KInference GitHub](https://github.com/JetBrains-Research/kinference)
- [KotlinDL GitHub](https://github.com/Kotlin/kotlindl)
- [PINTO Model Zoo - FaceMesh](https://github.com/PINTO0309/PINTO_model_zoo/tree/main/032_FaceMesh)
- [PINTO Model Zoo - Face Landmark with Attention](https://github.com/PINTO0309/PINTO_model_zoo/tree/main/282_face_landmark_with_attention)
- [MediaPipe Face Mesh Docs](https://mediapipe.readthedocs.io/en/latest/solutions/face_mesh.html)
- [MediaPipe TFLite to ONNX Conversion (Issue #197)](https://github.com/PINTO0309/PINTO_model_zoo/issues/197)
- [Qualcomm MediaPipe Face Detection ONNX (HuggingFace)](https://huggingface.co/qualcomm/MediaPipe-Face-Detection)
- [FaceONNX GitHub](https://github.com/FaceONNX/FaceONNX)
