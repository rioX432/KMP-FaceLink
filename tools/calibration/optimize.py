#!/usr/bin/env python3
"""
Automated parameter sweep for BlendShapeEnhancer defaults.

Uses scipy.optimize.differential_evolution to find optimal values for
geometricBlendWeight, per-shape sensitivityOverrides, and per-shape
deadZoneOverrides by minimizing weighted MAE against ARKit ground truth.

Usage:
    python optimize.py --captures captures/ --output optimized_params.json
    python optimize.py --dry-run
    python optimize.py --captures captures/ --validate

Requirements:
    pip install -r requirements.txt
"""

from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
from scipy.optimize import differential_evolution

# ── Constants ──

# Shapes enhanced by the near-zero strategy (max(geometric, ml))
NEAR_ZERO_SHAPES: list[str] = [
    "cheekPuff",
    "cheekSquintLeft",
    "cheekSquintRight",
    "eyeWideLeft",
    "eyeWideRight",
    "noseSneerLeft",
    "noseSneerRight",
]

# Shapes enhanced by the low-accuracy strategy (weighted blend)
LOW_ACCURACY_SHAPES: list[str] = [
    "browDownLeft",
    "browDownRight",
    "browInnerUp",
    "browOuterUpLeft",
    "browOuterUpRight",
    "jawForward",
    "jawLeft",
    "jawRight",
    "mouthDimpleLeft",
    "mouthDimpleRight",
    "mouthSmileLeft",
    "mouthSmileRight",
    "mouthFrownLeft",
    "mouthFrownRight",
    "mouthStretchLeft",
    "mouthStretchRight",
    "mouthPressLeft",
    "mouthPressRight",
]

# All enhanced shapes (union of near-zero and low-accuracy)
ALL_ENHANCED_SHAPES: list[str] = NEAR_ZERO_SHAPES + LOW_ACCURACY_SHAPES

# Shapes that also receive sensitivity scaling and dead zone in the Kotlin defaults.
# These are the shapes present in defaultSensitivityMap/defaultDeadZoneMap.
SENSITIVITY_SHAPES: list[str] = [
    "cheekPuff",
    "cheekSquintLeft",
    "cheekSquintRight",
    "browDownLeft",
    "browDownRight",
    "browInnerUp",
    "browOuterUpLeft",
    "browOuterUpRight",
    "eyeWideLeft",
    "eyeWideRight",
    "noseSneerLeft",
    "noseSneerRight",
    "jawForward",
    "jawLeft",
    "jawRight",
    "mouthDimpleLeft",
    "mouthDimpleRight",
    "mouthSmileLeft",
    "mouthSmileRight",
    "mouthFrownLeft",
    "mouthFrownRight",
    "mouthStretchLeft",
    "mouthStretchRight",
    "mouthPressLeft",
    "mouthPressRight",
    "eyeBlinkLeft",
    "eyeBlinkRight",
]

# FACS co-activation rules mirroring ExpressionCorrelator.kt defaultRules
CORRELATION_RULES: list[dict] = [
    {"source": "mouthSmileLeft", "target": "cheekSquintLeft", "threshold": 0.3, "factor": 0.25},
    {"source": "mouthSmileRight", "target": "cheekSquintRight", "threshold": 0.3, "factor": 0.25},
    {"source": "mouthSmileLeft", "target": "mouthStretchLeft", "threshold": 0.5, "factor": 0.15},
    {"source": "mouthSmileRight", "target": "mouthStretchRight", "threshold": 0.5, "factor": 0.15},
    {"source": "noseSneerLeft", "target": "browDownLeft", "threshold": 0.3, "factor": 0.20},
    {"source": "noseSneerRight", "target": "browDownRight", "threshold": 0.3, "factor": 0.20},
    {"source": "browInnerUp", "target": "browDownLeft", "threshold": 0.4, "factor": -0.15},
    {"source": "browInnerUp", "target": "browDownRight", "threshold": 0.4, "factor": -0.15},
    {"source": "mouthUpperUpLeft", "target": "noseSneerLeft", "threshold": 0.3, "factor": 0.20},
    {"source": "mouthUpperUpRight", "target": "noseSneerRight", "threshold": 0.3, "factor": 0.20},
]

