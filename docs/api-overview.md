# API Overview

KMP-FaceLink is a modular Kotlin Multiplatform SDK for real-time face, hand, and body tracking with extension modules for avatar driving, gesture actions, voice, LLM integration, and protocol output.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Your Application                             │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐           │
│  │ pipeline │  │   vmc    │  │   ifm    │  │   vts    │           │
│  │ ASR→LLM→ │  │ OSC/UDP  │  │iFacialMo│  │ VTube    │           │
│  │ TTS→Lip  │  │ VRM apps │  │cap proto │  │ Studio   │           │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘           │
│       │              │              │              │                 │
│  ┌────┴─────┐  ┌─────┴──────┐     │              │                 │
│  │  voice   │  │ networking │     │              │                 │
│  │ ASR/TTS  │  │  UDP/time  │     │              │                 │
│  └────┬─────┘  └────────────┘     │              │                 │
│       │                            │              │                 │
│  ┌────┴─────┐  ┌──────────┐       │              │                 │
│  │   llm    │  │  avatar   │      │              │                 │
│  │ Cloud +  │  │ Live2D /  │      │              │                 │
│  │ On-device│  │ VRM map   │      │              │                 │
│  └────┬─────┘  └────┬─────┘      │              │                 │
│       │              │             │              │                 │
│  ┌────┴─────┐  ┌─────┴──────┐    │              │                 │
│  │ actions  │  │   stream   │    │              │                 │
│  │ Gesture/ │  │  WebSocket │    │              │                 │
│  │ emotion  │  │  streaming │    │              │                 │
│  └────┬─────┘  └─────┬──────┘    │              │                 │
│       │               │           │              │                 │
│  ─────┴───────────────┴───────────┴──────────────┴─────────────    │
│                     kmp-facelink (core)                             │
│              FaceTracker · HandTracker · BodyTracker                │
│              HolisticTracker · BlendShape · Flow<T>                 │
├─────────────────────────────────────────────────────────────────────┤
│  Android: MediaPipe + CameraX    │    iOS: ARKit + Vision          │
└──────────────────────────────────┴──────────────────────────────────┘
```

## Modules

### Core: `kmp-facelink`

Real-time face, hand, and body tracking with a unified API across platforms.

| API | Description |
|-----|-------------|
| `createFaceTracker(context, config)` | 52 ARKit-compatible blend shapes + head transform |
| `createHandTracker(context, config)` | Hand landmarks + gesture classification |
| `createBodyTracker(context, config)` | Body pose landmarks (33 keypoints) |
| `createHolisticTracker(context, config)` | Simultaneous face + hands + body with shared camera |

All trackers follow the same pattern:

```kotlin
val tracker = createFaceTracker(platformContext, FaceTrackerConfig())
tracker.start()
tracker.trackingData.collect { data -> /* use data */ }
tracker.stop()
tracker.release()
```

**Key types:**
- `FaceTrackingData` — blend shapes, head transform, face landmarks, tracking state
- `HandTrackingData` — per-hand landmarks, gesture, handedness
- `BodyTrackingData` — body pose landmarks, tracking confidence
- `HolisticTrackingData` — combined face + hands + body
- `TrackingState` — `IDLE`, `STARTING`, `TRACKING`, `STOPPED`, `ERROR`, `RELEASED`
- `BlendShape` — enum of 52 ARKit blend shape parameters

### Avatar: `kmp-facelink-avatar`

Maps tracking data to avatar engine parameters.

```kotlin
// Live2D
val mapper = Live2DParameterMapper()
faceTracker.trackingData.collect { data ->
    val params: Map<String, Float> = mapper.map(data)
    // params["ParamAngleX"], params["ParamEyeLOpen"], params["ParamMouthOpenY"], ...
}

// VRM (0.x and 1.0)
val vrmMapper = VrmParameterMapper(version = VrmVersion.V1_0)
faceTracker.trackingData.collect { data ->
    val expressions: Map<String, Float> = vrmMapper.map(data)
    // expressions["happy"], expressions["lookLeft"], ...
}
```

### Actions: `kmp-facelink-actions`

Gesture and expression trigger system with debounce, cooldown, and emotion classification.

```kotlin
val actionSystem = ActionSystem()

