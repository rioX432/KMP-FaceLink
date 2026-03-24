package io.github.kmpfacelink.vts

import io.github.kmpfacelink.model.FaceTrackingData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * High-level VTube Studio parameter sender.
 *
 * Bridges face tracking data to VTube Studio via the Plugin API WebSocket.
 * Handles connection, authentication, and continuous parameter injection.
 *
 * Usage:
 * ```kotlin
 * val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
 * val sender = VtsSender(VtsConfig(
 *     pluginName = "MyApp",
 *     pluginDeveloper = "MyName",
 *     authToken = savedToken, // null for first-time
 * ))
 * sender.start(faceTracker.trackingData, scope)
 * // Save sender.authToken for next session
 * // ... later
 * sender.stop()
 * ```
 *
 * @param config VTS connection and plugin configuration
 * @param client Optional [VtsClient] instance (created internally if not provided)
 */
public class VtsSender(
    private val config: VtsConfig = VtsConfig(),
    private val client: VtsClient = VtsClient(config),
) {
    private var collectJob: Job? = null

    /**
     * The authentication token obtained during connection.
     * Save this value to avoid re-authentication on next session.
     * Null until [start] completes authentication.
     */
    public var authToken: String? = config.authToken
        private set

    /**
     * Starts connecting, authenticating, and streaming tracking data to VTS.
     *
     * The caller owns [scope] and is responsible for cancelling it
     * when sending is no longer needed.
     *
     * @param trackingFlow The face tracking data flow to consume
     * @param scope Coroutine scope for the connection and collection jobs
     */
    public fun start(
        trackingFlow: Flow<FaceTrackingData>,
        scope: CoroutineScope,
    ) {
        stop()
        collectJob = scope.launch {
            client.connect(scope)
            authToken = client.authenticate()
            trackingFlow.collect { data ->
                if (data.isTracking) {
                    val params = VtsParameterConverter.convert(data)
                    client.injectParameters(params, faceFound = true)
                } else {
                    client.injectParameters(emptyList(), faceFound = false)
                }
            }
        }
    }

    /**
     * Stops streaming and disconnects from VTube Studio.
     */
    public fun stop() {
        collectJob?.cancel()
        collectJob = null
    }
}