# Current Kotlin defaults for comparison
CURRENT_DEFAULTS = {
    "geometricBlendWeight": 0.7,
    "sensitivity": {
        "cheekPuff": 3.0, "cheekSquintLeft": 2.5, "cheekSquintRight": 2.5,
        "browDownLeft": 1.8, "browDownRight": 1.8, "browInnerUp": 1.5,
        "browOuterUpLeft": 1.5, "browOuterUpRight": 1.5,
        "eyeWideLeft": 2.5, "eyeWideRight": 2.5,
        "noseSneerLeft": 2.5, "noseSneerRight": 2.5,
        "jawForward": 2.0, "jawLeft": 2.0, "jawRight": 2.0,
        "mouthDimpleLeft": 2.0, "mouthDimpleRight": 2.0,
        "mouthSmileLeft": 2.0, "mouthSmileRight": 2.0,
        "mouthFrownLeft": 2.0, "mouthFrownRight": 2.0,
        "mouthStretchLeft": 2.0, "mouthStretchRight": 2.0,
        "mouthPressLeft": 2.0, "mouthPressRight": 2.0,
        "eyeBlinkLeft": 1.5, "eyeBlinkRight": 1.5,
    },
    "deadZone": {
        "cheekPuff": 0.03, "cheekSquintLeft": 0.03, "cheekSquintRight": 0.03,
        "browDownLeft": 0.02, "browDownRight": 0.02, "browInnerUp": 0.02,
        "browOuterUpLeft": 0.02, "browOuterUpRight": 0.02,
        "eyeWideLeft": 0.02, "eyeWideRight": 0.02,
        "noseSneerLeft": 0.03, "noseSneerRight": 0.03,
        "jawForward": 0.02, "jawLeft": 0.02, "jawRight": 0.02,
        "mouthDimpleLeft": 0.02, "mouthDimpleRight": 0.02,
        "mouthSmileLeft": 0.02, "mouthSmileRight": 0.02,
        "mouthFrownLeft": 0.02, "mouthFrownRight": 0.02,
        "mouthStretchLeft": 0.02, "mouthStretchRight": 0.02,
        "mouthPressLeft": 0.02, "mouthPressRight": 0.02,
        "eyeBlinkLeft": 0.02, "eyeBlinkRight": 0.02,
    },
}

# Validation split ratio
VALIDATION_SPLIT = 0.2

# Random seed for reproducibility
RANDOM_SEED = 42


# ── Data Loading ──

def load_all_sessions(
    captures_dir: Path,
) -> tuple[dict[str, np.ndarray], dict[str, np.ndarray]]:
    """Load and concatenate all session data into per-blendshape arrays.

    Returns:
        (arkit_arrays, mediapipe_arrays) where each is a dict mapping
        blendshape name to a numpy array of values.
    """
    # Import benchmark utilities lazily (requires pandas + benchmark.py)
    from benchmark import find_common_blendshapes, load_session

    session_dirs = sorted(
        d for d in captures_dir.iterdir()
        if d.is_dir() and d.name.startswith("session_")
    )

    if not session_dirs:
        print(f"Error: no session_* directories found in {captures_dir}", file=sys.stderr)
        sys.exit(1)

    all_arkit: dict[str, list[float]] = {}
    all_mediapipe: dict[str, list[float]] = {}
    total_sessions = 0
    total_frames = 0

    for session_dir in session_dirs:
        result = load_session(session_dir)
        if result is None:
            continue

        arkit_df, mediapipe_df = result
        blendshapes = find_common_blendshapes(arkit_df, mediapipe_df)

        for bs in blendshapes:
            if bs not in all_arkit:
                all_arkit[bs] = []
                all_mediapipe[bs] = []
            all_arkit[bs].extend(arkit_df[bs].tolist())
            all_mediapipe[bs].extend(mediapipe_df[bs].tolist())

        total_sessions += 1
        total_frames += len(arkit_df)

    if total_sessions == 0:
        print("Error: no valid sessions found", file=sys.stderr)
        sys.exit(1)

    print(f"Loaded {total_sessions} sessions, {total_frames} total frames")

    arkit_arrays = {bs: np.array(vals) for bs, vals in all_arkit.items()}
    mediapipe_arrays = {bs: np.array(vals) for bs, vals in all_mediapipe.items()}

    return arkit_arrays, mediapipe_arrays


