package io.github.kmpfacelink.internal.depth

import android.content.Context
import android.graphics.Bitmap
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.TensorBuffer
import io.github.kmpfacelink.util.FaceLinkLogger
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [MonocularDepthEstimator] backed by the LiteRT 2.x [CompiledModel] API.
 *
 * All model access happens on a single owning thread ([executor]) so depth inference is
 * serialized and isolated from MediaPipe's threads — the two accelerator pipelines never share
 * runtime state directly. Depth is opt-in; nothing here runs unless a caller explicitly creates
 * an estimator.
 */
internal class LiteRtDepthEstimator private constructor(
    private val model: CompiledModel,
    private val executor: ExecutorService,
    private val config: DepthEstimatorConfig,
    override val outputWidth: Int,
    override val outputHeight: Int,
) : MonocularDepthEstimator {

    private val released = AtomicBoolean(false)
    private val shapeChecked = AtomicBoolean(false)

    override fun estimate(bitmap: Bitmap): FloatArray {
        check(!released.get()) { "estimate() called after release()" }
        return executor.submit(Callable { runInference(bitmap) }).get()
    }

    private fun runInference(bitmap: Bitmap): FloatArray {
        // Guard the narrow window where this task was enqueued just before release()'s close task:
        // both run on the same single-thread executor, so a true here means model.close() is queued
        // ahead of or already applied to the model — never touch it. Thrown here, caught by callers.
        check(!released.get()) { "runInference skipped: estimator released" }
        val inputs = model.createInputBuffers()
        try {
            val outputs = model.createOutputBuffers()
            try {
                writeInput(inputs[0], bitmap)
                model.run(inputs, outputs)
                return readOutput(outputs[0]).also(::verifyShapeOnce)
            } finally {
                outputs.forEach { it.close() }
            }
        } finally {
            inputs.forEach { it.close() }
        }
    }

    private fun writeInput(buffer: TensorBuffer, bitmap: Bitmap) {
        when (config.ioType) {
            DepthIoType.FLOAT32 ->
                buffer.writeFloat(DepthImagePreprocessor.toInputFloats(bitmap, config.inputSize, config.normalization))
            DepthIoType.UINT8 -> {
                val quant = requireNotNull(config.inputQuant) { "inputQuant is required for UINT8 io type" }
                buffer.writeInt8(DepthImagePreprocessor.toInputBytes(bitmap, config.inputSize, quant))
            }
        }
    }

    private fun readOutput(buffer: TensorBuffer): FloatArray = when (config.ioType) {
        DepthIoType.FLOAT32 -> buffer.readFloat()
        DepthIoType.UINT8 -> {
            val quant = requireNotNull(config.outputQuant) { "outputQuant is required for UINT8 io type" }
            val raw = buffer.readInt8()
            FloatArray(raw.size) { (((raw[it].toInt() and BYTE_MASK) - quant.zeroPoint)) * quant.scale }
        }
    }

    /** Warns once if the produced depth map contradicts the assumed square input-resolution shape. */
    private fun verifyShapeOnce(depth: FloatArray) {
        if (!shapeChecked.compareAndSet(false, true)) return
        val expected = outputWidth * outputHeight
        if (depth.size != expected) {
            FaceLinkLogger.w(
                TAG,
                "Depth map size ${depth.size} != assumed $expected (${outputWidth}x$outputHeight); " +
                    "confirm model output shape (#898)",
            )
        }
    }

    override fun release() {
        if (!released.compareAndSet(false, true)) return
        try {
            executor.submit { model.close() }.get()
        } finally {
            executor.shutdown()
        }
    }

    internal companion object {
        private const val TAG = "LiteRtDepthEstimator"
        private const val THREAD_NAME = "facelink-depth"
        private const val BYTE_MASK = 0xFF

        /** Builds an estimator on its owning thread. Throws if the model asset cannot be loaded. */
        fun create(context: Context, config: DepthEstimatorConfig): LiteRtDepthEstimator {
            val executor = Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, THREAD_NAME)
            }
            return try {
                executor.submit(Callable { build(context, config, executor) }).get()
            } catch (e: Exception) {
                executor.shutdownNow()
                throw e
            }
        }

        private fun build(
            context: Context,
            config: DepthEstimatorConfig,
            executor: ExecutorService,
        ): LiteRtDepthEstimator {
            val accelerator = if (config.delegate == DepthDelegate.GPU) Accelerator.GPU else Accelerator.CPU
            val options = CompiledModel.Options(accelerator)
            val model = if (config.modelFilePath != null) {
                CompiledModel.create(config.modelFilePath, options)
            } else {
                CompiledModel.create(context.assets, config.modelAssetPath, options)
            }
            // PoC assumption: the depth map is square at input resolution (true for Depth Anything
            // V2). The actual output shape must be confirmed against the model on-device (#898);
            // runInference logs a warning if the produced map size contradicts this assumption.
            FaceLinkLogger.d(
                TAG,
                "Depth model ready: in=${config.inputSize} accel=$accelerator",
            )
            return LiteRtDepthEstimator(model, executor, config, config.inputSize, config.inputSize)
        }
    }
}
