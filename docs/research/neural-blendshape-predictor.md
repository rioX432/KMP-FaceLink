# Neural Blendshape Predictor — Integration Strategy

Research spike for a custom TFLite model that predicts 52 ARKit blendshape
coefficients from 478 MediaPipe face landmarks, using ARKit data as ground truth.

## Background

The current Android blendshape pipeline relies on MediaPipe's built-in blendshape
model, supplemented by geometric solvers (via `BlendShapeEnhancer`) for shapes where
MediaPipe accuracy is low. While the geometric solver + sensitivity/dead-zone tuning
approach works, it requires per-shape manual calibration and cannot capture complex
nonlinear landmark-to-blendshape relationships.

A neural predictor trained on paired ARKit/MediaPipe data could:
- Replace both MediaPipe blendshapes and geometric solvers with a single model
- Capture cross-shape correlations automatically
- Generalize better with more training data
- Run at sub-5ms on mobile (the architecture is 3 FC layers, ~400K params)

## Model Architecture

From the Blendshapes GHUM paper:

```
Input:  478 landmarks x 3 coords = 1434 floats
  -> FC(1434 -> 256) + BatchNorm + ReLU
  -> FC(256  -> 128) + BatchNorm + ReLU
  -> FC(128  ->  52) + Sigmoid
Output: 52 blendshape coefficients in [0, 1]
```

Estimated model size: ~400KB (float16 TFLite).

## Training Pipeline

See `tools/calibration/train_predictor.py` for the full implementation.

Key features:
- **Data source**: Paired MediaPipe landmarks (from RGB frames) + ARKit blendshapes
  (ground truth) from the calibration capture tool
- **Weighted MSE loss**: Dynamically upweights the 10 worst-performing shapes (3x)
- **Cross-subject validation**: Leave-one-subject-out to verify generalization
- **TFLite float16 conversion**: For mobile deployment
- **Dry-run mode**: Synthetic data for pipeline verification

## Success Criteria

| Criterion | Threshold | Rationale |
|---|---|---|
| MAE improvement over geometric solver | >= 15% | Must justify the added complexity |
| Inference latency | <= 5 ms | Must not add perceptible lag (60fps budget = 16ms) |
| Cross-subject generalization | Within 20% of training MAE | Must not overfit to specific faces |
| Model size | <= 1 MB | Must fit in mobile app bundle without bloat |

## Kotlin Integration Strategy (If Results Positive)

### Strategy Selector

```kotlin
public sealed class BlendShapePredictionStrategy {
    /**
     * Current approach: MediaPipe blendshapes + geometric solver enhancement.
     * Default on all platforms.
     */
    public data object Geometric : BlendShapePredictionStrategy()

    /**
     * Neural predictor: TFLite model mapping landmarks -> blendshapes.
     * Android-only. Requires bundled .tflite model file.
     */
    public data class Neural(
        val modelPath: String = "models/blendshape_predictor.tflite",
        val fallbackToGeometric: Boolean = true,
    ) : BlendShapePredictionStrategy()
}
```

### Neural Predictor (androidMain only)

```kotlin
internal class NeuralBlendShapePredictor(
    private val modelPath: String,
) : Closeable {
    private val interpreter: Interpreter  // TFLite Interpreter
    private val inputBuffer: ByteBuffer   // 1434 floats
    private val outputBuffer: ByteBuffer  // 52 floats

    /**
     * Predict 52 blendshape coefficients from 478 face landmarks.
     *
     * @param landmarks Flattened [x, y, z] array of length 1434
     * @return Map of blendshape name to coefficient in [0, 1]
     */
    fun predict(landmarks: FloatArray): Map<String, Float>

    override fun close()  // Release interpreter resources
}
```

### Feature Flag Approach

```kotlin
public class FaceTrackingConfig(
    // Existing fields...

    /**
     * Blendshape prediction strategy. Default is Geometric (current behavior).
     * Set to Neural to use the TFLite predictor (Android only).
     */
    public val predictionStrategy: BlendShapePredictionStrategy =
        BlendShapePredictionStrategy.Geometric,
)
```

Integration in `AndroidFaceTracker`:

```kotlin
// In processFrame():
val blendshapes = when (val strategy = config.predictionStrategy) {
    is BlendShapePredictionStrategy.Geometric -> {
        // Current path: MediaPipe blendshapes + geometric enhancement
        enhancer.enhance(mediaPipeBlendshapes, landmarks)
    }
    is BlendShapePredictionStrategy.Neural -> {
        try {
            neuralPredictor.predict(landmarks.toFlatArray())
        } catch (e: Exception) {
            if (strategy.fallbackToGeometric) {
                enhancer.enhance(mediaPipeBlendshapes, landmarks)
            } else {
                throw e
            }
        }
    }
}
```

### TFLite Dependency

Add to `androidMain` dependencies (behind a feature flag or optional module):

```kotlin
// Option A: In kmp-facelink's androidMain
implementation("org.tensorflow:tensorflow-lite:2.16.1")
implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

// Option B: Separate optional module (preferred)
// kmp-facelink-neural/build.gradle.kts
// Consumers opt-in: implementation("io.github.rioX432:kmp-facelink-neural:x.y.z")
```

Option B (separate module) is preferred because:
- Keeps the core SDK lightweight for users who don't need neural prediction
- TFLite dependency (~3MB AAR) only added when explicitly opted in
- Clean separation of concerns

### iOS Considerations

On iOS, ARKit already provides high-quality blendshapes natively. The neural
predictor is Android-only, addressing the Android-specific accuracy gap between
MediaPipe and ARKit.

If needed in the future, Core ML conversion (`coremltools`) produces `.mlmodelc`
files that run on the Apple Neural Engine.

## Decision Gate

After running `train_predictor.py` on real captured data:

1. **If all 4 criteria pass**: File implementation issue for `NeuralBlendShapePredictor`
   - Create `kmp-facelink-neural` optional module
   - Bundle TFLite model in AAR assets
   - Implement `NeuralBlendShapePredictor` with TFLite Interpreter
   - Add `BlendShapePredictionStrategy.Neural` to `FaceTrackingConfig`
   - Update documentation and quick-start guides

2. **If latency or size fails but accuracy passes**: Investigate model pruning,
   quantization (int8), or architecture reduction (fewer hidden units).

3. **If accuracy fails**: The geometric solver approach may be sufficient. Consider:
   - More training data (more subjects, more expressions)
   - Architecture changes (attention layers, skip connections)
   - Per-group models (separate models for eye/mouth/brow regions)

4. **If generalization fails**: Collect more diverse training data before
   re-evaluating. The model may need data augmentation or regularization.

## References

- Blendshapes GHUM paper: Li et al., "Learning Formation of Physically-Based
  Face Attributes" (2020)
- MediaPipe Face Landmarker: 478-point face mesh
- ARKit blendshapes: 52 Apple ARFaceAnchor blendShapeLocations
- TFLite on Android: https://www.tensorflow.org/lite/android
