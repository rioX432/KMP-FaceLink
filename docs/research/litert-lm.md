# LiteRT-LM Research: Optional Android LLM Backend for Gemma Models

**Issue:** #248
**Status:** Research complete
**Date:** 2026-03-24

## Executive Summary

This document evaluates Google's LiteRT-LM as an alternative on-device LLM inference backend for Android, complementing the existing llama.cpp integration. LiteRT-LM is Google's production-ready, open-source framework (successor to MediaPipe LLM Inference API) optimized for Gemma models with GPU/NPU acceleration via the ML Drift engine.

**Key finding:** LiteRT-LM provides significant performance advantages over llama.cpp specifically for Gemma models on Android (up to ~7x faster prefill on GPU, NPU further improves this). However, it uses a proprietary `.litertlm` model format (no GGUF support), limiting it to pre-converted models from HuggingFace. The recommended approach is to add LiteRT-LM as an optional backend alongside llama.cpp, selected automatically based on model family and device capabilities.

---

## 1. Current On-Device Architecture

### 1.1 llama.cpp Backend

The existing `kmp-facelink-llm` module uses llama.cpp via JNI for on-device inference:

- **Interface:** `InferenceEngine` with `load()`, `generate()`, `unload()`, `isLoaded`
- **Implementation:** `AndroidInferenceEngine` wrapping `LlamaCppBridge` (JNI)
- **Model format:** GGUF (universal, broad model support)
- **Acceleration:** CPU-only (`nGpuLayers = 0` by default), OpenCL GPU optional
- **Streaming:** `callbackFlow` bridging JNI callbacks to Kotlin Flow
- **Model management:** `ModelManager` + `ModelRegistry` with download/cache/SHA-256 verification

### 1.2 Routing Strategy

`HybridLlmClient` routes between on-device and cloud via `RoutingStrategy`:
- `AlwaysOnDevice` / `AlwaysCloud` for fixed routing
- `Auto(complexityThreshold)` using `IntentClassifier` score

### 1.3 Supported Models

`ModelRegistry` includes 7 GGUF models: Gemma 3n E2B/E4B, Gemma 3 1B/4B, Qwen3 0.6B, Phi-3/4 Mini. All INT4 quantized from HuggingFace.

---

## 2. LiteRT-LM Overview

### 2.1 What is LiteRT-LM?

LiteRT-LM is Google's production-ready, open-source inference framework for on-device LLMs. It powers Gemini Nano and Gemma in Chrome, Chromebook Plus, and Pixel Watch. Released June 2025 (v0.6.1), now at v0.8.1 stable (v0.9.0-beta in pre-release as of March 2026).

### 2.2 Key Features

| Feature | Details |
|---|---|
| Model format | `.litertlm` (proprietary, evolution of MediaPipe `.task` format) |
| CPU acceleration | XNNPACK delegate, 4-thread |
| GPU acceleration | ML Drift engine (OpenCL, OpenGL, Metal, WebGPU) |
| NPU acceleration | Qualcomm QNN, MediaTek NeuroPilot (early access) |
| Multimodal | Text, vision, audio input |
| Streaming | Kotlin Flow + callback-based API |
| Tool calling | Built-in function calling support |
| Conversation API | Session management with system instructions |
| Platforms | Android, iOS (Swift in dev), JVM, C++ |

### 2.3 Kotlin API

```kotlin
// Dependency
implementation("com.google.ai.edge.litertlm:litertlm-android:latest.release")

// Engine setup
val engineConfig = EngineConfig(
    modelPath = "/path/to/model.litertlm",
    backend = Backend.GPU(),  // or CPU(), NPU(nativeLibraryDir)
    cacheDir = "/tmp/"
)

val engine = Engine(engineConfig)
engine.initialize()  // ~10 seconds for model loading

// Conversation
val conversationConfig = ConversationConfig(
    systemInstruction = Contents.of("You are helpful"),
    samplerConfig = SamplerConfig(
        topK = 10,
        topP = 0.95f,
        temperature = 0.8f
    )
)

engine.createConversation(conversationConfig).use { conversation ->
    // Flow-based streaming (recommended)
    conversation.sendMessageAsync("Hello").collect { print(it) }
}
```

