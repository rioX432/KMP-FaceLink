package io.github.kmpfacelink.llm.internal

import io.github.kmpfacelink.llm.ConversationHistory
import io.github.kmpfacelink.llm.LlmClient
import io.github.kmpfacelink.llm.LlmState
import io.github.kmpfacelink.llm.RoutingStrategy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion

/**
 * Hybrid LLM client that routes queries between on-device and cloud backends.
 *
 * Routes each request based on the [routingStrategy] and [intentClassifier].
 * When on-device inference fails, automatically falls back to the cloud client.
 *
 * @param onDeviceClient Client for local on-device inference
 * @param cloudClient Client for cloud-based inference
 * @param routingStrategy Strategy controlling how queries are routed
 * @param intentClassifier Classifier for estimating query complexity
 */
internal class HybridLlmClient(
    private val onDeviceClient: LlmClient,
    private val cloudClient: LlmClient,
    private val routingStrategy: RoutingStrategy,
    private val intentClassifier: IntentClassifier = IntentClassifier(),
) : LlmClient {

    private val _state = MutableStateFlow<LlmState>(LlmState.Idle)
    override val state: StateFlow<LlmState> = _state.asStateFlow()

    override fun chat(history: ConversationHistory): Flow<String> = flow {
        _state.value = LlmState.Streaming
        val primary = selectClient(history)
        try {
            primary.chat(history).collect { emit(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Catch all non-cancellation exceptions for fallback to cloud
            if (primary === onDeviceClient) {
                cloudClient.chat(history).collect { emit(it) }
            } else {
                throw e
            }
        }
    }.onCompletion { cause ->
        _state.value = if (cause == null) {
            LlmState.Idle
        } else {
            LlmState.Error(cause.message ?: "Hybrid request failed")
        }
    }

    override suspend fun chatOnce(history: ConversationHistory): String {
        val primary = selectClient(history)
        return try {
            primary.chatOnce(history)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Catch all non-cancellation exceptions for fallback to cloud
            if (primary === onDeviceClient) {
                cloudClient.chatOnce(history)
            } else {
                throw e
            }
        }
    }

    override fun release() {
        onDeviceClient.release()
        cloudClient.release()
    }

    private fun selectClient(history: ConversationHistory): LlmClient =
        when (routingStrategy) {
            is RoutingStrategy.AlwaysOnDevice -> onDeviceClient
            is RoutingStrategy.AlwaysCloud -> cloudClient
            is RoutingStrategy.Auto -> selectByComplexity(history, routingStrategy)
        }

    private fun selectByComplexity(
        history: ConversationHistory,
        auto: RoutingStrategy.Auto,
    ): LlmClient {
        val complexity = intentClassifier.classify(history)
        return if (complexity.score < auto.complexityThreshold) {
            onDeviceClient
        } else {
            cloudClient
        }
    }
}
