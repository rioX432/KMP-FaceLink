// Copyright (c) KMP-FaceLink contributors. Licensed under Apache-2.0.

namespace KmpFaceLink
{
    /// <summary>
    /// Complete face tracking data for a single frame.
    /// Maps directly to KMP-FaceLink's FaceTrackingData model.
    /// </summary>
    public class FaceTrackingData
    {
        /// <summary>
        /// 52 ARKit-compatible blend shape values (0.0-1.0).
        /// Indexed by <see cref="BlendShape"/> enum ordinal.
        /// </summary>
        public float[] BlendShapes { get; private set; }

        /// <summary>6DoF head position and rotation.</summary>
        public HeadTransform HeadTransform { get; set; }

        /// <summary>Frame timestamp in milliseconds (monotonic clock).</summary>
        public long TimestampMs { get; set; }

        /// <summary>Whether a face is currently being tracked.</summary>
        public bool IsTracking { get; set; }

        public FaceTrackingData()
        {
            BlendShapes = new float[BlendShapeConstants.Count];
        }

        /// <summary>
        /// Get the value of a specific blend shape.
        /// </summary>
        public float GetBlendShape(BlendShape shape)
        {
            return BlendShapes[(int)shape];
        }

        /// <summary>
        /// Set the value of a specific blend shape.
        /// </summary>
        public void SetBlendShape(BlendShape shape, float value)
        {
            BlendShapes[(int)shape] = value;
        }

        /// <summary>
        /// Reset all blend shapes to 0.
        /// </summary>
        public void Clear()
        {
            for (int i = 0; i < BlendShapes.Length; i++)
            {
                BlendShapes[i] = 0f;
            }
            HeadTransform = default;
            TimestampMs = 0;
            IsTracking = false;
        }
    }
}
