import type { BlendShapeName } from './BlendShape';

/** Parsed face tracking data from the binary buffer. */
export interface FaceTrackingData {
  /**
   * 52 float slots indexed by BlendShape ordinal.
   *
   * A slot the estimator did not produce still occupies four bytes on the wire. Consult
   * {@link isObserved} before reading one directly, or use {@link valueOrNull}, which does it.
   */
  blendShapes: Float32Array;

  /** Whether the estimator produced a value for this ordinal in this frame. */
  isObserved: (ordinal: number) => boolean;

  /** The value for this ordinal, or null when the estimator did not produce it. */
  valueOrNull: (ordinal: number) => number | null;

  /** Head transform: pitch, yaw, roll (radians), posX, posY, posZ (meters). */
  headTransform: Float32Array;

  /** Whether face tracking is currently active. */
  isTracking: boolean;
}

/** Parsed hand tracking data from the binary buffer. */
export interface HandTrackingData {
  /** Number of tracked hands (0-2). */
  handCount: number;

  /** Per-hand data. Length matches handCount. */
  hands: TrackedHand[];
}

/** Single tracked hand data. */
export interface TrackedHand {
  /** 21 landmarks * 3 coords (x, y, z) = 63 floats. */
  landmarks: Float32Array;

  /** Hand side: 'left', 'right', or 'unknown'. */
  handedness: 'left' | 'right' | 'unknown';
}

/** Configuration for FaceLink initialization. */
export interface FaceLinkConfig {
  /** Enable face tracking (blend shapes + head transform). Default: true. */
  enableFace?: boolean;

  /** Enable hand tracking (21 landmarks per hand). Default: false. */
  enableHands?: boolean;
}

/** Blend shape name-value pair for convenience access. */
export type BlendShapeMap = Partial<Record<BlendShapeName, number>>;
