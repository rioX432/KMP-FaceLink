# React Native and Flutter Wrapper Packages Research

**Issue:** #477
**Status:** Research complete
**Date:** 2026-04-02

## Executive Summary

This document evaluates creating official wrapper packages for React Native (Turbo Module) and Flutter (Platform Channel / FFI) to expose KMP-FaceLink's tracking capabilities to those ecosystems. KMP-FaceLink already produces AAR (Android) and XCFramework (iOS) artifacts, and the existing Unity plugin demonstrates a proven polling-based bridge pattern that can be adapted for both frameworks.

**Key finding:** Both wrappers are feasible. React Native's Turbo Module with JSI ArrayBuffer support enables zero-copy binary data transfer at 60fps. Flutter's dart:ffi approach bypasses platform channel overhead entirely, matching native performance. A shared binary protocol (flat float arrays, ~242 bytes per face frame) already exists in the Unity plugin and can be reused. Estimated effort: 3-4 weeks for RN, 2-3 weeks for Flutter, with an additional 1-2 weeks for publishing and sample apps.

---

## 1. Current KMP Artifacts and Bridge Pattern

### Available Artifacts

| Platform | Artifact | Distribution |
|---|---|---|
| Android | AAR (`kmp-facelink-release.aar`) | Maven Central / local |
| iOS | XCFramework (`KMPFaceLink.xcframework`) | Swift Package Manager |

### Unity Plugin Bridge Pattern (Reference)

The existing Unity plugin establishes a proven bridge architecture:

- **Polling model:** Native code collects Flow emissions on background threads and buffers the latest frame. The consumer (Unity `Update()`) polls per-frame.
- **Android path:** C# -> AndroidJavaObject (JNI) -> Kotlin AAR `FaceLinkUnityFacade`
- **iOS path:** C# -> DllImport("__Internal") -> C bridge (`KmpFaceLinkBridge.m`) -> K/N ObjC API
- **Data format:** Raw float arrays (52 floats for blend shapes, 6 floats for head transform, 63 floats per hand)
- **Thread safety:** `os_unfair_lock` (iOS), JNI-safe polling (Android)

This pattern directly informs the RN and Flutter wrapper designs.

---

## 2. React Native Turbo Module Approach

### Architecture

```
JavaScript (TypeScript)
    │
    ▼
Turbo Module Spec (Codegen)
    │
    ├── Android: Kotlin/Java Turbo Module → KMP-FaceLink AAR
    │     └── JNI call into FaceLinkRnFacade (wraps Flow → poll buffer)
    │
    └── iOS: ObjC++ Turbo Module → KMPFaceLink.xcframework
          └── C bridge → K/N ObjC API (reuse Unity pattern)
```

### Data Transfer Strategy

**Option A: JSI ArrayBuffer (recommended)**

- Turbo Modules with JSI bindings can share `ArrayBuffer` directly with JS without serialization
- Performance: ~1ms for 8MB payload vs ~666ms with base64 (per RN benchmarks)
- For face tracking: 52 floats (208 bytes) + 6 floats (24 bytes) = 232 bytes per frame — trivially fast
- Requires implementing `RCTTurboModuleWithJSIBindings` (iOS) or `TurboModule` with direct `ReadableArray` of doubles (Android)
- JS side receives `Float32Array` and indexes by blend shape ordinal

**Option B: Standard Turbo Module types (simpler, lower performance)**

- Pass `ReadableMap` / `NSDictionary` per frame
- Higher overhead from bridge serialization (~0.5-1ms per frame)
- Still viable at 60fps (~16ms budget) but wastes ~6% of frame time

**Recommended:** Option A (JSI ArrayBuffer) for the core tracking data path. Option B for configuration/lifecycle methods.

### Streaming Pattern

RN does not have a direct equivalent to Kotlin `Flow`. Two approaches:

1. **EventEmitter (push model):** Native side collects from Flow, emits events to JS at frame rate. JS subscribes via `NativeEventEmitter`. Simple but adds bridge overhead per event.

2. **Polling via requestAnimationFrame (pull model, recommended):** Mirror the Unity pattern. Native buffers latest frame. JS polls via a synchronous JSI getter each `requestAnimationFrame` tick. Zero bridge overhead per frame — just a shared memory read.

