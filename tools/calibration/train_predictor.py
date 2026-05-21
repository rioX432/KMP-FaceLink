#!/usr/bin/env python3
"""
Train a custom TFLite model to predict 52 ARKit blendshape coefficients
from 478 MediaPipe face landmarks.

Architecture (Blendshapes GHUM paper):
    Input:  478 landmarks x 3 coords = 1434 floats
      -> FC(1434 -> 256) + BatchNorm + ReLU
      -> FC(256  -> 128) + BatchNorm + ReLU
      -> FC(128  ->  52) + Sigmoid
    Output: 52 blendshape coefficients in [0, 1]

Usage:
    # Dry run with synthetic data (verify pipeline without real captures)
    python train_predictor.py --dry-run

    # Train on captured data
    python train_predictor.py --captures captures/

    # Train with cross-subject validation
    python train_predictor.py --captures captures/ --cross-subject

Requirements:
    pip install -r requirements.txt
    pip install 'tensorflow>=2.15.0'

    Download the face landmarker model (if extracting landmarks from frames):
        wget -O face_landmarker_v2.task \\
          https://storage.googleapis.com/mediapipe-models/face_landmarker/\\
face_landmarker/float16/latest/face_landmarker.task
"""

from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path

import numpy as np

# -- Constants --

NUM_LANDMARKS = 478
NUM_COORDS = 3
INPUT_DIM = NUM_LANDMARKS * NUM_COORDS  # 1434
HIDDEN_1 = 256
HIDDEN_2 = 128
NUM_BLENDSHAPES = 52
RANDOM_SEED = 42

# All 52 ARKit blendshape names in canonical order
ARKIT_BLENDSHAPES: list[str] = [
    "browDownLeft", "browDownRight", "browInnerUp",
    "browOuterUpLeft", "browOuterUpRight",
    "cheekPuff", "cheekSquintLeft", "cheekSquintRight",
    "eyeBlinkLeft", "eyeBlinkRight",
    "eyeLookDownLeft", "eyeLookDownRight",
    "eyeLookInLeft", "eyeLookInRight",
    "eyeLookOutLeft", "eyeLookOutRight",
    "eyeLookUpLeft", "eyeLookUpRight",
    "eyeSquintLeft", "eyeSquintRight",
    "eyeWideLeft", "eyeWideRight",
    "jawForward", "jawLeft", "jawOpen", "jawRight",
    "mouthClose", "mouthDimpleLeft", "mouthDimpleRight",
    "mouthFrownLeft", "mouthFrownRight",
    "mouthFunnel", "mouthLeft",
    "mouthLowerDownLeft", "mouthLowerDownRight",
    "mouthPressLeft", "mouthPressRight",
    "mouthPucker", "mouthRight",
    "mouthRollLower", "mouthRollUpper",
    "mouthShrugLower", "mouthShrugUpper",
    "mouthSmileLeft", "mouthSmileRight",
    "mouthStretchLeft", "mouthStretchRight",
    "mouthUpperUpLeft", "mouthUpperUpRight",
    "noseSneerLeft", "noseSneerRight",
    "tongueOut",
]

# Success criteria thresholds
CRITERIA_MAE_IMPROVEMENT_PCT = 15.0  # >= 15% MAE improvement over geometric solver
CRITERIA_INFERENCE_LATENCY_MS = 5.0  # <= 5ms
CRITERIA_CROSS_SUBJECT_MAE_RATIO = 1.20  # within 20% of training MAE
CRITERIA_MODEL_SIZE_BYTES = 1_000_000  # <= 1MB


# -- Data Loading --