def generate_synthetic_data(
    n_frames: int = 3000,
    seed: int = RANDOM_SEED,
) -> tuple[dict[str, np.ndarray], dict[str, np.ndarray]]:
    """Generate synthetic ARKit/MediaPipe paired data for dry-run testing.

    Creates realistic-looking blendshape data where MediaPipe values are a
    noisy, attenuated version of ARKit ground truth, mimicking the real
    accuracy gap the enhancer is designed to correct.
    """
    rng = np.random.default_rng(seed)

    # All 52 ARKit blendshape names in camelCase
    all_shapes = [
        "eyeBlinkLeft", "eyeBlinkRight", "eyeLookDownLeft", "eyeLookDownRight",
        "eyeLookInLeft", "eyeLookInRight", "eyeLookOutLeft", "eyeLookOutRight",
        "eyeLookUpLeft", "eyeLookUpRight", "eyeSquintLeft", "eyeSquintRight",
        "eyeWideLeft", "eyeWideRight",
        "jawForward", "jawLeft", "jawOpen", "jawRight",
        "mouthClose", "mouthDimpleLeft", "mouthDimpleRight", "mouthFrownLeft",
        "mouthFrownRight", "mouthFunnel", "mouthLeft", "mouthLowerDownLeft",
        "mouthLowerDownRight", "mouthPressLeft", "mouthPressRight",
        "mouthPucker", "mouthRight", "mouthRollLower", "mouthRollUpper",
        "mouthShrugLower", "mouthShrugUpper", "mouthSmileLeft", "mouthSmileRight",
        "mouthStretchLeft", "mouthStretchRight", "mouthUpperUpLeft",
        "mouthUpperUpRight",
        "browDownLeft", "browDownRight", "browInnerUp", "browOuterUpLeft",
        "browOuterUpRight",
        "cheekPuff", "cheekSquintLeft", "cheekSquintRight",
        "noseSneerLeft", "noseSneerRight",
        "tongueOut",
    ]

    arkit_arrays: dict[str, np.ndarray] = {}
    mediapipe_arrays: dict[str, np.ndarray] = {}

    for shape in all_shapes:
        # Generate ARKit ground truth: sparse activations with occasional bursts
        base = rng.beta(0.5, 5.0, size=n_frames).astype(np.float64)

        # Near-zero shapes: ARKit has clear signal, MediaPipe is mostly zero
        if shape in NEAR_ZERO_SHAPES:
            arkit_vals = base * 0.8
            # MediaPipe almost always returns near-zero for these
            mp_vals = arkit_vals * rng.uniform(0.0, 0.15, size=n_frames)
            mp_vals += rng.normal(0, 0.01, size=n_frames)

        # Low-accuracy shapes: MediaPipe has signal but is noisy/attenuated
        elif shape in LOW_ACCURACY_SHAPES:
            arkit_vals = base * 0.9
            attenuation = rng.uniform(0.3, 0.7)
            mp_vals = arkit_vals * attenuation + rng.normal(0, 0.05, size=n_frames)

        # Other shapes: reasonable accuracy
        else:
            arkit_vals = base * 0.7
            mp_vals = arkit_vals * rng.uniform(0.7, 0.95) + rng.normal(0, 0.02, size=n_frames)

        arkit_arrays[shape] = np.clip(arkit_vals, 0.0, 1.0)
        mediapipe_arrays[shape] = np.clip(mp_vals, 0.0, 1.0)

    print(f"Generated synthetic data: {n_frames} frames, {len(all_shapes)} blendshapes")
    return arkit_arrays, mediapipe_arrays


