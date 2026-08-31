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
import io.github.kmpfacelink.model.BlendShapeWireSchema
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HandTrackingData
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
     * Returns the latest face data as a binary buffer, in wire format v2.
     *
     * Layout, little-endian:
     *   uint32 magic
     *   uint16 protocolVersion
     *   uint16 channelCount
     *   uint32 schemaFingerprint
     *   uint32 observedMaskLow
     *   uint32 observedMaskHigh
     *   float32[52] values
     *   float32[6] headTransform
     *   uint8 isTracking
     *
     * The mask is what makes this v2 rather than a renamed v1: the value slots are a fixed-size
     * transport, so a shape the estimator never produced still occupies four bytes. Without a mask
     * those bytes read as a measured zero. A reader must consult the mask before a value.
     *
     * The old method name is gone rather than kept as an alias. A JavaScript bundle built against
     * v1 now fails on a missing method instead of decoding v2 bytes against the v1 layout, which
     * would misread every field after the header.
     */
    @ReactMethod(isBlockingSynchronousMethod = true)
    fun getLatestFaceDataV2(): ByteArray? {
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

        private val BLEND_SHAPE_COUNT = BlendShapeWireSchema.CHANNEL_COUNT
        private const val HEAD_TRANSFORM_FLOATS = 6

        /** "FLNK" little-endian, so a reader can reject a buffer that is not ours at byte 0. */
        private const val FACE_WIRE_MAGIC = 0x4B4E4C46
        private const val FACE_WIRE_VERSION = 2
        private const val FACE_HEADER_BYTES = 4 + 2 + 2 + 4 + 4 + 4
        private const val FACE_BUFFER_SIZE =
            FACE_HEADER_BYTES + (BLEND_SHAPE_COUNT + HEAD_TRANSFORM_FLOATS) * 4 + 1

        private const val LANDMARKS_PER_HAND = 63
        private const val HAND_BLOCK_BYTES = LANDMARKS_PER_HAND * 4 + 4

        private fun encodeFaceData(data: FaceTrackingData): ByteArray {
            val buffer = ByteBuffer.allocate(FACE_BUFFER_SIZE)
                .order(ByteOrder.LITTLE_ENDIAN)

            // Which shapes this frame actually carries. Slots outside the mask are padding.
            var observedLow = 0
            var observedHigh = 0
            val blendShapes = FloatArray(BLEND_SHAPE_COUNT)
            for (entry in data.blendShapes) {
                val ordinal = entry.key.ordinal
                blendShapes[ordinal] = entry.value
                if (ordinal < Int.SIZE_BITS) {
                    observedLow = observedLow or (1 shl ordinal)
                } else {
                    observedHigh = observedHigh or (1 shl (ordinal - Int.SIZE_BITS))
                }
            }

            buffer.putInt(FACE_WIRE_MAGIC)
            buffer.putShort(FACE_WIRE_VERSION.toShort())
            buffer.putShort(BLEND_SHAPE_COUNT.toShort())
            buffer.putInt(BlendShapeWireSchema.FINGERPRINT)
            buffer.putInt(observedLow)
            buffer.putInt(observedHigh)

            for (value in blendShapes) {
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
