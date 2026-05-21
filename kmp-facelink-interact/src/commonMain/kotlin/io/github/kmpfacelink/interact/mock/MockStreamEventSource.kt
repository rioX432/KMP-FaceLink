package io.github.kmpfacelink.interact.mock

import io.github.kmpfacelink.interact.StreamEventSource
import io.github.kmpfacelink.interact.model.ConnectionState
import io.github.kmpfacelink.interact.model.StreamEvent
import io.github.kmpfacelink.interact.model.StreamPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

/**
 * A mock [StreamEventSource] that generates fake stream events at configurable intervals.
 *
 * Useful for sample app demos and integration testing without a real streaming platform.
 * Events are generated randomly based on the probabilities defined in [MockConfig].
 *
 * @param config Controls event generation interval and type probabilities.
 * @param platform The platform to associate with generated events.
 * @param random Random source for deterministic testing when seeded.
 * @param timeProvider Provides timestamps for generated events.
 */
public class MockStreamEventSource(
    private val config: MockConfig = MockConfig(),
    override val platform: StreamPlatform = StreamPlatform.TWITCH,
    private val random: Random = Random.Default,
    private val timeProvider: () -> Long = { 0L },
) : StreamEventSource {

    private val _events = MutableSharedFlow<StreamEvent>(extraBufferCapacity = EVENT_BUFFER)
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val generator = FakeDataGenerator(random, platform, timeProvider)
    private var emissionScope: CoroutineScope? = null
    private var emissionJob: Job? = null

    override val events: Flow<StreamEvent> = _events.asSharedFlow()
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    override suspend fun connect() {
        disconnect()
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        emissionScope = scope
        _connectionState.value = ConnectionState.CONNECTING
        _connectionState.value = ConnectionState.CONNECTED
        emissionJob = scope.launch { emitLoop() }
    }

    override suspend fun disconnect() {
        emissionScope?.cancel()
        emissionScope = null
        emissionJob = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private suspend fun CoroutineScope.emitLoop() {
        while (isActive) {
            delay(config.intervalMs)
            val event = generator.generate(config)
            _events.emit(event)
        }
    }
}

private const val EVENT_BUFFER = 64
