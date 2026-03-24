// Copyright (c) KMP-FaceLink contributors. Licensed under Apache-2.0.

using UnityEngine;

namespace KmpFaceLink
{
    /// <summary>
    /// A single hand landmark point with normalized coordinates.
    /// Coordinates are normalized to [0.0, 1.0] relative to the input image.
    /// </summary>
    public struct HandLandmarkPoint
    {
        /// <summary>The hand joint this point represents.</summary>
        public HandJoint Joint;

        /// <summary>Horizontal position, normalized 0.0 (left) to 1.0 (right).</summary>
        public float X;

        /// <summary>Vertical position, normalized 0.0 (top) to 1.0 (bottom).</summary>
        public float Y;

        /// <summary>
        /// Depth relative to the wrist (negative = closer to camera).
        /// Always 0 on iOS (Vision framework does not provide depth).
        /// </summary>
        public float Z;

        /// <summary>
        /// Convert to Unity Vector3.
        /// Flips Y to match Unity's bottom-up convention.
        /// </summary>
        public Vector3 ToUnityPosition()
        {
            return new Vector3(X, 1f - Y, Z);
        }
    }

    /// <summary>
    /// Tracking result for a single detected hand.
    /// </summary>
    public class TrackedHand
    {
        /// <summary>Whether the hand is left or right.</summary>
        public Handedness Handedness { get; set; }

        /// <summary>
        /// The 21 hand landmark points.
        /// Indexed by <see cref="HandJoint"/> enum ordinal.
        /// </summary>
        public HandLandmarkPoint[] Landmarks { get; private set; }

        public TrackedHand()
        {
            Landmarks = new HandLandmarkPoint[HandJointConstants.Count];
        }

        /// <summary>
        /// Get the landmark point for a specific joint.
        /// </summary>
        public HandLandmarkPoint GetLandmark(HandJoint joint)
        {
            return Landmarks[(int)joint];
        }
    }

    /// <summary>
    /// Complete hand tracking data for a single frame.
    /// Maps directly to KMP-FaceLink's HandTrackingData model.
    /// </summary>
    public class HandTrackingData
    {
        /// <summary>Tracked hands (up to maxHands configured in tracker).</summary>
        public TrackedHand[] Hands { get; set; }

        /// <summary>Number of hands currently tracked.</summary>
        public int HandCount { get; set; }

        /// <summary>Frame timestamp in milliseconds.</summary>
        public long TimestampMs { get; set; }

        /// <summary>Whether any hands are currently being tracked.</summary>
        public bool IsTracking { get; set; }

        /// <summary>Maximum number of hands to track (default 2).</summary>
        private const int MaxHands = 2;

        public HandTrackingData()
        {
            Hands = new TrackedHand[MaxHands];
            for (int i = 0; i < MaxHands; i++)
            {
                Hands[i] = new TrackedHand();
            }
        }

        /// <summary>
        /// Get the first (primary) tracked hand, or null if not tracking.
        /// </summary>
        public TrackedHand GetPrimaryHand()
        {
            return HandCount > 0 ? Hands[0] : null;
        }

        /// <summary>
        /// Reset all tracking data.
        /// </summary>
        public void Clear()
        {
            HandCount = 0;
            TimestampMs = 0;
            IsTracking = false;
        }
    }
}
