# KMP Camera Libraries Evaluation

**Issue:** #601
**Status:** Research complete
**Date:** 2026-04-06

## Executive Summary

This document evaluates emerging KMP camera libraries (CameraK, Compose-Camera) as potential replacements for the current platform-specific camera setup (CameraXManager + AVCaptureSession). **Recommendation: keep the current approach.** Neither library provides the low-level frame access, performance control, or multi-tracker frame distribution that KMP-FaceLink requires.

## Current Implementation

KMP-FaceLink has a mature, purpose-built camera pipeline on each platform:

### Android (CameraX)
- **CameraXManager** — CameraX lifecycle binding, resolution selector construction
- **SharedCameraSession** — Single ImageAnalysis use case distributing converted Bitmap frames to multiple trackers via ref-counted `SharedFrameHandler`
- **ImageProxyConverter** — Zero-alloc ImageProxy→Bitmap conversion with BitmapPool reuse, stride-mismatch handling, rotation, and horizontal mirroring
- **CameraPipelineHelper** — Frame throttling, in-flight bitmap tracking, async MediaPipe dispatch with error recovery

### iOS (AVFoundation)
- **SharedVisionCaptureSession** — Single AVCaptureSession distributing CMSampleBufferRef to multiple Vision-based trackers (hand, body)
- Direct AVCaptureVideoDataOutput delegate with configurable frame rate (15fps), YCbCr pixel format

### Key Requirements
1. **Raw platform-native frame access** — MediaPipe needs `Bitmap`/`MPImage` on Android; Vision framework needs `CMSampleBufferRef` on iOS
2. **Multi-tracker frame distribution** — Single camera session shared across face, hand, and body trackers
3. **Zero-copy / pooled conversion** — BitmapPool eliminates per-frame allocation and GC pressure
4. **Frame throttling** — Configurable frame rate limiting per tracker
5. **Resolution control** — Separate resolution selectors for preview vs analysis
6. **Lifecycle integration** — Android LifecycleOwner binding, iOS session start/stop

## Evaluated Libraries

### CameraK (Kashif-E/CameraK)

| Attribute | Detail |
|-----------|--------|
| Stars | ~482 |
| Latest version | 0.4 (March 2026) |
| Platforms | Android (CameraX), iOS (AVFoundation), Desktop |
| License | Apache 2.0 |
| Activity | Active (last commit March 2026) |

**Architecture:** Plugin-based system with `CameraKPlugin` interface. Plugins auto-activate when camera reaches Ready state. Built-in plugins: ImageSaver, QR scanner, OCR, video recording.

**Frame Access (AnalyzerPlugin):**
- Exposes frames as `ByteArray` via a `Channel<ByteArray>(CONFLATED)`
- Android: converts `ImageProxy.image` → `ByteArray` (lossy, allocates per frame)
- iOS: `CMSampleBuffer` → `CIImage` → `CGImage` → `UIImage` → `ByteArray` (extremely expensive conversion chain)
- No access to platform-native frame objects (`ImageProxy`, `CMSampleBufferRef`)
- No bitmap pooling or zero-copy path
- Single analyzer channel — no multi-consumer frame distribution

**Critical Gaps:**
- `ByteArray` format is unusable for MediaPipe (requires `Bitmap`/`MPImage`) and Vision framework (requires `CMSampleBufferRef`)
- iOS conversion chain (CMSampleBuffer→CIImage→CGImage→UIImage→ByteArray) would add 5-10ms per frame — unacceptable for 30-60fps tracking
- No frame throttling, no ref-counted multi-consumer distribution
- No resolution control for analysis vs preview
- Processing on main queue on iOS (delegate runs on main) — blocks UI

### Compose-Camera (l2hyunwoo/compose-camera)

| Attribute | Detail |
|-----------|--------|
| Stars | ~63 |
| Latest version | core-ui-1.2.2 (January 2026) |
| Platforms | Android (CameraX), iOS (AVFoundation) |
| License | Apache 2.0 |
| Activity | Moderate (last release January 2026) |

**Architecture:** Core/UI split. `CameraController` interface with extension/plugin system. `FrameAnalyzerPlugin<T>` interface for frame processing.

**Frame Access (CameraFrame):**
- Common API defines `CameraFrame` with `ByteArray` data + dimensions + format + rotation + timestamp
- **Also includes platform-native objects** — `ImageProxy` on Android, `CMSampleBuffer` on iOS (via `nativeImage` property)
- Android implementation passes `ImageProxy` directly to analyzers
- More promising than CameraK for native frame access

