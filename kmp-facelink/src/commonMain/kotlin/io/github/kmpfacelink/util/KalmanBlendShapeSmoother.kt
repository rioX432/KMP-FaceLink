package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.BlendShapeData

/**
 * 1D Kalman filter smoother for blend shape data.
 *
 * Uses a constant-velocity model where each blend shape's state is
 * [position, velocity]. The filter predict-update cycle runs once per frame,
 * followed by an optional multi-frame prediction look-ahead to reduce
 * perceived latency.
 *
 * @param processNoise Process noise covariance (Q scalar applied to the Q matrix).
 * @param measurementNoise Measurement noise covariance (R).
 * @param predictionFrames Number of extra state-transition steps for look-ahead.
 */
internal class KalmanBlendShapeSmoother(
    private val processNoise: Float,
    private val measurementNoise: Float,
    private val predictionFrames: Int,
) : BlendShapeSmoother {

    private var lastTimestampMs: Long = -1L
    private val states = mutableMapOf<BlendShape, KalmanState>()

    // Pre-allocated scratch map reused each frame to avoid per-call HashMap allocations.
    // Callers that store the result must copy it via HashMap(result).
    private val scratch: HashMap<BlendShape, Float> = BlendShapeMapPool().create()

    override fun smooth(data: BlendShapeData, timestampMs: Long): BlendShapeData {
        // First frame or same timestamp -- pass through and initialise states
        if (lastTimestampMs < 0 || timestampMs <= lastTimestampMs) {
            lastTimestampMs = timestampMs
            scratch.clear()
            for ((shape, value) in data) {
                states[shape] = KalmanState.initial(value)
                scratch[shape] = value
            }
            return scratch
        }

        val dt = (timestampMs - lastTimestampMs) / TimeConstants.MILLIS_PER_SECOND_F
        lastTimestampMs = timestampMs

        scratch.clear()
        for ((shape, rawValue) in data) {
            val state = states[shape]
            if (state == null) {
                states[shape] = KalmanState.initial(rawValue)
                scratch[shape] = rawValue
                continue
            }

            val updated = predictAndUpdate(state, dt, rawValue)
            states[shape] = updated
            scratch[shape] = projectForward(updated, dt)
        }

        return scratch
    }

    override fun reset() {
        states.clear()
        lastTimestampMs = -1L
    }

    /**
     * Runs the Kalman predict step followed by the update (measurement) step.
     */
    private fun predictAndUpdate(state: KalmanState, dt: Float, measurement: Float): KalmanState {
        // -- Predict --
        val predicted = predict(state, dt)

        // -- Update --
        return update(predicted, measurement)
    }

    /**
     * Kalman predict step: propagate state and covariance forward by [dt].
     *
     * State transition: x' = x + v*dt, v' = v
     * F = [[1, dt], [0, 1]]
     * P' = F * P * F^T + Q
     */
    private fun predict(state: KalmanState, dt: Float): KalmanState {
        val x = state.x + state.v * dt
        val v = state.v

        // P' = F * P * F^T + Q
        // F = [[1, dt], [0, 1]]
        // F * P = [[p00 + dt*p10, p01 + dt*p11], [p10, p11]]
        val fp00 = state.p00 + dt * state.p10
        val fp01 = state.p01 + dt * state.p11
        val fp10 = state.p10
        val fp11 = state.p11

        // (F * P) * F^T  where F^T = [[1, 0], [dt, 1]]
        val pp00 = fp00 + fp01 * dt + processNoise
        val pp01 = fp01
        val pp10 = fp10 + fp11 * dt
        val pp11 = fp11 + processNoise

        return KalmanState(x, v, pp00, pp01, pp10, pp11)
    }

    /**
     * Kalman update step: incorporate a scalar measurement.
     *
     * H = [1, 0], so innovation = z - x, S = P[0,0] + R.
     */
    private fun update(predicted: KalmanState, measurement: Float): KalmanState {
        val innovation = measurement - predicted.x
        val s = predicted.p00 + measurementNoise

        // Kalman gain K = P * H^T / S = [p00/s, p10/s]
        val k0 = predicted.p00 / s
        val k1 = predicted.p10 / s

        val x = predicted.x + k0 * innovation
        val v = predicted.v + k1 * innovation

        // P = (I - K*H) * P
        val p00 = predicted.p00 - k0 * predicted.p00
        val p01 = predicted.p01 - k0 * predicted.p01
        val p10 = predicted.p10 - k1 * predicted.p00
        val p11 = predicted.p11 - k1 * predicted.p01

        return KalmanState(x, v, p00, p01, p10, p11)
    }

    /**
     * Projects the state forward by [predictionFrames] extra steps and returns
     * the clamped position. Returns the current position if predictionFrames is 0.
     */
    private fun projectForward(state: KalmanState, dt: Float): Float {
        if (predictionFrames <= 0) return state.x.coerceIn(0f, 1f)
        val projected = state.x + state.v * dt * predictionFrames
        return projected.coerceIn(0f, 1f)
    }

    /**
     * Per-blend-shape Kalman filter state.
     *
     * @param x Estimated position (blend shape value).
     * @param v Estimated velocity.
     * @param p00 Covariance matrix element [0,0].
     * @param p01 Covariance matrix element [0,1].
     * @param p10 Covariance matrix element [1,0].
     * @param p11 Covariance matrix element [1,1].
     */
    private data class KalmanState(
        val x: Float,
        val v: Float,
        val p00: Float,
        val p01: Float,
        val p10: Float,
        val p11: Float,
    ) {
        companion object {
            /** Create an initial state centred on the first observation. */
            fun initial(value: Float): KalmanState = KalmanState(
                x = value,
                v = 0f,
                p00 = 1f,
                p01 = 0f,
                p10 = 0f,
                p11 = 1f,
            )
        }
    }
}
