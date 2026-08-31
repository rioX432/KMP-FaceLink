package com.kmpfacelink

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.turbomodule.core.interfaces.TurboModule
import io.github.kmpfacelink.api.FaceTracker
import io.github.kmpfacelink.api.HandTracker
import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.api.createFaceTracker
import io.github.kmpfacelink.api.createHandTracker
import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HandTrackingData
import io.github.kmpfacelink.model.densify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference

/**
 * React Native Turbo Module for KMP-FaceLink.
 *
 * Provides face and hand tracking via a polling model. The native side collects
 * Flow emissions on a background coroutine and buffers the latest frame data.
 * The JS side polls via synchronous JSI calls each requestAnimationFrame tick.
 *
 * Data is returned as binary ArrayBuffers to avoid serialization overhead.
 */
@ReactModule(name = FaceLinkModule.NAME)
class FaceLinkModule(
    reactContext: ReactApplicationContext,
) : com.facebook.react.bridge.ReactContextBaseJavaModule(reactContext), TurboModule {

    private var scope: CoroutineScope? = null
    private var lifecycleOwner: NoOpLifecycleOwner? = null
    private var faceTracker: FaceTracker? = null
    private var handTracker: HandTracker? = null
    private val latestFaceData = AtomicReference<FaceTrackingData?>(null)
    private val latestHandData = AtomicReference<HandTrackingData?>(null)

    @Volatile
    private var tracking = false

    @Volatile
    private var initialized = false

    override fun getName(): String = NAME

    @ReactMethod(isBlockingSynchronousMethod = true)
    fun initialize(enableFace: Boolean, enableHands: Boolean): Boolean {
        if (initialized) return true

        val context = reactApplicationContext.applicationContext
        val owner = NoOpLifecycleOwner()
        lifecycleOwner = owner
        val platformContext = PlatformContext(context, owner)

        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        if (enableFace) {
            faceTracker = createFaceTracker(platformContext)
        }
        if (enableHands) {
            handTracker = createHandTracker(platformContext)
        }

        initialized = true
        return true
    }

    @ReactMethod(isBlockingSynchronousMethod = true)
    fun start() {
        if (!initialized) return

        faceTracker?.let { tracker ->
            scope?.launch {
                tracker.start()
                tracker.trackingData.collect { data ->
                    latestFaceData.set(data)
                }
            }
        }
        handTracker?.let { tracker ->
            scope?.launch {
                tracker.start()
                tracker.trackingData.collect { data ->
                    latestHandData.set(data)
                }
            }
        }
        tracking = true
    }

    @ReactMethod(isBlockingSynchronousMethod = true)
    fun stop() {
        if (!initialized) return
        scope?.launch {
            faceTracker?.stop()
            handTracker?.stop()
        }
        tracking = false
    }

    @ReactMethod(isBlockingSynchronousMethod = true)
    fun dispose() {
        tracking = false
        faceTracker?.release()
        handTracker?.release()
        faceTracker = null
        handTracker = null
        lifecycleOwner?.destroy()
        lifecycleOwner = null
        scope?.cancel()
        scope = null
        initialized = false
    }

    @ReactMethod(isBlockingSynchronousMethod = true)
    fun isTracking(): Boolean = tracking

    /**
     * Returns the latest face data as a binary buffer.
     *
     * Layout: float32[52] blendShapes + float32[6] headTransform + uint8 isTracking
     * Total: 233 bytes
     */
    @ReactMethod(isBlockingSynchronousMethod = true)
    fun getLatestFaceData(): ByteArray? {
        val data = latestFaceData.get() ?: return null
        return encodeFaceData(data)
    }

    /**
     * Returns the latest hand data as a binary buffer.
     *
     * Layout: int32 handCount + (float32[63] landmarks + int32 handedness) per hand
     */
    @ReactMethod(isBlockingSynchronousMethod = true)
    fun getLatestHandData(): ByteArray? {
        val data = latestHandData.get() ?: return null
        return encodeHandData(data)
    }

    override fun invalidate() {
        dispose()
        super.invalidate()
    }

    companion object {
        const val NAME = "FaceLinkModule"

        private const val BLEND_SHAPE_COUNT = 52
        private const val HEAD_TRANSFORM_FLOATS = 6
        private const val FACE_BUFFER_SIZE = (BLEND_SHAPE_COUNT + HEAD_TRANSFORM_FLOATS) * 4 + 1
        private const val LANDMARKS_PER_HAND = 63
        private const val HAND_BLOCK_BYTES = LANDMARKS_PER_HAND * 4 + 4

        private fun encodeFaceData(data: FaceTrackingData): ByteArray {
            val buffer = ByteBuffer.allocate(FACE_BUFFER_SIZE)
                .order(ByteOrder.LITTLE_ENDIAN)

            // Write 52 blend shapes in ordinal order. Shapes the estimator did not produce occupy
            // a slot and read as a measured zero; `getLatestBlendShapeMask` reports which ones were
            // real. The mask is not in this buffer because its length is part of the JS-side
            // contract — adding it is a protocol change, not a local one.
            for (value in data.blendShapes.densify().values) {
                buffer.putFloat(value)
            }

            // Write 6 head transform floats
            val ht = data.headTransform
            buffer.putFloat(ht.pitch)
            buffer.putFloat(ht.yaw)
            buffer.putFloat(ht.roll)
            buffer.putFloat(ht.positionX)
            buffer.putFloat(ht.positionY)
            buffer.putFloat(ht.positionZ)

            // Write isTracking flag
            buffer.put(1.toByte())

            return buffer.array()
        }

        private fun encodeHandData(data: HandTrackingData): ByteArray {
            val hands = data.hands
            val handCount = minOf(hands.size, 2)
            val totalSize = 4 + handCount * HAND_BLOCK_BYTES
            val buffer = ByteBuffer.allocate(totalSize)
                .order(ByteOrder.LITTLE_ENDIAN)

            buffer.putInt(handCount)

            for (i in 0 until handCount) {
                val hand = hands[i]
                // Build landmark array indexed by joint ordinal
                val lmArray = FloatArray(LANDMARKS_PER_HAND)
                for (lm in hand.landmarks) {
                    val base = lm.joint.ordinal * 3
                    if (base + 2 < LANDMARKS_PER_HAND) {
                        lmArray[base] = lm.x
                        lmArray[base + 1] = lm.y
                        lmArray[base + 2] = lm.z
                    }
                }
                for (value in lmArray) {
                    buffer.putFloat(value)
                }
                // Write handedness
                buffer.putInt(hand.handedness.ordinal)
            }

            return buffer.array()
        }
    }
}
