package io.github.kmpfacelink.llm

/**
 * Approximate token counter for context window management.
 *
 * Uses character-based estimation (1 token ≈ [CHARS_PER_TOKEN] characters) which provides
 * a rough heuristic suitable for budgeting, not billing. Actual token counts vary
 * significantly across providers and tokenizers (BPE, SentencePiece, etc.), with
 * observed deviation of **±30 %** or more depending on the text composition:
 *
 * - **English prose** tends to be close to the 4-char estimate.
 * - **Code / markup** often tokenizes more efficiently (fewer tokens than predicted).
 * - **CJK / emoji / rare Unicode** may consume more tokens per character.
 * - **Provider-specific special tokens** (e.g. `<|im_start|>`) are not accounted for.
 *
 * For precise counts, use a provider-specific tokenizer (e.g. tiktoken for OpenAI,
 * SentencePiece for Llama-family models).
 */
public object TokenCounter {
    /**
     * Characters-per-token ratio used for estimation.
     *
     * This is a coarse heuristic (1 token ≈ 4 chars). Real-world accuracy varies
     * by ±30 % depending on the tokenizer and input language. Do not rely on this
     * for exact token billing or hard context-window limit enforcement.
     */
    private const val CHARS_PER_TOKEN = 4

    /** Estimates the token count for a single string. */
    public fun estimate(text: String): Int = (text.length + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN

    /** Estimates the total token count for a list of messages. */
    public fun estimate(messages: List<ChatMessage>): Int =
        messages.sumOf { estimate(it.content) + ROLE_OVERHEAD }

    internal const val ROLE_OVERHEAD = 4
}