def load_session_landmarks(
    session_dir: Path,
    landmarker: object,
) -> tuple[np.ndarray, np.ndarray] | None:
    """Extract landmarks and load ARKit blendshapes for a session.

    Uses MediaPipe FaceLandmarker to extract 478 landmarks from each frame,
    and loads arkit_blendshapes.csv as ground truth labels.

    Returns:
        (landmarks, blendshapes) arrays or None if session is invalid.
        landmarks: shape (N, 1434) — flattened x,y,z for 478 points
        blendshapes: shape (N, 52) — ARKit ground truth
    """
    import csv

    import mediapipe as mp

    frames_dir = session_dir / "frames"
    arkit_path = session_dir / "arkit_blendshapes.csv"
    timestamps_path = session_dir / "timestamps.csv"

    if not frames_dir.is_dir() or not arkit_path.exists():
        return None

    # Load ARKit blendshapes
    arkit_by_frame: dict[int, dict[str, float]] = {}
    with open(arkit_path, newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for i, row in enumerate(reader):
            frame_idx = int(row.get("frame_index", i))
            values = {}
            for bs in ARKIT_BLENDSHAPES:
                if bs in row:
                    values[bs] = float(row[bs])
            if len(values) == NUM_BLENDSHAPES:
                arkit_by_frame[frame_idx] = values

    # Load timestamps for frame alignment
    timestamps: dict[int, float] = {}
    if timestamps_path.exists():
        with open(timestamps_path, newline="", encoding="utf-8") as f:
            reader = csv.DictReader(f)
            for row in reader:
                timestamps[int(row["frame_index"])] = float(row["timestamp"])

    # Process frames
    frame_files = sorted(frames_dir.glob("*.jpg"))
    if not frame_files:
        return None

    landmarks_list: list[np.ndarray] = []
    blendshapes_list: list[np.ndarray] = []

    for frame_path in frame_files:
        frame_idx = int(frame_path.stem)

        # Skip frames without ARKit ground truth
        if frame_idx not in arkit_by_frame:
            continue

        image = mp.Image.create_from_file(str(frame_path))
        result = landmarker.detect(image)

        if not result.face_landmarks or len(result.face_landmarks) == 0:
            continue

        # Extract 478 landmarks as flat array [x0, y0, z0, x1, y1, z1, ...]
        face_lm = result.face_landmarks[0]
        if len(face_lm) != NUM_LANDMARKS:
            continue

        coords = np.zeros(INPUT_DIM, dtype=np.float32)
        for i, lm in enumerate(face_lm):
            coords[i * 3] = lm.x
            coords[i * 3 + 1] = lm.y
            coords[i * 3 + 2] = lm.z

        # Build blendshape label vector in canonical order
        arkit_vals = arkit_by_frame[frame_idx]
        labels = np.array(
            [arkit_vals[bs] for bs in ARKIT_BLENDSHAPES], dtype=np.float32
        )

        landmarks_list.append(coords)
        blendshapes_list.append(labels)

    if not landmarks_list:
        return None

    return np.array(landmarks_list), np.array(blendshapes_list)


def load_all_sessions(
    captures_dir: Path,
) -> list[tuple[str, np.ndarray, np.ndarray]]:
    """Load all capture sessions with landmark extraction.

    Returns list of (subject_name, landmarks, blendshapes) tuples.
    """
    from mediapipe.tasks import python as mp_python
    from mediapipe.tasks.python import vision as mp_vision

    model_path = Path(__file__).parent / "face_landmarker_v2.task"
    if not model_path.exists():
        print(f"Error: Model file not found at {model_path}", file=sys.stderr)
        print(
            "Download it with:\n"
            "  wget -O face_landmarker_v2.task \\\n"
            "    https://storage.googleapis.com/mediapipe-models/"
            "face_landmarker/face_landmarker/float16/latest/"
            "face_landmarker.task",
            file=sys.stderr,
        )
        sys.exit(1)

    base_options = mp_python.BaseOptions(model_asset_path=str(model_path))
    options = mp_vision.FaceLandmarkerOptions(
        base_options=base_options,
        output_face_blendshapes=False,
        output_facial_transformation_matrixes=False,
        num_faces=1,
    )
    landmarker = mp_vision.FaceLandmarker.create_from_options(options)

    session_dirs = sorted(
        d for d in captures_dir.iterdir()
        if d.is_dir() and d.name.startswith("session_")
    )

    if not session_dirs:
        print(
            f"Error: no session_* directories found in {captures_dir}",
            file=sys.stderr,
        )
        sys.exit(1)

    subjects: list[tuple[str, np.ndarray, np.ndarray]] = []

    for session_dir in session_dirs:
        # Read subject name from metadata.json
        metadata_path = session_dir / "metadata.json"
        subject = session_dir.name
        if metadata_path.exists():
            with open(metadata_path, encoding="utf-8") as f:
                meta = json.load(f)
                subject = meta.get("subject_name", session_dir.name)

        print(f"Processing {session_dir.name} (subject: {subject})...")
        result = load_session_landmarks(session_dir, landmarker)
        if result is None:
            print(f"  Skipped (no valid frames)")
            continue

        landmarks, blendshapes = result
        print(f"  Loaded {len(landmarks)} paired frames")
        subjects.append((subject, landmarks, blendshapes))

    landmarker.close()

    if not subjects:
        print("Error: no valid sessions found", file=sys.stderr)
        sys.exit(1)

    total = sum(len(lm) for _, lm, _ in subjects)
    print(f"Total: {len(subjects)} sessions, {total} frames")
    return subjects


def generate_synthetic_data(
    n_subjects: int = 3,
    n_frames_per_subject: int = 1000,
    seed: int = RANDOM_SEED,
) -> list[tuple[str, np.ndarray, np.ndarray]]:
    """Generate synthetic landmark-blendshape pairs for dry-run testing.

    Creates plausible paired data where blendshape activations correlate
    with specific landmark regions, mimicking real face tracking data.
    """
    rng = np.random.default_rng(seed)
    subjects: list[tuple[str, np.ndarray, np.ndarray]] = []

    for subj_idx in range(n_subjects):
        subject_name = f"synthetic_subject_{subj_idx:02d}"

        # Generate base blendshape activations (sparse, mostly near zero)
        blendshapes = np.zeros(
            (n_frames_per_subject, NUM_BLENDSHAPES), dtype=np.float32
        )
        for bs_idx in range(NUM_BLENDSHAPES):
            # Sparse activation: most frames near zero, occasional bursts
            base = rng.beta(0.5, 5.0, size=n_frames_per_subject).astype(
                np.float32
            )
            # Per-subject variation in activation range
            scale = rng.uniform(0.5, 1.0)
            blendshapes[:, bs_idx] = np.clip(base * scale, 0.0, 1.0)

        # Generate landmarks correlated with blendshapes
        # Base neutral face position with per-subject variation
        neutral_face = rng.normal(0.5, 0.1, size=INPUT_DIM).astype(np.float32)
        neutral_face = np.clip(neutral_face, 0.0, 1.0)

        # Create a random projection from blendshapes to landmark offsets
        # This simulates how blendshapes deform landmarks
        projection = rng.normal(0.0, 0.02, size=(NUM_BLENDSHAPES, INPUT_DIM)).astype(
            np.float32
        )

        landmarks = np.tile(neutral_face, (n_frames_per_subject, 1))
        landmarks += blendshapes @ projection
        # Add sensor noise
        landmarks += rng.normal(0.0, 0.005, size=landmarks.shape).astype(
            np.float32
        )
        landmarks = np.clip(landmarks, 0.0, 1.0)

        subjects.append((subject_name, landmarks, blendshapes))

    total = sum(len(lm) for _, lm, _ in subjects)
    print(
        f"Generated synthetic data: {n_subjects} subjects, "
        f"{total} total frames"
    )
    return subjects


# -- Model Definition --

def build_model() -> "tf.keras.Model":
    """Build the blendshape predictor model.

    Architecture from Blendshapes GHUM paper:
        FC(1434 -> 256) + BatchNorm + ReLU
        FC(256  -> 128) + BatchNorm + ReLU
        FC(128  ->  52) + Sigmoid
    """
    import tensorflow as tf

    model = tf.keras.Sequential([
        tf.keras.layers.InputLayer(shape=(INPUT_DIM,)),
        tf.keras.layers.Dense(HIDDEN_1),
        tf.keras.layers.BatchNormalization(),
        tf.keras.layers.ReLU(),
        tf.keras.layers.Dense(HIDDEN_2),
        tf.keras.layers.BatchNormalization(),
        tf.keras.layers.ReLU(),
        tf.keras.layers.Dense(NUM_BLENDSHAPES, activation="sigmoid"),
    ])

    return model


class WeightedMSELoss(object):
    """Weighted MSE loss that upweights worst-performing shapes.

    During training, per-shape weights are updated periodically to
    penalize shapes with the highest running MAE.
    """

    def __init__(self, n_shapes: int = NUM_BLENDSHAPES, boost_factor: float = 3.0):
        self.n_shapes = n_shapes
        self.boost_factor = boost_factor
        # Start with uniform weights
        self.shape_weights = np.ones(n_shapes, dtype=np.float32)

    def update_weights(self, y_true: np.ndarray, y_pred: np.ndarray) -> None:
        """Recompute per-shape weights based on current MAE."""
        per_shape_mae = np.mean(np.abs(y_true - y_pred), axis=0)
        # Identify top-10 worst shapes
        worst_indices = np.argsort(per_shape_mae)[-10:]
        self.shape_weights = np.ones(self.n_shapes, dtype=np.float32)
        self.shape_weights[worst_indices] = self.boost_factor

    def get_loss_fn(self) -> "callable":
        """Return a TensorFlow loss function using current weights."""
        import tensorflow as tf

        weights = tf.constant(self.shape_weights, dtype=tf.float32)

        def weighted_mse(y_true: tf.Tensor, y_pred: tf.Tensor) -> tf.Tensor:
            sq_error = tf.square(y_true - y_pred)
            return tf.reduce_mean(sq_error * weights)

        return weighted_mse


# -- Geometric Solver Baseline --

def compute_geometric_baseline_mae(
    landmarks: np.ndarray,
    blendshapes: np.ndarray,
) -> float:
    """Estimate geometric solver baseline MAE.

    The geometric solver uses heuristic landmark distances to estimate
    blendshapes. We simulate this with a simple linear regression
    (least-squares fit) from landmarks to blendshapes, which represents
    the best a linear geometric approach can achieve.
    """
    n_train = int(len(landmarks) * 0.8)
    x_train = landmarks[:n_train]
    y_train = blendshapes[:n_train]
    x_test = landmarks[n_train:]
    y_test = blendshapes[n_train:]

    # Ridge regression: (X^T X + lambda I)^-1 X^T Y
    ridge_lambda = 1.0
    xtx = x_train.T @ x_train + ridge_lambda * np.eye(x_train.shape[1])
    xty = x_train.T @ y_train
    weights = np.linalg.solve(xtx, xty)

    y_pred = np.clip(x_test @ weights, 0.0, 1.0)
    mae = float(np.mean(np.abs(y_test - y_pred)))
    return mae


# -- Training --

def train_model(
    subjects: list[tuple[str, np.ndarray, np.ndarray]],
    epochs: int = 100,
    batch_size: int = 256,
    learning_rate: float = 1e-3,
    weight_update_interval: int = 10,
    seed: int = RANDOM_SEED,
) -> tuple["tf.keras.Model", dict]:
    """Train the blendshape predictor on all subjects.

    Returns:
        (trained_model, metrics_dict)
    """
    import tensorflow as tf

    tf.random.set_seed(seed)

    # Concatenate all data
    all_landmarks = np.concatenate([lm for _, lm, _ in subjects])
    all_blendshapes = np.concatenate([bs for _, _, bs in subjects])

    # Shuffle
    rng = np.random.default_rng(seed)
    indices = rng.permutation(len(all_landmarks))
    all_landmarks = all_landmarks[indices]
    all_blendshapes = all_blendshapes[indices]

    # Train/val split (80/20)
    split = int(len(all_landmarks) * 0.8)
    x_train, x_val = all_landmarks[:split], all_landmarks[split:]
    y_train, y_val = all_blendshapes[:split], all_blendshapes[split:]

    print(f"Training samples: {len(x_train)}, validation: {len(x_val)}")

    # Compute geometric baseline
    print("Computing geometric solver baseline...")
    baseline_mae = compute_geometric_baseline_mae(all_landmarks, all_blendshapes)
    print(f"Geometric solver baseline MAE: {baseline_mae:.6f}")

    # Build and compile model
    model = build_model()
    weighted_loss = WeightedMSELoss()

    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=learning_rate),
        loss=weighted_loss.get_loss_fn(),
        metrics=["mae"],
    )

    model.summary()

    # Training loop with periodic weight updates
    best_val_mae = float("inf")
    best_weights = None
    patience = 20
    wait = 0

    for epoch in range(epochs):
        history = model.fit(
            x_train, y_train,
            validation_data=(x_val, y_val),
            epochs=1,
            batch_size=batch_size,
            verbose=0,
        )

        train_mae = history.history["mae"][0]
        val_mae = history.history["val_mae"][0]

        # Update shape weights periodically
        if (epoch + 1) % weight_update_interval == 0:
            y_pred = model.predict(x_val, verbose=0)
            weighted_loss.update_weights(y_val, y_pred)
            model.compile(
                optimizer=tf.keras.optimizers.Adam(
                    learning_rate=learning_rate
                ),
                loss=weighted_loss.get_loss_fn(),
                metrics=["mae"],
            )

        # Early stopping
        if val_mae < best_val_mae:
            best_val_mae = val_mae
            best_weights = model.get_weights()
            wait = 0
        else:
            wait += 1
            if wait >= patience:
                print(
                    f"Early stopping at epoch {epoch + 1}, "
                    f"best val MAE: {best_val_mae:.6f}"
                )
                break

        if (epoch + 1) % 10 == 0:
            print(
                f"Epoch {epoch + 1:3d} | "
                f"train MAE: {train_mae:.6f} | "
                f"val MAE: {val_mae:.6f}"
            )

    # Restore best weights
    if best_weights is not None:
        model.set_weights(best_weights)

    # Final evaluation
    y_pred = model.predict(x_val, verbose=0)
    final_mae = float(np.mean(np.abs(y_val - y_pred)))

    # Per-shape MAE
    per_shape_mae = np.mean(np.abs(y_val - y_pred), axis=0)
    shape_mae_dict = {
        ARKIT_BLENDSHAPES[i]: float(per_shape_mae[i])
        for i in range(NUM_BLENDSHAPES)
    }

    # Worst shapes
    worst_indices = np.argsort(per_shape_mae)[-10:]
    worst_shapes = [
        (ARKIT_BLENDSHAPES[i], float(per_shape_mae[i]))
        for i in reversed(worst_indices)
    ]

    improvement_pct = (
        (baseline_mae - final_mae) / baseline_mae * 100
        if baseline_mae > 0 else 0.0
    )

    metrics = {
        "baseline_mae": baseline_mae,
        "neural_mae": final_mae,
        "improvement_pct": improvement_pct,
        "per_shape_mae": shape_mae_dict,
        "worst_shapes": worst_shapes,
        "train_samples": len(x_train),
        "val_samples": len(x_val),
        "epochs_trained": epoch + 1,
    }

    return model, metrics


