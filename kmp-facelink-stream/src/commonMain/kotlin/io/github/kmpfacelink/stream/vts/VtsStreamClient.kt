package io.github.kmpfacelink.stream.vts

import io.github.kmpfacelink.stream.StreamClient
import io.github.kmpfacelink.stream.StreamState
import io.github.kmpfacelink.stream.internal.KtorWebSocketProvider
import io.github.kmpfacelink.stream.internal.WebSocketProvider
import io.github.kmpfacelink.stream.vts.protocol.VtsSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.CoroutineContext

/**
 * [StreamClient] implementation for VTubeStudio's WebSocket API.
 *
 * Handles the full VTS protocol lifecycle: connection, authentication
 * (token request or re-auth with saved token), parameter injection, and
 * automatic reconnection with exponential backoff.
 *
 * @param config VTubeStudio connection configuration
 */
@OptIn(ExperimentalAtomicApi::class)
public class VtsStreamClient internal constructor(
    private val config: VtsConfig,
    private val webSocketProvider: WebSocketProvider,
    coroutineContext: CoroutineContext = Dispatchers.Default,
) : StreamClient {

    /**
     * Creates a [VtsStreamClient] with the given configuration.
     */
    public constructor(config: VtsConfig) : this(config, KtorWebSocketProvider())

    private val scope = CoroutineScope(SupervisorJob() + coroutineContext)
    private val _state = MutableStateFlow<StreamState>(StreamState.Disconnected)
    private val sendMutex = Mutex()
    private val stateMutex = Mutex()
    private var connectionJob: Job? = null
    private val currentToken = AtomicReference<String?>(config.authToken)
    private val requestCounter = AtomicInt(0)

    override val state: StateFlow<StreamState> = _state.asStateFlow()

    override suspend fun connect() {
        stateMutex.withLock {
            if (_state.value is StreamState.Connected || _state.value is StreamState.Connecting) return
            startConnectionLocked()
        }
    }

    override suspend fun disconnect() {
        stateMutex.withLock {
            connectionJob?.cancel()
            connectionJob = null
        }
        webSocketProvider.close()
        _state.value = StreamState.Disconnected
    }

    override suspend fun sendParameters(parameters: Map<String, Float>, faceFound: Boolean) {
        if (_state.value !is StreamState.Connected) return
        sendMutex.withLock {
            val json = VtsSerializer.injectParameterDataRequest(
                requestId = nextRequestId(),
                faceFound = faceFound,
                parameters = parameters,
            )
            webSocketProvider.send(json)
        }
    }

    override fun release() {
        scope.cancel()
        webSocketProvider.release()
    }

    private fun startConnectionLocked() {
        connectionJob?.cancel()
        val job = scope.launch {
            _state.value = StreamState.Connecting
            connectAndAuthenticate()
        }
        connectionJob = job
    }

    private suspend fun connectAndAuthenticate() {
        try {
            webSocketProvider.connect(
                url = "ws://${config.host}:${config.port}",
                onMessage = ::handleMessage,
                onClose = { handleDisconnect() },
                onError = { handleError(it) },
            )
            authenticate()
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            handleError(e)
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private suspend fun handleMessage(text: String) {
        val response = VtsSerializer.decodeResponse(text)

        when (response.messageType) {
            "APIError" -> {
                val errorMsg = response.data?.message ?: "Unknown VTS error"
                _state.value = StreamState.Error(errorMsg, willRetry = false)
            }
            "AuthenticationTokenResponse" -> {
                val token = response.data?.authenticationToken
                if (token != null) {
                    currentToken.store(token)
                    config.onTokenReceived?.invoke(token)
                    sendAuthRequest(token)
                } else {
                    _state.value = StreamState.Error("No token in response", willRetry = false)
                }
            }
            "AuthenticationResponse" -> {
                val authenticated = response.data?.authenticated ?: false
                if (authenticated) {
                    _state.value = StreamState.Connected
                } else {
                    val reason = response.data?.reason ?: "Authentication failed"
                    currentToken.store(null)
                    _state.value = StreamState.Error(reason, willRetry = false)
                }
            }
            "InjectParameterDataResponse" -> {
                // Success — no action needed
            }
        }
    }

    private fun handleDisconnect() {
        if (_state.value is StreamState.Disconnected) return
        attemptReconnect()
    }

    private fun handleError(error: Throwable) {
        val message = error.message ?: "Connection error"
        _state.value = StreamState.Error(message, willRetry = config.reconnectPolicy.maxAttempts > 0)
        if (config.reconnectPolicy.maxAttempts > 0) {
            attemptReconnect()
        }
    }

    private fun attemptReconnect() {
        connectionJob?.cancel()
        val job = scope.launch {
            val policy = config.reconnectPolicy
            var attempt = 0
            var delayMs = policy.initialDelayMs

            while (attempt < policy.maxAttempts) {
                attempt++
                _state.value = StreamState.Reconnecting(attempt)
                delay(delayMs)

                try {
                    _state.value = StreamState.Connecting
                    connectAndAuthenticate()
                    if (_state.value is StreamState.Connected) return@launch
                } catch (@Suppress("TooGenericExceptionCaught") expected: Exception) {
                    // Expected during reconnection — retry on next loop iteration
                }

                delayMs = (delayMs * policy.backoffMultiplier).toLong()
                    .coerceAtMost(policy.maxDelayMs)
            }

            _state.value = StreamState.Error(
                "Reconnection failed after $attempt attempts",
                willRetry = false,
            )
        }
        connectionJob = job
    }

    internal suspend fun authenticate() {
        _state.value = StreamState.Authenticating
        val token = currentToken.load()
        if (token != null) {
            sendAuthRequest(token)
        } else {
            sendAuthTokenRequest()
        }
    }

    private suspend fun sendAuthTokenRequest() {
        val json = VtsSerializer.authTokenRequest(
            requestId = nextRequestId(),
            pluginName = config.pluginName,
            pluginDeveloper = config.pluginDeveloper,
            pluginIcon = config.pluginIcon,
        )
        webSocketProvider.send(json)
    }

    private suspend fun sendAuthRequest(token: String) {
        val json = VtsSerializer.authRequest(
            requestId = nextRequestId(),
            pluginName = config.pluginName,
            pluginDeveloper = config.pluginDeveloper,
            token = token,
        )
        webSocketProvider.send(json)
    }

    private fun nextRequestId(): String = "kmp-facelink-${requestCounter.addAndFetch(1)}"
}
