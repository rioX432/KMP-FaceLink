package io.github.kmpfacelink.internal.depth

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * On-device benchmark for the LiteRT depth estimator (#898 go/no-go gate).
 *
 * This is **not** a pass/fail correctness test — it measures latency and basic output sanity on a
 * real device and logs the result (tag [TAG]). It is skipped gracefully when the model asset is
 * absent, so it never fails CI on machines without the (large, uncommitted) `.tflite`.
 *
 * Run: `./gradlew :kmp-facelink:androidConnectedCheck` with a device connected.
 * The model must be placed at `src/androidDeviceTest/assets/models/depth_anything_v2_small.tflite`.
 */
@RunWith(AndroidJUnit4::class)
class DepthBenchmarkTest {

    @Test
    fun benchmarkDepthEstimatorLatency() {
        val context = InstrumentationRegistry.getInstrumentation().context
        // The KMP android-library plugin does not package test assets, so the model is supplied as
        // a filesystem path via the `modelPath` instrumentation argument (adb push it first). It is
        // staged into the app's internal files dir because LiteRT's native loader cannot open the
        // external-storage path directly under scoped storage.
        val modelPath = stageModel(context, InstrumentationRegistry.getArguments().getString("modelPath"))
        for (delegate in listOf(DepthDelegate.GPU, DepthDelegate.CPU)) {
            runBenchmark(context, delegate, modelPath)
        }
    }

    private fun stageModel(context: Context, sourcePath: String?): String? {
        if (sourcePath == null) return null
        val source = File(sourcePath)
        if (!source.exists()) return null
        val dest = File(context.filesDir, "depth_benchmark_model.tflite")
        if (!dest.exists() || dest.length() != source.length()) {
            source.inputStream().use { input -> dest.outputStream().use(input::copyTo) }
        }
        return dest.absolutePath
    }

    private fun runBenchmark(context: Context, delegate: DepthDelegate, modelPath: String?) {
        val estimator = createMonocularDepthEstimator(
            context,
            DepthEstimatorConfig(delegate = delegate, modelFilePath = modelPath),
        )
        assumeNotNull(
            "Depth model asset absent — skipping ($delegate). Place the .tflite to benchmark.",
            estimator,
        )
        requireNotNull(estimator)

        try {
            val frame = syntheticFrame()
            repeat(WARMUP_RUNS) { estimator.estimate(frame) }

            val latenciesMs = DoubleArray(MEASURE_RUNS)
            var sample = FloatArray(0)
            for (i in 0 until MEASURE_RUNS) {
                val start = System.nanoTime()
                sample = estimator.estimate(frame)
                latenciesMs[i] = (System.nanoTime() - start) / NANOS_PER_MS
            }
            frame.recycle()
            logResult(delegate, latenciesMs, sample, estimator.outputWidth, estimator.outputHeight)
        } finally {
            estimator.release()
        }
    }

    private fun logResult(
        delegate: DepthDelegate,
        latenciesMs: DoubleArray,
        sample: FloatArray,
        width: Int,
        height: Int,
    ) {
        val sorted = latenciesMs.sorted()
        val p50 = sorted[sorted.size / PERCENTILE_HALVING]
        val p95 = sorted[(sorted.size * P95_NUMERATOR) / PERCENTILE_DENOMINATOR]
        val min = sample.minOrNull() ?: 0f
        val max = sample.maxOrNull() ?: 0f
        Log.i(
            TAG,
            "[$delegate] p50=${fmt(p50)}ms p95=${fmt(p95)}ms (n=$MEASURE_RUNS) | " +
                "out=${width}x$height size=${sample.size} depthRange=[$min, $max] " +
                "degenerate=${min == max}",
        )
    }

    private fun syntheticFrame(): Bitmap {
        val bitmap = Bitmap.createBitmap(FRAME_WIDTH, FRAME_HEIGHT, Bitmap.Config.ARGB_8888)
        for (y in 0 until FRAME_HEIGHT) {
            for (x in 0 until FRAME_WIDTH) {
                val r = x * MAX_CHANNEL / FRAME_WIDTH
                val g = y * MAX_CHANNEL / FRAME_HEIGHT
                bitmap.setPixel(x, y, Color.rgb(r, g, HALF_CHANNEL))
            }
        }
        return bitmap
    }

    private fun fmt(value: Double): String = ((value * HUNDREDTHS).toLong() / HUNDREDTHS).toString()

    private companion object {
        const val TAG = "DepthBenchmark"
        const val WARMUP_RUNS = 5
        const val MEASURE_RUNS = 30
        const val NANOS_PER_MS = 1_000_000.0
        const val PERCENTILE_HALVING = 2
        const val P95_NUMERATOR = 95
        const val PERCENTILE_DENOMINATOR = 100
        const val FRAME_WIDTH = 640
        const val FRAME_HEIGHT = 480
        const val MAX_CHANNEL = 255
        const val HALF_CHANNEL = 128
        const val HUNDREDTHS = 100.0
    }
}
