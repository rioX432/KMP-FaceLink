# KMP-FaceLink Extension Strategy

Evolving KMP-FaceLink from a **pure tracking library** into a **Mobile VTuber SDK foundation**.

---

## 1. Market Position & Gap Analysis

### Existing Players

| Player | Platform | Focus |
|---|---|---|
| **aituber-kit** (tegnike) | Web (Next.js) | AI character chat app with Live2D/VRM |
| **Open-LLM-VTuber** (t41372) | Desktop (Python) | Voice-interactive AI VTuber backend |
| **VTubeStudio** | Desktop + iPhone as camera | Live2D VTuber tool with hotkey system |

### The Gap

**No Mobile Native VTuber SDK exists.**

- VTubeStudio uses iPhone only as a wireless tracking camera — avatar rendering is on desktop
- aituber-kit renders in the browser — no native mobile support
- No library integrates KMP face/hand tracking → avatar parameter conversion → gesture actions in a single stack

**KMP-FaceLink fills this gap** by providing the complete pipeline from tracking to avatar-ready output, natively on Android and iOS.

### Why KMP?

- Single codebase for Android + iOS tracking logic (already proven in core module)
- Kotlin/Swift interop via SKIE removes friction for iOS consumers
- Gradle multi-module structure makes optional modules zero-cost for pure-tracking users

---

## 2. Multi-Module Architecture

```
kmp-facelink (core)                ← Current library (no changes)
  │  Pure tracking: BlendShape, HeadTransform, HandLandmark
  │
kmp-facelink-avatar                ← NEW: Avatar parameter conversion
  │  BlendShape → Live2D Parameters (ParamAngleX etc.)
  │  BlendShape → VRM BlendShapeProxy
  │  HeadTransform → Avatar head bone
  │  Configurable mapping tables
  │
kmp-facelink-actions               ← NEW: Gesture/expression action system
  │  HandGesture → Action trigger (debounce, hold time)
  │  Expression trigger (wink, smile threshold)
  │  User-configurable bindings
  │
kmp-facelink-effects               ← NEW: Real-time face effects engine
  │  Tracking-driven visual effects (ears, particles, overlays)
  │  Expression-reactive effects (smile → hearts, wink → sparkle)
  │  Composable effect pipeline with anchor system
  │
kmp-facelink-stream                ← FUTURE: WebSocket streaming
     Mobile → Desktop (VTubeStudio protocol compatibility)
     Mobile → aituber-kit / Open-LLM-VTuber backend
```

### Dependency Graph

```
avatar  ──depends on──→  core
actions ──depends on──→  core
effects ──depends on──→  core  (+ optional actions for expression triggers)
stream  ──depends on──→  core  (future, out of scope)
```

**Pure tracking users import `core` only** — no bloat from avatar or action modules.

---

## 3. Module Details

### 3.1 kmp-facelink-avatar

**Purpose**: Convert 52 ARKit blend shapes + head transform into avatar engine parameters.

#### Live2D Parameter Mapping

| KMP-FaceLink Source | Live2D Parameter | Conversion |
|---|---|---|
| `EYE_BLINK_LEFT` | `ParamEyeLOpen` | `1.0 - value` (inverted) |
| `EYE_BLINK_RIGHT` | `ParamEyeROpen` | `1.0 - value` (inverted) |
| `headTransform.yaw` | `ParamAngleX` | Scale to -30..30 |
| `headTransform.pitch` | `ParamAngleY` | Scale to -30..30 |
| `headTransform.roll` | `ParamAngleZ` | Scale to -30..30 |
| `JAW_OPEN` | `ParamMouthOpenY` | Direct 0..1 |
| `MOUTH_SMILE_LEFT/RIGHT` | `ParamMouthForm` | Average, map to -1..1 |
| `BROW_INNER_UP` | `ParamBrowLY` / `ParamBrowRY` | Direct 0..1 |
| `EYE_LOOK_*` | `ParamEyeBallX` / `ParamEyeBallY` | Combine L/R directional |
| `CHEEK_PUFF` | `ParamCheek` | Direct 0..1 |

Full mapping covers all 52 blend shapes. The table above shows key conversions.

#### Design Decisions

