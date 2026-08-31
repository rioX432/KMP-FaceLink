// react-native-kmp-facelink
// Real-time face and hand tracking for React Native via KMP-FaceLink.

import NativeFaceLink from './NativeFaceLink';
import { BLEND_SHAPE_COUNT, HEAD_TRANSFORM_COUNT, schemaFingerprint } from './BlendShape';
import type {
  FaceTrackingData,
  HandTrackingData,
  TrackedHand,
  FaceLinkConfig,
} from './types';

export { BlendShape, BLEND_SHAPE_COUNT, HEAD_TRANSFORM_COUNT } from './BlendShape';
export type { BlendShapeName } from './BlendShape';
export type {
  FaceTrackingData,
  HandTrackingData,
  TrackedHand,
  FaceLinkConfig,
  BlendShapeMap,
} from './types';

const HANDEDNESS_LABELS: readonly ('left' | 'right' | 'unknown')[] = [
  'left',
  'right',
  'unknown',
];

/**
 * High-level wrapper around the KMP-FaceLink Turbo Module.
 *
 * Provides typed access to face and hand tracking data using a polling model.
 * Call {@link getLatestFaceData} or {@link getLatestHandData} each frame
 * (e.g., in a requestAnimationFrame loop) to read the latest tracking results.
 *
 * @example
 * ```ts
 * const faceLink = new FaceLink({ enableFace: true });
 * faceLink.start();
 *
 * function onFrame() {
 *   const data = faceLink.getLatestFaceData();
 *   if (data) {
 *     const jawOpen = data.valueOrNull(BlendShape.jawOpen);
 *     // ... use blend shapes for avatar animation
 *   }
 *   requestAnimationFrame(onFrame);
 * }
 * requestAnimationFrame(onFrame);
 * ```
 */
export class FaceLink {
  private initialized = false;

  /**
   * Create and initialize a FaceLink instance.
   * @param config - Tracking configuration
   */
  constructor(config: FaceLinkConfig = {}) {
    const enableFace = config.enableFace ?? true;
    const enableHands = config.enableHands ?? false;
    this.initialized = NativeFaceLink.initialize(enableFace, enableHands);
  }

  /** Start tracking. Begins collecting data into internal buffers. */
  start(): void {
    if (!this.initialized) return;
    NativeFaceLink.start();
  }

  /** Stop tracking. Can be restarted with start(). */
  stop(): void {
    if (!this.initialized) return;
    NativeFaceLink.stop();
  }

  /** Release all resources. Cannot be used after this. */
  dispose(): void {
    if (!this.initialized) return;
    NativeFaceLink.dispose();
    this.initialized = false;
  }

  /** Whether tracking is currently active. */
  isTracking(): boolean {
    if (!this.initialized) return false;
    return NativeFaceLink.isTracking();
  }

  /**
   * Get the latest face tracking data.
   *
   * Returns parsed face data with typed Float32Array views, or null if
   * no data is available. This is a synchronous JSI call — safe to call
   * every frame in a requestAnimationFrame loop.
   */
  getLatestFaceData(): FaceTrackingData | null {
    if (!this.initialized) return null;
    const buffer = NativeFaceLink.getLatestFaceDataV2();
    if (!buffer) return null;
    return parseFaceData(buffer);
  }

  /**
   * Get the latest hand tracking data.
   *
   * Returns parsed hand data, or null if no data is available.
   */
  getLatestHandData(): HandTrackingData | null {
    if (!this.initialized) return null;
    const buffer = NativeFaceLink.getLatestHandData();
    if (!buffer) return null;
    return parseHandData(buffer);
  }
}

/**
 * Parse the binary face data buffer into a typed FaceTrackingData object.
 *
 * Buffer layout:
 *   - float32[52]: blend shapes
 *   - float32[6]: head transform
 *   - int8[1]: isTracking flag
 */
/** "FLNK" little-endian. */
const FACE_WIRE_MAGIC = 0x4b4e4c46;
const FACE_WIRE_VERSION = 2;
const FACE_HEADER_BYTES = 20;

