# Gemini Nano Expression Enhancement Research

> Research summary for issue #703

## Status

Research complete (2026-04-09)

---

## Background

No existing face tracking SDK uses an on-device LLM to enhance expression output. Current SDKs (Banuba, DeepAR, ARKit, MediaPipe) rely purely on geometric tracking — they move blend shapes based on what the camera sees, nothing more. KMP-FaceLink has an opportunity to differentiate by layering on-device LLM context awareness: the avatar reacts to what the user is *saying* and *feeling*, not just their raw facial geometry.

This is particularly relevant for VTuber and avatar applications where a lifeless, geometrically-accurate avatar is less compelling than one that responds naturally to conversation context — adding subtle blinks when idle, softening expressions during calm dialogue, or amplifying excitement cues when the user's voice indicates enthusiasm.

---

## On-Device LLM Landscape

### Android: Gemma 3n via ML Kit GenAI and LiteRT-LM

**Gemma 3n E2B** is the primary target model for Android expression enhancement:

| Attribute | Value |
|---|---|
| Model | Gemma 3n E2B (INT4) |
| Runtime | ML Kit GenAI API (stable) / LiteRT-LM (Google transition target) |
| Throughput | 10–25 tok/s on flagship Android (e.g. Pixel 9, Galaxy S25) |
| RAM footprint | ~1.5 GB INT4 |
| Availability | Android 10+ via AICore Developer Preview; direct bundle via LiteRT-LM |

Google is actively transitioning from ML Kit GenAI to **LiteRT-LM** as the canonical on-device inference runtime (see `docs/research/litert-lm.md` for full analysis). For expression enhancement, both APIs are viable; LiteRT-LM is preferred for new integrations.

**AICore Developer Preview** ships Gemma 3n E2B as a system-level model on recent Pixel and Galaxy devices, avoiding a 2–3 GB in-app download. However, availability is device-gated, so bundled fallback via LiteRT-LM remains necessary.

### iOS: Apple Foundation Models (iOS 26+)

**Apple Foundation Models** (iOS 26 / WWDC 2025) provides a ~3B parameter on-device model at approximately 30 tok/s with structured output support. KMP-FaceLink already has `FoundationModelsLlmClient` and `FoundationModelsProvider` integrated — the infrastructure exists and is ready to be leveraged for expression enhancement on iOS.

Key capabilities relevant to this use case:
- **Structured output**: JSON schema-constrained generation — ideal for emitting expression parameters directly
- **~30 tok/s**: Fast enough for 1–5 second async classification cycles
- **System-level**: No model download required; available on all A17+ devices running iOS 26+

### Performance Summary

| Platform | Model | Throughput | Latency (50 tok) |
|---|---|---|---|
| Android flagship | Gemma 3n E2B (INT4 GPU) | 10–25 tok/s | 2–5 s |
| iOS 26+ (A17+) | Apple Foundation Models | ~30 tok/s | ~1.7 s |

---

## Real-Time Feasibility Analysis

### Why Per-Frame LLM is Infeasible

The face tracking pipeline runs at 30–60 fps, meaning each frame has a budget of 16–33 ms. A single LLM call takes **50–500 ms** depending on model, device, and output length. Running the LLM on every frame would:

- Consume the entire frame budget, causing tracking stalls
- Saturate the NPU/CPU, triggering thermal throttling within minutes
- Produce nonsensical results — LLMs need utterance-level context, not individual frames

### Viable Architecture: Async Classification Loop

The solution is to decouple the LLM from the tracking path entirely:

```
Real-time tracking path (16 ms)
  └── Raw blend shapes → ExpressionBlender → rendered output

Async LLM classification loop (1–5 s)
  └── Conversation context / audio transcript
      → LLM: "What expression should the avatar show?"
      → EmotionTemplate selection + intensity
      → Stored as "current emotional context"
      └── Applied by ExpressionBlender on next frame
```

