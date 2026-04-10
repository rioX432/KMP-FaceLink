#!/usr/bin/env python3
"""
Train N-euro Predictor weights for KMP-FaceLink blend shape smoothing.

Implements the training pipeline from:
  Shao et al., "N-euro Predictor: A Neural Network Approach for Smoothing and
  Predicting Motion Trajectory", Proc. ACM IMWUT 2023.

Usage:
  1. Collect blend shape CSVs via the sample app (one CSV per recording session).
     Each CSV row: timestamp_ms, blendshape_name, value
  2. Run: python train_neuro_weights.py --data-dir ./recordings --output weights.kt
  3. Copy the generated Kotlin code into NeuroPredictorWeights.kt

Requirements:
  pip install torch numpy pandas

See docs/research/neuro-predictor-smoothing.md for background.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import numpy as np
import torch
import torch.nn as nn
from torch.utils.data import DataLoader, TensorDataset

# -- Architecture constants (must match Kotlin code) --
HIDDEN_SIZE = 32
FORECAST_SIZE = 1
DEFAULT_WINDOW_SIZE = 8
INPUT_SIZE = DEFAULT_WINDOW_SIZE + 1  # 9


class NBeatsBlock(nn.Module):
    """Single N-BEATS block: FC(in->hidden, ReLU) -> FC(hidden->out)."""

    def __init__(self, input_size: int, hidden_size: int, output_size: int):
        super().__init__()
        self.fc1 = nn.Linear(input_size, hidden_size)
        self.fc2 = nn.Linear(hidden_size, output_size)
        self.relu = nn.ReLU()

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        h = self.relu(self.fc1(x))
        return self.fc2(h)


class NEuroPredictor(nn.Module):
    """2-block N-BEATS: trend + seasonality with residual connection."""

    def __init__(self, input_size: int = INPUT_SIZE, hidden_size: int = HIDDEN_SIZE,
                 forecast_size: int = FORECAST_SIZE):
        super().__init__()
        output_size = input_size + forecast_size
        self.input_size = input_size
        self.forecast_size = forecast_size

        self.trend_block = NBeatsBlock(input_size, hidden_size, output_size)
        self.seasonality_block = NBeatsBlock(input_size, hidden_size, output_size)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        # Block 1: trend
        out1 = self.trend_block(x)
        backcast1 = out1[:, :self.input_size]
        forecast1 = out1[:, self.input_size:]

        # Residual
        residual = x - backcast1

        # Block 2: seasonality
        out2 = self.seasonality_block(residual)
        forecast2 = out2[:, self.input_size:]

        return forecast1 + forecast2


def create_pseudo_ground_truth(series: np.ndarray, k: int = 3) -> np.ndarray:
    """
    Double-sided moving average for pseudo ground truth labels.
    f(t) = mean(series[t-k:t+k+1]) -- zero lag since it sees the future.
    """
    n = len(series)
    smoothed = np.copy(series)
    for t in range(k, n - k):
        smoothed[t] = np.mean(series[t - k:t + k + 1])
    return smoothed


def build_dataset(series: np.ndarray, window: int = INPUT_SIZE,
                  k: int = 3) -> tuple[np.ndarray, np.ndarray]:
    """
    Build (input_window, target_forecast) pairs from a 1D time series.
    """
    gt = create_pseudo_ground_truth(series, k)
    xs, ys = [], []
    for t in range(window, len(gt) - FORECAST_SIZE):
        xs.append(series[t - window:t])
        ys.append(gt[t:t + FORECAST_SIZE])
    return np.array(xs, dtype=np.float32), np.array(ys, dtype=np.float32)


def load_blend_shape_data(data_dir: Path) -> list[np.ndarray]:
    """Load all CSV recordings, return list of per-blendshape time series."""
    import pandas as pd

    all_series = []
    for csv_path in sorted(data_dir.glob("*.csv")):
        df = pd.read_csv(csv_path)
        for shape_name in df["blendshape_name"].unique():
            values = df[df["blendshape_name"] == shape_name]["value"].values
            if len(values) > INPUT_SIZE + 10:
                all_series.append(values.astype(np.float32))
    return all_series


def train(model: NEuroPredictor, train_loader: DataLoader, *,
          lr: float = 5e-4, epochs: int = 100, patience: int = 30,
          alpha: float = 0.5) -> None:
    """Train with combined position + acceleration loss."""
    optimizer = torch.optim.Adam(model.parameters(), lr=lr)
    scheduler = torch.optim.lr_scheduler.ReduceLROnPlateau(
        optimizer, factor=0.5, patience=10)

    best_loss = float("inf")
    wait = 0

    for epoch in range(epochs):
        model.train()
        total_loss = 0.0
        for x_batch, y_batch in train_loader:
            optimizer.zero_grad()
            pred = model(x_batch)

            # Position loss (MAE)
            loss_pos = nn.functional.l1_loss(pred, y_batch)

            # Acceleration loss (MAE of 2nd derivative approx)
            if x_batch.shape[1] >= 3:
                input_acc = x_batch[:, -1] - 2 * x_batch[:, -2] + x_batch[:, -3]
                pred_acc = pred[:, 0] - 2 * x_batch[:, -1] + x_batch[:, -2]
                loss_acc = nn.functional.l1_loss(pred_acc, input_acc)
            else:
                loss_acc = torch.tensor(0.0)

            loss = loss_pos + alpha * loss_acc
            loss.backward()
            optimizer.step()
            total_loss += loss.item()

        avg_loss = total_loss / len(train_loader)
        scheduler.step(avg_loss)

        if avg_loss < best_loss:
            best_loss = avg_loss
            wait = 0
        else:
            wait += 1
            if wait >= patience:
                print(f"Early stop at epoch {epoch + 1}, best loss: {best_loss:.6f}")
                break

        if (epoch + 1) % 10 == 0:
            print(f"Epoch {epoch + 1:3d} | loss: {avg_loss:.6f} | lr: {optimizer.param_groups[0]['lr']:.6f}")


def export_kotlin(model: NEuroPredictor, output_path: Path) -> None:
    """Export model weights as Kotlin source code."""
    def fmt_array(arr: np.ndarray) -> str:
        flat = arr.flatten()
        items = ", ".join(f"{v:.8f}f" for v in flat)
        return f"floatArrayOf({items})"

    sd = model.state_dict()
    lines = [
        "// Auto-generated by tools/train_neuro_weights.py -- do not edit manually.",
        "// Replace createDefaultWeights() body with this function.",
        "",
        "internal fun createTrainedWeights(): NeuroPredictorWeights = NeuroPredictorWeights(",
        "    trendBlock = BlockWeights(",
        f"        fc1Weight = {fmt_array(sd['trend_block.fc1.weight'].numpy())},",
        f"        fc1Bias = {fmt_array(sd['trend_block.fc1.bias'].numpy())},",
        f"        fc2Weight = {fmt_array(sd['trend_block.fc2.weight'].numpy())},",
        f"        fc2Bias = {fmt_array(sd['trend_block.fc2.bias'].numpy())},",
        "    ),",
        "    seasonalityBlock = BlockWeights(",
        f"        fc1Weight = {fmt_array(sd['seasonality_block.fc1.weight'].numpy())},",
        f"        fc1Bias = {fmt_array(sd['seasonality_block.fc1.bias'].numpy())},",
        f"        fc2Weight = {fmt_array(sd['seasonality_block.fc2.weight'].numpy())},",
        f"        fc2Bias = {fmt_array(sd['seasonality_block.fc2.bias'].numpy())},",
        "    ),",
        ")",
    ]

    output_path.write_text("\n".join(lines) + "\n")
    print(f"Exported weights to {output_path}")

    # Print model stats
    total_params = sum(p.numel() for p in model.parameters())
    print(f"Total parameters: {total_params}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--data-dir", type=Path, required=True,
                        help="Directory of blend shape CSV recordings")
    parser.add_argument("--output", type=Path, default=Path("weights.kt"),
                        help="Output Kotlin file (default: weights.kt)")
    parser.add_argument("--epochs", type=int, default=100)
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--lr", type=float, default=5e-4)
    parser.add_argument("--alpha", type=float, default=0.5,
                        help="Acceleration loss weight (0=no smoothness, 1=max smoothness)")
    args = parser.parse_args()

    if not args.data_dir.exists():
        print(f"Error: {args.data_dir} does not exist", file=sys.stderr)
        sys.exit(1)

    print("Loading data...")
    all_series = load_blend_shape_data(args.data_dir)
    if not all_series:
        print("Error: no valid time series found in CSVs", file=sys.stderr)
        sys.exit(1)

    print(f"Found {len(all_series)} time series")

    # Build dataset from all series
    all_x, all_y = [], []
    for series in all_series:
        x, y = build_dataset(series)
        all_x.append(x)
        all_y.append(y)

    x_train = torch.from_numpy(np.concatenate(all_x))
    y_train = torch.from_numpy(np.concatenate(all_y))
    print(f"Training samples: {len(x_train)}")

    loader = DataLoader(TensorDataset(x_train, y_train),
                        batch_size=args.batch_size, shuffle=True)

    model = NEuroPredictor()
    print(f"Model params: {sum(p.numel() for p in model.parameters())}")

    train(model, loader, lr=args.lr, epochs=args.epochs, alpha=args.alpha)
    export_kotlin(model, args.output)


if __name__ == "__main__":
    main()