def train_test_split(
    arkit: dict[str, np.ndarray],
    mediapipe: dict[str, np.ndarray],
    test_ratio: float = VALIDATION_SPLIT,
    seed: int = RANDOM_SEED,
) -> tuple[
    dict[str, np.ndarray], dict[str, np.ndarray],
    dict[str, np.ndarray], dict[str, np.ndarray],
]:
    """Split data into train/test sets.

    Returns:
        (arkit_train, mediapipe_train, arkit_test, mediapipe_test)
    """
    # Get the number of frames from any blendshape (all have the same length)
    any_key = next(iter(arkit))
    n = len(arkit[any_key])

    rng = np.random.default_rng(seed)
    indices = rng.permutation(n)
    split_idx = int(n * (1 - test_ratio))

    train_idx = indices[:split_idx]
    test_idx = indices[split_idx:]

    arkit_train = {bs: vals[train_idx] for bs, vals in arkit.items()}
    mediapipe_train = {bs: vals[train_idx] for bs, vals in mediapipe.items()}
    arkit_test = {bs: vals[test_idx] for bs, vals in arkit.items()}
    mediapipe_test = {bs: vals[test_idx] for bs, vals in mediapipe.items()}

    return arkit_train, mediapipe_train, arkit_test, mediapipe_test


# ── Enhancer Simulation ──

def simulate_enhancer(
    mediapipe: dict[str, np.ndarray],
    geometric_blend_weight: float,
    sensitivity_overrides: dict[str, float],
    dead_zone_overrides: dict[str, float],
    arkit: dict[str, np.ndarray] | None = None,
) -> dict[str, np.ndarray]:
    """Simulate the Kotlin BlendShapeEnhancer pipeline in Python.

    Mirrors the processing order from BlendShapeEnhancer.kt:
    1. Geometric solver override (near-zero: max, low-accuracy: weighted blend)
    2. Expression correlation (FACS co-activation rules)
    3. Sensitivity scaling
    4. Dead zone threshold

    Since we don't have landmark data to run actual geometric solvers, we use
    ARKit values as a proxy for the geometric solver output. This is valid
    because the geometric solvers are designed to approximate ARKit behavior.

    When arkit is None (no ground truth available), geometric override is
    skipped and only sensitivity + dead zone are applied.
    """
    # Start with a copy of mediapipe values
    result: dict[str, np.ndarray] = {
        bs: vals.copy() for bs, vals in mediapipe.items()
    }

    # Step 1: Geometric solver override (using ARKit as geometric solver proxy)
    if arkit is not None:
        # Near-zero shapes: max(geometric, ml)
        for shape in NEAR_ZERO_SHAPES:
            if shape in result and shape in arkit:
                result[shape] = np.clip(
                    np.maximum(arkit[shape], result[shape]), 0.0, 1.0
                )

        # Low-accuracy shapes: weighted blend
        w = geometric_blend_weight
        for shape in LOW_ACCURACY_SHAPES:
            if shape in result and shape in arkit:
                result[shape] = np.clip(
                    w * arkit[shape] + (1.0 - w) * result[shape], 0.0, 1.0
                )

    # Step 2: Expression correlation (FACS co-activation)
    for rule in CORRELATION_RULES:
        src = rule["source"]
        tgt = rule["target"]
        threshold = rule["threshold"]
        factor = rule["factor"]
        if src in result and tgt in result:
            source_vals = result[src]
            mask = source_vals > threshold
            boost = (source_vals - threshold) * factor * mask
            result[tgt] = np.clip(result[tgt] + boost, 0.0, 1.0)

    # Step 3: Sensitivity scaling
    for shape, multiplier in sensitivity_overrides.items():
        if shape in result:
            result[shape] = np.clip(result[shape] * multiplier, 0.0, 1.0)

    # Step 4: Dead zone threshold
    for shape, threshold in dead_zone_overrides.items():
        if shape in result:
            result[shape] = np.where(result[shape] < threshold, 0.0, result[shape])

    return result


# ── Optimization ──