### 2.4 Android Manifest Requirements (GPU)

```xml
<uses-native-library android:name="libvndksupport.so" android:required="false"/>
<uses-native-library android:name="libOpenCL.so" android:required="false"/>
```

---

## 3. Performance Benchmarks

### 3.1 LiteRT-LM Benchmarks (from Google)

Measured with 1024 token prefill, 256 token decode:

| Model | Device | Backend | Prefill (tok/s) | Decode (tok/s) |
|---|---|---|---|---|
| Gemma 3n E2B | MacBook Pro M3 | CPU | 232.5 | 27.6 |
| Gemma 3n E2B | Samsung S24 Ultra | CPU | 110.5 | 16.1 |
| Gemma 3n E2B | Samsung S24 Ultra | GPU | 816.4 | 15.6 |
| Gemma 3n E2B | Vivo X300 Pro | NPU | 1,671.3 | 28.4 |
| Gemma 3 1B | MacBook Pro M3 | CPU | 423.0 | 67.0 |
| Gemma 3 1B | Samsung S25 | NPU | 5,837.0 | 85.0 |
| Gemma 3n E4B | Samsung S24 | GPU | 548.0 | 9.4 |

### 3.2 llama.cpp Estimates on Android

Based on community benchmarks for GGUF Q4_0 models on flagship Android (2024-2025):

| Model | Device | Backend | Prefill (tok/s) | Decode (tok/s) |
|---|---|---|---|---|
| Gemma 3n E2B Q4 | Flagship Android | CPU | ~50-80 | ~10-20 |
| Gemma 3 1B Q4 | Flagship Android | CPU | ~80-120 | ~15-25 |
| Gemma 3n E4B Q4 | Flagship Android | CPU | ~30-50 | ~5-12 |

Note: llama.cpp Android GPU (Vulkan) support exists but is less mature than CPU path.

### 3.3 Comparison Analysis

| Metric | llama.cpp (CPU) | LiteRT-LM (CPU) | LiteRT-LM (GPU) | LiteRT-LM (NPU) |
|---|---|---|---|---|
| Gemma 3n E2B prefill | ~50-80 tok/s | 110.5 tok/s | 816.4 tok/s | 1,671.3 tok/s |
| Gemma 3n E2B decode | ~10-20 tok/s | 16.1 tok/s | 15.6 tok/s | 28.4 tok/s |
| Improvement (prefill) | baseline | ~1.5-2x | ~10-16x | ~20-33x |
| Improvement (decode) | baseline | ~1x | ~1x | ~1.5-2.8x |

**Key takeaways:**

1. **Prefill is dramatically faster with LiteRT-LM GPU/NPU.** This directly improves time-to-first-token (TTFT), which is critical for conversational AI avatar responsiveness.
2. **Decode speed is comparable on CPU**, but NPU provides meaningful improvement for sustained generation.
3. **GPU prefill is the sweet spot**: 816 tok/s vs ~60 tok/s means ~10x faster context processing, turning multi-second waits into sub-second.
4. Google states LiteRT "demonstrates a clear performance advantage, outperforming llama.cpp on CPU/GPU for both prefill and decode" for Gemma models on Samsung Galaxy S25 Ultra.

---

## 4. Model Format Considerations

### 4.1 GGUF vs .litertlm

| Aspect | GGUF (llama.cpp) | .litertlm (LiteRT-LM) |
|---|---|---|
| Format | Open, standardized | Proprietary to Google AI Edge |
| Model coverage | Thousands of models on HuggingFace | ~10 models on litert-community |
| Conversion tools | `llama.cpp/convert_*.py` from HF | No public conversion from GGUF |
| Quantization | Q4_0, Q4_K_M, Q8_0, etc. | INT4 (weights) + FP32 (activations) |
| Bundled tokenizer | Optional (often external) | Always bundled in `.litertlm` |

### 4.2 Available .litertlm Models

From `huggingface.co/litert-community`:

| Model | Size | Quantization | Context |
|---|---|---|---|
| Gemma 3 1B IT | 557 MB | INT4 | 4096 |
| Gemma 3n E2B IT | 2,965 MB | INT4 | 32K |
| Gemma 3n E4B IT | ~5 GB | INT4 | 32K |
| Phi-4-mini | 3,728 MB | INT8 | 4096 |
| Qwen2.5 1.5B | 1,524 MB | INT8 | 4096 |