```typescript
// JS API (polling model)
import { FaceLink } from 'react-native-kmp-facelink';

const faceLink = FaceLink.create({ enableFace: true, enableHands: false });
faceLink.start();

// In animation loop
function onFrame() {
  const data = faceLink.getLatestFaceData(); // JSI sync call, returns Float32Array
  if (data) {
    const jawOpen = data[BlendShape.jawOpen]; // index-based access
    // ... use blend shapes
  }
  requestAnimationFrame(onFrame);
}
```

### Package Structure

```
react-native-kmp-facelink/
├── package.json
├── src/
│   ├── index.ts              # Public API
│   ├── NativeFaceLink.ts     # Turbo Module spec (Codegen input)
│   └── BlendShape.ts         # Enum constants (ordinal mapping)
├── android/
│   ├── build.gradle.kts
│   ├── src/main/java/.../
│   │   ├── FaceLinkModule.kt       # Turbo Module impl
│   │   └── FaceLinkRnFacade.kt     # Flow → poll buffer adapter
│   └── libs/
│       └── kmp-facelink-release.aar
├── ios/
│   ├── FaceLinkModule.mm           # ObjC++ Turbo Module impl
│   ├── FaceLinkBridge.m            # C bridge (adapt from Unity)
│   └── FaceLinkBridge.h
├── react-native-kmp-facelink.podspec
└── example/                        # Sample RN app
```

### npm Publishing Requirements

- Package name: `react-native-kmp-facelink`
- Prefix convention: no `RTN` prefix needed for third-party packages
- Must include: `package.json` with `react-native` field, Codegen spec, podspec for iOS
- React Native >= 0.76 (New Architecture stable)
- Turbo Module Codegen auto-runs during `pod install` / Gradle build
- AAR bundled in `android/libs/`, XCFramework referenced via podspec vendored_frameworks
- Size estimate: ~5MB (AAR) + ~8MB (XCFramework) = ~13MB total

---

## 3. Flutter Platform Channel / FFI Approach

### Architecture Options

| Approach | Latency | Complexity | Recommended For |
|---|---|---|---|
| MethodChannel | ~1-2ms per call | Low | Config, lifecycle |
| EventChannel | ~0.5-1ms per event | Medium | Push-based streaming |
| dart:ffi | ~0.01ms per call | High | High-frequency data (recommended) |

### Recommended: Hybrid FFI + MethodChannel

```
Dart (Flutter)
    │
    ├── dart:ffi → shared C bridge (libkmpfacelink_bridge.so / .dylib)
    │     └── Polling: getLatestBlendShapes(), getLatestHeadTransform()
    │
    └── MethodChannel → Kotlin/Swift lifecycle methods
          └── initialize(), startTracking(), stopTracking(), dispose()
```

**Why FFI for data:** dart:ffi calls native C functions synchronously on the same thread with zero serialization overhead. For 52 floats at 60fps, this is essentially free (~10 microseconds per call). The C bridge already exists in the Unity plugin and can be compiled as a shared library.

**Why MethodChannel for lifecycle:** Start/stop/configure calls happen infrequently and benefit from the simpler async MethodChannel API. No performance concern.

### C Bridge (Shared with Unity)

The existing `KmpFaceLinkBridge.h/.m` C API can be extracted into a shared C bridge library:

```c
// Already defined in Unity plugin — reuse directly
bool KmpFaceLink_Initialize(bool enableFace, bool enableHands);
void KmpFaceLink_StartTracking(void);
void KmpFaceLink_StopTracking(void);
void KmpFaceLink_GetLatestBlendShapes(float* blendShapes, int count);
void KmpFaceLink_GetLatestHeadTransform(float* transform, int count);
```

Dart FFI bindings (auto-generated with `ffigen`):

```dart
final class FaceLinkBindings {
  final DynamicLibrary _lib;

  late final _getBlendShapes = _lib.lookupFunction<
    Void Function(Pointer<Float>, Int32),
    void Function(Pointer<Float>, int)
  >('KmpFaceLink_GetLatestBlendShapes');

  void getLatestBlendShapes(Pointer<Float> buffer, int count) {
    _getBlendShapes(buffer, count);
  }
}
```

### Flutter API Design

