package io.github.kmpfacelink.voice

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Manages the common state transition pattern for TTS/ASR engines:
 * set processing state → execute block → set idle state (or error state on failure).
 *
 * On success, resets state to [idleState] and returns the block's result.
 * On failure, sets state to [errorState] and re-throws the exception.
 *
 * @param state The mutable state flow to update.
 * @param processingState The state to set before executing the block.
 * @param idleState The state to set on success.
 * @param errorState Factory that creates an error state from the exception message.
 * @param block The suspend block containing the engine's core logic.
 * @return The result of the block.
 */
internal suspend inline fun <S, R> withEngineStateTransition(
    state: MutableStateFlow<S>,
    processingState: S,
    idleState: S,
    errorState: (String) -> S,
    block: () -> R,
): R {
    state.value = processingState
    return try {
        val result = block()
        state.value = idleState
        result
    } catch (e: Exception) {
        state.value = errorState(e.message ?: "Unknown error")
        throw e
    }
}
