// Turbo Module spec — this file is the Codegen input.
// React Native Codegen generates native interface bindings from this spec.

import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
  /**
   * Initialize the tracker. Must be called before start().
   * @param enableFace - Enable face tracking (blend shapes + head transform)
   * @param enableHands - Enable hand tracking (21 landmarks per hand)
   * @returns true if initialization succeeded
   */
  initialize(enableFace: boolean, enableHands: boolean): boolean;

  /** Start tracking. Begins collecting data into internal buffers. */
  start(): void;

  /** Stop tracking. Can be restarted with start(). */
  stop(): void;

  /** Release all resources. Cannot be used after this. */
  dispose(): void;

  /**
   * Get the latest face tracking data as a binary buffer.
   *
   * Layout (little-endian):
   *   - float32[52]: blend shapes (ARKit ordinal order)
   *   - float32[6]: head transform (pitch, yaw, roll, posX, posY, posZ)
   *   - int8: isTracking (1 = tracking, 0 = not tracking)
   *
   * Total: 233 bytes (52*4 + 6*4 + 1)
   *
   * Returns null if no face data is available yet.
   */
  getLatestFaceDataV2(): ArrayBuffer | null;

  /**
   * Get the latest hand tracking data as a binary buffer.
   *
   * Layout (little-endian):
   *   - int32: hand count (0-2)
   *   - For each hand:
   *     - float32[63]: 21 landmarks * 3 coords (x, y, z)
   *     - int32: handedness (0=left, 1=right, 2=unknown)
   *
   * Total (2 hands): 4 + 2*(63*4 + 4) = 520 bytes
   *
   * Returns null if no hand data is available yet.
   */
  getLatestHandData(): ArrayBuffer | null;

  /** Whether tracking is currently active. */
  isTracking(): boolean;
}

export default TurboModuleRegistry.getEnforcing<Spec>('FaceLinkModule');
