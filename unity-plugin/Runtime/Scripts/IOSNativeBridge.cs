// Copyright (c) KMP-FaceLink contributors. Licensed under Apache-2.0.

#if UNITY_IOS && !UNITY_EDITOR
using System.Runtime.InteropServices;
using UnityEngine;

namespace KmpFaceLink
{
    /// <summary>
    /// iOS native bridge using DllImport to call into the KMP-FaceLink
    /// XCFramework via a thin C wrapper.
    ///
    /// Architecture:
    /// C# -> [DllImport("__Internal")] -> C bridge functions -> Swift/ObjC -> KMP-FaceLink
    ///
    /// On iOS, native plugins are statically linked into the Unity executable,
    /// so DllImport uses "__Internal" instead of a library name.
    ///
    /// The C bridge header is expected at:
    ///   Plugins/iOS/KmpFaceLinkBridge.h / .m
    ///
    /// The XCFramework must be added to the Xcode project via a post-build script
    /// or Unity's native plugin importer settings.
    /// </summary>
    internal class IOSNativeBridge : INativeBridge
    {
        // --- C bridge function declarations ---

        [DllImport("__Internal")]
        private static extern bool KmpFaceLink_Initialize(bool enableFace, bool enableHands);

        [DllImport("__Internal")]
        private static extern void KmpFaceLink_StartTracking();

        [DllImport("__Internal")]
        private static extern void KmpFaceLink_StopTracking();

        [DllImport("__Internal")]
        private static extern void KmpFaceLink_Dispose();

        [DllImport("__Internal")]
        private static extern bool KmpFaceLink_IsTracking();

        [DllImport("__Internal")]
        private static extern long KmpFaceLink_GetLatestFaceTimestamp();

        [DllImport("__Internal")]
        private static extern void KmpFaceLink_GetLatestBlendShapes(
            [Out] float[] blendShapes, int count);

        [DllImport("__Internal")]
        private static extern void KmpFaceLink_GetLatestHeadTransform(
            [Out] float[] transform, int count);

        [DllImport("__Internal")]
        private static extern long KmpFaceLink_GetLatestHandTimestamp();

        [DllImport("__Internal")]
        private static extern int KmpFaceLink_GetLatestHandCount();

        [DllImport("__Internal")]
        private static extern void KmpFaceLink_GetHandLandmarks(
            int handIndex, [Out] float[] landmarks, int count);

        [DllImport("__Internal")]
        private static extern int KmpFaceLink_GetHandedness(int handIndex);

        // --- State ---

        private long _lastFaceTimestamp;
        private long _lastHandTimestamp;
        private readonly float[] _blendShapeBuffer = new float[BlendShapeConstants.Count];
        private readonly float[] _headTransformBuffer = new float[6];
        private readonly float[] _handLandmarkBuffer = new float[HandJointConstants.Count * 3];

        public bool IsInitialized { get; private set; }
        public bool IsTracking => IsInitialized && KmpFaceLink_IsTracking();

        public bool Initialize(bool enableFace, bool enableHands)
        {
            try
            {
                bool result = KmpFaceLink_Initialize(enableFace, enableHands);
                IsInitialized = result;
                return result;
            }
            catch (System.Exception e)
            {
                Debug.LogError($"[KmpFaceLink] iOS init failed: {e.Message}");
                return false;
            }
        }

        public void StartTracking()
        {
            if (!IsInitialized) return;
            KmpFaceLink_StartTracking();
        }

        public void StopTracking()
        {
            if (!IsInitialized) return;
            KmpFaceLink_StopTracking();
        }

        public void Dispose()
        {
            if (IsInitialized)
            {
                KmpFaceLink_Dispose();
                IsInitialized = false;
            }
        }

        public bool PollFaceData(FaceTrackingData data)
        {
            if (!IsTracking) return false;

            long timestamp = KmpFaceLink_GetLatestFaceTimestamp();
            if (timestamp <= _lastFaceTimestamp) return false;
            _lastFaceTimestamp = timestamp;

            KmpFaceLink_GetLatestBlendShapes(_blendShapeBuffer, BlendShapeConstants.Count);
            System.Array.Copy(_blendShapeBuffer, data.BlendShapes, BlendShapeConstants.Count);

            KmpFaceLink_GetLatestHeadTransform(_headTransformBuffer, 6);
            data.HeadTransform = new HeadTransform
            {
                Pitch = _headTransformBuffer[0],
                Yaw = _headTransformBuffer[1],
                Roll = _headTransformBuffer[2],
                PositionX = _headTransformBuffer[3],
                PositionY = _headTransformBuffer[4],
                PositionZ = _headTransformBuffer[5],
            };

            data.TimestampMs = timestamp;
            data.IsTracking = true;
            return true;
        }

        public bool PollHandData(HandTrackingData data)
        {
            if (!IsTracking) return false;

            long timestamp = KmpFaceLink_GetLatestHandTimestamp();
            if (timestamp <= _lastHandTimestamp) return false;
            _lastHandTimestamp = timestamp;

            int handCount = KmpFaceLink_GetLatestHandCount();
            data.HandCount = handCount;
            data.IsTracking = handCount > 0;
            data.TimestampMs = timestamp;

            for (int h = 0; h < handCount && h < 2; h++)
            {
                KmpFaceLink_GetHandLandmarks(h, _handLandmarkBuffer, HandJointConstants.Count * 3);
                int handedness = KmpFaceLink_GetHandedness(h);

                data.Hands[h].Handedness = handedness == 0 ? Handedness.Left : Handedness.Right;

                for (int j = 0; j < HandJointConstants.Count; j++)
                {
                    data.Hands[h].Landmarks[j] = new HandLandmarkPoint
                    {
                        Joint = (HandJoint)j,
                        X = _handLandmarkBuffer[j * 3],
                        Y = _handLandmarkBuffer[j * 3 + 1],
                        Z = _handLandmarkBuffer[j * 3 + 2],
                    };
                }
            }

            return true;
        }
    }
}
#endif