def identify_worst_shapes(
    arkit: dict[str, np.ndarray],
    mediapipe: dict[str, np.ndarray],
    n_worst: int = 10,
) -> list[str]:
    """Identify the N shapes with highest MAE among enhanced shapes.

    Only considers shapes that are actually in both datasets.
    """
    mae_scores: list[tuple[str, float]] = []

    for shape in SENSITIVITY_SHAPES:
        if shape in arkit and shape in mediapipe:
            mae = float(np.mean(np.abs(arkit[shape] - mediapipe[shape])))
            mae_scores.append((shape, mae))

    mae_scores.sort(key=lambda x: x[1], reverse=True)
    worst = [shape for shape, _ in mae_scores[:n_worst]]
    return worst


def build_parameter_vector(
    worst_shapes: list[str],
) -> tuple[list[tuple[float, float]], list[str]]:
    """Build optimizer bounds and parameter names.

    Parameter vector layout:
    [0]: geometricBlendWeight (0.3-1.0)
    [1..N]: sensitivity overrides for worst shapes (0.5-5.0)
    [N+1..2N]: dead zone overrides for worst shapes (0.0-0.1)

    Returns:
        (bounds, param_names)
    """
    bounds: list[tuple[float, float]] = []
    param_names: list[str] = []

    # geometricBlendWeight
    bounds.append((0.3, 1.0))
    param_names.append("geometricBlendWeight")

    # Per-shape sensitivity overrides
    for shape in worst_shapes:
        bounds.append((0.5, 5.0))
        param_names.append(f"sensitivity_{shape}")

    # Per-shape dead zone overrides
    for shape in worst_shapes:
        bounds.append((0.0, 0.1))
        param_names.append(f"deadZone_{shape}")

    return bounds, param_names


def decode_params(
    x: np.ndarray,
    worst_shapes: list[str],
) -> tuple[float, dict[str, float], dict[str, float]]:
    """Decode optimizer parameter vector into named parameters."""
    n = len(worst_shapes)

    geometric_blend_weight = float(x[0])

    sensitivity_overrides = dict(CURRENT_DEFAULTS["sensitivity"])
    for i, shape in enumerate(worst_shapes):
        sensitivity_overrides[shape] = float(x[1 + i])

    dead_zone_overrides = dict(CURRENT_DEFAULTS["deadZone"])
    for i, shape in enumerate(worst_shapes):
        dead_zone_overrides[shape] = float(x[1 + n + i])

    return geometric_blend_weight, sensitivity_overrides, dead_zone_overrides


def weighted_mae(
    arkit: dict[str, np.ndarray],
    enhanced: dict[str, np.ndarray],
    worst_shapes: list[str],
) -> float:
    """Compute weighted MAE across all enhanced shapes.

    Worst shapes receive 3x weight to prioritize their improvement.
    """
    total_error = 0.0
    total_weight = 0.0

    for shape in SENSITIVITY_SHAPES:
        if shape not in arkit or shape not in enhanced:
            continue

        mae = float(np.mean(np.abs(arkit[shape] - enhanced[shape])))
        weight = 3.0 if shape in worst_shapes else 1.0
        total_error += mae * weight
        total_weight += weight

    if total_weight == 0:
        return 1.0

    return total_error / total_weight


def make_objective(
    arkit: dict[str, np.ndarray],
    mediapipe: dict[str, np.ndarray],
    worst_shapes: list[str],
):
    """Create the objective function for differential_evolution."""
    call_count = [0]

    def objective(x: np.ndarray) -> float:
        call_count[0] += 1

        geo_w, sens, dz = decode_params(x, worst_shapes)

        enhanced = simulate_enhancer(
            mediapipe=mediapipe,
            geometric_blend_weight=geo_w,
            sensitivity_overrides=sens,
            dead_zone_overrides=dz,
            arkit=arkit,
        )

        return weighted_mae(arkit, enhanced, worst_shapes)

    return objective, call_count


def compute_per_shape_mae(
    arkit: dict[str, np.ndarray],
    values: dict[str, np.ndarray],
) -> dict[str, float]:
    """Compute per-shape MAE for all enhanced shapes."""
    result: dict[str, float] = {}
    for shape in SENSITIVITY_SHAPES:
        if shape in arkit and shape in values:
            result[shape] = float(np.mean(np.abs(arkit[shape] - values[shape])))
    return result