// Register a gesture trigger
actionSystem.register(
    ActionBinding(
        trigger = GestureTrigger(gesture = HandGesture.VICTORY, holdTimeMs = 500),
        cooldownMs = 1000,
    )
)

// Register an expression trigger
actionSystem.register(
    ActionBinding(
        trigger = ExpressionTrigger(
            blendShape = BlendShape.EYE_BLINK_LEFT,
            threshold = 0.8f,
        ),
    )
)

// Collect action events
actionSystem.events.collect { event ->
    println("Action fired: ${event.bindingId}")
}

// Feed tracking data
faceTracker.trackingData.collect { actionSystem.processFace(it) }
handTracker.trackingData.collect { actionSystem.processHand(it) }
```

**Emotion classification** (FACS-based cosine similarity):

```kotlin
val classifier = EmotionClassifier()
faceTracker.trackingData.collect { data ->
    val result: EmotionResult = classifier.classify(data.blendShapes)
    println("${result.emotion}: ${result.confidence}")  // e.g., "happy: 0.87"
}
```

### Voice: `kmp-facelink-voice`

ASR (speech-to-text), TTS (text-to-speech), and lip sync.

```kotlin
val voicePipeline = VoicePipeline(config)

// Text-to-speech with lip sync blend shapes
voicePipeline.speak("Hello, world!").collect { lipSyncFrame ->
    // lipSyncFrame.blendShapes — mouth shapes synchronized to audio
}

// Speech recognition
voicePipeline.startListening()
voicePipeline.transcriptions.collect { text ->
    println("User said: $text")
}
```

**Supported backends:**
- ASR: Whisper (on-device via whisper.cpp)
- TTS: OpenAI TTS, ElevenLabs, Voicevox (on-device)

### LLM: `kmp-facelink-llm`

Streaming LLM API with cloud, on-device, and hybrid routing.

```kotlin
// Cloud LLM
val client = LlmClient.create(
    LlmConfig.OpenAi(apiKey = "sk-...", model = "gpt-4o-mini")
)

// On-device LLM (Gemma 3n, llama.cpp)
val onDevice = LlmClient.create(
    LlmConfig.OnDevice(modelId = "gemma-3n-e2b-int4")
)

// Hybrid: auto-routes simple queries to on-device, complex to cloud
val hybrid = LlmClient.create(
    LlmConfig.Hybrid(
        onDeviceConfig = LlmConfig.OnDevice(modelId = "gemma-3n-e2b-int4"),
        cloudConfig = LlmConfig.OpenAi(apiKey = "sk-..."),
        routingStrategy = RoutingStrategy.Auto(complexityThreshold = 0.5f),
    )
)

// Streaming chat
val history = ConversationHistory()
history.addUser("Hello!")
client.chat(history).collect { token -> print(token) }
```

See [On-Device LLM](on-device-llm.md) for model details, benchmarks, and setup.

### Pipeline: `kmp-facelink-pipeline`

Orchestrates the full AI avatar pipeline: ASR -> LLM -> TTS -> LipSync -> Expression.

```kotlin
val pipeline = buildAvatarPipeline {
    llm(LlmConfig.OpenAi(apiKey = "sk-...", model = "gpt-4o-mini"))
    persona(PersonaConfig(name = "Ai", systemPrompt = "You are a friendly assistant"))
    ttsEngine(myTtsEngine)
    lipSyncEngine(myLipSyncEngine)
    expressionBlendRatio(0.7f)
}

pipeline.start()

// Voice input mode
pipeline.listen()  // ASR -> LLM -> TTS -> lip sync

// Text input mode
pipeline.processText("Tell me a joke")

// Collect avatar output for rendering
pipeline.output.collect { frame ->
    // frame.blendShapes — combined tracking + lip sync + emotion
    // frame.audioChunk — TTS audio to play
    // frame.text — LLM response text
}
```

### Protocol Output Modules

#### VMC: `kmp-facelink-vmc`

Send tracking data to VRM applications (VSeeFace, VMagicMirror, etc.) via VMC Protocol (OSC over UDP).

```kotlin
val sender = VmcSender(VmcConfig(host = "192.168.1.100", port = 39539))
sender.start(faceTracker.trackingData, coroutineScope)

