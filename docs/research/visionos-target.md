# visionOS Spatial Computing Platform Target

## Status

**Research only** — No code changes. Kotlin/Native does not have a visionOS target as of Kotlin 2.2.0.

## visionOS Tracking API Inventory

### Hand Tracking

visionOS provides skeletal hand tracking through ARKit's `HandTrackingProvider` data provider, which is fundamentally different from the iOS Vision framework approach used in KMP-FaceLink today.

| Feature | Details |
|---|---|
| Framework | ARKit (not Vision framework) |
| Provider | `HandTrackingProvider` |
| Data type | `HandAnchor` with skeletal joint hierarchy |
| Update rate | **90 Hz** (visionOS 2.0+; was 30 Hz in visionOS 1.x) |
| Prediction | Hand-tracking prediction available in visionOS 2.0+ (extrapolates future hand position, reduces effective latency by up to 22ms) |
| Joint model | Skeletal hierarchy rooted at `.wrist`; all fingers + thumb + forearm |
| Coordinate system | 3D world-space (meters), not 2D normalized image coordinates |
| Hands | Both hands simultaneously |
| Access pattern | `anchorUpdates` (AsyncSequence) or `latestAnchors` (polling) |
| Requires | Full Space (ImmersiveSpace), not available in Shared Space |
| Simulator | Not supported — physical device only |

### Face Tracking

Face tracking via ARKit's `ARFaceTrackingConfiguration` is **not available on visionOS**. The TrueDepth camera system used for face tracking on iPhone/iPad does not exist on Vision Pro.

| Feature | Details |
|---|---|
| ARFaceTrackingConfiguration | **Not supported** on visionOS |
| BlendShape data | **Not accessible** to third-party developers |
| Persona system | Apple's internal face tracking for Personas; not exposed via public API |
| Persona improvements | visionOS 26 significantly improved Persona fidelity (hair, eyelashes, expressivity) but this remains a system-level feature |

**Impact:** KMP-FaceLink's `FaceTracker` interface (which relies on ARKit blend shapes) cannot be implemented on visionOS with current APIs. This is the most significant gap.

### Body Tracking

| Feature | Details |
|---|---|
| Provider | `ARHumanBodyManager` (visionOS) / `BodyTrackingProvider` |
| Event | `humanBodiesChanged` for real-time skeleton updates |
| Skeleton | 3D joint positions usable for avatar animation |
| Use case | Fitness apps, full-body avatar driving |

### Other ARKit Features in visionOS

Available in Full Space mode:

| Feature | Provider |
|---|---|
| World tracking | `WorldTrackingProvider` (device pose, origin) |
| Plane estimation | `PlaneDetectionProvider` |
| Scene reconstruction | `SceneReconstructionProvider` |
| Image anchoring | `ImageTrackingProvider` |
| Object tracking | `ObjectTrackingProvider` (visionOS 2.0+) |
| Hand tracking | `HandTrackingProvider` |

## visionOS vs iOS Tracking API Comparison

### Hand Tracking

| Aspect | iOS (current KMP-FaceLink) | visionOS |
|---|---|---|
| Framework | Vision (`VNDetectHumanHandPoseRequest`) | ARKit (`HandTrackingProvider`) |
| Input | Camera frames via `AVCaptureSession` | Headset sensors (no camera setup needed) |
| Coordinate system | 2D normalized (0-1) image coordinates | 3D world-space (meters) |
| Update rate | ~15 fps (throttled in KMP-FaceLink) | 90 Hz native |
| Skeleton | 21 landmarks per hand | Full skeletal hierarchy with parent joints |
| Data access | Synchronous `performRequests` on pixel buffer | AsyncSequence (`anchorUpdates`) |
| Camera management | Manual `AVCaptureSession` setup | None — system-managed sensors |
| Handedness | `chirality` property | `chirality` on `HandAnchor` |

### Face Tracking

| Aspect | iOS (current KMP-FaceLink) | visionOS |
|---|---|---|
| Framework | ARKit (`ARFaceTrackingConfiguration`) | **Not available** |
| Hardware | TrueDepth front camera | No equivalent sensor exposure |
| BlendShapes | 52 ARKit blend shapes | Not accessible |
| Head transform | `simd_float4x4` from `ARFaceAnchor` | N/A |