def run_optimization(
    arkit: dict[str, np.ndarray],
    mediapipe: dict[str, np.ndarray],
    n_worst: int = 10,
    max_iterations: int = 200,
    population_size: int = 30,
    seed: int = RANDOM_SEED,
) -> dict:
    """Run differential evolution optimization.

    Returns a dict with optimized parameters and metadata.
    """
    worst_shapes = identify_worst_shapes(arkit, mediapipe, n_worst)
    print(f"\nWorst {len(worst_shapes)} shapes by MAE (optimization targets):")
    for i, shape in enumerate(worst_shapes, 1):
        mae = float(np.mean(np.abs(arkit[shape] - mediapipe[shape])))
        print(f"  {i}. {shape} (MAE: {mae:.4f})")

    bounds, param_names = build_parameter_vector(worst_shapes)
    objective, call_count = make_objective(arkit, mediapipe, worst_shapes)

    # Compute baseline (current defaults) MAE
    baseline_enhanced = simulate_enhancer(
        mediapipe=mediapipe,
        geometric_blend_weight=CURRENT_DEFAULTS["geometricBlendWeight"],
        sensitivity_overrides=CURRENT_DEFAULTS["sensitivity"],
        dead_zone_overrides=CURRENT_DEFAULTS["deadZone"],
        arkit=arkit,
    )
    baseline_mae = weighted_mae(arkit, baseline_enhanced, worst_shapes)
    baseline_per_shape = compute_per_shape_mae(arkit, baseline_enhanced)
    print(f"\nBaseline weighted MAE (current defaults): {baseline_mae:.6f}")

    # Also compute raw mediapipe MAE (no enhancement)
    raw_mae = weighted_mae(arkit, mediapipe, worst_shapes)
    print(f"Raw MediaPipe weighted MAE (no enhancement): {raw_mae:.6f}")

    print(f"\nRunning differential evolution ({len(bounds)} params, "
          f"pop={population_size}, maxiter={max_iterations})...")

    result = differential_evolution(
        objective,
        bounds=bounds,
        seed=seed,
        maxiter=max_iterations,
        popsize=population_size,
        tol=1e-6,
        mutation=(0.5, 1.5),
        recombination=0.9,
        disp=True,
        polish=True,
    )

    geo_w, sens, dz = decode_params(result.x, worst_shapes)

    # Compute optimized per-shape MAE
    optimized_enhanced = simulate_enhancer(
        mediapipe=mediapipe,
        geometric_blend_weight=geo_w,
        sensitivity_overrides=sens,
        dead_zone_overrides=dz,
        arkit=arkit,
    )
    optimized_mae = weighted_mae(arkit, optimized_enhanced, worst_shapes)
    optimized_per_shape = compute_per_shape_mae(arkit, optimized_enhanced)

    print(f"\nOptimization complete after {call_count[0]} evaluations")
    print(f"  Baseline weighted MAE: {baseline_mae:.6f}")
    print(f"  Optimized weighted MAE: {optimized_mae:.6f}")
    improvement = (baseline_mae - optimized_mae) / baseline_mae * 100
    print(f"  Improvement: {improvement:.1f}%")

    # Build output
    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    any_key = next(iter(arkit))
    n_frames = len(arkit[any_key])

    # Only include overrides that differ from current defaults
    sensitivity_changes: dict[str, float] = {}
    dead_zone_changes: dict[str, float] = {}
    for shape in worst_shapes:
        sensitivity_changes[shape] = round(sens[shape], 3)
        dead_zone_changes[shape] = round(dz[shape], 4)

    # Per-shape improvement breakdown
    per_shape_improvement: dict[str, dict[str, float]] = {}
    for shape in worst_shapes:
        if shape in baseline_per_shape and shape in optimized_per_shape:
            before = baseline_per_shape[shape]
            after = optimized_per_shape[shape]
            per_shape_improvement[shape] = {
                "before_mae": round(before, 6),
                "after_mae": round(after, 6),
                "improvement_pct": round(
                    (before - after) / before * 100 if before > 0 else 0.0, 1
                ),
            }

    output = {
        "geometricBlendWeight": round(geo_w, 4),
        "sensitivityOverrides": sensitivity_changes,
        "deadZoneOverrides": dead_zone_changes,
        "metadata": {
            "optimizationDate": now,
            "datasetFrames": n_frames,
            "worstShapes": worst_shapes,
            "baselineWeightedMAE": round(baseline_mae, 6),
            "optimizedWeightedMAE": round(optimized_mae, 6),
            "rawMediaPipeWeightedMAE": round(raw_mae, 6),
            "improvementPct": round(improvement, 1),
            "optimizerIterations": result.nit,
            "optimizerEvaluations": call_count[0],
            "optimizerSuccess": bool(result.success),
            "optimizerMessage": result.message,
        },
        "perShapeImprovement": per_shape_improvement,
    }

    return output


