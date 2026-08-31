package io.github.kmpfacelink.unity

import android.content.Context
import io.github.kmpfacelink.api.FaceTracker
import io.github.kmpfacelink.api.HandTracker
import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.api.createFaceTracker
import io.github.kmpfacelink.api.createHandTracker
import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.BlendShapeWireSchema
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HandTrackingData
import io.github.kmpfacelink.model.Handedness
import io.github.kmpfacelink.model.LiveLinkBlendShapes
import io.github.kmpfacelink.model.densify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/**
 * Unity-facing facade that converts KMP-FaceLink's Flow-based streaming API
 * into a polling model suitable for Unity's frame-based update loop.
 *
 * Called from Unity C# via AndroidJavaObject (JNI). Unity polls [getLatestBlendShapes],
 * [getLatestHeadTransform], etc. each frame in its Update() method.
 *
 * Thread safety: Flow collection runs on [Dispatchers.Default]. The latest frame data
 * is stored in [AtomicReference] fields, ensuring lock-free thread-safe reads from
 * Unity's main thread.
 */
@Suppress("TooManyFunctions")
public class FaceLinkUnityFacade(
    context: Context,
    enableFace: Boolean,
    enableHands: Boolean,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lifecycleOwner = NoOpLifecycleOwner()
    private val platformContext = PlatformContext(context, lifecycleOwner)

    private val faceTracker: FaceTracker? = if (enableFace) {
        createFaceTracker(platformContext)
    } else {
        null
    }

    private val handTracker: HandTracker? = if (enableHands) {
        createHandTracker(platformContext)
    } else {
        null
    }

    private val latestFaceData = AtomicReference<FaceTrackingData?>(null)
    private val latestHandData = AtomicReference<HandTrackingData?>(null)

    /** Start tracking and begin collecting Flow emissions into buffers. */
    public fun startTracking() {
        faceTracker?.let { tracker ->
            scope.launch {
                tracker.start()
                tracker.trackingData.collect { data ->
                    latestFaceData.set(data)
                }
            }
        }
        handTracker?.let { tracker ->
            scope.launch {
                tracker.start()
                tracker.trackingData.collect { data ->
                    latestHandData.set(data)
                }
            }
        }
    }

    /** Stop tracking. Trackers can be restarted. */
    public fun stopTracking() {
        scope.launch {
            faceTracker?.stop()
            handTracker?.stop()
        }
    }

    /** Release all resources. Cannot be used after this. */
    public fun dispose() {
        faceTracker?.release()
        handTracker?.release()
        lifecycleOwner.destroy()
        scope.cancel()
    }

    // -- Face data polling (called from Unity C# via JNI) --

    /** Returns the timestamp of the latest face frame, or 0 if none. */
    public fun getLatestFaceTimestamp(): Long {
        return latestFaceData.get()?.timestampMs ?: 0L
    }

    /**
     * Returns the 52 blend shape values as a float array ordered by [BlendShape.ordinal].
     * Returns null if no face data is available.
     */
    public fun getLatestBlendShapes(): FloatArray? {
        val data = latestFaceData.get() ?: return null
        return blendShapesToArray(data.blendShapes)
    }

    /**
     * Returns the 52 blend shape values in canonical ARKit **Live Link Face** wire
     * order (see [LiveLinkBlendShapes]), mapped **by name** — never by enum ordinal.
     * Channels the tracker does not produce (e.g. `tongueOut` on Android/MediaPipe)
     * are 0-filled. Returns null if no face data is available.
     */
    public fun getLatestBlendShapesLiveLink(): FloatArray? {
        val data = latestFaceData.get() ?: return null
        return LiveLinkBlendShapes.lower(data.blendShapes)
    }

    /**
     * Bit set of the channels [getLatestBlendShapes] filled from a value, by ordinal.
     *
     * The array has a slot for every shape whether or not the estimator produced it, so a slot for
     * an absent shape holds `0f` and reads exactly like a value that was really there. Estimators
     * differ in what they produce, so a consumer that skips this mask will act on shapes it never
     * received. The bit does not say where the value came from. Returns 0 when no face data is
     * available.
     */
    public fun getLatestBlendShapeMask(): Long {
        val data = latestFaceData.get() ?: return 0L
        return BlendShapeWireSchema.presentMaskByOrdinal(data.blendShapes)
    }

    /**
     * The same bit set for [getLatestBlendShapesLiveLink].
     *
     * The two orderings differ, so the masks are not interchangeable.
     */
    public fun getLatestBlendShapeMaskLiveLink(): Long {
        val data = latestFaceData.get() ?: return 0L
        return LiveLinkBlendShapes.presentMask(data.blendShapes)
    }

    /** FNV-1a over the blend shape names in ordinal order; see [BlendShapeWireSchema]. */
    public fun getBlendShapeSchemaFingerprint(): Int = BlendShapeWireSchema.FINGERPRINT

    /**
     * Returns head transform as 6 floats: [pitch, yaw, roll, posX, posY, posZ].
     * Rotations are Euler degrees; positions are meters in a right-handed camera-space
     * frame. See kmp-facelink-cbridge/README.md for the full contract.
     * Returns null if no face data is available.
     */
    public fun getLatestHeadTransform(): FloatArray? {
        val data = latestFaceData.get() ?: return null
        val ht = data.headTransform
        return floatArrayOf(ht.pitch, ht.yaw, ht.roll, ht.positionX, ht.positionY, ht.positionZ)
    }

    // -- Hand data polling (called from Unity C# via JNI) --

    /** Returns the timestamp of the latest hand frame, or 0 if none. */
    public fun getLatestHandTimestamp(): Long {
        return latestHandData.get()?.timestampMs ?: 0L
    }

    /** Returns the number of tracked hands, or 0 if none. */
    public fun getLatestHandCount(): Int {
        return latestHandData.get()?.hands?.size ?: 0
    }

    /**
     * Returns 21 landmarks * 3 floats (x, y, z) = 63 floats for the given hand index.
     * Returns null if the hand index is out of range.
     */
    public fun getHandLandmarks(handIndex: Int): FloatArray? {
        val data = latestHandData.get() ?: return null
        val hands = data.hands
        if (handIndex < 0 || handIndex >= hands.size) return null
        return handLandmarksToArray(hands[handIndex].landmarks)
    }

    /** Returns 0 for LEFT, 1 for RIGHT, 2 for UNKNOWN (also when no such hand exists). */
    public fun getHandedness(handIndex: Int): Int {
        val data = latestHandData.get() ?: return Handedness.UNKNOWN.ordinal
        val hands = data.hands
        if (handIndex < 0 || handIndex >= hands.size) return Handedness.UNKNOWN.ordinal
        return hands[handIndex].handedness.ordinal
    }

    private companion object {
        private const val HAND_LANDMARK_COUNT = 21
        private const val COORDINATES_PER_LANDMARK = 3

        // Absent shapes arrive here as `substitute`, and `densify` keeps the record of which
        // ones those were. This boundary drops that record because the array is all it can send;
        // carrying it across would need a presence field in the Unity-side contract.
        private fun blendShapesToArray(
            blendShapes: Map<BlendShape, Float>,
        ): FloatArray = blendShapes.densify().values

        private fun handLandmarksToArray(
            landmarks: List<io.github.kmpfacelink.model.HandLandmarkPoint>,
        ): FloatArray {
            val result = FloatArray(HAND_LANDMARK_COUNT * COORDINATES_PER_LANDMARK)
            for (lm in landmarks) {
                val idx = lm.joint.ordinal * COORDINATES_PER_LANDMARK
                result[idx] = lm.x
                result[idx + 1] = lm.y
                result[idx + 2] = lm.z
            }
            return result
        }
    }
}