def run_cross_subject_validation(
    subjects: list[tuple[str, np.ndarray, np.ndarray]],
    epochs: int = 100,
    batch_size: int = 256,
    learning_rate: float = 1e-3,
    seed: int = RANDOM_SEED,
) -> dict:
    """Leave-one-subject-out cross-validation.

    For each subject, train on all other subjects and validate on the
    held-out subject. Reports per-fold and aggregate metrics.
    """
    import tensorflow as tf

    unique_subjects: dict[str, list[int]] = {}
    for i, (name, _, _) in enumerate(subjects):
        if name not in unique_subjects:
            unique_subjects[name] = []
        unique_subjects[name].append(i)

    subject_names = sorted(unique_subjects.keys())
    n_folds = len(subject_names)

    if n_folds < 2:
        print(
            "Warning: need >= 2 distinct subjects for cross-subject "
            "validation. Running standard train/val split.",
            file=sys.stderr,
        )
        _, metrics = train_model(
            subjects, epochs=epochs, batch_size=batch_size,
            learning_rate=learning_rate, seed=seed,
        )
        return metrics

    print(f"\nCross-subject validation: {n_folds} folds")
    print(f"Subjects: {', '.join(subject_names)}")
    print("=" * 70)

    fold_results: list[dict] = []
    train_maes: list[float] = []

    for fold, held_out_name in enumerate(subject_names):
        print(f"\n--- Fold {fold + 1}/{n_folds}: held out = {held_out_name} ---")

        # Split by subject
        train_subjects = [
            (name, lm, bs) for i, (name, lm, bs) in enumerate(subjects)
            if name != held_out_name
        ]
        val_subjects = [
            (name, lm, bs) for i, (name, lm, bs) in enumerate(subjects)
            if name == held_out_name
        ]

        if not train_subjects or not val_subjects:
            print(f"  Skipping fold (empty train or val set)")
            continue

        # Concatenate training data
        x_train = np.concatenate([lm for _, lm, _ in train_subjects])
        y_train = np.concatenate([bs for _, _, bs in train_subjects])
        x_val = np.concatenate([lm for _, lm, _ in val_subjects])
        y_val = np.concatenate([bs for _, _, bs in val_subjects])

        print(f"  Train: {len(x_train)} frames, Val: {len(x_val)} frames")

        tf.random.set_seed(seed + fold)
        model = build_model()
        model.compile(
            optimizer=tf.keras.optimizers.Adam(learning_rate=learning_rate),
            loss="mse",
            metrics=["mae"],
        )

        model.fit(
            x_train, y_train,
            validation_data=(x_val, y_val),
            epochs=epochs,
            batch_size=batch_size,
            verbose=0,
            callbacks=[
                tf.keras.callbacks.EarlyStopping(
                    monitor="val_mae", patience=20, restore_best_weights=True,
                ),
            ],
        )

        # Evaluate
        y_train_pred = model.predict(x_train, verbose=0)
        y_val_pred = model.predict(x_val, verbose=0)
        train_mae = float(np.mean(np.abs(y_train - y_train_pred)))
        val_mae = float(np.mean(np.abs(y_val - y_val_pred)))
        train_maes.append(train_mae)

        fold_results.append({
            "fold": fold + 1,
            "held_out_subject": held_out_name,
            "train_frames": len(x_train),
            "val_frames": len(x_val),
            "train_mae": train_mae,
            "val_mae": val_mae,
        })

        print(f"  Train MAE: {train_mae:.6f}, Val MAE: {val_mae:.6f}")

    # Aggregate results
    val_maes = [r["val_mae"] for r in fold_results]
    avg_train_mae = float(np.mean(train_maes))
    avg_val_mae = float(np.mean(val_maes))
    std_val_mae = float(np.std(val_maes))

    # Cross-subject generalization ratio
    generalization_ratio = avg_val_mae / avg_train_mae if avg_train_mae > 0 else 0.0

    print("\n" + "=" * 70)
    print("Cross-Subject Validation Summary")
    print("=" * 70)
    print(f"  Avg train MAE: {avg_train_mae:.6f}")
    print(f"  Avg val MAE:   {avg_val_mae:.6f} +/- {std_val_mae:.6f}")
    print(f"  Generalization ratio: {generalization_ratio:.3f}")

    if generalization_ratio <= CRITERIA_CROSS_SUBJECT_MAE_RATIO:
        print(
            f"  PASS: ratio {generalization_ratio:.3f} <= "
            f"{CRITERIA_CROSS_SUBJECT_MAE_RATIO} threshold"
        )
    else:
        print(
            f"  FAIL: ratio {generalization_ratio:.3f} > "
            f"{CRITERIA_CROSS_SUBJECT_MAE_RATIO} threshold"
        )

    return {
        "folds": fold_results,
        "avg_train_mae": avg_train_mae,
        "avg_val_mae": avg_val_mae,
        "std_val_mae": std_val_mae,
        "generalization_ratio": generalization_ratio,
    }