### 4.3 No GGUF-to-LiteRT-LM Conversion

There is **no public tool** to convert GGUF models to `.litertlm` format. The `.litertlm` format bundles the tokenizer, model weights, and hardware-specific optimizations together. Models must be obtained pre-converted from HuggingFace `litert-community` or Google's repositories.

**Implication:** We cannot use our existing GGUF model downloads with LiteRT-LM. Each backend needs its own model files. The `ModelRegistry` must track both GGUF and `.litertlm` variants for models that support both backends.

---

## 5. Integration Design

### 5.1 LiteRtLmEngine: InferenceEngine Implementation

```kotlin
// New file: androidMain/.../internal/LiteRtLmEngine.kt
internal class LiteRtLmEngine(
    private val backend: LiteRtBackend = LiteRtBackend.GPU,
    private val cacheDir: String? = null,
) : InferenceEngine {

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    override suspend fun load(modelPath: String) {
        withContext(Dispatchers.IO) {
            val config = EngineConfig(
                modelPath = modelPath,
                backend = backend.toSdkBackend(),
                cacheDir = cacheDir,
            )
            engine = Engine(config).also { it.initialize() }
        }
    }

    override fun generate(
        prompt: String,
        maxTokens: Int,
        temperature: Double,
    ): Flow<String> = flow {
        val eng = engine ?: error("Engine not loaded")
        val convConfig = ConversationConfig(
            samplerConfig = SamplerConfig(temperature = temperature.toFloat()),
        )
        eng.createConversation(convConfig).use { conv ->
            conv.sendMessageAsync(prompt).collect { emit(it.toString()) }
        }
    }

    override suspend fun unload() {
        withContext(Dispatchers.IO) {
            conversation?.close()
            conversation = null
            engine?.close()
            engine = null
        }
    }

    override val isLoaded: Boolean
        get() = engine != null
}

internal enum class LiteRtBackend { CPU, GPU, NPU }
```

### 5.2 ModelInfo Extension

Add a `format` field to `ModelInfo` to distinguish GGUF and LiteRT-LM models:

```kotlin
public enum class ModelFormat {
    GGUF,
    LITERT_LM,
}

public data class ModelInfo(
    val id: String,
    val displayName: String,
    val sizeBytes: Long,
    val quantization: Quantization,
    val downloadUrl: String,
    val sha256Checksum: String,
    val ramRequirementBytes: Long = 0L,
    val format: ModelFormat = ModelFormat.GGUF,  // NEW: backward compatible
)
```

### 5.3 ModelRegistry Updates

Add `.litertlm` variants alongside existing GGUF entries:

```kotlin
// Existing GGUF entry
ModelInfo(
    id = "gemma-3n-e2b-int4",
    format = ModelFormat.GGUF,
    downloadUrl = "$HF_BASE/google/gemma-3n-E2B-it-qat-q4_0-gguf/...",
    ...
),
// New LiteRT-LM entry
ModelInfo(
    id = "gemma-3n-e2b-int4-litert",
    format = ModelFormat.LITERT_LM,
    downloadUrl = "$HF_BASE/google/gemma-3n-E2B-it-litert-lm/...",
    ...
),
```

### 5.4 OnDevice Config Extension

Add optional backend preference to `LlmConfig.OnDevice`:

```kotlin
public data class OnDevice(
    val modelId: String,
    val maxTokens: Int = DEFAULT_MAX_TOKENS,
    val temperature: Double = DEFAULT_TEMPERATURE,
    val preferredBackend: OnDeviceBackend = OnDeviceBackend.AUTO, // NEW
) : LlmConfig()

public enum class OnDeviceBackend {
    AUTO,       // Select best available (LiteRT-LM GPU > llama.cpp CPU)
    LLAMA_CPP,  // Force llama.cpp (GGUF)
    LITERT_LM,  // Force LiteRT-LM (.litertlm)
}
```

### 5.5 Routing Strategy Updates

The `Auto` routing in `HybridLlmClient` currently routes by query complexity. With dual backends, we add backend-level routing:

