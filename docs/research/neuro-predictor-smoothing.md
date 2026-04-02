# N-euro Predictor: Neural Smoothing Filter Research

Research for [#472](https://github.com/rioX432/KMP-FaceLink/issues/472).

## Algorithm Summary

N-euro Predictor is a residual-learning-based neural network that simultaneously smooths
and predicts motion trajectories. Unlike traditional filters (EMA, OneEuro) that face an
inherent jitter-lag tradeoff, N-euro learns to reduce both jitter and lag at the same time.

The name is an homage to the One Euro filter ("Neuro" network-based predictor).

### How It Works

1. **Input abstraction**: Raw sensor data (images/video) is first abstracted into univariate
   time series (e.g., 3D coordinates over time). This increases information density and
   reduces domain gap between different users/environments.

2. **Pseudo ground truth labeling**: Since noise-free tracking data is practically impossible
   to obtain, training labels are created by running a double-sided (bidirectional) moving
   average filter offline: `f(t) = (1/(2k+1)) * sum(L_{t+j}, j=-k..k)`. This filter can
   "see the future" so it has zero lag -- making it a good approximation of ground truth.

3. **Residual learning via two blocks**: The network processes the input through two
   sequential blocks. The first block learns the **trend** (general motion direction/speed),
   and the second block learns the **seasonality** (periodic features like walking bobbing).
   Each block produces a backcast (reconstruction of input) and a forecast (prediction).
   The residual from block 1 feeds into block 2. The final output sums both forecasts:
   `y_hat = y_hat_1 + y_hat_2`.

4. **Combined loss function**: `L = L_pos + alpha * L_acc`, where L_pos is position error
   (MAE between prediction and pseudo ground truth) and L_acc is acceleration error (MAE of
   second derivatives). The alpha parameter controls the smoothness-accuracy tradeoff.

## Architecture Details

Based on the paper's Figure 2 and Section 3.2:

```
Input: I x T time series (I = 1 or 3 spatial dims, T = temporal window length)
  |
  v
[Block 1: Trend]
  Fully Connected Layer (32 units, ReLU) --> theta_1
    |-> Trend Model (backcast) f^b(theta_1) --> x_hat_1
    |-> Trend Model (forecast) f^f(theta_1) --> y_hat_1
  Residual: x_2 = x_1 - x_hat_1
  |
  v
[Block 2: Seasonality]
  Fully Connected Layer (32 units, ReLU) --> theta_2
    |-> Seasonality Model (backcast) g^b(theta_2) --> x_hat_2
    |-> Seasonality Model (forecast) g^f(theta_2) --> y_hat_2
  |
  v
Final forecast: y_hat = y_hat_1 + y_hat_2
```

The architecture is based on **N-BEATS** (Neural Basis Expansion Analysis for Time Series),
but heavily pruned:
- Only 2 blocks (N-BEATS uses 30+)
- Only 32 hidden units per FC layer
- Block 1 uses trend basis functions, Block 2 uses seasonality basis functions
- No stacking of multiple basic blocks

### Model Size

| Metric | Value |
|--------|-------|
| Trainable parameters | **1,200 (1.2k)** |
| Model file size | **5 kB** |
| Comparison: LSTM baseline | 346k params, 1.45 MB (>200x larger) |

### Inference Time (MacBook Pro 2021)

| Method | Running Time |
|--------|-------------|
| Double Exponential Filter | 0.05 ms |
| One Euro Filter | 0.01 ms |
| LSTM | 2 ms |
| **N-euro** | **0.95 ms** |

The paper explicitly states: "We prune the network to contain the minimum number of
parameters while retaining the ability to predict and smooth motion trajectories, and it
can perform inferences within 1 ms."

## Training Requirements

### Data Collection
- 11 participants (4 female, 7 male), ages 20-57
- 5 minutes of motion per participant at 160Hz tracking rate
- Total: ~528k data frames (11 x 5 x 60 x 160)
- Participants performed varying speeds: slow walking (0.9 m/s avg), running/jumping (3.5 m/s avg)
- Speed range: 0.5 m/s to 6 m/s

### Hyperparameters
- Observation window length: **300 ms** (T)
- Prediction length: **50 ms** (configurable per application)
- Moving step: 1 frame
- At 40Hz: T = 12 frames, prediction = 2 frames
- At 160Hz: T = 48 frames, prediction = 8 frames

### Training Setup
- Framework: **PyTorch** (PyTorch Forecasting library)
- Optimizer: Adam, starting LR 0.0005, halved on plateau
- Batch size: 32
- Max epochs: 100, early stop at 30 epochs no improvement
- Training time: **~15 minutes** on MacBook Pro 2021
- Evaluation: leave-one-user-out cross-validation (train on 10 users, test on 1)

### Pseudo Ground Truth
- For 160Hz: double-sided moving average with window size k=3
- For 40Hz: double-sided filter with k=5 on 160Hz data, downsample, then k=3 on 40Hz data

## Performance vs. OneEuro Filter

### Quantitative (160Hz, all speeds combined)
| Metric | N-euro | OneEuro | Improvement |
|--------|--------|---------|-------------|
| MAE (prediction accuracy) | 3.9 cm | 9.2 cm | **57% better** (OneEuro has no prediction) |
| MAAE (smoothness) | 2.2 cm/s^2 | 2.2 cm/s^2 | **On par** |

### Quantitative (40Hz, all speeds combined)
| Metric | N-euro | OneEuro | Improvement |
|--------|--------|---------|-------------|
| MAE (prediction accuracy) | 5.8 cm | 9.2 cm | **37% better** |
| MAAE (smoothness) | 2.0 cm/s^2 | 2.1 cm/s^2 | **On par** |

### vs. Double Exponential (best predictive baseline)
- Prediction error reduced by **36%**
- Smoothness improved by **42%**

### Key Advantage: No Hyperparameter Tuning Per Speed
- Traditional filters need different parameters for low-speed vs. high-speed motion
- N-euro uses the **same model** for all speeds -- no per-application tuning needed
- At higher speeds, the advantage of N-euro becomes much more pronounced

### User Study Results (n=34)
- N-euro received the **highest overall user experience rating** in both VR and AR settings
- Statistically significant improvement over all baselines (p < 0.001)
- Lag reduction on par with double exponential filter
- Jitter on par with one euro filter (slightly higher in AR setting)

## Generalization

The paper tested N-euro (trained on head motion data) on:
1. **Hand motion** (waving, drinking, boxing via MediaPipe): avg prediction error 1.1 cm for
   waving/drinking, 27.6 cm for boxing (out-of-distribution speed)
2. **Cursor movement** (drawing shapes): avg prediction error 12.4 pixels, 47% error rate

Key insight: N-euro generalizes reasonably well to different motion types **without
retraining**, as long as the speed/acceleration distribution is similar to training data.

## Pre-trained Model Availability

### GitHub Repository
- **URL**: https://github.com/QijiaShao/N-euro
- **Status**: Placeholder repo with demo code
- **Contents**:
  - `N-euro-demo.ipynb` -- Jupyter notebook demo
  - `best_model_0202_small_model_10.pth` -- **Pre-trained PyTorch model available**
  - Modified `timeseries.py` for PyTorch Forecasting library
- **License**: Not specified
- **Dependency**: PyTorch Forecasting library (requires patching `timeseries.py`)

### What the Pre-trained Model Covers
The provided model was trained on head movement data at 40Hz. It would need to be
retrained or fine-tuned for blend shape time-series data.

## On-Device Runtime Options

### Option A: ONNX Runtime (Recommended)
- Export PyTorch model to ONNX format (straightforward for this simple architecture)
- Android: ONNX Runtime Mobile with NNAPI/XNNPACK acceleration
- iOS: ONNX Runtime Mobile with CoreML/XNNPACK acceleration
- KMP integration: Platform `expect/actual` wrappers around ONNX Runtime
- Existing KMP reference: [OnnxRuntimeKotlinMultiplatform](https://github.com/wh173d3v11/OnnxRuntimeKotlinMultiplatform)
- Also: [KInference](https://github.com/JetBrains-Research/kinference) -- pure Kotlin ONNX inference (no native deps)

### Option B: TFLite
- Convert PyTorch -> ONNX -> TFLite
- Android: TFLite with GPU/NNAPI delegate
- iOS: TFLite with CoreML delegate
- Extra conversion step adds complexity

### Option C: Pure Kotlin Reimplementation
- Model has only 1.2k parameters and uses simple operations (FC layers, ReLU, polynomial basis)
- Could be reimplemented as pure Kotlin math in `commonMain`
- **No external runtime dependency** -- biggest advantage
- The trend/seasonality basis functions are just polynomial/harmonic expansions
- Inference is just matrix multiplications + additions (~1200 multiply-accumulate ops)

### Recommendation: Option C (Pure Kotlin)
Given the extremely small model size (1.2k params, 5 kB), a pure Kotlin implementation
is the most practical approach:
- Zero additional dependencies (no ONNX Runtime, no TFLite)
- Works in `commonMain` -- same code on Android, iOS, JVM
- Consistent with existing filter implementations (OneEuro, EMA are pure Kotlin)
- Inference time will be well under 1 ms even without hardware acceleration
- Weight loading from bundled resource file (5 kB)

## Integration Approach for KMP-FaceLink

### 1. SmoothingConfig Extension

Add a new variant to the existing sealed class:

```kotlin
public data class NeuroPredictive(
    val observationWindowMs: Float = 300f,
    val predictionMs: Float = 50f,
    val smoothingAlpha: Float = 0.5f,  // L_acc weight
) : SmoothingConfig()
```

### 2. Implementation as BlendShapeSmoother

Create `NeuroPredictiveSmoother` implementing `BlendShapeSmoother`:
- Maintains a circular buffer of recent blend shape values per shape (window = T frames)
- On each `smooth()` call, runs the N-euro forward pass
- Returns smoothed + predicted blend shape values
- The forward pass is pure math: 2x FC(32) + trend/seasonality basis expansion

### 3. Model Weights

Two options:
- **Bundled weights**: Ship pre-trained weights as a Kotlin constant array (5 kB)
- **Downloadable weights**: Load from assets for swappable models

### 4. Training Pipeline (Separate Concern)

- Provide a Python script in `tools/` for users to train on their own data
- Export weights as JSON/binary that the Kotlin code can load
- Default weights trained on blend shape data from MediaPipe/ARKit tracking sessions

### 5. Adaptation for Blend Shapes

The paper processes 3D positions (I=3, x/y/z). For blend shapes:
- Each blend shape is a univariate time series (I=1)
- Window of T frames per shape
- Could process all 52 blend shapes independently (52 parallel inferences)
- Or batch them into a single inference (more efficient)

At I=1, T=12 (for 40Hz tracking), each blend shape inference is:
- Block 1: FC(12->32) + trend basis -> 12+2 outputs
- Block 2: FC(12->32) + seasonality basis -> 12+2 outputs
- Total: ~2400 multiply-accumulate operations per blend shape
- 52 blend shapes: ~125k multiply-accumulate operations total
- Well within the sub-1ms budget on any modern mobile CPU

## Feasibility Assessment

### Feasibility: HIGH

| Factor | Assessment |
|--------|-----------|
| Model size | Excellent -- 5 kB, 1.2k params |
| Inference time | Excellent -- < 1ms on laptop, sub-ms expected on mobile |
| Implementation complexity | Medium -- pure Kotlin math, ~200-300 lines |
| Training data | Medium -- need blend shape time series, can collect from existing tracking |
| Pre-trained model | Available for head motion; needs retraining for blend shapes |
| Runtime dependency | None if pure Kotlin (Option C) |
| Integration effort | Low -- fits existing BlendShapeSmoother interface cleanly |
| Risk: accuracy on blend shapes | Medium -- paper tested on 3D positions, not blend shapes directly |

### Key Risks

1. **Domain transfer**: The paper validates on 3D position trajectories. Blend shape values
   (0-1 range, 52 independent channels) have different dynamics. The model architecture
   should transfer well, but weights need retraining.

2. **Training data collection**: Need to collect blend shape time series with pseudo ground
   truth labels. This requires recording sessions at high frame rates.

3. **Per-shape vs. shared model**: The paper uses one model for all dimensions. For blend
   shapes, we may need category-aware models (similar to CategoryAwareSmoother) where
   mouth/eye/brow each have separately trained weights.

4. **Turning points**: The paper notes higher errors at direction-changing moments. For
   blend shapes, rapid expression changes (blink, smile onset) may show brief artifacts.

### Recommended Implementation Plan

1. **Phase 1 (Research validation)**: Port the PyTorch demo to pure Kotlin, verify
   inference correctness with the provided pre-trained weights
2. **Phase 2 (Data collection)**: Record blend shape time series from the sample app,
   create pseudo ground truth labels
3. **Phase 3 (Training)**: Train N-euro model on blend shape data, export weights
4. **Phase 4 (Integration)**: Add `SmoothingConfig.NeuroPredictive` and
   `NeuroPredictiveSmoother`, ship bundled weights
5. **Phase 5 (Benchmarking)**: Compare jitter/lag metrics vs. OneEuro on real device data

## References

- Shao, Q., Wang, J., Zhou, B., Tran, V.A., Krishnan, G., & Nayar, S. (2023).
  N-euro Predictor: A Neural Network Approach for Smoothing and Predicting Motion
  Trajectory. *Proc. ACM Interact. Mob. Wearable Ubiquittic Technol.* 7, 3, Article 120.
  https://doi.org/10.1145/3610884
- Paper PDF: https://jianwang-cmu.github.io/23Neuro/N_euro_predictor.pdf
- Reference implementation: https://github.com/QijiaShao/N-euro
- N-BEATS (base architecture): Oreshkin, B.N., et al. (2020). N-BEATS: Neural basis
  expansion analysis for interpretable time series forecasting. *ICLR 2020*.
- KInference (pure Kotlin ONNX): https://github.com/JetBrains-Research/kinference
- ONNX Runtime KMP: https://github.com/wh173d3v11/OnnxRuntimeKotlinMultiplatform