# ── Validation ──

def run_validation(
    arkit: dict[str, np.ndarray],
    mediapipe: dict[str, np.ndarray],
    n_worst: int = 10,
    seed: int = RANDOM_SEED,
) -> None:
    """Run optimization with train/test split and report generalization."""
    print("=" * 70)
    print("Cross-validation mode: 80/20 train/test split")
    print("=" * 70)

    arkit_train, mp_train, arkit_test, mp_test = train_test_split(
        arkit, mediapipe, test_ratio=VALIDATION_SPLIT, seed=seed,
    )

    any_key = next(iter(arkit_train))
    print(f"Train set: {len(arkit_train[any_key])} frames")
    print(f"Test set: {len(arkit_test[any_key])} frames")

    # Optimize on train set
    print("\n--- Training ---")
    train_result = run_optimization(arkit_train, mp_train, n_worst=n_worst, seed=seed)

    # Evaluate on held-out test set
    print("\n--- Test Set Evaluation ---")
    worst_shapes = train_result["metadata"]["worstShapes"]

    # Baseline on test set
    baseline_enhanced_test = simulate_enhancer(
        mediapipe=mp_test,
        geometric_blend_weight=CURRENT_DEFAULTS["geometricBlendWeight"],
        sensitivity_overrides=CURRENT_DEFAULTS["sensitivity"],
        dead_zone_overrides=CURRENT_DEFAULTS["deadZone"],
        arkit=arkit_test,
    )
    baseline_test_mae = weighted_mae(arkit_test, baseline_enhanced_test, worst_shapes)

    # Optimized on test set
    optimized_sens = dict(CURRENT_DEFAULTS["sensitivity"])
    optimized_sens.update(train_result["sensitivityOverrides"])
    optimized_dz = dict(CURRENT_DEFAULTS["deadZone"])
    optimized_dz.update(train_result["deadZoneOverrides"])

    optimized_enhanced_test = simulate_enhancer(
        mediapipe=mp_test,
        geometric_blend_weight=train_result["geometricBlendWeight"],
        sensitivity_overrides=optimized_sens,
        dead_zone_overrides=optimized_dz,
        arkit=arkit_test,
    )
    optimized_test_mae = weighted_mae(arkit_test, optimized_enhanced_test, worst_shapes)

    test_improvement = (
        (baseline_test_mae - optimized_test_mae) / baseline_test_mae * 100
        if baseline_test_mae > 0 else 0.0
    )

    print(f"\n{'Metric':<35} {'Train':<12} {'Test':<12}")
    print("-" * 59)
    print(f"{'Baseline weighted MAE':<35} "
          f"{train_result['metadata']['baselineWeightedMAE']:<12.6f} "
          f"{baseline_test_mae:<12.6f}")
    print(f"{'Optimized weighted MAE':<35} "
          f"{train_result['metadata']['optimizedWeightedMAE']:<12.6f} "
          f"{optimized_test_mae:<12.6f}")
    print(f"{'Improvement %':<35} "
          f"{train_result['metadata']['improvementPct']:<12.1f} "
          f"{test_improvement:<12.1f}")

    # Per-shape test results
    baseline_per_shape_test = compute_per_shape_mae(arkit_test, baseline_enhanced_test)
    optimized_per_shape_test = compute_per_shape_mae(arkit_test, optimized_enhanced_test)

    print(f"\n{'Shape':<25} {'Before (test)':<15} {'After (test)':<15} {'Change':<10}")
    print("-" * 65)
    for shape in worst_shapes:
        if shape in baseline_per_shape_test and shape in optimized_per_shape_test:
            before = baseline_per_shape_test[shape]
            after = optimized_per_shape_test[shape]
            change = (before - after) / before * 100 if before > 0 else 0.0
            sign = "+" if change < 0 else ""
            print(f"{shape:<25} {before:<15.6f} {after:<15.6f} {sign}{change:<10.1f}%")

    # Overfitting check
    train_imp = train_result["metadata"]["improvementPct"]
    if test_improvement < train_imp * 0.5:
        print("\n** WARNING: Test improvement is less than 50% of training improvement. "
              "Possible overfitting. Consider collecting more data. **")
    elif test_improvement > 0:
        print(f"\nGeneralization looks healthy: test improvement ({test_improvement:.1f}%) "
              f"is consistent with training ({train_imp:.1f}%).")
    else:
        print("\n** WARNING: Optimized parameters perform worse on test set. "
              "The model may be overfitting to training data. **")