```kotlin
// In OnDeviceClientFactory (Android)
internal fun selectEngine(modelId: String, backend: OnDeviceBackend): InferenceEngine {
    val model = ModelRegistry.findById(modelId)
    return when (backend) {
        OnDeviceBackend.LLAMA_CPP -> AndroidInferenceEngine()
        OnDeviceBackend.LITERT_LM -> LiteRtLmEngine(backend = LiteRtBackend.GPU)
        OnDeviceBackend.AUTO -> {
            // Prefer LiteRT-LM for Gemma models (optimized GPU path)
            if (model != null && model.id.startsWith("gemma")) {
                val liteRtModel = findLiteRtVariant(model)
                if (liteRtModel != null) {
                    LiteRtLmEngine(backend = LiteRtBackend.GPU)
                } else {
                    AndroidInferenceEngine()
                }
            } else {
                // Non-Gemma models: use llama.cpp (broader GGUF compatibility)
                AndroidInferenceEngine()
            }
        }
    }
}
```

**AUTO selection logic:**
1. Gemma models with `.litertlm` available -> LiteRT-LM (GPU)
2. Gemma models without `.litertlm` -> llama.cpp (GGUF)
3. Non-Gemma models (Phi, Qwen, etc.) -> llama.cpp (GGUF)

This aligns with LiteRT-LM's strength (Gemma-optimized) while keeping llama.cpp as the universal fallback.

---

## 6. Dependency and Size Impact

### 6.1 AAR Size

| Dependency | Estimated Size |
|---|---|
| llama.cpp JNI (current) | ~5-8 MB (native .so per ABI) |
| LiteRT-LM Android SDK | ~15-25 MB (includes ML Drift GPU engine) |
| Combined | ~20-33 MB |

### 6.2 Mitigation

- Make LiteRT-LM an **optional dependency** via Gradle feature variants:
  ```kotlin
  // build.gradle.kts
  dependencies {
      implementation("io.github.kmpfacelink:kmp-facelink-llm:x.y.z")
      // Optional: add LiteRT-LM backend for GPU-accelerated Gemma inference
      implementation("io.github.kmpfacelink:kmp-facelink-llm-litert:x.y.z")
  }
  ```
- Users who only need llama.cpp don't pay the SDK size cost
- LiteRT-LM backend is discovered at runtime via `ServiceLoader` or explicit registration

---

## 7. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| LiteRT-LM SDK API breaks (still pre-1.0) | Medium | High | Pin version, abstract behind InferenceEngine interface |
| `.litertlm` format changes between versions | Low | Medium | Model version tracking in ModelRegistry |
| GPU not available on all devices | Medium | Low | Fallback to CPU backend, then to llama.cpp |
| Dual model downloads waste storage | Medium | Medium | ModelManager selects one format per model based on backend |
| NPU early access program limits adoption | High | Low | NPU is a bonus; GPU is the primary target |
| LiteRT-LM doesn't support non-Gemma models well | Low | Low | llama.cpp remains default for non-Gemma |
| SDK size bloat for apps not using LiteRT-LM | Medium | Medium | Optional dependency via separate module |

---

## 8. Comparison Summary

| Aspect | llama.cpp | LiteRT-LM |
|---|---|---|
| Model format | GGUF (universal) | .litertlm (Google only) |
| Model ecosystem | Thousands of models | ~10 models |
| Gemma optimization | Generic GGUF path | Purpose-built, ML Drift GPU engine |
| GPU acceleration | Vulkan (less mature on Android) | OpenCL/GL via ML Drift (production-proven) |
| NPU support | None | Qualcomm QNN, MediaTek (early access) |
| Prefill speed (Gemma) | ~50-80 tok/s | 110-1671 tok/s (CPU/GPU/NPU) |
| Decode speed (Gemma) | ~10-20 tok/s | 16-85 tok/s (CPU/GPU/NPU) |
| TTFT impact | ~12-20s for 1K context | ~1-9s (GPU), ~0.6s (NPU) |
| Multimodal | Text only | Text + vision + audio |
| Tool calling | No | Yes (built-in) |
| Conversation API | Manual prompt formatting | Built-in session management |
| Kotlin API | JNI bridge (manual) | Official Kotlin SDK with Flow |
| SDK maturity | Stable (years of use) | v0.8.1 stable, v0.9.0-beta |
| Binary size | ~5-8 MB | ~15-25 MB |
| Platform | Any (cross-platform) | Android-first (iOS Swift in dev) |

