# On-Device LLM Integration

KMP-FaceLink LLM module provides on-device inference alongside cloud LLM providers, with hybrid routing that automatically selects the best backend per query.

## Architecture Overview

```
LlmClient.create(config)
        │
        ▼
   ┌─────────┐
   │LlmConfig│
   └────┬────┘
        │
   ┌────┴──────────────────────────────┐
   │            LlmClientFactory       │
   └────┬──────────┬──────────┬────────┘
        │          │          │
   ┌────▼───┐ ┌───▼────┐ ┌──▼──────────┐
   │ Cloud  │ │OnDevice│ │   Hybrid     │
   │Clients │ │ Client │ │   Client     │
   │(OpenAI,│ │        │ │(routes between│
   │Anthro, │ │        │ │ on-device &  │
   │Gemini) │ │        │ │ cloud)       │
   └────────┘ └───┬────┘ └─────────────┘
                  │
          ┌───────┴────────┐
          │InferenceEngine │ (interface)
          └───────┬────────┘
                  │
       ┌──────────┼──────────┐
       │                     │
┌──────▼───────┐  ┌──────────▼────────┐
│ Android:     │  │ iOS:              │
│ llama.cpp    │  │ MLX Swift         │
│ (JNI bridge)│  │ (K/N interop)     │
└──────────────┘  └───────────────────┘
```

### Key Components

| Component | Location | Purpose |
|-----------|----------|---------|
| `LlmClient` | `commonMain/.../llm/LlmClient.kt` | Public interface for all LLM interactions |
| `LlmConfig.OnDevice` | `commonMain/.../llm/LlmConfig.kt` | Configuration for on-device models |
| `LlmConfig.Hybrid` | `commonMain/.../llm/LlmConfig.kt` | Configuration for hybrid routing |
| `InferenceEngine` | `commonMain/.../llm/InferenceEngine.kt` | Platform abstraction for native inference |
| `ModelRegistry` | `commonMain/.../llm/ModelRegistry.kt` | Catalog of available models with RAM-based selection |
| `ModelManager` | `commonMain/.../llm/ModelManager.kt` | Model download and cache management |
| `RoutingStrategy` | `commonMain/.../llm/RoutingStrategy.kt` | Hybrid routing decision logic |
| `IntentClassifier` | `commonMain/.../llm/internal/IntentClassifier.kt` | Query complexity estimation |
| `LlmBenchmarkRunner` | `commonMain/.../llm/benchmark/LlmBenchmarkRunner.kt` | Benchmarking framework |

## Supported Models

Models are defined in `ModelRegistry` and downloaded as GGUF files.

### Recommended Models (Gemma 3n)

Gemma 3n (2026) uses MatFormer selective layer activation to achieve high effective parameter counts from larger raw models, optimized for mobile: ~0.75% battery per hour, multimodal (audio+vision), built-in ASR.

| Model ID | Display Name | Download Size | RAM Required | Quantization | Notes |
|----------|-------------|---------------|-------------|-------------|-------|
| `gemma-3n-e2b-int4` | Gemma 3n E2B (INT4) | ~1.8 GB | ~2 GB | INT4 | **Default.** 5B raw / 2B effective params. Best balance of quality and size for VTuber chat |
| `gemma-3n-e4b-int4` | Gemma 3n E4B (INT4) | ~3.2 GB | ~4.5 GB | INT4 | High-quality. 5B raw / 4B effective params. For devices with >4 GB RAM |

### Lightweight Fallback

| Model ID | Display Name | Download Size | RAM Required | Quantization | Notes |
|----------|-------------|---------------|-------------|-------------|-------|
| `qwen3-0.6b-int4` | Qwen3 0.6B (INT4) | ~500 MB | ~800 MB | INT4 | Ultra-lightweight fallback for devices with <1 GB available RAM |

### Legacy Models

| Model ID | Display Name | Download Size | RAM Required | Quantization | Notes |
|----------|-------------|---------------|-------------|-------------|-------|
| `gemma-3-1b-int4` | Gemma 3 1B (INT4) | ~800 MB | ~1.5 GB | INT4 | Previous default; superseded by Gemma 3n E2B |
| `gemma-3-4b-int4` | Gemma 3 4B (INT4) | ~2.5 GB | ~4 GB | INT4 | Superseded by Gemma 3n E4B |
| `phi-3-mini-int4` | Phi-3 Mini (INT4) | ~2.3 GB | ~4 GB | INT4 | Strong reasoning for its size |
| `phi-4-mini-int4` | Phi-4 Mini (INT4) | ~2.4 GB | ~4 GB | INT4 | Latest Phi series |

