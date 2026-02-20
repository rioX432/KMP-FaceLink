# Task List

Task list linked to GitHub Issues.
On completion, update status to `DONE` and close the corresponding GitHub Issue.

## Foundation

| # | Issue | Status | Summary |
|---|-------|--------|---------|
| 5 | [CI/CD with GitHub Actions](https://github.com/rioX432/KMP-FaceLink/issues/5) | DONE | GitHub Actions CI + detekt setup |
| 10 | [KDoc API documentation](https://github.com/rioX432/KMP-FaceLink/issues/10) | DONE | Dokka + KDoc for all public APIs |

## Face Tracking Improvements

| # | Issue | Status | Summary |
|---|-------|--------|---------|
| 3 | [One Euro Filter](https://github.com/rioX432/KMP-FaceLink/issues/3) | DONE | Adaptive smoothing filter |
| 9 | [MediaPipe blend shape accuracy](https://github.com/rioX432/KMP-FaceLink/issues/9) | DONE | Improve low-accuracy parameters |
| 12 | [Thread safety](https://github.com/rioX432/KMP-FaceLink/issues/12) | DONE | Concurrency safety improvements |
| 20 | [Landmark overlay alignment](https://github.com/rioX432/KMP-FaceLink/issues/20) | DONE | Fix overlay-preview coordinate mismatch |

## iOS

| # | Issue | Status | Summary |
|---|-------|--------|---------|
| 11 | [SKIE plugin](https://github.com/rioX432/KMP-FaceLink/issues/11) | DONE | Swift-Kotlin Flow interop |
| 2 | [iOS sample app](https://github.com/rioX432/KMP-FaceLink/issues/2) | DONE | Xcode project setup |
| 24 | [Camera preview](https://github.com/rioX432/KMP-FaceLink/issues/24) | DONE | ARKit camera feed display |
| 25 | [Landmark overlay](https://github.com/rioX432/KMP-FaceLink/issues/25) | DONE | Face landmark visualization |
| 26 | [Smoothing settings UI](https://github.com/rioX432/KMP-FaceLink/issues/26) | DONE | Real-time smoothing adjustment |

## Multi-Tracking (Future)

| # | Issue | Status | Summary |
|---|-------|--------|---------|
| 6 | [Hand tracking (Phase 2)](https://github.com/rioX432/KMP-FaceLink/issues/6) | DONE | Hand landmarks + gestures |
| 7 | [Body tracking (Phase 3)](https://github.com/rioX432/KMP-FaceLink/issues/7) | DONE | Pose estimation |
| 8 | [Holistic tracking (Phase 4)](https://github.com/rioX432/KMP-FaceLink/issues/8) | TODO | Face + Hands + Body integration |

## Technology Improvements

| # | Issue | Status | Summary |
|---|-------|--------|---------|
| 34 | [Update MediaPipe to v0.10.32](https://github.com/rioX432/KMP-FaceLink/issues/34) | DONE | Dependency update + regression testing |
| 35 | [Increase Android camera resolution](https://github.com/rioX432/KMP-FaceLink/issues/35) | DONE | Higher input resolution for better detection |
| 36 | [Improve geometric blend shape solvers](https://github.com/rioX432/KMP-FaceLink/issues/36) | DONE | Per-side solvers, near-zero tuning |
| 37 | [Investigate N-euro Predictor](https://github.com/rioX432/KMP-FaceLink/issues/37) | CLOSED | Not feasible — requires LSTM + no reference impl |
| 38 | [Evaluate MediaPipe Holistic Landmarker](https://github.com/rioX432/KMP-FaceLink/issues/38) | CLOSED | Not ready — runtime crashes, revisit in 0.11.x |

## Phase 1: SDK Extension Modules (Q1–Q2 2026)

| # | Issue | Status | Summary |
|---|-------|--------|---------|
| 44 | [Extension strategy documentation](https://github.com/rioX432/KMP-FaceLink/issues/44) | DONE | Strategy doc, roadmap, competitive analysis |
| 42 | [kmp-facelink-avatar module](https://github.com/rioX432/KMP-FaceLink/issues/42) | DONE | Live2D parameter mapping (BlendShape → ParamAngleX etc.) |
| 43 | [kmp-facelink-actions module](https://github.com/rioX432/KMP-FaceLink/issues/43) | DONE | Gesture/expression trigger system with debounce |
| 45 | [kmp-facelink-effects module](https://github.com/rioX432/KMP-FaceLink/issues/45) | DONE | Real-time face effects engine (TikTok-style filters) |
| 46 | [Sample Live2D avatar](https://github.com/rioX432/KMP-FaceLink/issues/46) | DONE | Bundled avatar for demo + integration reference |
| 60 | [PerfectSync mapping](https://github.com/rioX432/KMP-FaceLink/issues/60) | DONE | VTubeStudio Perfect Sync (52 ARKit blend shape pass-through) |
| 56 | [iOS Live2D avatar crash](https://github.com/rioX432/KMP-FaceLink/issues/56) | DONE | Fix Metal rendering crash on Avatar tab |
| 4 | [Publish to Maven Central](https://github.com/rioX432/KMP-FaceLink/issues/4) | TODO | maven-publish + XCFramework distribution |

## Phase 2: Native Rendering (Q3–Q4 2026)

| # | Issue | Status | Summary |
|---|-------|--------|---------|
| 47 | [SDK API stability review](https://github.com/rioX432/KMP-FaceLink/issues/47) | DONE | Audit public APIs, semver policy, stability annotations |
| 48 | [kmp-facelink-live2d module](https://github.com/rioX432/KMP-FaceLink/issues/48) | DONE | Live2D Cubism SDK Native KMP wrapper |
| 7 | [Body tracking](https://github.com/rioX432/KMP-FaceLink/issues/7) | DONE | Pose estimation |

## Phase 3: Communication (Q1–Q2 2027)

| # | Issue | Status | Summary |
|---|-------|--------|---------|
| 49 | [kmp-facelink-stream module](https://github.com/rioX432/KMP-FaceLink/issues/49) | DONE | WebSocket streaming (VTubeStudio/aituber-kit/Open-LLM-VTuber) |
| 8 | [Holistic tracking](https://github.com/rioX432/KMP-FaceLink/issues/8) | TODO | Face + Hands + Body integration |

## Phase 4: Voice & LLM (Q3–Q4 2027)

| # | Issue | Status | Summary |
|---|-------|--------|---------|
| 50 | [kmp-facelink-voice module](https://github.com/rioX432/KMP-FaceLink/issues/50) | DONE | ASR (Whisper) / TTS (VOICEVOX, ElevenLabs) + lip sync |
| 51 | [kmp-facelink-llm module](https://github.com/rioX432/KMP-FaceLink/issues/51) | TODO | LLM streaming API (OpenAI, Anthropic, Gemini) |

## Tech Debt

| # | Issue | Status | Summary |
|---|-------|--------|---------|
| 64 | [CI pipeline for new modules](https://github.com/rioX432/KMP-FaceLink/issues/64) | DONE | Add stream & voice modules to CI |
| 65 | [Resource leak in KtorWebSocketProvider](https://github.com/rioX432/KMP-FaceLink/issues/65) | DONE | Add release() to close HttpClient |
| 66 | [Thread safety in VtsStreamClient](https://github.com/rioX432/KMP-FaceLink/issues/66) | DONE | AtomicInt/AtomicReference + Mutex for connectionJob |
| 67 | [Missing release() in ActionSystem/EffectEngine](https://github.com/rioX432/KMP-FaceLink/issues/67) | DONE | Add resource cleanup methods |
| 68 | [Extract shared constants in voice module](https://github.com/rioX432/KMP-FaceLink/issues/68) | DONE | AudioConstants object for magic numbers |
| 69 | [Split oversized MainActivity](https://github.com/rioX432/KMP-FaceLink/issues/69) | DONE | Extract FaceTrackingScreen + HandTrackingScreen |
| 70 | [Live2D renderer unit tests](https://github.com/rioX432/KMP-FaceLink/issues/70) | DONE | 9 test cases for lifecycle and state |
| 71 | [Version catalog for sample app](https://github.com/rioX432/KMP-FaceLink/issues/71) | DONE | Migrate hardcoded deps to libs.versions.toml |
| 72 | [ProGuard rules for release builds](https://github.com/rioX432/KMP-FaceLink/issues/72) | DONE | Keep rules for MediaPipe, Ktor, Live2D |
| 73 | [Lifecycle interface standardization](https://github.com/rioX432/KMP-FaceLink/issues/73) | DONE | Releasable fun interface for all trackers/clients |
| 74 | [Reduce @Suppress usage](https://github.com/rioX432/KMP-FaceLink/issues/74) | DONE | Replace magic numbers with named constants |

Note: Commercial app (Mobile-LLM-VTuber) is tracked in its own private repo.