```dart
// Public API
class FaceLink {
  static Future<FaceLink> create({
    bool enableFace = true,
    bool enableHands = false,
  });

  Future<void> start();
  Future<void> stop();
  Future<void> dispose();

  // Polling (FFI, zero-overhead)
  FaceTrackingData? getLatestFaceData();
  HandTrackingData? getLatestHandData();

  // Stream (convenience wrapper over polling)
  Stream<FaceTrackingData> get faceTrackingStream;
  Stream<HandTrackingData> get handTrackingStream;
}
```

The `Stream` wrapper internally polls via a `Timer.periodic` at the target frame rate and emits new frames when the timestamp changes — identical to how the Unity `Update()` loop works.

### Package Structure

```
flutter_kmp_facelink/
├── pubspec.yaml
├── lib/
│   ├── flutter_kmp_facelink.dart   # Public API barrel
│   ├── src/
│   │   ├── facelink.dart           # FaceLink class
│   │   ├── facelink_bindings.dart  # FFI bindings (ffigen)
│   │   ├── blend_shape.dart        # BlendShape enum
│   │   └── models.dart             # FaceTrackingData, etc.
├── android/
│   ├── build.gradle.kts
│   ├── src/main/
│   │   ├── kotlin/.../FaceLinkPlugin.kt    # MethodChannel handler
│   │   └── jniLibs/                        # Prebuilt .so bridge
│   └── libs/
│       └── kmp-facelink-release.aar
├── ios/
│   ├── Classes/
│   │   ├── FaceLinkPlugin.swift            # MethodChannel handler
│   │   └── FaceLinkBridge.m/.h             # C bridge (shared)
│   └── flutter_kmp_facelink.podspec
├── src/                                    # C bridge source (build hook)
│   └── kmpfacelink_bridge.c
├── build.dart                              # Native build hook (Flutter 3.38+)
└── example/                                # Sample Flutter app
```

### pub.dev Publishing Requirements

- Package name: `flutter_kmp_facelink`
- Must include: `pubspec.yaml` with `flutter.plugin` section, `LICENSE` (BSD-3-Clause)
- Federated plugin structure recommended for separate platform packages
- Size limit: <100MB gzipped, <256MB uncompressed
- Dependencies must be hosted on pub.dev or SDK dependencies only
- AAR bundled in `android/libs/`, XCFramework via podspec vendored_frameworks
- Minimum Flutter SDK: 3.38+ (for build hooks / `package:ffi` template)

---

## 4. Binary Protocol Design

### Current Data Layout (from Unity Plugin)

The Unity plugin already uses a compact binary layout that avoids JSON:

| Data | Type | Size | Description |
|---|---|---|---|
| Blend shapes | `float[52]` | 208 bytes | ARKit-compatible, ordinal-indexed |
| Head transform | `float[6]` | 24 bytes | pitch, yaw, roll, posX, posY, posZ |
| Timestamp | `int64` | 8 bytes | Monotonic milliseconds |
| Is tracking | `bool` | 1 byte | Face detection status |
| **Face total** | | **241 bytes** | |
| Hand landmarks (per hand) | `float[63]` | 252 bytes | 21 joints * 3 coords |
| Handedness | `int32` | 4 bytes | 0=left, 1=right |
| Hand count | `int32` | 4 bytes | 0-2 |
| **Hands total (2 hands)** | | **516 bytes** | |
| **Full frame (face + 2 hands)** | | **~757 bytes** | |

### Why Not JSON?

| Format | Size (face frame) | Encode time | Decode time |
|---|---|---|---|
| JSON | ~2,500 bytes | ~50-100 µs | ~80-150 µs |
| Binary (float array) | 241 bytes | ~1 µs (memcpy) | ~1 µs (pointer cast) |

At 60fps, JSON serialization would consume ~6-15ms of the 16.6ms frame budget. Binary is effectively free.

### Protocol for RN and Flutter

Both wrappers should use the same binary layout:

1. **Native side:** Flow collector writes into a pre-allocated float array buffer (already implemented in Unity bridge)
2. **Bridge:** Exposes raw pointer / array getter functions
3. **Consumer side:**
   - **RN:** JSI `ArrayBuffer` wrapping the float buffer → `Float32Array` view in JS
   - **Flutter:** `Pointer<Float>` via dart:ffi → `Float32List.view()` in Dart

