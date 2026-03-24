// Copyright (c) KMP-FaceLink contributors. Licensed under Apache-2.0.

namespace KmpFaceLink
{
    /// <summary>
    /// Platform abstraction for native KMP-FaceLink library calls.
    /// Each platform (Android, iOS) provides its own implementation
    /// that communicates with the native tracking library.
    /// </summary>
    internal interface INativeBridge
    {
        /// <summary>Initialize the native tracking library.</summary>
        /// <param name="enableFace">Enable face tracking.</param>
        /// <param name="enableHands">Enable hand tracking.</param>
        /// <returns>True if initialization succeeded.</returns>
        bool Initialize(bool enableFace, bool enableHands);

        /// <summary>Start tracking. Camera access begins.</summary>
        void StartTracking();

        /// <summary>Stop tracking. Camera is released.</summary>
        void StopTracking();

        /// <summary>Release all native resources.</summary>
        void Dispose();

        /// <summary>Whether the native library is initialized.</summary>
        bool IsInitialized { get; }

        /// <summary>Whether tracking is currently active.</summary>
        bool IsTracking { get; }

        /// <summary>
        /// Poll the latest face tracking data from native.
        /// Writes into the provided data object to avoid allocation.
        /// </summary>
        /// <param name="data">Reusable data object to populate.</param>
        /// <returns>True if new data was available.</returns>
        bool PollFaceData(FaceTrackingData data);

        /// <summary>
        /// Poll the latest hand tracking data from native.
        /// Writes into the provided data object to avoid allocation.
        /// </summary>
        /// <param name="data">Reusable data object to populate.</param>
        /// <returns>True if new data was available.</returns>
        bool PollHandData(HandTrackingData data);
    }
}