- **Configurable mapping** — Live2D models vary in parameter naming. The default follows Live2D Cubism standard parameters, but users can override any mapping
- **Output format**: `Map<String, Float>` (parameter name → value)
- **Kalidokit-inspired** approach: landmarks → blend shapes → avatar params
- **Avvy 43-float protocol compatible** — output can be serialized to match Avvy's memory bridge format

#### API Sketch

```kotlin
// Default Live2D mapping
val mapper = Live2DParameterMapper(
    config = Live2DMapperConfig.default()
)

// Custom overrides
val customMapper = Live2DParameterMapper(
    config = Live2DMapperConfig.default().copy(
        overrides = mapOf(
            BlendShape.JAW_OPEN to ParameterMapping("MyCustomMouthOpen", 0f, 1.5f)
        )
    )
)

// Usage with tracking flow
faceTracker.trackingData.collect { data ->
    val params: Map<String, Float> = mapper.map(data)
    // params["ParamAngleX"] = 15.2f
    // params["ParamEyeLOpen"] = 0.8f
    // Feed to Live2D Cubism SDK, Spine, or any renderer
}
```

#### VRM Support (Future)

Same module can host a `VRMBlendShapeMapper` that outputs VRM-compatible blend shape proxy values. The mapping architecture is renderer-agnostic by design.

---

### 3.2 kmp-facelink-actions

**Purpose**: Fire actions from gesture/expression triggers with debounce and state management.

#### Trigger Types

```kotlin
sealed class ActionTrigger {
    // Hand gesture triggers
    data class GestureTrigger(
        val gesture: HandGesture,
        val holdTimeMs: Long = 0,
        val hand: Handedness? = null  // null = either hand
    ) : ActionTrigger()

    // Face expression triggers
    data class ExpressionTrigger(
        val blendShape: BlendShape,
        val threshold: Float,
        val direction: Direction = Direction.ABOVE  // ABOVE or BELOW threshold
    ) : ActionTrigger()

    // Combined triggers (e.g., peace sign + wink)
    data class CombinedTrigger(
        val triggers: List<ActionTrigger>,
        val requireSimultaneous: Boolean = true
    ) : ActionTrigger()
}
```

#### Built-in Triggers

**Hand gestures** (from existing `HandGesture` enum):
- `VICTORY`, `THUMB_UP`, `THUMB_DOWN`, `OPEN_PALM`, `CLOSED_FIST`, `POINTING_UP`

**Face expressions**:
- Wink: `EYE_BLINK_LEFT` or `EYE_BLINK_RIGHT` above threshold while other eye is open
- Smile: `MOUTH_SMILE_LEFT + MOUTH_SMILE_RIGHT` average above threshold
- Tongue out: `TONGUE_OUT` above threshold
- Surprised: `JAW_OPEN` + `EYE_WIDE_LEFT/RIGHT` combined

#### Anti-Misfire Design

| Mechanism | Purpose |
|---|---|
| `holdTimeMs` | Gesture must be held for N ms before firing |
| `cooldownMs` | Minimum gap between consecutive fires |
| `debounceMs` | Ignore rapid on/off flickering |
| State machine | Track gesture start → hold → end lifecycle |

#### API Sketch

```kotlin
val actionSystem = ActionSystem()

// Register gesture action
actionSystem.register(
    trigger = GestureTrigger(HandGesture.VICTORY, holdTimeMs = 500),
    action = { println("Peace sign held for 500ms!") }
)

// Register expression action
actionSystem.register(
    trigger = ExpressionTrigger(BlendShape.EYE_BLINK_LEFT, threshold = 0.8f),
    action = { println("Left wink detected!") }
)

// Register combined trigger
actionSystem.register(
    trigger = CombinedTrigger(
        triggers = listOf(
            GestureTrigger(HandGesture.OPEN_PALM),
            ExpressionTrigger(BlendShape.MOUTH_SMILE_LEFT, threshold = 0.6f)
        )
    ),
    action = { println("Waving + smiling!") }
)

// Feed tracking data
faceTracker.trackingData.collect { actionSystem.processFace(it) }
handTracker.trackingData.collect { actionSystem.processHand(it) }
```

---

### 3.3 kmp-facelink-effects

**Purpose**: Real-time face effects driven by tracking data — like TikTok/Instagram face filters, but as a programmable SDK layer.

#### Concept