### Default Model Selection

`ModelRegistry.recommendForRam(deviceRamBytes)` selects the best model based on available device RAM:

| Device RAM | Selected Model | Rationale |
|-----------|---------------|-----------|
| < 1 GB | Qwen3 0.6B | Ultra-lightweight fallback for very constrained devices |
| 1–4 GB | Gemma 3n E2B | Default. Excellent quality-to-size ratio with MatFormer architecture |
| > 4 GB | Gemma 3n E4B | High-quality variant with 4B effective parameters |

### Benchmark References

Performance data from external benchmarks (SLM Leaderboard 2026, Google developer guides):

| Model | Params (effective) | MMLU | MT-Bench | Tokens/sec (Pixel 9) | Battery/hr |
|-------|-------------------|------|----------|----------------------|-----------|
| Gemma 3n E2B | 2B | ~58% | ~7.2 | ~35 tok/s | ~0.75% |
| Gemma 3n E4B | 4B | ~65% | ~7.8 | ~18 tok/s | ~1.2% |
| Qwen3 0.6B | 0.6B | ~42% | ~5.8 | ~80 tok/s | ~0.3% |
| Gemma 3 1B | 1B | ~45% | ~6.0 | ~50 tok/s | ~0.5% |

> **Note:** These are reference numbers from published benchmarks. Actual performance varies by device, thermal state, and concurrent workloads. Run `LlmBenchmarkRunner` for device-specific measurements.

## Setup Instructions

### Android (llama.cpp)

> **Status:** Kotlin JNI stubs exist. Native library linking is not yet complete.

When ready to integrate:

1. Clone llama.cpp into the project:
   ```bash
   git clone https://github.com/ggml-org/llama.cpp external/llama.cpp
   ```

2. Build with NDK (requires NDK r26+):
   ```bash
   cd external/llama.cpp
   mkdir build-android && cd build-android
   cmake .. \
     -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
     -DANDROID_ABI=arm64-v8a \
     -DANDROID_PLATFORM=android-26 \
     -DGGML_OPENMP=OFF
   cmake --build . --config Release
   ```

3. Copy the built `libllama.so` and `libggml.so` to `kmp-facelink-llm/src/androidMain/jniLibs/arm64-v8a/`.

4. The `LlamaCppBridge` class in `androidMain` provides JNI bindings that call into the native library.

### iOS (MLX Swift)

> **Status:** Kotlin/Native stubs exist. MLX Swift package linking is not yet complete.

When ready to integrate:

1. Add the MLX Swift package to `iosApp/project.yml`:
   ```yaml
   packages:
     mlx-swift:
       url: https://github.com/ml-explore/mlx-swift
       from: 0.21.0
     mlx-swift-examples:
       url: https://github.com/ml-explore/mlx-swift-examples
       from: 1.19.0
   ```

2. Run `xcodegen generate` to update the Xcode project.

3. The `IosInferenceEngine` in `iosMain` bridges to MLX via Kotlin/Native interop.

4. Models are converted from GGUF to MLX format at download time (handled by `ModelManager`).

## API Usage

### Basic On-Device Chat

```kotlin
// Use the default model (Gemma 3n E2B)
val defaultModel = ModelRegistry.defaultModel()

// Or select based on device RAM
val recommended = ModelRegistry.recommendForRam(deviceRamBytes = 4_000_000_000L)

// Create an on-device client
val config = LlmConfig.OnDevice(
    modelId = recommended.id,
    maxTokens = 512,
    temperature = 0.7,
)
val client = LlmClient.create(config)

// Prepare conversation
val history = ConversationHistory()
history.addUser("What is the capital of France?")

// Stream response tokens
client.chat(history).collect { token ->
    print(token) // "The", " capital", " of", " France", " is", " Paris", "."
}

// Or get complete response
val response = client.chatOnce(history)
println(response) // "The capital of France is Paris."

// Release when done
client.release()
```

### Hybrid Routing (On-Device + Cloud)

```kotlin
val config = LlmConfig.Hybrid(
    onDeviceConfig = LlmConfig.OnDevice(modelId = "gemma-3n-e2b-int4"),
    cloudConfig = LlmConfig.OpenAi(
        apiKey = "sk-...",
        model = "gpt-4o-mini",
    ),
    routingStrategy = RoutingStrategy.Auto(complexityThreshold = 0.5f),
)
val client = LlmClient.create(config)

// Simple queries route to on-device automatically
history.addUser("Hi!")
client.chatOnce(history) // → on-device (low complexity)

// Complex queries route to cloud
history.clear()
history.addUser("Write a detailed analysis of quantum computing trends...")
client.chatOnce(history) // → cloud (high complexity)
```