**Critical Gaps:**
- Very early maturity (63 stars, no major adopters)
- Frame distribution to multiple analyzers requires coordinated `ImageProxy` closing — the library acknowledges this but doesn't solve it
- No bitmap pooling or zero-copy conversion
- No frame throttling infrastructure
- No shared session concept for multi-tracker use cases
- Missing our SharedCameraSession's ref-counted bitmap lifecycle management

## Comparison Matrix

| Capability | KMP-FaceLink (current) | CameraK 0.4 | Compose-Camera 1.2 |
|------------|----------------------|-------------|-------------------|
| Platform-native frame access | Full (ImageProxy, CMSampleBufferRef) | ByteArray only | CameraFrame with native refs |
| Multi-tracker frame distribution | Ref-counted SharedFrameHandler | Single channel | Multiple analyzers (unmanaged lifecycle) |
| Zero-copy bitmap pooling | BitmapPool + reuse | Per-frame allocation | Per-frame allocation |
| Frame throttling | Per-tracker configurable | None | None |
| Resolution control (preview vs analysis) | Separate selectors | Single config | Single config |
| In-flight bitmap tracking | AtomicReference + ConcurrentHashMap | None | None |
| iOS frame rate control | Direct AVCaptureDevice configuration | Library-managed | Library-managed |
| Stride-mismatch handling | Custom packed-buffer path | Not applicable (ByteArray) | Not applicable |
| Maturity for production ML | Battle-tested | Not designed for ML | Early stage |

## Analysis

### Why Not CameraK
CameraK's AnalyzerPlugin is designed for simple use cases (QR scanning, OCR) where a `ByteArray` snapshot is sufficient. For real-time ML inference at 30-60fps:
- Converting ByteArray back to platform-native format would double the conversion cost
- No way to feed frames to MediaPipe's `detectAsync(MPImage, timestamp)` API without reconstructing a Bitmap from ByteArray
- iOS conversion chain is prohibitively expensive
- Single-consumer design cannot support simultaneous face + hand + body tracking

### Why Not Compose-Camera
Compose-Camera's `CameraFrame` with native refs is architecturally closer to what we need, but:
- Too immature for production dependency (63 stars, no major adopters)
- Would still require building all our custom infrastructure on top: BitmapPool, SharedFrameHandler ref-counting, frame throttling, stride-mismatch handling
- The net code reduction would be minimal — we'd replace ~200 lines of well-tested CameraX/AVFoundation setup code, but still need ~400 lines of custom frame pipeline logic
- Adds an external dependency and version coupling for marginal benefit

### Why Not Fork Either Library
- Our camera code is ~600 lines total across both platforms — not a maintenance burden
- The code is stable, well-tested, and tightly integrated with MediaPipe/Vision requirements
- Forking would create a maintenance burden of tracking upstream changes
- Our requirements are specialized enough that a general-purpose camera library would need extensive modification

### Cost-Benefit Summary
- **Lines saved by adopting:** ~200 (camera setup boilerplate)
- **Lines still needed:** ~400 (custom frame pipeline, pooling, throttling)
- **New dependencies added:** 1 library + transitive dependencies
- **Risk introduced:** API breakage, version conflicts, performance regressions
- **Maintenance reduction:** Negligible — the custom pipeline is where complexity lives, not camera setup

## Recommendation

**Keep the current approach.** The existing platform-specific camera implementation is:
1. **Well-optimized** — zero-alloc bitmap pooling, stride-mismatch handling, per-tracker throttling
2. **Purpose-built** — designed specifically for multi-tracker real-time ML inference
3. **Small surface area** — ~600 lines total, stable, rarely changes
4. **Tightly integrated** — directly feeds MediaPipe `MPImage` and Vision `CMSampleBufferRef`

Neither CameraK nor Compose-Camera addresses the actual complexity in our camera pipeline (frame distribution, bitmap lifecycle, throttling). They only abstract away the simple parts (CameraX/AVCaptureSession setup) that we've already solved.

## Future Considerations

- **Re-evaluate in 12 months** if CameraK or Compose-Camera adds native frame access with multi-consumer support
- **Watch for JetBrains official camera API** — if Compose Multiplatform adds camera support, it would have the backing and stability to justify adoption
- **Consider contributing** upstream to Compose-Camera's `FrameAnalyzerPlugin` with multi-consumer lifecycle management, which could benefit the broader KMP ecosystem