TikTok and Instagram apply visual effects (cat ears, sparkles, face distortion) by anchoring overlays to facial landmarks in real-time. KMP-FaceLink already provides the tracking data — this module turns that data into an **effect parameter stream** that renderers can consume.

The key insight: **face filters and VTuber avatar driving share the same pipeline** (tracking → parameter conversion → visual output). By providing effects as a module, KMP-FaceLink serves both use cases from a single SDK.

#### Effect Types

| Type | Description | Example |
|---|---|---|
| **Anchor effects** | Attach visuals to tracked landmarks | Cat ears on head, glasses on nose bridge |
| **Expression-reactive** | Trigger/modulate effects based on blend shapes | Hearts on smile, sparkle on wink, fire on mouth open |
| **Hand-reactive** | Effects triggered by hand gestures | Particles from open palm, lightning from pointing |
| **Transform effects** | Modify avatar/face parameters procedurally | Exaggerated expressions, face distortion, chibi mode |
| **Ambient effects** | Background/overlay effects driven by tracking state | Aura that pulses with head movement |

#### Architecture

This module does **not** render effects directly — it outputs effect parameters and anchor positions that the app's rendering layer (Compose Canvas, UIKit, SceneKit, Live2D, etc.) can consume.

```
Tracking Data (core)
    ↓
EffectEngine (this module)
    ├── AnchorResolver: landmark index → screen position
    ├── ExpressionEvaluator: blend shape → effect intensity
    └── EffectState: active effects + their current parameters
    ↓
EffectOutput (consumed by renderer)
    ├── anchors: Map<AnchorPoint, Position2D>
    ├── parameters: Map<String, Float>  (effect intensities, scales, rotations)
    └── activeEffects: List<ActiveEffect>
```

#### Design Decisions

- **Renderer-agnostic** — outputs positions and parameters, not pixels. Works with any rendering technology
- **Composable pipeline** — multiple effects stack and blend (e.g., cat ears + sparkle eyes simultaneously)
- **Expression-driven** — effects react to `BlendShape` values in real time (smile intensity → heart particle rate)
- **Anchor system** — predefined anchor points (forehead, nose tip, chin, left/right ear, etc.) mapped from face landmarks
- **Optional `actions` integration** — can use `ActionTrigger` from the actions module for on/off effect toggling

#### API Sketch

```kotlin
val effectEngine = EffectEngine()

// Anchor-based effect: cat ears follow head position/rotation
effectEngine.addEffect(
    AnchorEffect(
        id = "cat-ears",
        anchor = AnchorPoint.FOREHEAD,
        rotationSource = RotationSource.HEAD_TRANSFORM
    )
)

// Expression-reactive effect: hearts when smiling
effectEngine.addEffect(
    ExpressionEffect(
        id = "smile-hearts",
        trigger = BlendShape.MOUTH_SMILE_LEFT to BlendShape.MOUTH_SMILE_RIGHT,
        threshold = 0.6f,
        intensityMapping = LinearMapping(0.6f..1.0f)  // smile strength → effect intensity
    )
)

// Transform effect: exaggerate eye blinks for cartoon style
effectEngine.addEffect(
    TransformEffect(
        id = "cartoon-eyes",
        source = BlendShape.EYE_BLINK_LEFT,
        transform = { value -> (value * 1.5f).coerceIn(0f, 1f) }
    )
)

// Process tracking data → effect output
faceTracker.trackingData.collect { data ->
    val output: EffectOutput = effectEngine.process(data)
    // output.anchors["cat-ears"] → Position2D(x, y) + rotation
    // output.parameters["smile-hearts.intensity"] → 0.85f
    // output.activeEffects → [ActiveEffect("cat-ears"), ActiveEffect("smile-hearts")]
    // Feed to your renderer
}
```

#### Use Cases Beyond VTuber

- **AR photo/video apps** — TikTok-style face filters as a KMP library
- **Video call effects** — professional backgrounds, face touch-up driven by blend shapes
- **Gaming** — player facial expressions control in-game character effects
- **Accessibility** — expression-to-action for hands-free interaction

---

## 4. Avatar Asset Strategy

### Bundled Sample Avatar

The sample apps (`androidApp/`, `iosApp/`) will include a **bundled Live2D avatar** to demonstrate the full tracking → avatar driving pipeline out of the box. This serves as:

