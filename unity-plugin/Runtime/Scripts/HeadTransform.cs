// Copyright (c) KMP-FaceLink contributors. Licensed under Apache-2.0.

using UnityEngine;

namespace KmpFaceLink
{
    /// <summary>
    /// 3D head transformation data from face tracking.
    /// Uses a right-handed coordinate system (same as KMP-FaceLink core).
    /// Unity uses left-handed, so conversion helpers are provided.
    /// </summary>
    public struct HeadTransform
    {
        /// <summary>Rotation around X-axis (nodding up/down), in degrees.</summary>
        public float Pitch;

        /// <summary>Rotation around Y-axis (turning left/right), in degrees.</summary>
        public float Yaw;

        /// <summary>Rotation around Z-axis (tilting head), in degrees.</summary>
        public float Roll;

        /// <summary>Translation along X-axis (left/right).</summary>
        public float PositionX;

        /// <summary>Translation along Y-axis (up/down).</summary>
        public float PositionY;

        /// <summary>Translation along Z-axis (forward/backward).</summary>
        public float PositionZ;

        /// <summary>
        /// Convert to Unity Quaternion.
        /// Flips yaw and roll signs to convert from right-handed to left-handed.
        /// </summary>
        public Quaternion ToUnityRotation()
        {
            return Quaternion.Euler(Pitch, -Yaw, -Roll);
        }

        /// <summary>
        /// Convert to Unity Vector3 position.
        /// Flips X-axis to convert from right-handed to left-handed.
        /// </summary>
        public Vector3 ToUnityPosition()
        {
            return new Vector3(-PositionX, PositionY, PositionZ);
        }
    }
}
