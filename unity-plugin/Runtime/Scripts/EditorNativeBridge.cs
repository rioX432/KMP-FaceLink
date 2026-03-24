// Copyright (c) KMP-FaceLink contributors. Licensed under Apache-2.0.

#if UNITY_EDITOR
using UnityEngine;

namespace KmpFaceLink
{
    /// <summary>
    /// Editor stub that provides simulated tracking data for development.
    /// Uses sine waves to animate blend shapes so developers can test
    /// avatar integration without a device.
    /// </summary>
    internal class EditorNativeBridge : INativeBridge
    {
        public bool IsInitialized { get; private set; }
        public bool IsTracking { get; private set; }

        private float _startTime;

        public bool Initialize(bool enableFace, bool enableHands)
        {
            Debug.Log("[KmpFaceLink] Editor mode: using simulated tracking data.");
            IsInitialized = true;
            return true;
        }

        public void StartTracking()
        {
            _startTime = Time.realtimeSinceStartup;
            IsTracking = true;
        }

        public void StopTracking()
        {
            IsTracking = false;
        }

        public void Dispose()
        {
            IsInitialized = false;
            IsTracking = false;
        }

        public bool PollFaceData(FaceTrackingData data)
        {
            if (!IsTracking) return false;

            float t = Time.realtimeSinceStartup - _startTime;

            // Simulate eye blinking every ~3 seconds
            float blinkCycle = Mathf.PingPong(t * 2f, 3f);
            float blinkValue = blinkCycle < 0.15f ? 1f : 0f;
            data.SetBlendShape(BlendShape.EyeBlinkLeft, blinkValue);
            data.SetBlendShape(BlendShape.EyeBlinkRight, blinkValue);

            // Simulate mouth movement
            data.SetBlendShape(BlendShape.JawOpen, Mathf.Abs(Mathf.Sin(t * 1.5f)) * 0.3f);
            data.SetBlendShape(BlendShape.MouthSmileLeft, Mathf.Sin(t * 0.8f) * 0.3f + 0.3f);
            data.SetBlendShape(BlendShape.MouthSmileRight, Mathf.Sin(t * 0.8f) * 0.3f + 0.3f);

            // Simulate gentle head rotation
            data.HeadTransform = new HeadTransform
            {
                Pitch = Mathf.Sin(t * 0.5f) * 5f,
                Yaw = Mathf.Sin(t * 0.3f) * 10f,
                Roll = Mathf.Sin(t * 0.7f) * 3f,
            };

            data.TimestampMs = (long)(t * 1000f);
            data.IsTracking = true;
            return true;
        }

        public bool PollHandData(HandTrackingData data)
        {
            if (!IsTracking) return false;

            // Simulate one hand with a waving motion
            float t = Time.realtimeSinceStartup - _startTime;

            data.HandCount = 1;
            data.IsTracking = true;
            data.TimestampMs = (long)(t * 1000f);

            data.Hands[0].Handedness = Handedness.Right;

            // Place landmarks in a simple spread pattern
            for (int j = 0; j < HandJointConstants.Count; j++)
            {
                float fingerOffset = j / (float)HandJointConstants.Count;
                data.Hands[0].Landmarks[j] = new HandLandmarkPoint
                {
                    Joint = (HandJoint)j,
                    X = 0.5f + Mathf.Sin(t + fingerOffset) * 0.1f,
                    Y = 0.3f + fingerOffset * 0.4f,
                    Z = 0f,
                };
            }

            return true;
        }
    }
}
#endif
