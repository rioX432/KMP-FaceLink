# GCN-Based Blendshape Retargeting Research

**Issue:** #603
**Status:** Research complete
**Date:** 2026-04-06

## Executive Summary

This document evaluates Graph Convolutional Network (GCN) approaches for blendshape retargeting and their applicability to KMP-FaceLink's existing `BlendShapeEnhancer` pipeline. Two key papers were analyzed: (1) a GCN-VAE framework that directly predicts 51 ARKit blendshape coefficients from 3D mesh deformations, and (2) a GAN-based unsupervised GCN retargeting framework using shared latent spaces.

**Key finding:** GCN retargeting operates on a fundamentally different problem (3D mesh → 3D mesh transfer between characters) than KMP-FaceLink's pipeline (2D/3D landmarks → blendshape coefficients). Direct adoption is **not recommended**. However, a lightweight GCN-inspired approach could replace the hand-tuned `ExpressionCorrelator` rules with a learned AU co-activation model, offering modest accuracy gains at acceptable latency cost.

**Recommendation:** Do not pursue full GCN retargeting. Instead, consider a small learned correlation model (MLP or 1-layer GCN on the 52-node blendshape graph) as a future `ExpressionCorrelator` replacement once ground-truth ARKit data is available for training.

---

## 1. Paper Analysis

### 1.1 Paper A: "A new blendshape-based retargeting for 3D facial expression" (2025)

**Source:** International Journal of Computers and Applications, DOI: 10.1080/1206212X.2025.2571553

#### Architecture

- **Encoder:** 7 GConv layers + 2 FC layers, compressing 3D mesh vertices into a latent vector
- **Decoder:** FC layer maps latent vector to 51 blendshape coefficients
- **Framework:** Graph VAE with KL-divergence regularization for smooth latent space
- **Input:** Combined deformation gradients + vertex displacements from rest pose
- **Output:** 51 ARKit-compatible blendshape coefficients
- **Training:** Two-stage learning with per-blendshape mask loss

#### Key Innovation

The GCN-VAE encodes a source 3D face mesh into a latent space, then decodes to blendshape weights rather than target mesh vertices. This makes the output avatar-independent — any target rigged with ARKit blendshapes can be driven without retraining.

#### Limitations

- Requires 3D mesh input (not 2D landmarks)
- Training requires 4D scan dataset (Multiface)
- No mobile inference benchmarks reported
- Desktop training: ~20 hours on RTX 3080

### 1.2 Paper B: "Facial Animation Retargeting by Unsupervised Learning of GCN" (Dou & Mukai, 2024)

**Source:** NicoInt 2024, Tokyo Metropolitan University / PlatinumGames

#### Architecture

- **Dual autoencoders:** E_A/D_A for source character, E_B/D_B for target character
- **Shared latent space** via landmark-based graph pooling/unpooling
- **Encoder:** GConv layers: V×3 → V×128 → V×64 → V×32, then graph pooling to K×32 → K×16 → K×16 → K×3, where V=vertices (~3200), K=88 landmarks
- **Decoder:** Mirror architecture with graph unpooling
- **Loss:** L_rec + L_cyc + L_adv (reconstruction + cycle consistency + adversarial)
- **GAN:** Two discriminators C_A, C_B for adversarial training

#### Graph Construction

- 88 facial landmarks define the graph nodes
- Each mesh vertex assigned to nearest landmark (Euclidean)
- Graph pooling: average motion of vertices assigned to each landmark
- Graph unpooling: distribute landmark features to assigned vertices via transposed assignment matrix

#### Results

- Successfully transfers simple expressions (mouth open, cheek raise)
- Fails on complex/combined expressions (smile + scrunch nose)
- Jaggy artifacts from lack of spatial smoothness in loss
- No quantitative metrics reported (qualitative only)

---

## 2. Applicability to KMP-FaceLink

### 2.1 Current Pipeline Architecture

```
Camera Frame
  → MediaPipe FaceLandmarker (478 landmarks + 52 blendshapes)
  → BlendShapeEnhancer
    → Step 1: Geometric solver override (LandmarkSolvers, MouthSolvers, CheekBrowSolvers)
    → Step 1.5: ExpressionCorrelator (FACS AU co-activation rules)
    → Step 2: Sensitivity scaling
    → Step 3: Dead zone threshold
  → BlendShapeData (52 ARKit coefficients)
```