- **Proof of concept** — users see the SDK working end-to-end immediately
- **Integration reference** — shows how to wire `kmp-facelink-avatar` output to a Live2D renderer
- **Development aid** — contributors can test avatar-related changes without sourcing their own model

### Avatar Creation Workflow

Creating a Live2D model requires:

1. **Character illustration** — draw the character in parts (layers for eyes, mouth, brows, hair, body, etc.)
   - Tools: Clip Studio Paint, Procreate, Photoshop, or AI-assisted generation
   - Must be drawn as **separated layers** (not a flat illustration) — each movable part on its own layer
2. **Live2D Cubism Editor** — import the layered PSD and rig the model
   - Define mesh deformations for each part
   - Set up parameters (ParamAngleX, ParamEyeLOpen, etc.) and keyforms
   - Map deformations to parameter ranges
   - Free version available (limited to simpler models, sufficient for a sample)
3. **Export** — `.moc3` file + texture atlas for runtime use
4. **Integration** — load the model with Live2D Cubism SDK Native and drive it with `kmp-facelink-avatar` output

### Avatar Sourcing Options for SDK Users

| Source | Pros | Cons |
|---|---|---|
| **Self-made (Cubism Editor)** | Full control, unique | Requires illustration + rigging skills |
| **Avvy** | Easy creation, high quality | Service-dependent |
| **VRoid Studio** | Free, 3D → VRM export | 3D style only, not Live2D |
| **BOOTH / nizima marketplace** | Professional quality, variety | Cost, license restrictions |
| **AI-generated + manual rigging** | Fast illustration, unique | Still needs Cubism Editor rigging |
| **Commission** | Professional result | Cost |

### Recommended Approach for This Project

1. Create a **simple but expressive** Live2D model for the sample app
   - Minimum viable parameters: eye blink, eye direction, head rotation, mouth open, smile
   - Doesn't need to be production quality — clarity over beauty
2. License it under **MIT** (same as the project) so anyone can use it
3. Document the creation process as a guide for other developers

---

## 5. Roadmap (Long-Term)

Aligned with the phases defined in `strategy.md`. Estimated as quarterly milestones over ~2 years.

---

### Phase 1: SDK Foundation (Q1–Q2 2026)

**Goal**: KMP-FaceLink を「トラッキング + アバター駆動」の完成された SDK として Maven Central に公開する。

| # | Task | Status | Milestone | Track |
|---|---|---|---|---|
| — | Core library (face + hand tracking) | Done | — | SDK |
| — | External publishing improvements | Done | — | SDK |
| 42 | `kmp-facelink-avatar` module | TODO | Q1 | SDK |
| 43 | `kmp-facelink-actions` module | TODO | Q1 | SDK |
| 45 | `kmp-facelink-effects` module | TODO | Q2 | SDK |
| 46 | Sample Live2D avatar creation | TODO | Q2 | SDK |
| 4 | Maven Central publish | TODO | Q2 | SDK |

**Q1 deliverable**: avatar + actions モジュールが動作し、blend shape → Live2D params → sample app でアバター駆動のデモが見られる状態。

**Q2 deliverable**: effects モジュール追加、サンプルアバター同梱、Maven Central で `io.github.riox432:kmp-facelink-*` として配布開始。

---

### Phase 2: Native Rendering + App Prototype (Q3–Q4 2026)

**Goal**: Live2D Cubism SDK Native を KMP から直接扱えるようにし、Unity (UaaL) 不要のアバター描画を実現する。同時に商用アプリの初期プロトタイプを開始。