# -- TFLite Conversion --

def convert_to_tflite(
    model: "tf.keras.Model",
    output_path: Path,
    quantize_float16: bool = True,
) -> int:
    """Convert Keras model to TFLite with optional float16 quantization.

    Returns the model file size in bytes.
    """
    import tensorflow as tf

    converter = tf.lite.TFLiteConverter.from_keras_model(model)

    if quantize_float16:
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        converter.target_spec.supported_types = [tf.float16]

    tflite_model = converter.convert()

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_bytes(tflite_model)

    size_bytes = len(tflite_model)
    print(f"TFLite model saved to {output_path} ({size_bytes:,} bytes)")
    return size_bytes


# -- Benchmarking --

def benchmark_inference(tflite_path: Path, n_runs: int = 1000) -> float:
    """Benchmark TFLite inference latency.

    Returns average inference time in milliseconds.
    """
    import tensorflow as tf

    interpreter = tf.lite.Interpreter(model_path=str(tflite_path))
    interpreter.allocate_tensors()

    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    # Generate random input
    rng = np.random.default_rng(RANDOM_SEED)
    dummy_input = rng.random(
        (1, INPUT_DIM), dtype=np.float32
    ).astype(np.float32)

    # Warm up
    for _ in range(100):
        interpreter.set_tensor(input_details[0]["index"], dummy_input)
        interpreter.invoke()

    # Timed runs
    start = time.perf_counter()
    for _ in range(n_runs):
        interpreter.set_tensor(input_details[0]["index"], dummy_input)
        interpreter.invoke()
    elapsed = time.perf_counter() - start

    avg_ms = (elapsed / n_runs) * 1000
    print(f"Inference latency: {avg_ms:.3f} ms (avg over {n_runs} runs)")
    return avg_ms


