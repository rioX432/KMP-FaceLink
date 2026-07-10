# Kotlin/Wasm Web Target for Browser-Based Tracking

## Status

**Experimental** — face, hand, and body tracking are implemented via MediaPipe JS interop (`MediaPipeWasmFaceTracker` / `MediaPipeWasmHandTracker` / `MediaPipeWasmBodyTracker`). Remaining gaps: holistic tracking is a no-op stub, smoothing (`SmoothingConfig` filters) is not yet implemented on the Wasm trackers, and depth estimation is not available (Android-only).

## Kotlin/Wasm Maturity (as of Kotlin 2.2.0)

- `wasmJs` target: **Beta** (expected Stable late 2026)
- Requires `@OptIn(ExperimentalWasmDsl::class)` in build scripts
- Browser requirement: WebAssembly GC support (Chrome 119+, Firefox 120+, Safari 18.2+)
- kotlinx.coroutines 1.10.x: wasmJs supported
- kotlinx.serialization 1.8.x: wasmJs supported
- Ktor: wasmJs supported for client (not server)

## What's Done

### Modules with `wasmJs` target added

| Module | Status | Notes |
|---|---|---|
| `kmp-facelink` | Compiles | Face/hand/body tracking via MediaPipe JS interop; holistic is a no-op stub |
| `kmp-facelink-avatar` | Compiles | Pure common code, no platform actuals needed |
| `kmp-facelink-actions` | Compiles | Pure common code, no platform actuals needed |

### wasmJs actual implementations (kmp-facelink core)

- `PlatformContext` — empty class (no browser context needed)
- `PlatformLock` — no-op (Wasm is single-threaded)
- `currentThreadId()` — returns `1L` (single-threaded)
- `FaceLinkLogger` — delegates to `console.warn`/`console.error`/`console.log`
- `FaceTrackerFactory` — returns `MediaPipeWasmFaceTracker` (real tracking via MediaPipe JS)
- `HandTrackerFactory` — returns `MediaPipeWasmHandTracker` (real tracking via MediaPipe JS)
- `BodyTrackerFactory` — returns `MediaPipeWasmBodyTracker` (real tracking via MediaPipe JS)
- `HolisticTrackerFactory` — returns the `NoOpHolisticTracker` stub (the remaining Wasm no-op)
- `WebGpuCapability` — detects the available GPU backend (WebGPU / WebGL / none) and WebNN device type

## Modules NOT Yet Targetable

| Module | Blocker |
|---|---|
| `kmp-facelink-voice` | Platform-specific TTS/ASR (Android MediaPlayer, iOS AVSpeechSynthesizer) |
| `kmp-facelink-llm` | Ktor client works but on-device LLM bindings are platform-native |
| `kmp-facelink-pipeline` | Depends on voice + LLM |
| `kmp-facelink-networking` | UDP sockets not available in browser; would need WebSocket fallback |
| `kmp-facelink-vmc` | UDP-based protocol; same networking blocker |
| `kmp-facelink-ifm` | UDP-based protocol |
| `kmp-facelink-stream` | Ktor server (CIO engine) does not support wasmJs |
| `kmp-facelink-vts` | WebSocket client works but needs testing |

## Roadmap: Full Browser-Based Tracking

### Phase 1: MediaPipe JS Interop (Done)

Replaced no-op face tracker with `MediaPipeWasmFaceTracker`:

1. Uses `js()` inline functions as a thin bridge to MediaPipe Tasks Vision JS API
2. Accesses browser camera via `navigator.mediaDevices.getUserMedia()`
3. Processes video frames through `FaceLandmarker.detectForVideo()` on `requestAnimationFrame`
4. Maps JS results to `FaceTrackingData` (blend shapes, landmarks, head transform) via `WasmResultMapper`
5. Emits results through Kotlin Flow

**Host page requirement:** Must set `globalThis.__faceLinkVision` to the loaded `@mediapipe/tasks-vision` module before starting the tracker.

### Phase 2: Hand & Body Tracking (Done)

- `MediaPipeWasmHandTracker` — MediaPipe Hand Landmarker JS for hand tracking
- `MediaPipeWasmBodyTracker` — MediaPipe Pose Landmarker JS for body tracking
- Same pattern (and same `globalThis.__faceLinkVision` host-page requirement) as face tracking
- Not yet ported: smoothing (`SmoothingConfig` filters) and the holistic tracker

### Phase 3: Protocol Modules

- `kmp-facelink-vts`: WebSocket-based, most feasible for web
- `kmp-facelink-stream`: Replace Ktor server with browser-side WebSocket client connecting to a remote server
- VMC/iFM: Not feasible in browser (UDP not available); consider WebSocket bridge

### Phase 4: Voice & LLM

- Web Speech API for TTS/ASR
- WebLLM or cloud API for LLM inference
- Web Audio API for lip sync analysis

## Browser Compatibility

| Browser | WebAssembly GC | Status |
|---|---|---|
| Chrome 119+ | Yes | Supported |
| Firefox 120+ | Yes | Supported |
| Safari 18.2+ | Yes | Supported |
| Edge 119+ | Yes | Supported (Chromium-based) |

## Build Commands

```bash
# Compile wasmJs targets
./gradlew :kmp-facelink:compileKotlinWasmJs
./gradlew :kmp-facelink-avatar:compileKotlinWasmJs
./gradlew :kmp-facelink-actions:compileKotlinWasmJs

# Run wasmJs tests in browser
./gradlew :kmp-facelink-avatar:wasmJsBrowserTest
./gradlew :kmp-facelink-actions:wasmJsBrowserTest
```

## References

- [Kotlin/Wasm documentation](https://kotlinlang.org/docs/wasm-overview.html)
- [MediaPipe JS Solutions](https://ai.google.dev/edge/mediapipe/solutions/guide#web)
- [WebAssembly GC proposal](https://github.com/nicolo-ribaudo/tc39-proposal-wasm-gc)
- [Kotlin/Wasm interop with JS](https://kotlinlang.org/docs/wasm-js-interop.html)
