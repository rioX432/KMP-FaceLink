package io.github.kmpfacelink.llm.internal

/**
 * Callback interface invoked from JNI for each generated token during streaming.
 *
 * Implementations are passed to [LlamaCppBridge.generate] and called from native code.
 */
internal fun interface LlamaCallback {
    /**
     * Called when a new token is generated.
     *
     * @param token The text representation of the generated token
     */
    fun onToken(token: String)
}
