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
- **Fixed-layout output** — the mapped values can be serialized into a host app's fixed-size float protocol (e.g. a shared-memory bridge to a Unity-based renderer)

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
| **Avatar-maker apps** | Easy creation, high quality | Service-dependent |
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

## 5. Competitive Advantage

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
