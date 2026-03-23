package io.github.kmpfacelink.pipeline.internal

/**
 * Buffers streaming text chunks and splits them into complete sentences.
 *
 * Recognizes sentence boundaries at `.`, `!`, `?`, and their full-width equivalents.
 * Handles common abbreviations (Dr., Mr., Mrs., Ms., etc.) as non-boundaries.
 */
internal class SentenceBuffer {

    private val buffer = StringBuilder()

    /**
     * Appends a text chunk and returns any completed sentences.
     *
     * @param chunk Incoming text chunk from LLM streaming
     * @return List of completed sentences (may be empty)
     */
    fun append(chunk: String): List<String> {
        buffer.append(chunk)
        return extractSentences()
    }

    /**
     * Flushes any remaining buffered text as a final sentence.
     *
     * @return Remaining text, or null if buffer is empty
     */
    fun flush(): String? {
        val remaining = buffer.toString().trim()
        buffer.clear()
        return remaining.ifEmpty { null }
    }

    private fun extractSentences(): List<String> {
        val sentences = mutableListOf<String>()
        while (true) {
            val boundary = findNextBoundary() ?: break
            val sentence = buffer.substring(0, boundary + 1).trim()
            buffer.delete(0, boundary + 1)
            if (sentence.isNotEmpty()) {
                sentences.add(sentence)
            }
        }
        return sentences
    }

    private fun findNextBoundary(): Int? {
        val text = buffer.toString()
        var i = 0
        while (i < text.length) {
            if (isSentenceTerminator(text[i]) && !isAbbreviation(text, i)) {
                return i
            }
            i++
        }
        return null
    }

    internal companion object {

        private val ABBREVIATION_PATTERNS = listOf(
            "dr", "mr", "mrs", "ms", "prof", "sr", "jr",
            "inc", "ltd", "corp", "co", "etc", "vs",
            "st", "ave", "blvd",
            "jan", "feb", "mar", "apr", "aug", "sep", "oct", "nov", "dec",
        )

        internal fun isSentenceTerminator(ch: Char): Boolean =
            ch == '.' || ch == '!' || ch == '?' ||
                ch == '\u3002' || ch == '\uFF01' || ch == '\uFF1F'

        internal fun isAbbreviation(text: String, dotIndex: Int): Boolean {
            if (text[dotIndex] != '.') return false
            val beforeDot = text.substring(0, dotIndex).trimEnd()
            if (beforeDot.isEmpty()) return false

            val lastWord = beforeDot.split(" ", "\n", "\t").last().lowercase()
            return lastWord in ABBREVIATION_PATTERNS
        }
    }
}