// Or send pre-mapped VRM expressions
val vrmMapper = VrmParameterMapper(version = VrmVersion.V1_0)
val expressionFlow = faceTracker.trackingData.map { vrmMapper.map(it) }
sender.startWithExpressions(expressionFlow, coroutineScope)

// Receive VMC data from other apps
val receiver = VmcReceiver(VmcConfig(port = 39539))
receiver.start(coroutineScope)
receiver.messages.collect { message -> /* handle VMC messages */ }
```

#### iFacialMocap: `kmp-facelink-ifm`

Send tracking data using the iFacialMocap protocol (compatible with VTube Studio as a MeowFace replacement).

#### WebSocket Stream: `kmp-facelink-stream`

Stream tracking data over WebSocket for phone-as-tracker use cases.

#### VTube Studio: `kmp-facelink-vts`

Direct integration with VTube Studio's Plugin API via WebSocket.

### Networking: `kmp-facelink-networking`

Shared UDP socket and time utilities used by protocol modules (VMC, iFacialMocap). Not typically used directly by app developers.

## Common Use Cases

### VTuber Avatar Driving

```kotlin
// Track face -> map to Live2D params -> drive avatar
val tracker = createFaceTracker(context, FaceTrackerConfig())
val mapper = Live2DParameterMapper()

tracker.start()
tracker.trackingData.collect { data ->
    val params = mapper.map(data)
    // Feed params to your Live2D renderer
    live2dModel.setParameterValues(params)
}
```

### Phone as Wireless Tracker

```kotlin
// On phone: track + send to desktop via VMC
val tracker = createFaceTracker(context)
val sender = VmcSender(VmcConfig(host = "192.168.1.100"))
sender.start(tracker.trackingData, scope)
```

### AI Avatar Chatbot

```kotlin
// Full pipeline: listen -> think -> speak -> animate
val pipeline = buildAvatarPipeline {
    llm(LlmConfig.Hybrid(
        onDeviceConfig = LlmConfig.OnDevice(modelId = "gemma-3n-e2b-int4"),
        cloudConfig = LlmConfig.OpenAi(apiKey = "sk-..."),
        routingStrategy = RoutingStrategy.Auto(complexityThreshold = 0.5f),
    ))
    persona(PersonaConfig(name = "Assistant"))
}
pipeline.start()
pipeline.listen()
pipeline.output.collect { frame -> /* render avatar + play audio */ }
```

### Gesture-Controlled Actions

```kotlin
val actions = ActionSystem()
actions.register(
    ActionBinding(
        trigger = GestureTrigger(HandGesture.THUMB_UP, holdTimeMs = 300),
    )
)
actions.events.collect { event ->
    when (event.bindingId) { /* handle actions */ }
}
handTracker.trackingData.collect { actions.processHand(it) }
```

## Smoothing Filters

All trackers support runtime-configurable smoothing:

| Filter | Best For | Parameters |
|--------|----------|------------|
| `SmoothingConfig.None` | Raw data, debugging | — |
| `SmoothingConfig.Ema(alpha)` | General use | `alpha`: 0-1 (higher = less smoothing) |
| `SmoothingConfig.OneEuro(...)` | Low latency + jitter removal | `minCutoff`, `beta`, `dCutoff` |
| `SmoothingConfig.CategoryAware(...)` | Per-region tuning | Separate OneEuro params per facial region |

```kotlin
tracker.updateSmoothing(SmoothingConfig.OneEuro(minCutoff = 1.0f, beta = 0.007f))
```

## API Reference (Dokka)

KDoc API reference can be generated locally:

```bash
./gradlew dokkaHtml
```

Output is at `build/dokka/html/index.html`. The reference covers all public APIs across all modules.

## Further Reading

- [Quick Start: Android](quick-start-android.md)
- [Quick Start: iOS](quick-start-ios.md)
- [On-Device LLM](on-device-llm.md) — model selection, benchmarks, hybrid routing
- [Extension Strategy](extension-strategy.md) — module architecture and roadmap
