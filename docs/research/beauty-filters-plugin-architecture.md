# Beauty Filters / AR Effects Plugin Architecture Research

> Research summary for issue #702

Last updated: 2026-04-09

## Status

Research complete — ready for architecture decision.

---

## Background

Beauty filters and real-time AR effects are a natural extension of KMP-FaceLink's face tracking capabilities. Commercial SDKs such as Banuba, DeepAR, and BytePlus all offer beauty filter layers tightly coupled to their own tracking engines. KMP-FaceLink's architecture — unified cross-platform tracking + open plugin model — can differentiate by providing a vendor-neutral effects API that any rendering backend can implement.

---

## Open-Source Engine Landscape

### GPUPixel (C++11 / OpenGL ES, MIT)

| Attribute | Details |
|---|---|
| **License** | MIT |
| **Language** | C++11 with OpenGL ES 2.0/3.0 |
| **Platforms** | Android, iOS, macOS, Windows, Linux (via OpenGL) |
| **Key Features** | Skin smoothing (bilateral filter + frequency separation), face slimming, eye enlargement, composable filter chain |
| **Integration** | JNI (Android), Objective-C++ bridge (iOS) |
| **Maturity** | Commercial-grade; derived from GPUImage lineage |
| **Verdict** | **Most promising** open-source beauty filter engine for KMP-FaceLink integration |

### Jeeliz

| Attribute | Details |
|---|---|
| **License** | Apache 2.0 |
| **Language** | JavaScript (WebGL) |
| **Platforms** | Browser only |
| **Verdict** | Not usable for native Android/iOS targets — excluded |

### MediaPipe Selfie Segmentation

| Attribute | Details |
|---|---|
| **License** | Apache 2.0 |
| **Language** | C++ / TensorFlow Lite |
| **Platforms** | Android, iOS |
| **Key Features** | Background segmentation mask (not beauty filters per se, but useful for background replacement effects) |
| **Verdict** | Useful as a complementary effect primitive; not a general beauty filter engine |

### Commercial References

| SDK | Architecture Style | Notes |
|---|---|---|
| **Banuba** | Monolithic — AR, tracking, rendering bundled | No raw tracking data export; heavy SDK size |
| **DeepAR** | Modular plugin — effects loaded as separate assets | Acquired by Zalando (2025); uncertain roadmap |
| **BytePlus** | Integrated pipeline | Requires BytePlus account; opaque pricing; ByteDance association risks |

**Takeaway:** DeepAR's modular plugin model (effects as loadable assets, registered via a standard interface) is the clearest design reference for KMP-FaceLink.

---

## Current KMP-FaceLink API Surface

### What `FaceTrackingData` Provides

| Data | Android | iOS |
|---|---|---|
| 52 ARKit-compatible blend shapes (`BlendShapeData`) | Yes | Yes |
| 478 face mesh landmarks (`List<FaceLandmark>`) | Yes (normalized 0–1) | **Empty** — ARKit does not expose raw mesh vertices |
| 6DoF head transform (`HeadTransform`) | Yes | Yes |
| Gaze direction (`GazeData`) | Yes | Yes |
| Multi-face support (`List<TrackedFace>`) | Yes | Yes |
| Frame dimensions (`sourceImageWidth/Height`) | Yes | Yes |

### What Is Missing for Effects

| Capability | Current Status | Notes |
|---|---|---|
| **Camera frame access** (raw pixel data / texture) | Locked in `internal` classes (`ImageProxyConverter`, camera session) | Biggest gap; exposes private implementation details |
| **Render surface / GL context** | None — KMP-FaceLink is a tracking-only library | Must be provided by the host app or the effects module |
| **iOS face mesh vertices** | Unavailable via ARKit public API | ARKit exposes only blend shapes, not the 478-point mesh on iOS |

---

## Recommended Plugin Architecture

### New Module: `kmp-facelink-effects`

A separate Gradle/SPM module so adopters who do not need effects pay zero overhead.

```
kmp-facelink/                   ← tracking-only, no changes to public API
kmp-facelink-effects/           ← new module
  commonMain/
    EffectRenderer.kt           ← fun interface
    EffectsSession.kt           ← lifecycle manager
  androidMain/
    AndroidEffectRenderer.kt    ← OpenGL ES / GPUPixel bridge via JNI
  iosMain/
    IosEffectRenderer.kt        ← Metal / GPUPixel bridge via Obj-C++
```

### `EffectRenderer` Interface

```kotlin
public fun interface EffectRenderer {
    /**
     * Called once per frame after tracking completes.
     *
     * @param trackingData Latest face tracking result.
     * @param renderContext Platform-specific render surface handle (e.g. EGLContext on Android,
     *                      MTLCommandQueue on iOS). Null when no GPU surface is bound.
     */
    public fun onFrame(trackingData: FaceTrackingData, renderContext: Any?)
}
```

The `fun interface` (SAM) design keeps integration code minimal — a single lambda is enough for simple effects.

