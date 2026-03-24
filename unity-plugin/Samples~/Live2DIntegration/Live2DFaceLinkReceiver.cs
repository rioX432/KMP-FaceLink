// Copyright (c) KMP-FaceLink contributors. Licensed under Apache-2.0.

using UnityEngine;

namespace KmpFaceLink.Samples
{
    /// <summary>
    /// Sample script for driving a Live2D Cubism model with KMP-FaceLink face tracking.
    ///
    /// This script maps ARKit blend shapes to Live2D Cubism parameter IDs.
    /// Requires Live2D Cubism SDK for Unity (not included).
    ///
    /// Setup:
    ///   1. Import Live2D Cubism SDK for Unity
    ///   2. Import your Live2D model (.moc3 + textures)
    ///   3. Attach this script to the Live2D model's root GameObject
    ///   4. Assign the FaceLinkManager reference
    ///   5. Press Play — the model should follow your face
    ///
    /// The mapping follows ARKit-to-Live2D conventions used by VTube Studio,
    /// iFacialMocap, and other VTuber tools.
    /// </summary>
    public class Live2DFaceLinkReceiver : MonoBehaviour
    {
        [SerializeField] private FaceLinkManager _faceLinkManager;

        [Header("Parameter Sensitivity")]
        [Range(0f, 2f)]
        [SerializeField] private float _eyeBlinkScale = 1f;

        [Range(0f, 2f)]
        [SerializeField] private float _mouthScale = 1f;

        [Range(0f, 30f)]
        [SerializeField] private float _headRotationScale = 1f;

        [Header("Smoothing")]
        [Range(0f, 1f)]
        [Tooltip("Lower = smoother but more lag. 1 = no smoothing.")]
        [SerializeField] private float _smoothing = 0.6f;

        // Cached parameter values for smoothing
        private float _smoothEyeBlinkL;
        private float _smoothEyeBlinkR;
        private float _smoothMouthOpen;
        private float _smoothMouthSmile;
        private float _smoothHeadPitch;
        private float _smoothHeadYaw;
        private float _smoothHeadRoll;

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
            if (!data.IsTracking) return;

            // Extract key parameters from blend shapes
            float eyeBlinkL = data.GetBlendShape(BlendShape.EyeBlinkLeft) * _eyeBlinkScale;
            float eyeBlinkR = data.GetBlendShape(BlendShape.EyeBlinkRight) * _eyeBlinkScale;
            float mouthOpen = data.GetBlendShape(BlendShape.JawOpen) * _mouthScale;
            float mouthSmile = (data.GetBlendShape(BlendShape.MouthSmileLeft)
                + data.GetBlendShape(BlendShape.MouthSmileRight)) * 0.5f * _mouthScale;

            // Apply smoothing (exponential moving average)
            _smoothEyeBlinkL = Mathf.Lerp(_smoothEyeBlinkL, eyeBlinkL, _smoothing);
            _smoothEyeBlinkR = Mathf.Lerp(_smoothEyeBlinkR, eyeBlinkR, _smoothing);
            _smoothMouthOpen = Mathf.Lerp(_smoothMouthOpen, mouthOpen, _smoothing);
            _smoothMouthSmile = Mathf.Lerp(_smoothMouthSmile, mouthSmile, _smoothing);
            _smoothHeadPitch = Mathf.Lerp(_smoothHeadPitch, data.HeadTransform.Pitch, _smoothing);
            _smoothHeadYaw = Mathf.Lerp(_smoothHeadYaw, data.HeadTransform.Yaw, _smoothing);
            _smoothHeadRoll = Mathf.Lerp(_smoothHeadRoll, data.HeadTransform.Roll, _smoothing);

            // ---------------------------------------------------------------
            // Apply to Live2D Cubism parameters.
            //
            // Uncomment and adapt these lines when Live2D Cubism SDK is imported.
            // The CubismModel.Parameters array maps to your model's .moc3 params.
            //
            // var model = GetComponent<CubismModel>();
            // if (model == null) return;
            //
            // // Eye blink (Live2D convention: 0 = open, 1 = closed)
            // SetParam(model, "ParamEyeLOpen", 1f - _smoothEyeBlinkL);
            // SetParam(model, "ParamEyeROpen", 1f - _smoothEyeBlinkR);
            //
            // // Mouth
            // SetParam(model, "ParamMouthOpenY", _smoothMouthOpen);
            // SetParam(model, "ParamMouthForm", _smoothMouthSmile);
            //
            // // Head rotation (Live2D convention: -30 to 30 degrees)
            // SetParam(model, "ParamAngleX", _smoothHeadYaw * _headRotationScale);
            // SetParam(model, "ParamAngleY", _smoothHeadPitch * _headRotationScale);
            // SetParam(model, "ParamAngleZ", _smoothHeadRoll * _headRotationScale);
            //
            // // Eye gaze (derived from head rotation as approximation)
            // SetParam(model, "ParamEyeBallX", _smoothHeadYaw * 0.1f);
            // SetParam(model, "ParamEyeBallY", _smoothHeadPitch * 0.1f);
            //
            // // Body lean (subtle movement from head rotation)
            // SetParam(model, "ParamBodyAngleX", _smoothHeadYaw * 0.3f);
            // SetParam(model, "ParamBodyAngleY", _smoothHeadPitch * 0.2f);
            // SetParam(model, "ParamBodyAngleZ", _smoothHeadRoll * 0.2f);
            // ---------------------------------------------------------------

            // Debug output (remove in production)
            Debug.LogFormat(
                "[Live2D] EyeL={0:F2} EyeR={1:F2} Mouth={2:F2} Yaw={3:F1}",
                _smoothEyeBlinkL, _smoothEyeBlinkR, _smoothMouthOpen, _smoothHeadYaw);
        }

        // Helper to safely set a Live2D parameter by ID string.
        // Uncomment when Live2D Cubism SDK is available.
        //
        // private void SetParam(CubismModel model, string paramId, float value)
        // {
        //     var param = model.Parameters.FindById(paramId);
        //     if (param != null)
        //     {
        //         param.Value = value;
        //     }
        // }
    }
}
