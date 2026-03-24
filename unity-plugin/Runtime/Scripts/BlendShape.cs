// Copyright (c) KMP-FaceLink contributors. Licensed under Apache-2.0.

namespace KmpFaceLink
{
    /// <summary>
    /// ARKit-compatible 52 blend shape parameters.
    /// Naming follows Apple ARKit ARFaceAnchor.BlendShapeLocation conventions.
    /// Ordinal values match the KMP-FaceLink BlendShape enum order.
    /// </summary>
    public enum BlendShape
    {
        // Eyes (14)
        EyeBlinkLeft = 0,
        EyeBlinkRight = 1,
        EyeLookDownLeft = 2,
        EyeLookDownRight = 3,
        EyeLookInLeft = 4,
        EyeLookInRight = 5,
        EyeLookOutLeft = 6,
        EyeLookOutRight = 7,
        EyeLookUpLeft = 8,
        EyeLookUpRight = 9,
        EyeSquintLeft = 10,
        EyeSquintRight = 11,
        EyeWideLeft = 12,
        EyeWideRight = 13,

        // Jaw (4)
        JawForward = 14,
        JawLeft = 15,
        JawOpen = 16,
        JawRight = 17,

        // Mouth (23)
        MouthClose = 18,
        MouthDimpleLeft = 19,
        MouthDimpleRight = 20,
        MouthFrownLeft = 21,
        MouthFrownRight = 22,
        MouthFunnel = 23,
        MouthLeft = 24,
        MouthLowerDownLeft = 25,
        MouthLowerDownRight = 26,
        MouthPressLeft = 27,
        MouthPressRight = 28,
        MouthPucker = 29,
        MouthRight = 30,
        MouthRollLower = 31,
        MouthRollUpper = 32,
        MouthShrugLower = 33,
        MouthShrugUpper = 34,
        MouthSmileLeft = 35,
        MouthSmileRight = 36,
        MouthStretchLeft = 37,
        MouthStretchRight = 38,
        MouthUpperUpLeft = 39,
        MouthUpperUpRight = 40,

        // Brow (5)
        BrowDownLeft = 41,
        BrowDownRight = 42,
        BrowInnerUp = 43,
        BrowOuterUpLeft = 44,
        BrowOuterUpRight = 45,

        // Cheek (3)
        CheekPuff = 46,
        CheekSquintLeft = 47,
        CheekSquintRight = 48,

        // Nose (2)
        NoseSneerLeft = 49,
        NoseSneerRight = 50,

        // Tongue (1)
        TongueOut = 51,
    }

    /// <summary>
    /// Total number of ARKit blend shapes.
    /// </summary>
    public static class BlendShapeConstants
    {
        public const int Count = 52;
    }
}
