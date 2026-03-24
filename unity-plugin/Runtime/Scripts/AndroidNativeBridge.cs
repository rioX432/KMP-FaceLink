// Copyright (c) KMP-FaceLink contributors. Licensed under Apache-2.0.

#if UNITY_ANDROID && !UNITY_EDITOR
using UnityEngine;

namespace KmpFaceLink
{
    /// <summary>
    /// Android native bridge using AndroidJavaObject to call into the
    /// KMP-FaceLink AAR library via JNI.
    ///
    /// Architecture:
    /// C# -> AndroidJavaObject (JNI) -> Kotlin KMP-FaceLink AAR
    ///
    /// The AAR is expected at:
    ///   Plugins/Android/kmp-facelink-release.aar
    ///
    /// The bridge calls into a thin Java facade class that wraps the
    /// KMP-FaceLink Kotlin API, converting Flow-based streaming data
    /// into a pollable buffer suitable for Unity's frame-based loop.
    /// </summary>
    internal class AndroidNativeBridge : INativeBridge
    {
        private const string FacadeClass = "io.github.kmpfacelink.unity.FaceLinkUnityFacade";

        private AndroidJavaObject _facade;
        private long _lastFaceTimestamp;
        private long _lastHandTimestamp;

        public bool IsInitialized { get; private set; }
        public bool IsTracking { get; private set; }

        public bool Initialize(bool enableFace, bool enableHands)
        {
            try
            {
                // Get Unity's current Activity as the Android Context
                using (var unityPlayer = new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
                using (var activity = unityPlayer.GetStatic<AndroidJavaObject>("currentActivity"))
                {
                    _facade = new AndroidJavaObject(FacadeClass, activity, enableFace, enableHands);
                }
                IsInitialized = true;
                return true;
            }
            catch (System.Exception e)
            {
                Debug.LogError($"[KmpFaceLink] Android init failed: {e.Message}");
                return false;
            }
        }

        public void StartTracking()
        {
            if (!IsInitialized) return;
            _facade.Call("startTracking");
            IsTracking = true;
        }

        public void StopTracking()
        {
            if (!IsInitialized) return;
            _facade.Call("stopTracking");
            IsTracking = false;
        }

        public void Dispose()
        {
            if (_facade != null)
            {
                _facade.Call("dispose");
                _facade.Dispose();
                _facade = null;
            }
            IsInitialized = false;
            IsTracking = false;
        }

        public bool PollFaceData(FaceTrackingData data)
        {
            if (!IsTracking) return false;

            // Get the latest timestamp to check for new data
            long timestamp = _facade.Call<long>("getLatestFaceTimestamp");
            if (timestamp <= _lastFaceTimestamp) return false;
            _lastFaceTimestamp = timestamp;

            // Retrieve blend shape array (52 floats) from native
            float[] blendShapes = _facade.Call<float[]>("getLatestBlendShapes");
            if (blendShapes == null || blendShapes.Length != BlendShapeConstants.Count) return false;

            System.Array.Copy(blendShapes, data.BlendShapes, BlendShapeConstants.Count);

            // Retrieve head transform (6 floats: pitch, yaw, roll, px, py, pz)
            float[] headTransform = _facade.Call<float[]>("getLatestHeadTransform");
            if (headTransform != null && headTransform.Length >= 6)
            {
                data.HeadTransform = new HeadTransform
                {
                    Pitch = headTransform[0],
                    Yaw = headTransform[1],
                    Roll = headTransform[2],
                    PositionX = headTransform[3],
                    PositionY = headTransform[4],
                    PositionZ = headTransform[5],
                };
            }

            data.TimestampMs = timestamp;
            data.IsTracking = true;
            return true;
        }

        public bool PollHandData(HandTrackingData data)
        {
            if (!IsTracking) return false;

            long timestamp = _facade.Call<long>("getLatestHandTimestamp");
            if (timestamp <= _lastHandTimestamp) return false;
            _lastHandTimestamp = timestamp;

            int handCount = _facade.Call<int>("getLatestHandCount");
            if (handCount <= 0)
            {
                data.HandCount = 0;
                data.IsTracking = false;
                return true;
            }

            data.HandCount = handCount;
            data.IsTracking = true;
            data.TimestampMs = timestamp;

            for (int h = 0; h < handCount && h < 2; h++)
            {
                // Each hand: 21 landmarks * 3 floats (x, y, z) = 63 floats
                float[] landmarks = _facade.Call<float[]>("getHandLandmarks", h);
                int handedness = _facade.Call<int>("getHandedness", h);

                data.Hands[h].Handedness = handedness == 0 ? Handedness.Left : Handedness.Right;

                if (landmarks != null && landmarks.Length >= HandJointConstants.Count * 3)
                {
                    for (int j = 0; j < HandJointConstants.Count; j++)
                    {
                        data.Hands[h].Landmarks[j] = new HandLandmarkPoint
                        {
                            Joint = (HandJoint)j,
                            X = landmarks[j * 3],
                            Y = landmarks[j * 3 + 1],
                            Z = landmarks[j * 3 + 2],
                        };
                    }
                }
            }

            return true;
        }
    }
}
#endif
