// Copyright (c) KMP-FaceLink contributors. Licensed under Apache-2.0.

using UnityEngine;
using UnityEngine.UI;

namespace KmpFaceLink.Samples
{
    /// <summary>
    /// Sample script that visualizes face tracking blend shapes as UI sliders.
    /// Attach to a GameObject with a FaceLinkManager component.
    /// </summary>
    public class FaceTrackingDemo : MonoBehaviour
    {
        [SerializeField] private FaceLinkManager _faceLinkManager;

        [Header("UI References")]
        [SerializeField] private Text _statusText;
        [SerializeField] private Text _blendShapeText;

        [Header("Head Transform Visualization")]
        [SerializeField] private Transform _headTarget;

        private void OnEnable()
        {
            if (_faceLinkManager != null)
            {
                _faceLinkManager.OnFaceDataUpdated += HandleFaceData;
            }
        }

        private void OnDisable()
        {
            if (_faceLinkManager != null)
            {
                _faceLinkManager.OnFaceDataUpdated -= HandleFaceData;
            }
        }

        private void HandleFaceData(FaceTrackingData data)
        {
            // Update status
            if (_statusText != null)
            {
                _statusText.text = data.IsTracking ? "Tracking" : "No Face";
            }

            // Display top blend shapes
            if (_blendShapeText != null)
            {
                _blendShapeText.text = string.Format(
                    "EyeBlinkL: {0:F2}\nEyeBlinkR: {1:F2}\nJawOpen: {2:F2}\nSmileL: {3:F2}\nSmileR: {4:F2}",
                    data.GetBlendShape(BlendShape.EyeBlinkLeft),
                    data.GetBlendShape(BlendShape.EyeBlinkRight),
                    data.GetBlendShape(BlendShape.JawOpen),
                    data.GetBlendShape(BlendShape.MouthSmileLeft),
                    data.GetBlendShape(BlendShape.MouthSmileRight));
            }

            // Apply head rotation to target transform
            if (_headTarget != null)
            {
                _headTarget.localRotation = data.HeadTransform.ToUnityRotation();
                _headTarget.localPosition = data.HeadTransform.ToUnityPosition();
            }
        }
    }
}