On iOS, ARKit directly provides high-quality blendshapes — `BlendShapeEnhancer` is only used on Android/JVM where MediaPipe's ML output needs correction.

### 2.2 Problem Domain Mismatch

| Aspect | GCN Retargeting Papers | KMP-FaceLink |
|---|---|---|
| **Input** | 3D mesh vertices (V×3, V>3000) | 478 2D/3D landmarks + 52 ML blendshapes |
| **Output** | Target mesh vertices or blendshape weights | Corrected blendshape weights |
| **Problem** | Cross-character expression transfer | Single-character blendshape refinement |
| **Topology** | Full mesh adjacency graph | Sparse landmark set (no mesh) |
| **Training data** | 4D scan datasets (Multiface) | No ground-truth dataset available |
| **Inference target** | Desktop GPU (RTX 3080) | Mobile CPU/GPU (real-time, <5ms) |

The papers solve mesh-to-mesh retargeting between different character models. KMP-FaceLink's problem is correcting MediaPipe's blendshape output using geometric landmarks — a much simpler signal refinement task.

### 2.3 What GCN Could Replace

The only component where a learned model could meaningfully improve over the current approach is the **ExpressionCorrelator**:

| Component | Current Approach | GCN Potential | Verdict |
|---|---|---|---|
| Geometric solvers | Hand-tuned IOD-normalized ratios | GCN overkill for ratio computation | **No benefit** |
| ExpressionCorrelator | 10 hand-coded FACS rules | Learned AU co-activation graph | **Possible improvement** |
| Sensitivity scaling | Per-shape multiplier map | Could be learned | **Marginal benefit** |
| Dead zone threshold | Per-shape threshold map | Not applicable | **No benefit** |

---

## 3. GCN-Inspired Architecture Proposal (If Pursued)

### 3.1 Lightweight Blendshape Correlation Network

A 52-node graph where each node is a blendshape and edges encode FACS AU relationships:

```
Input: 52 blendshape values (from MediaPipe + geometric solvers)
  → 1-layer GCN (52 nodes, adjacency from FACS AU pairs)
    → Node features: [blendshape_value, geometric_value, confidence]
    → Hidden dim: 16
    → Output: 52 refined blendshape values
  → Clamped to [0, 1]
```

**Estimated model size:** ~13K parameters (52×3×16 + 52×16×1 + biases)
**Estimated inference:** <0.5ms on mobile CPU (comparable to current ExpressionCorrelator)

### 3.2 Graph Construction

```
Nodes: 52 ARKit blendshapes
Edges (bidirectional):
  - FACS AU pairs (smile↔cheekSquint, sneer↔browDown, etc.)
  - Symmetric pairs (left↔right for each bilateral shape)
  - Anatomical neighbors (jawOpen↔mouthClose, etc.)
Adjacency: ~120 edges, sparse matrix
```

### 3.3 Training Data Requirements

This is the **critical blocker**. Training requires paired data:
- **Input:** MediaPipe blendshape values (what we have now)
- **Ground truth:** ARKit blendshape values (from iOS device)

**Collection approach:** Record simultaneous MediaPipe (Android) + ARKit (iOS) sessions of the same face performing diverse expressions. Align by timestamp. ~10K frames minimum.

### 3.4 Integration Point

```kotlin
// BlendShapeEnhancer.kt — proposed change
sealed class CorrelationStrategy {
    object Rules : CorrelationStrategy()     // Current ExpressionCorrelator
    class Learned(val model: OnnxModel) : CorrelationStrategy()  // GCN model
}
```

The GCN model would slot in at Step 1.5, replacing `ExpressionCorrelator.apply()` with a single ONNX inference call. The rest of the pipeline (geometric solvers, sensitivity, dead zone) remains unchanged.

---

## 4. Latency Analysis

### 4.1 Current Pipeline Timing (per frame)

| Stage | Estimated Time |
|---|---|
| MediaPipe FaceLandmarker | ~8-15ms |
| Geometric solvers (25 solvers) | ~0.1ms |
| ExpressionCorrelator (10 rules) | ~0.01ms |
| Sensitivity + Dead zone | ~0.01ms |
| **Total enhancement** | **~0.12ms** |