No serialization step exists in either path — data is accessed directly from the shared buffer.

---

## 5. Performance Analysis

### Can RN/Flutter Handle 60fps Blend Shape Updates?

**Yes.** The critical path is:

| Step | RN (JSI polling) | Flutter (FFI polling) |
|---|---|---|
| Poll native buffer | ~0.01ms (JSI sync) | ~0.01ms (FFI sync) |
| Read 52 floats | ~0.001ms (ArrayBuffer view) | ~0.001ms (Float32List view) |
| Apply to UI/avatar | ~1-5ms (app-dependent) | ~1-5ms (app-dependent) |
| **Total per frame** | **~1-5ms** | **~1-5ms** |
| **Frame budget (60fps)** | **16.6ms** | **16.6ms** |
| **Headroom** | **~70-90%** | **~70-90%** |

### Potential Bottlenecks

1. **RN EventEmitter (push model):** Each event crosses the bridge asynchronously. At 60fps this adds ~0.5-1ms per frame. Viable but wastes budget — prefer polling.
2. **Flutter EventChannel:** Similar to RN EventEmitter. The async message passing overhead is unnecessary when FFI polling is available.
3. **GC pressure:** Avoid allocating new objects per frame. Both wrappers should reuse buffers (RN: single `ArrayBuffer`, Flutter: pre-allocated `Pointer<Float>`).
4. **JS/Dart thread contention:** Tracking runs on a background thread; polling reads from a lock-protected buffer. The lock hold time is <1µs (memcpy of 208 bytes), so contention is negligible.

### Comparison with Competitors

No existing KMP tracking SDK provides RN/Flutter wrappers. The closest competitors are:

- **react-native-vision-camera-face-detector:** MLKit-based, lower-level than KMP-FaceLink (raw landmarks, no blend shapes)
- **google_mlkit_face_detection (Flutter):** Similar to above, no ARKit-compatible blend shape output
- **KBY-AI Face SDK (RN):** Commercial, focused on recognition/liveness, not real-time blend shape streaming

KMP-FaceLink wrappers would be the first to provide ARKit-compatible blend shape data at 60fps in both ecosystems.

---

## 6. Existing Examples of KMP Libraries Wrapped for RN/Flutter

### KMP → React Native

- **react-native-kmp-migration** (GitHub: HenryQuan): Uses Kotlin/JS compilation to share KMP logic directly in RN. Not applicable here since we need native platform APIs (camera, ARKit).
- **General pattern:** Create a "facade" Kotlin class that exposes a simplified API, then wrap it in a Turbo Module. This is exactly what the Unity plugin's `FaceLinkUnityFacade` does.

### KMP → Flutter

- No well-known examples of KMP libraries wrapped for Flutter via FFI exist in the ecosystem.
- The typical pattern is: compile KMP to a native library (AAR/.framework), then call it from Flutter via Platform Channel or FFI.
- Flutter's `package:ffi` template (Flutter 3.38+) with build hooks simplifies this significantly.

### Insight

The KMP → RN/Flutter wrapper space is largely uncharted. Being first with a high-quality tracking SDK wrapper is a competitive advantage.

---

## 7. Effort Estimation

### React Native Wrapper

| Task | Effort | Notes |
|---|---|---|
| Turbo Module scaffold + Codegen spec | 2 days | `create-react-native-library` tool |
| Android Turbo Module (Kotlin) | 3 days | Adapt Unity `FaceLinkUnityFacade`, JNI bridging |
| iOS Turbo Module (ObjC++) | 3 days | Adapt Unity C bridge, JSI ArrayBuffer |
| TypeScript API + BlendShape constants | 1 day | |
| Sample app | 2 days | Minimal RN app showing blend shape bars |
| Testing + polish | 2 days | |
| npm publishing setup | 1 day | CI, README, package.json |
| **Total** | **~14 days (3 weeks)** | |

### Flutter Wrapper

