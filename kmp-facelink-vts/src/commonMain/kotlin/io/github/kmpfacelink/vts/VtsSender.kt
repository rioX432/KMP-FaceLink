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
 * ## Phone-as-Tracker Example
 *
 * Use a mobile device as a wireless face tracker for VTube Studio running
 * on a desktop PC on the same Wi-Fi network:
 *
 * ```kotlin
 * // On the mobile device (Android/iOS)
 * val faceTracker = FaceTracker(context)
 * val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
 *
 * val sender = VtsSender(VtsConfig(
 *     host = "192.168.1.100",  // Desktop PC IP address
 *     pluginName = "MyTracker",
 *     pluginDeveloper = "MyName",
 *     authToken = savedToken,  // null for first-time (shows VTS popup)
 * ))
 *
 * faceTracker.start()
 * sender.start(faceTracker.trackingData, scope)
 *
 * // After start(), save the token for next session:
 * preferences.save("vts_token", sender.authToken)
 *
 * // When done:
 * sender.stop()
 * faceTracker.stop()
 * scope.cancel()
 * ```
 *
 * ## Token Persistence
 *
 * The first authentication triggers a permission popup in VTube Studio.
 * After the user approves, [authToken] is populated with a reusable token.
 * Save this token and pass it via [VtsConfig.authToken] on subsequent sessions
 * to skip the popup.
 *
 * @param config VTS connection and plugin configuration
 * @param client Optional [VtsClient] instance (created internally if not provided)
 * @see VtsClient for lower-level API access including post-processing
 * @see VtsParameterConverter for the blendshape-to-VTS parameter mapping
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