### 4.2 GCN Model Inference Estimates

| Model Variant | Params | Mobile CPU | Mobile GPU/NPU |
|---|---|---|---|
| 1-layer GCN (proposed) | ~13K | <0.5ms | <0.2ms |
| 3-layer GCN | ~50K | ~1-2ms | <0.5ms |
| Full VAE (Paper A) | >500K | ~5-10ms | ~2-5ms |
| Full dual AE (Paper B) | >2M | ~15-30ms | ~5-10ms |

The 1-layer GCN stays within budget. The full paper architectures would consume too much of the frame budget on mobile.

---

## 5. Comparison: Current vs. GCN Approaches

### 5.1 Accuracy (Qualitative Assessment)

| Expression | Current Geometric+Rules | GCN (Projected) |
|---|---|---|
| Basic smile | Good (solver + correlation boost) | Potentially better correlation |
| Cheek puff | Fair (noisy, high sensitivity needed) | No improvement without mesh data |
| Brow raise/lower | Good (CheekBrowSolvers) | Marginal improvement |
| Complex combos | Limited (only 10 correlation rules) | Better if trained on diverse data |
| Asymmetric expressions | Good (per-side solvers) | Better with graph structure |

### 5.2 Maintainability

| Aspect | Current | GCN |
|---|---|---|
| Tuning | Manual threshold editing | Retrain with new data |
| Debuggability | Transparent (read the code) | Black-box model |
| New expressions | Add solver function + rule | Collect data + retrain |
| Platform support | Pure Kotlin (commonMain) | ONNX Runtime dependency |
| Binary size impact | 0 bytes | ~50-100KB (model + runtime) |

---

## 6. Recommendation

### Do Not Pursue (Full GCN Retargeting)

1. **Wrong problem domain:** The papers solve mesh-to-mesh retargeting. KMP-FaceLink does landmark-to-blendshape refinement.
2. **No training data:** No paired MediaPipe/ARKit dataset exists. Creating one requires synchronized multi-device recording infrastructure.
3. **Latency risk:** Full GCN architectures from the papers exceed the mobile frame budget.
4. **Current approach works well:** The geometric solvers + ExpressionCorrelator pipeline covers the most impactful corrections. The remaining accuracy gap is in MediaPipe's base ML model, which GCN post-processing cannot fix.

### Consider Later (Learned Correlation Model)

If paired MediaPipe/ARKit training data becomes available (e.g., from Issue #597 neural lip sync data collection), a lightweight learned model replacing `ExpressionCorrelator` could be worthwhile:

- **Phase 1:** Collect paired data via simultaneous Android + iOS recording
- **Phase 2:** Train 1-layer GCN or small MLP (~13K params)
- **Phase 3:** Export to ONNX, integrate as optional `CorrelationStrategy`
- **Phase 4:** A/B test against current rule-based correlator

### Immediate Actionable Improvements (No ML Required)

Instead of pursuing GCN, the following improvements to the existing pipeline would yield better ROI:

1. **Add more ExpressionCorrelator rules** — currently only 10 rules covering 5 AU combinations. FACS literature documents 30+ reliable co-activation patterns.
2. **Temporal smoothing** — add per-shape exponential moving average to reduce jitter (see `neuro-predictor-smoothing.md`).
3. **Per-user calibration** — store neutral-pose baseline per user to improve geometric solver accuracy (the `NEUTRAL_CHEEK_IOD_RATIO` and `NEUTRAL_MOUTH_HALF_IOD_RATIO` constants are currently hardcoded averages).

---

## References

1. "A new blendshape-based retargeting for 3D facial expression" — Int. J. Computers and Applications, 2025. DOI: 10.1080/1206212X.2025.2571553
2. Dou & Mukai, "Facial Animation Retargeting by Unsupervised Learning of Graph Convolutional Networks" — NicoInt 2024, Tokyo Metropolitan University / PlatinumGames
3. Zhang et al., "Facial expression retargeting from human to avatar made easy" — IEEE TVCG 2020
4. Kim et al., "Deep Learning-Based Unsupervised Human Facial Retargeting" — Computer Graphics Forum 2021
5. Qin et al., "Neural face rigging for animating and retargeting facial meshes in the wild" — arXiv 2023
