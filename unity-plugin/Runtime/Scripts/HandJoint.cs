// Copyright (c) KMP-FaceLink contributors. Licensed under Apache-2.0.

namespace KmpFaceLink
{
    /// <summary>
    /// The 21 hand landmarks defined by MediaPipe Hand Landmarker.
    /// Ordinal values match MediaPipe landmark indices (0-20) and
    /// KMP-FaceLink's HandJoint enum.
    /// </summary>
    public enum HandJoint
    {
        Wrist = 0,
        ThumbCmc = 1,
        ThumbMcp = 2,
        ThumbIp = 3,
        ThumbTip = 4,
        IndexFingerMcp = 5,
        IndexFingerPip = 6,
        IndexFingerDip = 7,
        IndexFingerTip = 8,
        MiddleFingerMcp = 9,
        MiddleFingerPip = 10,
        MiddleFingerDip = 11,
        MiddleFingerTip = 12,
        RingFingerMcp = 13,
        RingFingerPip = 14,
        RingFingerDip = 15,
        RingFingerTip = 16,
        PinkyMcp = 17,
        PinkyPip = 18,
        PinkyDip = 19,
        PinkyTip = 20,
    }

    /// <summary>
    /// Total number of hand landmark joints.
    /// </summary>
    public static class HandJointConstants
    {
        public const int Count = 21;
    }

    /// <summary>
    /// Hand side classification.
    /// </summary>
    public enum Handedness
    {
        Left,
        Right,
    }
}
