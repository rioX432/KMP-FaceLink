package io.github.kmpfacelink.llm.internal

/**
 * Platform-specific file output sink for writing downloaded bytes.
 *
 * @param path Absolute path to the output file
 * @param append If true, appends to the existing file (for resume support)
 */
internal expect class PlatformFileSink(path: String, append: Boolean) {
    /** Writes [length] bytes from [buffer] starting at [offset]. */
    fun write(buffer: ByteArray, offset: Int, length: Int)

    /** Closes the sink, flushing any buffered data. */
    fun close()
}