---

## 9. Recommendation

**Proceed with implementation as an optional backend module.**

LiteRT-LM offers compelling advantages for the VTuber AI avatar use case:

1. **Time-to-first-token matters most for avatars.** When a user speaks and waits for the AI avatar to respond, prefill speed directly determines perceived latency. LiteRT-LM GPU provides ~10x faster prefill than llama.cpp CPU, reducing the "thinking" pause from 12-20 seconds to 1-2 seconds for a 1K token context.

2. **Gemma is our default model family.** `ModelRegistry.defaultModel()` returns Gemma 3n E2B, and `recommendForRam()` always selects a Gemma model. LiteRT-LM is purpose-built for Gemma, so the optimization target aligns perfectly.

3. **Integration is clean.** The `InferenceEngine` interface is a perfect abstraction boundary. `LiteRtLmEngine` implements the same `load()` / `generate()` / `unload()` contract, making it a drop-in alternative. The LiteRT-LM Kotlin SDK already provides Flow-based streaming that maps directly to our API.

4. **Risk is low.** llama.cpp remains the universal fallback. LiteRT-LM is additive, not a replacement. The optional dependency model means zero impact on apps that don't need it.

5. **Multimodal is a future bonus.** LiteRT-LM's built-in vision and audio input support could enable future features (camera-aware AI, voice-to-voice without ASR).

### Actionable Next Steps

1. **Create issue: Implement `LiteRtLmEngine`** -- Android `InferenceEngine` implementation wrapping LiteRT-LM SDK, with GPU backend and fallback to CPU.

2. **Create issue: Add `ModelFormat` to `ModelInfo`** -- Extend `ModelInfo` with format field, add `.litertlm` model entries to `ModelRegistry` for Gemma 3n E2B, E4B, and Gemma 3 1B.

3. **Create issue: Add `OnDeviceBackend` to `LlmConfig.OnDevice`** -- Backend preference enum (AUTO/LLAMA_CPP/LITERT_LM) with auto-selection logic favoring LiteRT-LM for Gemma models.

4. **Create issue: Separate `kmp-facelink-llm-litert` module** -- Optional Gradle module containing LiteRT-LM dependency and `LiteRtLmEngine`, discoverable at runtime.

5. **Create issue: Benchmark harness for backend comparison** -- Extend existing `LlmBenchmarkRunner` to compare llama.cpp vs LiteRT-LM on the same Gemma model, measuring prefill speed, decode speed, TTFT, memory usage, and thermal impact.

6. **Create issue: Model download routing** -- `ModelManager` downloads the correct format (GGUF or .litertlm) based on selected backend, avoiding duplicate storage.

---

## References

- [LiteRT-LM GitHub](https://github.com/google-ai-edge/LiteRT-LM)
- [LiteRT-LM Kotlin API](https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md)
- [LiteRT-LM Releases](https://github.com/google-ai-edge/LiteRT-LM/releases)
- [LiteRT-LM Android Getting Started](https://ai.google.dev/edge/litert-lm/get-started/android)
- [LiteRT: The Universal Framework for On-Device AI (Google Blog)](https://developers.googleblog.com/litert-the-universal-framework-for-on-device-ai/)
- [On-device GenAI with LiteRT-LM (Google Blog)](https://developers.googleblog.com/on-device-genai-in-chrome-chromebook-plus-and-pixel-watch-with-litert-lm/)
- [Gemma 3n E2B LiteRT-LM on HuggingFace](https://huggingface.co/google/gemma-3n-E2B-it-litert-lm)
- [Gemma 3n E4B LiteRT-LM on HuggingFace](https://huggingface.co/google/gemma-3n-E4B-it-litert-lm)
- [LiteRT-LM Maven (Android)](https://mvnrepository.com/artifact/com.google.ai.edge.litertlm/litertlm-android)
- [Google AI Edge Gallery](https://github.com/google-ai-edge/gallery)
