import type { BlendShapeName } from './BlendShape';

/** Parsed face tracking data from the binary buffer. */
export interface FaceTrackingData {
  /** 52 blend shape values (0.0 - 1.0), indexed by BlendShape ordinal. */
  blendShapes: Float32Array;

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
