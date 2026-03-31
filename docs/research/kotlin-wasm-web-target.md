# Kotlin/Wasm Web Target for Browser-Based Tracking

## Status

**Experimental** — computation-only modules compile and work; tracking APIs are no-op stubs.

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
| `kmp-facelink` | Compiles | No-op tracker stubs; utility actuals (logger, lock, thread ID) |
| `kmp-facelink-avatar` | Compiles | Pure common code, no platform actuals needed |
| `kmp-facelink-actions` | Compiles | Pure common code, no platform actuals needed |

### wasmJs actual implementations (kmp-facelink core)

- `PlatformContext` — empty class (no browser context needed)
- `PlatformLock` — no-op (Wasm is single-threaded)
- `currentThreadId()` — returns `1L` (single-threaded)
- `FaceLinkLogger` — delegates to `console.warn`/`console.error`/`console.log`
- Tracker factories — return `NoOp*Tracker` stubs that emit `TrackingState.ERROR`

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

### Phase 1: MediaPipe JS Interop (Next)

Replace no-op tracker stubs with actual browser-based tracking using MediaPipe JS:

1. Use `@JsModule` / `external` declarations to call MediaPipe JS from Kotlin/Wasm
2. Access browser camera via `navigator.mediaDevices.getUserMedia()`
3. Process video frames through MediaPipe Face Landmarker JS
4. Convert JS landmarks to `FaceTrackingData` model
5. Emit results through Kotlin Flow

**Key challenge:** Kotlin/Wasm JS interop is limited — complex JS object graphs require careful `external` type definitions or a thin JS bridge layer.

### Phase 2: Hand & Body Tracking

- MediaPipe Hand Landmarker JS for hand tracking
- MediaPipe Pose Landmarker JS for body tracking
- Same pattern as face tracking

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