# ── CLI ──

def parse_args() -> argparse.Namespace:
    """Parse command-line arguments."""
    parser = argparse.ArgumentParser(
        description="Optimize BlendShapeEnhancer parameters via differential evolution.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument(
        "--captures",
        type=Path,
        default=Path("captures"),
        help="Directory containing session_* subdirectories (default: captures/)",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("tools/calibration/optimized_params.json"),
        help="Output JSON path (default: tools/calibration/optimized_params.json)",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Run with synthetic data to verify the pipeline works",
    )
    parser.add_argument(
        "--validate",
        action="store_true",
        help="Run with 80/20 cross-validation split",
    )
    parser.add_argument(
        "--n-worst",
        type=int,
        default=10,
        help="Number of worst shapes to optimize (default: 10)",
    )
    parser.add_argument(
        "--max-iter",
        type=int,
        default=200,
        help="Maximum optimizer iterations (default: 200)",
    )
    parser.add_argument(
        "--pop-size",
        type=int,
        default=30,
        help="Differential evolution population size (default: 30)",
    )
    parser.add_argument(
        "--seed",
        type=int,
        default=RANDOM_SEED,
        help=f"Random seed for reproducibility (default: {RANDOM_SEED})",
    )
    return parser.parse_args()


def main() -> None:
    """Entry point."""
    args = parse_args()

    if args.dry_run:
        print("=" * 70)
        print("DRY RUN: Using synthetic data to verify pipeline")
        print("=" * 70)
        arkit, mediapipe = generate_synthetic_data(seed=args.seed)

        if args.validate:
            run_validation(
                arkit, mediapipe,
                n_worst=args.n_worst,
                seed=args.seed,
            )
        else:
            result = run_optimization(
                arkit, mediapipe,
                n_worst=args.n_worst,
                max_iterations=args.max_iter,
                population_size=args.pop_size,
                seed=args.seed,
            )

            output_path = args.output.resolve()
            output_path.parent.mkdir(parents=True, exist_ok=True)
            output_path.write_text(
                json.dumps(result, indent=2) + "\n", encoding="utf-8"
            )
            print(f"\nResults written to {output_path}")
    else:
        captures_dir = args.captures.resolve()
        if not captures_dir.is_dir():
            print(f"Error: {captures_dir} is not a directory", file=sys.stderr)
            sys.exit(1)

        arkit, mediapipe = load_all_sessions(captures_dir)

        if args.validate:
            run_validation(
                arkit, mediapipe,
                n_worst=args.n_worst,
                seed=args.seed,
            )
        else:
            result = run_optimization(
                arkit, mediapipe,
                n_worst=args.n_worst,
                max_iterations=args.max_iter,
                population_size=args.pop_size,
                seed=args.seed,
            )

            output_path = args.output.resolve()
            output_path.parent.mkdir(parents=True, exist_ok=True)
            output_path.write_text(
                json.dumps(result, indent=2) + "\n", encoding="utf-8"
            )
            print(f"\nResults written to {output_path}")


if __name__ == "__main__":
    main()