# -- Success Criteria Check --

def check_success_criteria(
    improvement_pct: float,
    latency_ms: float,
    model_size_bytes: int,
    cross_subject_ratio: float | None,
) -> dict[str, dict]:
    """Check all success criteria and return pass/fail for each."""
    results: dict[str, dict] = {}

    results["mae_improvement"] = {
        "criterion": f">= {CRITERIA_MAE_IMPROVEMENT_PCT}% MAE improvement",
        "value": f"{improvement_pct:.1f}%",
        "pass": improvement_pct >= CRITERIA_MAE_IMPROVEMENT_PCT,
    }

    results["inference_latency"] = {
        "criterion": f"<= {CRITERIA_INFERENCE_LATENCY_MS} ms",
        "value": f"{latency_ms:.3f} ms",
        "pass": latency_ms <= CRITERIA_INFERENCE_LATENCY_MS,
    }

    results["model_size"] = {
        "criterion": f"<= {CRITERIA_MODEL_SIZE_BYTES / 1024:.0f} KB",
        "value": f"{model_size_bytes / 1024:.1f} KB",
        "pass": model_size_bytes <= CRITERIA_MODEL_SIZE_BYTES,
    }

    if cross_subject_ratio is not None:
        results["cross_subject_generalization"] = {
            "criterion": (
                f"cross-subject MAE within "
                f"{int((CRITERIA_CROSS_SUBJECT_MAE_RATIO - 1) * 100)}% "
                f"of training"
            ),
            "value": f"{cross_subject_ratio:.3f}x",
            "pass": cross_subject_ratio <= CRITERIA_CROSS_SUBJECT_MAE_RATIO,
        }

    return results


