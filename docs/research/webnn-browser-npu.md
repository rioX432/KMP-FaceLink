# WebNN API: Browser NPU Access for Web-Based Inference

## Status: Monitoring (P3)

**Last updated:** April 2026
**Spec status:** W3C Candidate Recommendation (updated January 22, 2026)
**Production-ready:** No — Chrome 146 ships stable WebNN (Feb 2026), but only Chromium browsers; Firefox/Safari not yet implemented.

## What Is WebNN?

The [Web Neural Network (WebNN) API](https://www.w3.org/TR/webnn/) is a W3C standard providing a dedicated low-level API for neural network inference hardware acceleration in the browser. It enables web applications to dispatch ML workloads to the best available hardware backend — CPU, GPU, or NPU — through a unified interface.

Key differentiator from WebGPU: WebNN can access **NPUs (Neural Processing Units)** — dedicated AI accelerators present in modern laptops and phones (Intel Core Ultra, Qualcomm Snapdragon X, Apple Neural Engine). WebGPU cannot access NPUs.

## Current Browser Support (April 2026)

| Browser | Status | NPU Support | Notes |
|---|---|---|---|
| Chrome 146+ | **Stable** | Yes (Windows) | Shipped Feb 25, 2026 |
| Edge 146+ | **Stable** | Yes (Windows) | Roughly same timeline as Chrome |
| Firefox | Not implemented | — | Gecko team has shown "positive interest"; no timeline |
| Safari | Not implemented | — | Apple participates in WebML WG but no public commitment |
| Opera/Brave/etc. | Behind flag | Inherited from Chromium | Depends on Chromium adoption |

**Verdict:** Only Chromium-based browsers have shipped. Does not meet our "2+ browser engines" threshold for prototyping.

## Specification Evolution

Between the April 2024 CR snapshot and January 2026 CR update, 100+ significant changes:

- **Third wave of operators** for enhanced transformer model support
- **MLTensor API** for efficient buffer sharing between WebNN and WebGPU
- **Abstract device selection** mechanism (`"cpu"`, `"gpu"`, `"npu"`)
- Strengthened security/privacy considerations (fingerprinting mitigations)
- New accessibility considerations

The comment period closed March 22, 2026. Next step: advancing toward Proposed Recommendation if feedback is positive.

## Compatibility with Existing WebGPU Path

### Current Architecture (kmp-facelink wasmJs)

```
Browser Camera → MediaPipe Tasks Vision JS → FaceLandmarker
                                                ↓
                                        WebGPU/WebGL delegate
                                                ↓
                                        GPU inference
```

`WebGpuCapability.kt` detects the best GPU backend (WebGPU → WebGL → CPU fallback) and passes it to MediaPipe as a delegate hint.

### WebNN Integration Paths

**Path A: ONNX Runtime Web + WebNN EP (most promising)**

ONNX Runtime Web already supports WebNN as an execution provider:
```javascript
const session = await ort.InferenceSession.create(model, {
    executionProviders: [{ name: 'webnn', deviceType: 'npu' }]
});
```

This would require replacing MediaPipe's inference backend with ONNX Runtime, or running them side by side. Not all ONNX operators are supported by WebNN yet (subset coverage compared to WASM/WebGPU).

**Path B: MediaPipe + WebNN (future)**

MediaPipe Tasks does not currently support WebNN as a delegate. Google may add this in the future since both MediaPipe and WebNN are Google-led projects, but no announcement has been made.

**Path C: Direct WebNN API usage**

Building custom inference directly on the WebNN API. Maximum control but requires managing model loading, operator graphs, and tensor management manually. Not practical for our use case.

### Recommendation

**Path A (ONNX Runtime Web)** is the most viable when the time comes. It aligns with our existing ONNX Runtime usage on JVM/desktop targets and provides a clean abstraction over hardware backends.

However, the current MediaPipe-based approach with WebGPU acceleration is sufficient. WebNN becomes relevant when:
1. NPU-equipped devices are common among target users
2. MediaPipe's WebGPU inference becomes a bottleneck
3. We want to run larger models (e.g., neural lip sync) in the browser

## NPU Hardware Landscape

| Platform | NPU | WebNN Backend |
|---|---|---|
| Intel Core Ultra (Meteor Lake+) | Intel NPU | DirectML (Windows) |
| Qualcomm Snapdragon X | Hexagon NPU | DirectML (Windows) |
| Apple Silicon (M1+) | Apple Neural Engine | **Not accessible** — no Safari WebNN |
| AMD Ryzen AI | XDNA NPU | DirectML (Windows) |
| Android (Snapdragon) | Hexagon DSP | Chromium Android (experimental) |

Notable gap: Apple Neural Engine is inaccessible via WebNN because Safari has not implemented the API. This significantly limits NPU coverage for our user base.

## MLTensor: WebNN ↔ WebGPU Interop

The new MLTensor API enables zero-copy buffer sharing between WebNN and WebGPU. This is critical for our architecture because:

- Pre/post-processing (image resize, normalization) can stay on WebGPU
- Inference can dispatch to NPU via WebNN
- No CPU roundtrip for tensor data

This makes a hybrid WebGPU+WebNN pipeline feasible without performance penalties from data transfer.

## Action Items

### Now (Monitoring)
- [x] Document WebNN status and compatibility analysis (this document)
- [x] Add WebNN capability detection scaffold to `WebGpuCapability.kt`
- [ ] Track Firefox and Safari implementation signals quarterly

### When 2+ Browser Engines Ship Stable Support
- [ ] Prototype ONNX Runtime Web with WebNN EP for face landmark inference
- [ ] Benchmark NPU vs WebGPU inference latency on Intel Core Ultra / Snapdragon X
- [ ] Evaluate MLTensor for zero-copy WebGPU ↔ WebNN pipeline

### When MediaPipe Supports WebNN Delegate
- [ ] Evaluate direct MediaPipe + WebNN integration (simplest path)
- [ ] Compare with ONNX Runtime Web approach

## References

- [W3C WebNN Specification](https://www.w3.org/TR/webnn/)
- [WebNN Browser Compatibility](https://webnn.io/en/api-reference/browser-compatibility/api)
- [ONNX Runtime Web — WebNN EP](https://onnxruntime.ai/docs/tutorials/web/ep-webnn.html)
- [Chrome 146 WebNN Launch](https://chromestatus.com/feature/5738583487938560)
- [WebNN + WebGPU Interop (MLTensor)](https://www.w3.org/TR/webnn/#api-mltensor)
- [Can I Use — WebNN](https://caniuse.com/?search=webnn)