The LLM operates on a separate coroutine, classifying conversation context every 1–5 seconds. Its output updates a shared `StateFlow<EmotionContext>` that the real-time path reads without blocking. This pattern is analogous to `AdaptiveRouter`'s thermal state monitoring — a background signal that influences but never blocks the hot path.

---

## Existing Infrastructure

KMP-FaceLink already contains the components needed to build this feature without major new abstractions:

| Component | Location | Role |
|---|---|---|
| `ExpressionBlender` | `kmp-facelink-pipeline/.../expression/ExpressionBlender.kt` | Blends lip sync + emotion blend shapes; emotion layer is the hook point for LLM output |
| `EmotionTemplate` | `kmp-facelink-actions/.../emotion/EmotionTemplate.kt` | FACS-derived blend shape patterns per emotion (HAPPY, SAD, ANGRY, SURPRISED, DISGUSTED, FEAR) |
| `TextSentimentAnalyzer` | `kmp-facelink-pipeline/.../expression/TextSentimentAnalyzer.kt` | Keyword-based heuristic emotion classifier; LLM replaces/augments this for higher accuracy |
| `AdaptiveRouter` | `kmp-facelink-llm/.../internal/AdaptiveRouter.kt` | Multi-signal routing (thermal, latency, cost, complexity); expression enhancement uses the same routing logic |
| `FoundationModelsLlmClient` | `kmp-facelink-llm/.../internal/FoundationModelsLlmClient.kt` | iOS Foundation Models client; already integrated, ready to consume |
| `HybridLlmClient` | `kmp-facelink-llm/.../internal/HybridLlmClient.kt` | Routes between on-device and cloud; expression enhancement plugs in as an on-device-preferred use case |

The `ExpressionBlender.blend()` method already accepts an `emotion: Map<BlendShape, Float>` parameter — this is exactly where LLM-derived expression patterns slot in. No API changes are needed at the blending layer.

---

## Recommended Architecture

### Component Design

```
ConversationContext (text transcript / TTS input)
    │
    ▼
ExpressionClassifier (new)
    ├── FastPath: TextSentimentAnalyzer (heuristic, ~0 ms, existing)
    └── SlowPath: LlmExpressionClassifier (new, ~1-5 s async)
          ├── Android: Gemma 3n E2B via LiteRT-LM / ML Kit GenAI
          └── iOS: FoundationModelsLlmClient (existing)
    │
    ▼
EmotionContext (StateFlow<EmotionState>) ← updated every 1–5 s
    │
    ▼
ExpressionBlender.blend(lipSync, emotionFromTemplate(currentState))
    │
    ▼
Rendered blend shapes (real-time, 60 fps)
```

### LLM Prompt Design

For expression classification, the prompt should be minimal and produce structured output:

```
System: You are an avatar expression classifier. 
        Respond with JSON only: {"emotion": "<HAPPY|SAD|ANGRY|SURPRISED|NEUTRAL|FEAR>", "intensity": <0.0-1.0>}

User: The user just said: "That's amazing! I can't believe it worked!"
```

Structured output (Apple Foundation Models) or constrained decoding (Gemma) ensures the LLM always returns parseable JSON, making the output safe to apply without validation overhead in the hot path.

### Idle Animation Layer (Phase 1)

Before introducing LLM dependency, idle animations (blinks, micro-saccades, subtle breathing) can ship independently using heuristic timers. These address the "dead eyes" problem common in tracking-only avatars and require no LLM:

- **Blink**: random interval 3–7 s, `EYE_BLINK_LEFT` + `EYE_BLINK_RIGHT` with 120 ms ease
- **Saccade**: micro random offset to `EYE_LOOK_*` blend shapes every 2–4 s
- **Breathing**: 0.5 Hz sinusoidal modulation on `CHEST_*` / `JAW_OPEN` (~0.02 amplitude)

These layer on top of `ExpressionBlender` without touching the LLM path, meaning they ship and provide value independently.

---

## Implementation Plan

