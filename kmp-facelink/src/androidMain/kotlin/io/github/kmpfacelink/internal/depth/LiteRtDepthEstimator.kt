package io.github.kmpfacelink.internal.depth

import android.content.Context
import android.graphics.Bitmap
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
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
        val pixels = DepthImagePreprocessor.toInputFloats(bitmap, config.inputSize, config.normalization)
        val inputs = model.createInputBuffers()
        try {
            val outputs = model.createOutputBuffers()
            try {
                inputs[0].writeFloat(pixels)
                model.run(inputs, outputs)
                return outputs[0].readFloat().also(::verifyShapeOnce)
            } finally {
                outputs.forEach { it.close() }
            }
        } finally {
            inputs.forEach { it.close() }
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