def print_criteria_report(criteria: dict[str, dict]) -> bool:
    """Print success criteria report and return True if all pass."""
    print("\n" + "=" * 70)
    print("SUCCESS CRITERIA EVALUATION")
    print("=" * 70)

    all_pass = True
    for name, info in criteria.items():
        status = "PASS" if info["pass"] else "FAIL"
        if not info["pass"]:
            all_pass = False
        print(f"  [{status}] {name}: {info['value']} ({info['criterion']})")

    print("=" * 70)
    if all_pass:
        print("DECISION: All criteria met. Neural predictor is feasible.")
    else:
        failed = [n for n, i in criteria.items() if not i["pass"]]
        print(f"DECISION: {len(failed)} criteria failed: {', '.join(failed)}")
        print("  Neural predictor needs further investigation or tuning.")

    return all_pass


# -- CLI --

def parse_args() -> argparse.Namespace:
    """Parse command-line arguments."""
    parser = argparse.ArgumentParser(
        description=(
            "Train a TFLite blendshape predictor from MediaPipe landmarks."
        ),
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument(
        "--captures",
        type=Path,
        default=Path("captures"),
        help="Directory containing session_* subdirectories",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("tools/calibration/neural_predictor"),
        help="Output directory for model and metrics",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Run with synthetic data to verify the pipeline",
    )
    parser.add_argument(
        "--cross-subject",
        action="store_true",
        help="Run leave-one-subject-out cross-validation",
    )
    parser.add_argument(
        "--epochs",
        type=int,
        default=100,
        help="Maximum training epochs (default: 100)",
    )
    parser.add_argument(
        "--batch-size",
        type=int,
        default=256,
        help="Training batch size (default: 256)",
    )
    parser.add_argument(
        "--lr",
        type=float,
        default=1e-3,
        help="Learning rate (default: 1e-3)",
    )
    parser.add_argument(
        "--seed",
        type=int,
        default=RANDOM_SEED,
        help=f"Random seed (default: {RANDOM_SEED})",
    )
    return parser.parse_args()


def main() -> None:
    """Entry point."""
    args = parse_args()

    output_dir = args.output_dir.resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    # Load or generate data
    if args.dry_run:
        print("=" * 70)
        print("DRY RUN: Using synthetic data to verify pipeline")
        print("=" * 70)
        subjects = generate_synthetic_data(seed=args.seed)
    else:
        captures_dir = args.captures.resolve()
        if not captures_dir.is_dir():
            print(
                f"Error: {captures_dir} is not a directory", file=sys.stderr
            )
            sys.exit(1)
        subjects = load_all_sessions(captures_dir)

    # Cross-subject validation
    cross_subject_ratio: float | None = None
    if args.cross_subject:
        cv_results = run_cross_subject_validation(
            subjects,
            epochs=args.epochs,
            batch_size=args.batch_size,
            learning_rate=args.lr,
            seed=args.seed,
        )
        cross_subject_ratio = cv_results.get("generalization_ratio")

        # Save cross-validation results
        cv_path = output_dir / "cross_validation_results.json"
        cv_path.write_text(
            json.dumps(cv_results, indent=2, default=str) + "\n",
            encoding="utf-8",
        )
        print(f"Cross-validation results saved to {cv_path}")

    # Train final model on all data
    print("\n" + "=" * 70)
    print("Training final model on all data")
    print("=" * 70)

    model, metrics = train_model(
        subjects,
        epochs=args.epochs,
        batch_size=args.batch_size,
        learning_rate=args.lr,
        seed=args.seed,
    )

    # Convert to TFLite
    tflite_path = output_dir / "blendshape_predictor.tflite"
    model_size = convert_to_tflite(model, tflite_path, quantize_float16=True)

    # Benchmark inference
    latency_ms = benchmark_inference(tflite_path)

    # Save Keras model
    keras_path = output_dir / "blendshape_predictor.keras"
    model.save(keras_path)
    print(f"Keras model saved to {keras_path}")

    # Check success criteria
    criteria = check_success_criteria(
        improvement_pct=metrics["improvement_pct"],
        latency_ms=latency_ms,
        model_size_bytes=model_size,
        cross_subject_ratio=cross_subject_ratio,
    )
    all_pass = print_criteria_report(criteria)

    # Save full results
    results = {
        "metrics": {
            k: v for k, v in metrics.items()
            if k != "per_shape_mae"  # verbose, save separately
        },
        "per_shape_mae": metrics["per_shape_mae"],
        "tflite_model_size_bytes": model_size,
        "inference_latency_ms": latency_ms,
        "success_criteria": criteria,
        "all_criteria_pass": all_pass,
    }

    if cross_subject_ratio is not None:
        results["cross_subject_generalization_ratio"] = cross_subject_ratio

    results_path = output_dir / "training_results.json"
    results_path.write_text(
        json.dumps(results, indent=2, default=str) + "\n",
        encoding="utf-8",
    )
    print(f"\nFull results saved to {results_path}")

    # Print summary
    print("\n" + "=" * 70)
    print("SUMMARY")
    print("=" * 70)
    print(f"  Geometric baseline MAE: {metrics['baseline_mae']:.6f}")
    print(f"  Neural predictor MAE:   {metrics['neural_mae']:.6f}")
    print(f"  Improvement:            {metrics['improvement_pct']:.1f}%")
    print(f"  Inference latency:      {latency_ms:.3f} ms")
    print(f"  Model size:             {model_size / 1024:.1f} KB")
    print(f"  Epochs trained:         {metrics['epochs_trained']}")
    print(f"\n  Top 5 worst shapes:")
    for shape, mae in metrics["worst_shapes"][:5]:
        print(f"    {shape}: MAE {mae:.6f}")


if __name__ == "__main__":
    main()