### Body Tracking

| Aspect | iOS (current KMP-FaceLink) | visionOS |
|---|---|---|
| Framework | Vision (`VNDetectHumanBodyPoseRequest`) | ARKit (`ARHumanBodyManager`) |
| Input | Camera frames via `AVCaptureSession` | Headset sensors |
| Coordinates | 2D normalized image coordinates | 3D world-space |
| Access pattern | Synchronous request on pixel buffer | Event-driven (`humanBodiesChanged`) |

## Kotlin/Native visionOS Compilation Status

### Current State (Kotlin 2.2.0, March 2026)

- **visionOS is NOT a supported Kotlin/Native target**
- JetBrains YouTrack issue: [KT-59571 — Add support for visionOS SDK](https://youtrack.jetbrains.com/issue/KT-59571)
- Official supported Apple targets: iOS (arm64, simulatorArm64, x64), macOS (arm64, x64), watchOS (arm64, simulatorArm64, x64, deviceArm64), tvOS (arm64, simulatorArm64, x64)
- The 2025 KMP roadmap focuses on Swift Export stabilization (Stable in 2026) and build speed improvements — visionOS is not mentioned

### What Would Be Needed

For Kotlin/Native to support visionOS:

1. **New compiler targets**: `xros` (device) and `xrossimulator` (simulator) LLVM targets
2. **Platform libraries**: cinterop definitions for visionOS frameworks (RealityKit, ARKit visionOS APIs, SwiftUI spatial types)
3. **Gradle plugin**: `visionosArm64()`, `visionosSimulatorArm64()` target presets
4. **Library ecosystem**: kotlinx.coroutines, kotlinx.serialization, Ktor would all need visionOS target artifacts

### Community Workarounds

| Approach | Description | Viability |
|---|---|---|
| SKIP framework | Transpiles Swift to Kotlin (Android direction); not applicable for K/N → visionOS | Not viable |
| Manual LLVM target | Community attempts to add xros target to K/N compiler | Fragile, unsupported |
| Swift wrapper | Write visionOS tracking in Swift, bridge to shared KMP code via Objective-C/Swift interop | **Most viable short-term** |
| Compose Multiplatform | No visionOS target support | Not viable |

## Architecture Plan for Future Integration

### Recommended Approach: Swift Bridge Layer

Since Kotlin/Native won't have visionOS support in the near term, the recommended architecture uses a Swift bridge:

```
┌─────────────────────────────────────────────────────┐
│  visionOS App (Swift / SwiftUI / RealityKit)        │
│                                                      │
│  ┌─────────────────────────────────────────────┐    │
│  │  VisionOSTrackingBridge (Swift)              │    │
│  │  - HandTrackingProvider → HandTrackingData   │    │
│  │  - ARHumanBodyManager → BodyTrackingData     │    │
│  │  - Converts 3D world coords to model types   │    │
│  └──────────────────┬──────────────────────────┘    │
│                     │ JSON / Protobuf / WebSocket    │
│  ┌──────────────────▼──────────────────────────┐    │
│  │  KMP-FaceLink shared modules                 │    │
│  │  - kmp-facelink-avatar (parameter mapping)   │    │
│  │  - kmp-facelink-actions (gesture triggers)   │    │
│  │  - kmp-facelink-pipeline (orchestration)     │    │
│  │  - kmp-facelink-networking (VMC/VTS output)  │    │
│  └──────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────┘
```

**Phase 1 — Swift-only tracking + KMP processing (feasible now):**
- Write `HandTrackingProvider` wrapper in Swift
- Serialize `HandTrackingData` and send to a KMP module running on macOS/JVM (local network)
- Reuse existing avatar mapping, actions, and protocol modules

**Phase 2 — Native visionOS KMP target (when Kotlin adds support):**
- Add `visionosArm64()` / `visionosSimulatorArm64()` targets
- Create `visionosMain` source set with actual implementations
- `HandTracker` actual: wraps `HandTrackingProvider` directly via cinterop
- `BodyTracker` actual: wraps `ARHumanBodyManager` via cinterop
- `FaceTracker` actual: no-op stub (face tracking unavailable on visionOS)

### Key Adaptation Points

| KMP-FaceLink component | visionOS adaptation needed |
|---|---|
| `FaceTracker` | No-op or unsupported (no face tracking API) |
| `HandTracker` | New impl: `HandTrackingProvider` instead of Vision framework; 3D coords instead of 2D |
| `BodyTracker` | New impl: `ARHumanBodyManager` instead of Vision framework; 3D coords |
| `HandTrackingData.landmarks` | Currently 2D (x, y, z normalized); visionOS provides real 3D world coordinates — need coordinate space abstraction |
| `CameraFacing` / `switchCamera()` | Not applicable on visionOS (no camera concept) |
| `AVCaptureSession` management | Not needed — sensors are system-managed |
| `FrameThrottler` | Less critical at 90 Hz but thermal management still relevant |
| `SharedVisionCaptureSession` | Not applicable on visionOS |

### Model Changes Required

The biggest API impact is the coordinate system difference:

- **Current:** 2D normalized coordinates (0.0–1.0) from Vision framework, plus optional 3D from VisionBodyTracker3D
- **visionOS:** Native 3D world coordinates in meters

Options:
1. **Add coordinate space enum** to `HandTrackingData` / `BodyTrackingData` (backward compatible)
2. **Always normalize** visionOS 3D data to match existing 2D contract (loses spatial information)
3. **Dual output** — provide both normalized 2D projections and raw 3D world coordinates

Option 1 is recommended as it preserves spatial computing capabilities while maintaining backward compatibility.

## Feasibility Assessment

| Criterion | Assessment |
|---|---|
| Hand tracking | **Feasible with Swift bridge** — best-in-class 90Hz skeletal data |
| Face tracking | **Not feasible** — no public API on visionOS |
| Body tracking | **Feasible with Swift bridge** — 3D skeleton available |
| Kotlin/Native target | **Blocked** — no visionOS target; earliest estimate H2 2026 or 2027 |
| Shared KMP modules | **Usable now** — avatar, actions, pipeline, networking modules run on JVM/macOS and can receive visionOS tracking data over local network |
| Market readiness | **Low** — Vision Pro install base is small; developer interest is growing |

## Timeline Recommendation

| Phase | Timeframe | Deliverable |
|---|---|---|
| **0. Research** (this doc) | Now | API inventory, architecture plan |
| **1. Swift prototype** | When prioritized | Standalone Swift visionOS app that captures hand + body data and sends to KMP pipeline over WebSocket |
| **2. Coordinate abstraction** | Before Phase 1 or concurrent | Add coordinate space metadata to tracking data models; ensure downstream modules handle 3D input |
| **3. Native KMP target** | After JetBrains ships visionOS target (KT-59571) | Add `visionosMain` source set with cinterop-based tracker implementations |
| **4. Face tracking workaround** | TBD | Monitor Apple's Persona API evolution; consider camera passthrough face detection as fallback |

**Recommendation:** Defer active development until either (a) Kotlin/Native adds visionOS target support, or (b) a concrete user/business need justifies the Swift bridge approach. Monitor KT-59571 and WWDC announcements annually.

## References

- [ARKit in visionOS — Apple Developer Documentation](https://developer.apple.com/documentation/arkit/arkit-in-visionos)
- [HandTrackingProvider — Apple Developer Documentation](https://developer.apple.com/documentation/arkit/handtrackingprovider/)
- [Tracking and visualizing hand movement — Apple Developer](https://developer.apple.com/documentation/visionOS/tracking-and-visualizing-hand-movement)
- [Meet ARKit for spatial computing — WWDC23](https://developer.apple.com/videos/play/wwdc2023/10082/)
- [KT-59571 — Add support for visionOS SDK](https://youtrack.jetbrains.com/issue/KT-59571)
- [Kotlin/Native supported targets](https://kotlinlang.org/docs/native-target-support.html)
- [KMP Development Roadmap for 2025](https://blog.jetbrains.com/kotlin/2024/10/kotlin-multiplatform-development-roadmap-for-2025/)
- [visionOS 2 hand tracking improvements](https://www.roadtovr.com/visionos-2-hand-tracking-latency-prediction/)
- [visionOS What's New — Apple Developer](https://developer.apple.com/visionos/whats-new/)