### Routing Strategies

```kotlin
// Always use on-device (offline-only mode)
RoutingStrategy.AlwaysOnDevice

// Always use cloud (on-device as fallback only)
RoutingStrategy.AlwaysCloud

// Automatic routing based on query complexity
RoutingStrategy.Auto(complexityThreshold = 0.5f)
// Queries below 0.5 complexity → on-device
// Queries at or above 0.5 complexity → cloud
// On-device failures automatically fall back to cloud
```

### Model Management

```kotlin
val modelManager = DefaultModelManager.create()

// Download the recommended model
val modelInfo = ModelRegistry.recommendForRam(deviceRamBytes = 4_000_000_000L)
modelManager.download(modelInfo).collect { progress ->
    println("${progress.percent}% downloaded")
}

// Check if model is available
val isReady = modelManager.isDownloaded(modelInfo.id)

// List cached models
val cached = modelManager.listCached()

// Delete a model
modelManager.delete(modelInfo.id)
```

## Benchmarking Guide

The benchmarking framework (`io.github.kmpfacelink.llm.benchmark`) provides tools for measuring on-device LLM performance once native libraries are integrated.

### Running Benchmarks

```kotlin
// Create a client to benchmark
val config = LlmConfig.OnDevice(modelId = "gemma-3n-e2b-int4")
val client = LlmClient.create(config)

// Run with standard prompts
val runner = LlmBenchmarkRunner(
    client = client,
    modelId = "gemma-3n-e2b-int4",
    deviceInfo = "Pixel 8 Pro / Android 15",
)
val results = runner.runBenchmark(BenchmarkPrompts.standardSet())

// Inspect results
for (result in results) {
    println("Tokens/sec: ${result.tokensPerSecond}")
    println("First token: ${result.firstTokenLatencyMs}ms")
    println("Total time: ${result.totalGenerationMs}ms")
    println("Completion tokens: ${result.completionTokens}")
    println("---")
}

client.release()
```

### Custom Prompts

```kotlin
val customPrompts = listOf(
    "Hello!",
    "Translate 'good morning' to Japanese.",
    "Summarize the theory of relativity in three sentences.",
)
val results = runner.runBenchmark(customPrompts)
```

### Metrics Collected

| Metric | Description | Unit |
|--------|-------------|------|
| `tokensPerSecond` | Average generation throughput | tok/s |
| `firstTokenLatencyMs` | Time to first token emission | ms |
| `totalGenerationMs` | Total wall-clock generation time | ms |
| `promptTokens` | Estimated prompt token count | count |
| `completionTokens` | Number of generated tokens | count |
| `peakMemoryMb` | Peak memory usage (platform-specific, 0 if not measured) | MB |

## Known Limitations

- **Native libraries not linked:** llama.cpp (Android) and MLX Swift (iOS) stubs exist but native binaries are not yet integrated. On-device inference will throw at runtime until linked.
- **No actual benchmark data:** Performance numbers will be available after native integration. Reference numbers in this doc are from external benchmarks.
- **Memory measurement:** `peakMemoryMb` is always 0 — platform-specific memory profiling is future work.
- **Thermal throttling:** Not yet monitored. Extended inference on mobile devices may trigger thermal throttling that degrades performance.
- **Model conversion:** iOS MLX format conversion from GGUF is stubbed but not implemented.
- **Checksum verification:** Model SHA-256 checksums in `ModelRegistry` are placeholders.

## References

- [Gemma 3n Developer Guide](https://developers.googleblog.com/en/introducing-gemma-3n-developer-guide/)
- [Gemma 3n Overview](https://ai.google.dev/gemma/docs/gemma-3n)
- [SLM Leaderboard 2026](https://awesomeagents.ai/leaderboards/small-language-model-leaderboard/)

## Future Work

- Link llama.cpp native library for Android (NDK build + JNI)
- Link MLX Swift package for iOS
- Implement platform-specific memory and thermal monitoring in benchmarks
- Add real benchmark data tables per device/model combination
- Support additional quantization formats (INT8, FP16)
- Implement model format conversion (GGUF → MLX) for iOS
- Add streaming performance metrics (token-to-token jitter)
- Integrate with CI for automated regression benchmarking
- Explore Gemma 3n built-in ASR to simplify Whisper dependency in voice module