| Task | Milestone | Track |
|---|---|---|
| Live2D Cubism SDK Native の調査・技術検証 | Q3 | SDK |
| `kmp-facelink-live2d` — Cubism Native KMP ラッパー | Q3–Q4 | SDK |
| Spine runtime 対応（同モジュール or 別モジュール） | Q4 | SDK |
| サンプルアプリでアバターがネイティブ描画される状態 | Q4 | SDK |
| Body tracking (Issue #7) — pose estimation | Q4 | SDK |
| SDK API stability review & semantic versioning | Q3 | SDK |
| AI Companion app — repo 作成 + アーキテクチャ設計 | Q3 | App |
| AI Companion app — アバター表示プロトタイプ (SDK 連携) | Q4 | App |

**この Phase の戦略的意味**:
- Avvy の UaaL 脱却検証に直結 → 本業への還元が最も大きいフェーズ
- 「KMP で Live2D アバターをネイティブ駆動」は現状どのライブラリにも存在しない
- 商用アプリが SDK の「最初の外部ユーザー」になり、API 設計のフィードバックループが回り始める

---

### Phase 3: Communication + App Core (Q1–Q2 2027)

**Goal**: モバイル ↔ デスクトップ間のリアルタイム通信を確立し、既存の VTuber エコシステムと接続する。商用アプリの中核機能を実装。

| Task | Milestone | Track |
|---|---|---|
| `kmp-facelink-stream` — WebSocket streaming module | Q1 | SDK |
| VTubeStudio protocol compatibility (iFacialMocap) | Q1 | SDK |
| aituber-kit WebSocket 連携 | Q2 | SDK |
| Open-LLM-VTuber backend 連携 | Q2 | SDK |
| Holistic tracking (Issue #8) — Face + Hands + Body unified | Q2 | SDK |
| AI Companion app — LLM 会話機能 (OpenAI/Anthropic/Gemini) | Q1 | App |
| AI Companion app — TTS 音声合成 + リップシンク | Q1–Q2 | App |
| AI Companion app — ASR 音声認識 (Whisper) | Q2 | App |
| AI Companion app — キャラクター/ペルソナ管理 UI | Q2 | App |

**この Phase の戦略的意味**:
- モバイルを「無線トラッキングカメラ」としても「スタンドアロン」としても使える
- 商用アプリが SDK の全モジュールを統合した最初のリアルプロダクトになる
- aituber-kit / Open-LLM-VTuber コミュニティとの接点ができる

---

### Phase 4: App Launch + Monetization (Q3–Q4 2027)

**Goal**: AI Companion アプリを App Store / Google Play に公開。KMP-FaceLink SDK スタック全体の商用実証。

| Task | Milestone | Track |
|---|---|---|
| SDK — 音声モジュール切り出し (`kmp-facelink-voice`) | Q3 | SDK |
| SDK — LLM 統合モジュール (`kmp-facelink-llm`) | Q3 | SDK |
| SDK — ドキュメント・チュートリアル整備 | Q3–Q4 | SDK |
| AI Companion app — サブスクリプション課金 (RevenueCat) | Q3 | App |
| AI Companion app — Avvy アバターインポート連携 | Q3 | App |
| AI Companion app — エフェクト課金パック | Q4 | App |
| AI Companion app — クローズドベータ → App Store 公開 | Q4 | App |

**この Phase の戦略的意味**:
- AI Companion (Mobile-LLM-VTuber) = 収益化プロダクト（サブスク + エフェクト課金）
- KMP-FaceLink の既存サンプルアプリ (androidApp/, iosApp/) が OSS デモとして機能
- SDK の商用実証 + App Store 収益の両立

---

### Phase 5: Ecosystem Growth (2028〜)

**Goal**: SDK エコシステムを拡大し、複数の商用アプリを展開する。

| Task | Track | Notes |
|---|---|---|
| KMP AITuber SDK 統合パッケージ化 | SDK | tracking + avatar + effects + stream + voice + LLM |
| プラグインシステム | SDK | 独自 LLM / TTS / renderer を差し込める設計 |
| VTuber-Asset-Gen 連携 | SDK | AI 生成アバター → SDK で即駆動 |
| Face Filter App (2nd product) | App | TikTok 風エフェクトアプリ — freemium |
| VTuber Toolkit App (3rd product) | App | モバイル VTuber 配信アプリ — サブスク |
| コミュニティ形成 | SDK | Discord, 技術ブログ, カンファレンス登壇 |

```
最終形 (SDK):

val aituber = AITuberKit {
    llm = OpenAI(apiKey)
    tts = VoicevoxEngine(url)
    asr = WhisperEngine()
    avatar = Live2DRenderer(modelPath)
    faceTracking = FaceLinkTracker()
    effects = EffectEngine(sparkleOnWink, heartsOnSmile)
}

AITuberView(aituber)  // ← これだけでモバイル AITuber が動く
```

```
最終形 (商用アプリ群):

AI Companion     — 「自分のアバターと話す」日常 AI 友達アプリ
Face Filter      — 「顔にエフェクトをかける」カジュアルカメラアプリ
VTuber Toolkit   — 「モバイルで VTuber 配信する」配信者向けプロアプリ
```

---

### Timeline Overview

```
             SDK Track (KMP-FaceLink)            App Track (Mobile-LLM-VTuber)
             ────────────────────────            ────────────────────────────
2026 Q1-Q2  ████████ Phase 1: Foundation         (SDK only — modules + publish)
2026 Q3-Q4  ████████ Phase 2: Native Rendering   ░░░░ App prototype (private repo)
2027 Q1-Q2  ████████ Phase 3: Communication      ████████ App core features
2027 Q3-Q4  ████ Phase 4: Voice/LLM modules      ████████ App launch + monetization
2028〜       ████████ Phase 5: Ecosystem           ████████ Multi-app expansion
```

### Avvy Synergy at Each Phase

| Phase | Avvy への還元 |
|---|---|
| 1: SDK Foundation | Calculator 改善 → 精度向上 |
| 2: Native Rendering | UaaL 脱却 → アプリ軽量化・高速起動 |
| 3: Communication | ストリーミング技術 → リモートトラッキング |
| 4: App Launch | リップシンク・AI 機能 → コンパニオン化 |
| 5: Ecosystem | 技術ブランディング → 採用・認知 |

---

## 6. OSS Boundary & Commercialization

### Principle: Open Core + Closed App

KMP-FaceLink is a **pure OSS SDK** (MIT). Consumer apps built on top live in **separate proprietary repositories**.

```
OSS (this repo — MIT)                    Proprietary (separate repos)
──────────────────────────────           ──────────────────────────────
kmp-facelink (core)                      AI Companion App
kmp-facelink-avatar                        - UX, design, onboarding
kmp-facelink-actions                       - Character/persona management
kmp-facelink-effects                       - Subscription billing
kmp-facelink-stream                        - Avvy avatar import integration
kmp-facelink-live2d                        - Tuned prompts & voice config
Sample apps (demo only)                    - App Store distribution

↓                                        ↓
"Distribute the pickaxes"               "Mine the gold"
```

### What stays in this repo

- All SDK modules — tracking, avatar mapping, actions, effects, streaming
- Sample apps — minimal demos proving each module works (not consumer-grade)
- Documentation, API reference, integration guides

### What goes in separate repos

| App | Repo | Description | Revenue model | Phase |
|---|---|---|---|---|
| **AI Companion** | `Mobile-LLM-VTuber` (private) | Talk to your avatar as a friend | Subscription + effect packs | Phase 2–4 |
| **Face Filter App** | TBD | TikTok-style effects | Freemium | Phase 5 |
| **VTuber Toolkit** | TBD | Mobile VTuber streaming | Subscription | Phase 5 |

### Why this split works

1. **SDK must be OSS to gain adoption** — no one depends on a closed SDK from an unknown developer
2. **App value is in UX, not code** — prompts, tuning, polish, and ecosystem integration aren't in the SDK
3. **OSS SDK = free distribution channel** — every SDK user is a potential app user / contributor
4. **No conflict** — the SDK enables others to build competing apps, which grows the ecosystem and validates the platform

### Reference models

| Company | OSS (free) | Commercial (paid) |
|---|---|---|
| Vercel | Next.js | Vercel Platform |
| Supabase | Supabase Core | Supabase Cloud |
| Live2D | Cubism SDK (free tier) | Cubism Editor |
| tegnike | aituber-kit | ← Not monetized yet (opportunity) |

---

## 7. Competitive Advantage

| Feature | VTubeStudio | aituber-kit | KMP-FaceLink |
|---|---|---|---|
| Platform | Desktop + iPhone camera | Web | **Mobile Native (Android + iOS)** |
| Face tracking | OpenSeeFace / iPhone | None | MediaPipe + ARKit |
| Hand tracking | Leap Motion / MediaPipe | None | MediaPipe + Vision |
| Avatar rendering | Built-in Live2D | Browser Live2D/VRM | **SDK output → any renderer** |
| Gesture actions | Hotkey-based | None | **Programmable triggers** |
| Face effects | None | None | **Expression-driven effect engine** |
| Open source | No | Yes | **Yes** |
| Mobile avatar driving | No | No | **Yes** |
| Configurable mapping | Limited | N/A | **Full override support** |

### Key Differentiators

1. **Only mobile-native option** — runs entirely on device, no desktop required
2. **Renderer-agnostic** — outputs `Map<String, Float>`, works with Live2D, Spine, VRM, or custom renderers
3. **Programmable actions** — developers define gesture→action bindings, not just hotkeys
4. **Modular** — import only what you need (core only, or core + avatar, or full stack)
5. **Face effects as SDK** — TikTok-style face filters as a programmable library, not a closed app
6. **KMP ecosystem** — single codebase, Kotlin + Swift consumers, Gradle dependency management

---

## 8. AI Cost Strategy

Voice-first AI companion apps are expensive to run. Every conversation turn costs money across LLM, TTS, and ASR. Sustainable operation requires a deliberate cost architecture.

### Cost Per Conversation Turn (Cloud APIs)

| Service | Unit price | Per response | Notes |
|---|---|---|---|
| LLM (GPT-4o) | ~$2.50/1M input tokens | ~$0.005 | ~2K tokens with context |
| TTS (ElevenLabs) | ~$0.30/1K chars | ~$0.03 | ~100 chars per response |
| ASR (Whisper API) | $0.006/min | ~$0.001 | ~10 sec per user utterance |
| **Total per turn** | | **~$0.04** | |

At 30 turns/day: **~$1.20/day = ~$36/month per active user**. A $5/month subscription does not cover this.

### Cost Reduction Strategies

#### 1. On-Device Processing (Zero Marginal Cost)

| Component | Cloud | On-device alternative | Savings |
|---|---|---|---|
| ASR | Whisper API ($0.006/min) | Whisper.cpp | 100% |
| TTS | ElevenLabs ($0.30/1K chars) | On-device VOICEVOX / system TTS | 100% |
| LLM (casual) | GPT-4o ($2.50/1M tokens) | Gemma 3 / Phi-4 on-device | 100% |

**On-device stack eliminates ~90% of API costs** for typical casual conversations.

#### 2. Hybrid LLM Routing

Not every conversation needs GPT-4o. Route intelligently:

```
User speaks → ASR (on-device) → Intent classification (on-device, tiny model)
    │
    ├── Casual chat ("How are you?", "What's for dinner?")
    │   → On-device small LLM (Gemma 3 2B / Phi-4 mini)
    │   → Cost: $0
    │
    ├── Complex conversation (advice, deep topics, creative)
    │   → Cloud LLM (GPT-4o / Claude)
    │   → Cost: ~$0.005/turn
    │
    └── Scripted response (greetings, reactions, time-based)
        → Template selection (no LLM needed)
        → Cost: $0
```

#### 3. Avatar Idle Behavior (No AI Cost)

The avatar's daily routine (reading, cooking, sleeping, etc.) is **purely scripted/procedural**:

- Activity schedule based on real-world time (morning → coffee, afternoon → reading, night → sleeping)
- Random idle animations and status messages from a predefined pool
- Occasional "thoughts" displayed as text bubbles — selected from templates, not generated
- LLM is invoked **only when the user taps Talk**

#### 4. Token Optimization

- **Short persona prompts** — concise system prompts (<200 tokens) instead of verbose character descriptions
- **Sliding context window** — keep only last N turns in context, summarize older history
- **Response length control** — persona configured for brief, casual responses (50-100 chars typical)
- **Streaming with early stop** — if user interrupts, stop generation immediately

### Pricing Model vs. Cost

| Tier | Price | Allowed usage | Estimated cost/user/month |
|---|---|---|---|
| Free | $0 | 10 voice turns/day, on-device only | ~$0 |
| Pro | $4.99/mo | Unlimited, cloud LLM available | ~$3-8 (varies by usage) |
| Pro+ | $9.99/mo | Unlimited, premium TTS (ElevenLabs), priority | ~$8-15 |

**Key insight**: Free tier is sustainable at $0 cost because everything runs on-device. Pro tier is viable because hybrid routing keeps average cost well below the subscription price.

### SDK Implications

The on-device strategy affects SDK module design:

- `kmp-facelink-voice`: Must support both on-device (Whisper.cpp, VOICEVOX) and cloud (API) backends behind the same interface
- `kmp-facelink-llm`: Must support on-device models (ONNX Runtime / llama.cpp) alongside cloud APIs, with transparent routing
- These are not just "fallbacks" — on-device is the **primary** path, cloud is the premium upgrade