### Registration Model (DeepAR-style)

```kotlin
val session = EffectsSession.create(faceTracker)
session.addRenderer(BeautyFilterRenderer(smoothingLevel = 0.6f))
session.addRenderer(FaceMeshWireframeRenderer())
session.start()
```

- Renderers are composable and ordered; each receives the same `FaceTrackingData`.
- Platform-specific `EffectRenderer` implementations live in `androidMain` / `iosMain` with `expect`/`actual` for any GPU context setup.

---

## Prototype Plan

**Target effect:** Face mesh wireframe overlay (Android-first)

**Why wireframe first:**
- Uses existing 478 landmarks already present in `FaceTrackingData.landmarks` on Android.
- No camera frame exposure required — overlay drawn on top of camera preview via `SurfaceView`/`Canvas`.
- Self-contained in ~2–3 days; validates the `EffectRenderer` interface end-to-end.
- Visible demo output for issue #702.

**Implementation sketch:**
1. Add `kmp-facelink-effects` module (Android target only for prototype).
2. Implement `FaceMeshWireframeRenderer : EffectRenderer` — draws landmark triangulation using `Canvas.drawLine` on an overlay `SurfaceView`.
3. Wire into `androidApp` sample.
4. Confirm interface ergonomics before designing iOS counterpart.

**Estimated effort:** 2–3 days (prototype), 1 additional week for GPUPixel integration.

---

## Implementation Phases

### Phase 1 — Plugin Interface + Wireframe Prototype (~1 week)

| Task | Effort |
|---|---|
| Create `kmp-facelink-effects` Gradle module | 0.5 day |
| Define `EffectRenderer` / `EffectsSession` in `commonMain` | 0.5 day |
| `FaceMeshWireframeRenderer` on Android (canvas overlay) | 1–2 days |
| Sample app integration + docs | 0.5 day |

**Gate:** Interface design reviewed and stable before Phase 2.

### Phase 2 — Camera Frame Exposure + GPUPixel (~2–3 weeks)

| Task | Effort |
|---|---|
| Refactor Android `internal` camera pipeline to expose frame callback | 3–5 days |
| GPUPixel JNI bridge (Android) | 3–5 days |
| `BeautyFilterRenderer` implementing skin smoothing / face slimming | 2–3 days |
| iOS Metal bridge (if ARKit landmark gap addressed or workaround found) | 3–5 days |

---

## Risks & Open Questions

### Risk 1: Camera Frame Exposure Refactoring (High)

The camera frame is currently locked inside `internal` implementation classes (`ImageProxyConverter`, CameraX session). Exposing it safely requires:

- A new public callback interface without leaking `android.media.Image` / `ImageProxy` into `commonMain`.
- Careful lifecycle management (frame ownership, buffer reuse with `BitmapPool`).
- Potential API-breaking changes or new opt-in annotation.

**Mitigation:** Phase 1 wireframe prototype is entirely independent of camera frame access. Phase 2 scopes this work explicitly.

### Risk 2: iOS Landmark Gap (Medium)

ARKit does not expose the 478-point face mesh in `FaceTrackingData.landmarks` (always empty on iOS). Effects that require mesh-based warping (face slimming, eye enlargement) cannot work on iOS via the current API.

**Options:**
- Implement mesh-based effects using ARKit's `ARFaceGeometry` (6000+ vertices) directly in `iosMain` — requires camera frame access via ARKit session, which is also locked internally.
- Restrict Phase 1/2 effects to blend-shape-driven effects on iOS (available on both platforms).
- Investigate exposing `ARFaceGeometry` as an optional platform extension.

### Risk 3: GPUPixel OpenGL ES / Metal Context Management (Medium)

GPUPixel manages its own GL context. Sharing or creating a compatible context alongside the host app's rendering surface requires careful coordination. Metal on iOS adds additional complexity (no direct GL).

**Mitigation:** Validate context sharing in the Android wireframe prototype. Assess Metal path separately in Phase 2 planning.

### Open Questions

1. Should `renderContext: Any?` in `EffectRenderer.onFrame` be typed via an `expect`/`actual` platform wrapper instead of `Any?`?
2. Should `kmp-facelink-effects` ship GPUPixel as a bundled dependency (larger AAR) or as an optional peer dependency?
3. What frame format should the camera frame callback expose — `Bitmap`, `ByteArray` (NV21/YUV420), or a GPU texture handle?

---

## References

- GPUPixel GitHub: https://github.com/pixpark/gpupixel
- DeepAR SDK documentation (archived): https://docs.deepar.ai
- ARKit `ARFaceGeometry` reference: https://developer.apple.com/documentation/arkit/arfacegeometry
- MediaPipe Face Mesh (478 landmarks): https://developers.google.com/mediapipe/solutions/vision/face_landmarker
- Related issue: #702
- Competitive analysis: [docs/research/competitive-analysis.md](./competitive-analysis.md)
