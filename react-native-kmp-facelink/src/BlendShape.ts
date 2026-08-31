/**
 * ARKit-compatible 52 blend shape indices.
 *
 * These ordinals match the KMP-FaceLink BlendShape enum and the float array
 * layout returned by getLatestFaceData(). Use these constants to index into
 * the Float32Array view of the face data buffer.
 *
 * @example
 * ```ts
 * const faceData = faceLink.getLatestFaceData();
 * if (faceData) {
 *   const view = new Float32Array(faceData);
 *   const jawOpen = view[BlendShape.jawOpen];
 * }
 * ```
 */
export const BlendShape = {
  // Eyes (14)
  eyeBlinkLeft: 0,
  eyeBlinkRight: 1,
  eyeLookDownLeft: 2,
  eyeLookDownRight: 3,
  eyeLookInLeft: 4,
  eyeLookInRight: 5,
  eyeLookOutLeft: 6,
  eyeLookOutRight: 7,
  eyeLookUpLeft: 8,
  eyeLookUpRight: 9,
  eyeSquintLeft: 10,
  eyeSquintRight: 11,
  eyeWideLeft: 12,
  eyeWideRight: 13,

  // Jaw (4)
  jawForward: 14,
  jawLeft: 15,
  jawOpen: 16,
  jawRight: 17,

  // Mouth (23)
  mouthClose: 18,
  mouthDimpleLeft: 19,
  mouthDimpleRight: 20,
  mouthFrownLeft: 21,
  mouthFrownRight: 22,
  mouthFunnel: 23,
  mouthLeft: 24,
  mouthLowerDownLeft: 25,
  mouthLowerDownRight: 26,
  mouthPressLeft: 27,
  mouthPressRight: 28,
  mouthPucker: 29,
  mouthRight: 30,
  mouthRollLower: 31,
  mouthRollUpper: 32,
  mouthShrugLower: 33,
  mouthShrugUpper: 34,
  mouthSmileLeft: 35,
  mouthSmileRight: 36,
  mouthStretchLeft: 37,
  mouthStretchRight: 38,
  mouthUpperUpLeft: 39,
  mouthUpperUpRight: 40,

  // Brow (5)
  browDownLeft: 41,
  browDownRight: 42,
  browInnerUp: 43,
  browOuterUpLeft: 44,
  browOuterUpRight: 45,

  // Cheek (3)
  cheekPuff: 46,
  cheekSquintLeft: 47,
  cheekSquintRight: 48,

  // Nose (2)
  noseSneerLeft: 49,
  noseSneerRight: 50,

  // Tongue (1)
  tongueOut: 51,
} as const;

export type BlendShapeName = keyof typeof BlendShape;

/** Total number of ARKit blend shapes. */
export const BLEND_SHAPE_COUNT = 52;

/** Number of head transform floats (pitch, yaw, roll, posX, posY, posZ). */
export const HEAD_TRANSFORM_COUNT = 6;

/**
 * FNV-1a over the blend shape names in ordinal order.
 *
 * The native side computes the same hash over its own enum and writes it into every face buffer.
 * This table is hand-written, so without that check a shape added or reordered natively would shift
 * every ordinal past it, and each value would be read as a believable number for the wrong
 * expression — no crash, no type error, just quietly wrong faces.
 */
export function schemaFingerprint(): number {
  const names = Object.keys(BlendShape) as BlendShapeName[];
  const ordered = names
    .slice()
    .sort((a, b) => BlendShape[a] - BlendShape[b])
    .join(',');
  let hash = 0x811c9dc5;
  for (let i = 0; i < ordered.length + 1; i += 1) {
    const code = i < ordered.length ? ordered.charCodeAt(i) : ','.charCodeAt(0);
    hash = Math.imul(hash ^ code, 16777619);
  }
  return hash | 0;
}