### Phase 1: Heuristic Idle Animations (~5 days)

**Goal**: Ships independently. Addresses "dead eyes" / lifeless avatar problem without any LLM dependency.

| Task | Effort |
|---|---|
| `IdleAnimationController` — blink, saccade, breathing timers | 2 d |
| Integration with `ExpressionBlender` as a third blend layer | 1 d |
| Unit tests + golden blend shape snapshots | 1 d |
| Tune timing parameters (user-configurable API) | 0.5 d |
| PR + review | 0.5 d |

**Deliverable**: `IdleAnimationController` in `kmp-facelink-pipeline`, enabled by default, with configurable timing.

### Phase 2: LLM-Driven Context-Aware Enhancement (~9 days)

**Goal**: Async LLM classification that updates the emotion layer every 1–5 seconds.

| Task | Effort |
|---|---|
| `LlmExpressionClassifier` — common interface + prompt design | 1.5 d |
| Android implementation (Gemma 3n via LiteRT-LM / ML Kit GenAI) | 2 d |
| iOS implementation (wrapping `FoundationModelsLlmClient`) | 1.5 d |
| `ExpressionEnhancementCoordinator` — async loop + `StateFlow<EmotionState>` | 1.5 d |
| Integration with `ExpressionBlender` | 0.5 d |
| Error handling: LLM unavailable → graceful fallback to `TextSentimentAnalyzer` | 0.5 d |
| Integration tests (mock LLM, verify blend shape output) | 1 d |
| PR + review | 0.5 d |

**Deliverable**: `ExpressionEnhancementCoordinator` in `kmp-facelink-pipeline`, opt-in via `AvatarPipelineConfig`, with automatic fallback to heuristic sentiment analysis when LLM is unavailable or thermal state is critical.

---

## Risks & Open Questions

| Risk / Question | Severity | Notes |
|---|---|---|
| Gemma 3n E2B not available on non-Pixel/Galaxy Android | High | AICore Developer Preview is device-gated. Bundled LiteRT-LM model download (~2.5 GB) is the fallback — significant for users on limited data plans |
| iOS 26 adoption rate at launch | Medium | Apple Foundation Models require iOS 26+; Phase 2 iOS path is gated on user upgrade. Phase 1 (idle animations) has no OS gate |
| Thermal impact of 1-5 s LLM polling | Medium | Need to benchmark sustained 1 Hz classification on Pixel 9 / iPhone 16. `AdaptiveRouter` thermal signals should gate classification frequency |
| Structured output correctness | Low | Apple Foundation Models has native JSON schema support. For Gemma, constrained decoding or retry-with-parse-error is needed — adds ~50 ms on malformed output |
| Personality profile design | Open | Should expression intensity scale with avatar "personality" settings (shy vs expressive)? Needs UX design before Phase 2 |
| Latency budget for 5 s cycle | Open | 5 s may feel sluggish for fast-paced streams. Measure perceptual threshold with user testing — 2 s may be the sweet spot |
| `TextSentimentAnalyzer` vs LLM accuracy delta | Open | Quantify accuracy improvement to justify the latency/complexity cost of LLM path |

---

## References

- [ML Kit GenAI API (Android)](https://developers.google.com/ml-kit/genai-apis)
- [AICore Developer Preview announcement](https://android-developers.googleblog.com/2026/04/AI-Core-Developer-Preview.html)
- [LiteRT-LM GitHub](https://github.com/google-ai-edge/LiteRT-LM)
- [Apple Foundation Models (WWDC 2025)](https://developer.apple.com/videos/play/wwdc2025/286/)
- [Gemma 3n E2B on HuggingFace (LiteRT-LM)](https://huggingface.co/google/gemma-3n-E2B-it-litert-lm)
- `docs/research/litert-lm.md` — full LiteRT-LM evaluation and benchmark data
- FACS (Facial Action Coding System) — Ekman & Friesen, basis for `EmotionTemplate` weights