/**
 * Decodes a v2 face buffer, refusing anything that is not exactly that.
 *
 * Every header field is checked before a single value is read. A buffer whose magic, version,
 * channel count, schema fingerprint or length disagrees is rejected outright: decoding it anyway
 * would produce a full set of plausible numbers with no indication that they mean nothing.
 */
function parseFaceData(buffer: ArrayBuffer): FaceTrackingData {
  if (buffer.byteLength !== FACE_HEADER_BYTES + (BLEND_SHAPE_COUNT + HEAD_TRANSFORM_COUNT) * 4 + 1) {
    throw new Error(`FaceLink: unexpected face buffer length ${buffer.byteLength}`);
  }
  const header = new DataView(buffer);
  const magic = header.getUint32(0, true);
  if (magic !== FACE_WIRE_MAGIC) {
    throw new Error(`FaceLink: not a face buffer (magic 0x${magic.toString(16)})`);
  }
  const version = header.getUint16(4, true);
  if (version !== FACE_WIRE_VERSION) {
    throw new Error(`FaceLink: face wire version ${version}, expected ${FACE_WIRE_VERSION}`);
  }
  const channelCount = header.getUint16(6, true);
  if (channelCount !== BLEND_SHAPE_COUNT) {
    throw new Error(`FaceLink: native reports ${channelCount} channels, this build knows ${BLEND_SHAPE_COUNT}`);
  }
  const nativeFingerprint = header.getInt32(8, true);
  if (nativeFingerprint !== schemaFingerprint()) {
    throw new Error(
      'FaceLink: blend shape schema differs between native and JavaScript; ordinals would not line up',
    );
  }
  const observedLow = header.getUint32(12, true);
  const observedHigh = header.getUint32(16, true);

  const floatView = new Float32Array(buffer, FACE_HEADER_BYTES, BLEND_SHAPE_COUNT + HEAD_TRANSFORM_COUNT);
  const byteView = new Uint8Array(buffer);
  const trackingFlagOffset =
    FACE_HEADER_BYTES + (BLEND_SHAPE_COUNT + HEAD_TRANSFORM_COUNT) * 4;

  const isObserved = (ordinal: number): boolean =>
    ordinal < 32
      ? (observedLow & (1 << ordinal)) !== 0
      : (observedHigh & (1 << (ordinal - 32))) !== 0;

  return {
    blendShapes: floatView.slice(0, BLEND_SHAPE_COUNT),
    headTransform: floatView.slice(BLEND_SHAPE_COUNT, BLEND_SHAPE_COUNT + HEAD_TRANSFORM_COUNT),
    isTracking: byteView[trackingFlagOffset] === 1,
    isObserved,
    valueOrNull: (ordinal: number) => (isObserved(ordinal) ? floatView[ordinal] : null),
  };
}

/**
 * Parse the binary hand data buffer into a typed HandTrackingData object.
 *
 * Buffer layout:
 *   - int32: hand count
 *   - For each hand:
 *     - float32[63]: landmarks (21 joints * 3 coords)
 *     - int32: handedness
 */
function parseHandData(buffer: ArrayBuffer): HandTrackingData {
  const dataView = new DataView(buffer);
  const handCount = dataView.getInt32(0, true); // little-endian
  const hands: TrackedHand[] = [];

  const LANDMARKS_PER_HAND = 63;
  const HAND_BLOCK_BYTES = LANDMARKS_PER_HAND * 4 + 4; // 63 floats + 1 int32

  for (let i = 0; i < handCount && i < 2; i++) {
    const baseOffset = 4 + i * HAND_BLOCK_BYTES;
    const landmarks = new Float32Array(buffer, baseOffset, LANDMARKS_PER_HAND);
    const handednessValue = dataView.getInt32(baseOffset + LANDMARKS_PER_HAND * 4, true);

    hands.push({
      landmarks: landmarks.slice(),
      handedness: HANDEDNESS_LABELS[handednessValue] ?? 'unknown',
    });
  }

  return { handCount, hands };
}
