package io.github.kmpfacelink.llm.internal

import io.github.kmpfacelink.llm.DownloadProgress
import io.github.kmpfacelink.llm.ModelInfo
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.flow.FlowCollector

private const val DOWNLOAD_BUFFER_SIZE = 8192

/**
 * Executes the HTTP download with optional resume support via Range header.
 *
 * Downloads to a `.part` file first, then renames to the final path on success.
 *
 * @return true if the download completed, false if it was already complete
 */
@Suppress("LongParameterList")
internal suspend fun executeDownload(
    httpClient: HttpClient,
    modelInfo: ModelInfo,
    filePath: String,
    existingSize: Long,
    collector: FlowCollector<DownloadProgress>,
): Boolean {
    val partialPath = "$filePath.part"

    httpClient.prepareGet(modelInfo.downloadUrl) {
        if (existingSize > 0L) {
            header(HttpHeaders.Range, "bytes=$existingSize-")
        }
    }.execute { response ->
        if (!response.status.isSuccess()) {
            error("Download failed with status ${response.status.value}")
        }

        val contentLength = response.contentLength() ?: modelInfo.sizeBytes
        val totalBytes = contentLength + existingSize
        var bytesDownloaded = existingSize

        val channel = response.bodyAsChannel()
        val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
        val sink = PlatformFileSink(partialPath, append = existingSize > 0L)

        try {
            writeDownloadedBytes(channel, buffer, sink, bytesDownloaded, totalBytes, collector)
        } finally {
            sink.close()
        }
    }

    // Rename .part to final file
    FileSystem.rename(partialPath, filePath)
    return true
}

@Suppress("LongParameterList")
private suspend fun writeDownloadedBytes(
    channel: io.ktor.utils.io.ByteReadChannel,
    buffer: ByteArray,
    sink: PlatformFileSink,
    initialBytes: Long,
    totalBytes: Long,
    collector: FlowCollector<DownloadProgress>,
) {
    var bytesDownloaded = initialBytes
    while (!channel.isClosedForRead) {
        val bytesRead = channel.readAvailable(buffer)
        if (bytesRead <= 0) continue
        sink.write(buffer, 0, bytesRead)
        bytesDownloaded += bytesRead
        val progress = if (totalBytes > 0L) {
            (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        collector.emit(DownloadProgress(bytesDownloaded, totalBytes, progress))
    }
}
