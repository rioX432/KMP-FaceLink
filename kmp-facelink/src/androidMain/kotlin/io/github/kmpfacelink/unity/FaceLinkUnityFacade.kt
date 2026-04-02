package io.github.kmpfacelink.unity

import android.content.Context
import io.github.kmpfacelink.api.FaceTracker
import io.github.kmpfacelink.api.HandTracker
import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.api.createFaceTracker
import io.github.kmpfacelink.api.createHandTracker
import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HandTrackingData
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
     * Returns head transform as 6 floats: [pitch, yaw, roll, posX, posY, posZ].
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

    /** Returns 0 for LEFT, 1 for RIGHT, 2 for UNKNOWN. */
    public fun getHandedness(handIndex: Int): Int {
        val data = latestHandData.get() ?: return 1
        val hands = data.hands
        if (handIndex < 0 || handIndex >= hands.size) return 1
        return hands[handIndex].handedness.ordinal
    }

    private companion object {
        private const val BLEND_SHAPE_COUNT = 52
        private const val HAND_LANDMARK_COUNT = 21
        private const val COORDINATES_PER_LANDMARK = 3

        private fun blendShapesToArray(
            blendShapes: Map<BlendShape, Float>,
        ): FloatArray {
            val result = FloatArray(BLEND_SHAPE_COUNT)
            for (entry in blendShapes) {
                result[entry.key.ordinal] = entry.value
            }
            return result
        }

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