| Task | Effort | Notes |
|---|---|---|
| Plugin scaffold (federated) | 1 day | `flutter create --template=plugin_ffi` |
| C bridge extraction + build hook | 2 days | Extract from Unity, compile as .so/.dylib |
| Android MethodChannel + AAR integration | 2 days | Lifecycle only |
| iOS MethodChannel + podspec | 2 days | Lifecycle only |
| Dart FFI bindings (ffigen) | 1 day | Auto-generated from C header |
| Dart API + Stream wrapper | 2 days | |
| Sample app | 1 day | Minimal Flutter app |
| Testing + polish | 2 days | |
| pub.dev publishing setup | 1 day | CI, README, pubspec |
| **Total** | **~14 days (3 weeks)** | |

### Shared Work

| Task | Effort | Notes |
|---|---|---|
| Extract C bridge into shared module | 2 days | Reusable across Unity/RN/Flutter |
| Binary protocol documentation | 1 day | Shared spec for all bridge consumers |
| **Total** | **~3 days** | |

### Grand Total

**~6-7 weeks** for both wrappers (with some parallelization possible).

**Recommended order:** Flutter first (simpler FFI path, reuses C bridge directly), then RN (more complex JSI setup). Alternatively, share the C bridge extraction work first, then develop both in parallel.

---

## 8. Recommendations

1. **Extract the C bridge into a shared `kmp-facelink-cbridge` module** — the same C header/implementation serves Unity, Flutter (FFI), and RN (JSI). Avoid maintaining three copies.

2. **Use polling over push for both wrappers** — the Unity plugin proves this pattern works at 60fps with minimal complexity. Push models (EventEmitter/EventChannel) add unnecessary bridge overhead.

3. **Start with Flutter** — the dart:ffi path is more straightforward and the C bridge can be reused almost verbatim from the Unity plugin.

4. **Use JSI ArrayBuffer for RN** — standard Turbo Module types work but waste frame budget on serialization. JSI ArrayBuffer provides near-zero-cost data transfer.

5. **Publish as separate packages** — `react-native-kmp-facelink` on npm, `flutter_kmp_facelink` on pub.dev. Keep them independent of each other and of the core KMP library's release cycle.

6. **Target RN 0.76+ and Flutter 3.38+** — both versions have stable support for the required features (New Architecture / build hooks).

---

## References

- [React Native Turbo Modules Introduction](https://reactnative.dev/docs/turbo-native-modules-introduction)
- [React Native New Architecture - Turbo Modules](https://github.com/reactwg/react-native-new-architecture/blob/main/docs/turbo-modules.md)
- [Custom JSI Bindings in TurboModules (Callstack)](https://www.callstack.com/blog/how-to-add-custom-jsi-bindings-in-your-turbomodule-and-why-you-might-need-them)
- [ArrayBuffer Support in TurboModules RFC](https://github.com/react-native-community/discussions-and-proposals/pull/947)
- [JSI ArrayBuffer Discussion](https://github.com/reactwg/react-native-new-architecture/discussions/157)
- [Wrapping Native iOS Modules with XCFramework for React Native](https://medium.com/@rashadmilton14/wrapping-native-ios-modules-with-xcframework-for-react-native-5595b4f93354)
- [Flutter Platform Channels](https://docs.flutter.dev/platform-integration/platform-channels)
- [Flutter Bind to Native Code using FFI](https://docs.flutter.dev/platform-integration/bind-native-code)
- [Improving Platform Channel Performance in Flutter](https://medium.com/flutter/improving-platform-channel-performance-in-flutter-e5b4e5df04af)
- [Dart FFI for High-Performance Flutter Integrations](https://vibe-studio.ai/insights/how-to-use-dart-ffi-for-high-performance-flutter-integrations)
- [Flutter Developing Packages & Plugins](https://docs.flutter.dev/packages-and-plugins/developing-packages)
- [Dart Publishing Packages](https://dart.dev/tools/pub/publishing)
- [Expo Wrapping Third-Party Native Libraries](https://docs.expo.dev/modules/third-party-library/)
- [KMP vs React Native (Kotlin docs)](https://kotlinlang.org/docs/multiplatform/kotlin-multiplatform-react-native.html)
- [KMP vs Flutter (Kotlin docs)](https://kotlinlang.org/docs/multiplatform/kotlin-multiplatform-flutter.html)
- [react-native-vision-camera-face-detector](https://github.com/luicfrr/react-native-vision-camera-face-detector)
