// Copyright (c) KMP-FaceLink contributors. Licensed under Apache-2.0.

using System;
using UnityEngine;

namespace KmpFaceLink
{
    /// <summary>
    /// Main entry point for KMP-FaceLink in Unity.
    /// Attach this MonoBehaviour to a GameObject to start tracking.
    ///
    /// Data is polled each frame in Update() and exposed via events
    /// and public properties. This avoids thread-safety issues since
    /// Unity's main thread is the only consumer.
    /// </summary>
    public class FaceLinkManager : MonoBehaviour
    {
        [Header("Tracking Configuration")]
        [Tooltip("Enable face tracking (blend shapes + head transform).")]
        [SerializeField] private bool _enableFaceTracking = true;

        [Tooltip("Enable hand tracking (21 landmarks per hand).")]
        [SerializeField] private bool _enableHandTracking = false;

        [Tooltip("Automatically start tracking on Awake.")]
        [SerializeField] private bool _autoStart = true;

        /// <summary>Fired when new face tracking data is available.</summary>
        public event Action<FaceTrackingData> OnFaceDataUpdated;

        /// <summary>Fired when new hand tracking data is available.</summary>
        public event Action<HandTrackingData> OnHandDataUpdated;

        /// <summary>The latest face tracking data. Updated each frame.</summary>
        public FaceTrackingData LatestFaceData { get; private set; }

        /// <summary>The latest hand tracking data. Updated each frame.</summary>
        public HandTrackingData LatestHandData { get; private set; }

        /// <summary>Whether the native library is initialized.</summary>
        public bool IsInitialized => _bridge?.IsInitialized ?? false;

        /// <summary>Whether tracking is currently active.</summary>
        public bool IsTracking => _bridge?.IsTracking ?? false;

        private INativeBridge _bridge;
        private bool _wasTrackingBeforePause;

        private void Awake()
        {
            LatestFaceData = new FaceTrackingData();
            LatestHandData = new HandTrackingData();

            _bridge = CreateBridge();

            if (!_bridge.Initialize(_enableFaceTracking, _enableHandTracking))
            {
                Debug.LogError("[KmpFaceLink] Failed to initialize native bridge.");
                return;
            }

            if (_autoStart)
            {
                StartTracking();
            }
        }

        private void Update()
        {
            if (_bridge == null || !_bridge.IsTracking) return;

            if (_enableFaceTracking && _bridge.PollFaceData(LatestFaceData))
            {
                OnFaceDataUpdated?.Invoke(LatestFaceData);
            }

            if (_enableHandTracking && _bridge.PollHandData(LatestHandData))
            {
                OnHandDataUpdated?.Invoke(LatestHandData);
            }
        }

        private void OnApplicationPause(bool pauseStatus)
        {
            if (_bridge == null) return;

            if (pauseStatus)
            {
                // App going to background — stop tracking to release camera
                if (_bridge.IsTracking)
                {
                    _wasTrackingBeforePause = true;
                    _bridge.StopTracking();
                }
            }
            else
            {
                // App returning to foreground — resume if was tracking before
                if (_wasTrackingBeforePause)
                {
                    _wasTrackingBeforePause = false;
                    _bridge.StartTracking();
                }
            }
        }

        private void OnApplicationQuit()
        {
            _bridge?.Dispose();
        }

        private void OnDestroy()
        {
            _bridge?.Dispose();
            _bridge = null;
        }

        /// <summary>Start tracking. Call this if autoStart is disabled.</summary>
        public void StartTracking()
        {
            _bridge?.StartTracking();
        }

        /// <summary>Stop tracking. Camera is released.</summary>
        public void StopTracking()
        {
            _bridge?.StopTracking();
        }

        private static INativeBridge CreateBridge()
        {
#if UNITY_EDITOR
            return new EditorNativeBridge();
#elif UNITY_ANDROID
            return new AndroidNativeBridge();
#elif UNITY_IOS
            return new IOSNativeBridge();
#else
            Debug.LogWarning("[KmpFaceLink] Unsupported platform. Using editor stub.");
            return new EditorNativeBridge();
#endif
        }
    }
}
