package io.github.kmpfacelink.llm

/**
 * Represents the progress of a model file download.
 *
 * @property bytesDownloaded Number of bytes downloaded so far
 * @property totalBytes Total size of the file in bytes, or -1 if unknown
 * @property progress Download progress as a fraction from 0.0 to 1.0
 */
public data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val progress: Float,
)
