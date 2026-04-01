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
 * Groups the parameters needed to initiate a model download.
 */
internal data class DownloadRequest(
    val modelInfo: ModelInfo,
    val filePath: String,
    val existingSize: Long,
)

/**
 * Groups the state needed to track and report download progress.
 */
private data class WriteProgress(
    val initialBytes: Long,
    val totalBytes: Long,
    val collector: FlowCollector<DownloadProgress>,
)

/**
 * Executes the HTTP download with optional resume support via Range header.
 *
 * Downloads to a `.part` file first, then renames to the final path on success.
 *
 * @return true if the download completed, false if it was already complete
 */
internal suspend fun executeDownload(
    httpClient: HttpClient,
    request: DownloadRequest,
    collector: FlowCollector<DownloadProgress>,
): Boolean {
    val partialPath = "${request.filePath}.part"

    httpClient.prepareGet(request.modelInfo.downloadUrl) {
        if (request.existingSize > 0L) {
            header(HttpHeaders.Range, "bytes=${request.existingSize}-")
        }
    }.execute { response ->
        if (!response.status.isSuccess()) {
            error("Download failed with status ${response.status.value}")
        }

        val contentLength = response.contentLength() ?: request.modelInfo.sizeBytes
        val totalBytes = contentLength + request.existingSize

        val channel = response.bodyAsChannel()
        val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
        val sink = PlatformFileSink(partialPath, append = request.existingSize > 0L)

        try {
            val progress = WriteProgress(request.existingSize, totalBytes, collector)
            writeDownloadedBytes(channel, buffer, sink, progress)
        } finally {
            sink.close()
        }
    }

    // Rename .part to final file
    FileSystem.rename(partialPath, request.filePath)
    return true
}

private suspend fun writeDownloadedBytes(
    channel: io.ktor.utils.io.ByteReadChannel,
    buffer: ByteArray,
    sink: PlatformFileSink,
    progress: WriteProgress,
) {
    var bytesDownloaded = progress.initialBytes
    while (!channel.isClosedForRead) {
        val bytesRead = channel.readAvailable(buffer)
        if (bytesRead <= 0) continue
        sink.write(buffer, 0, bytesRead)
        bytesDownloaded += bytesRead
        val ratio = if (progress.totalBytes > 0L) {
            (bytesDownloaded.toFloat() / progress.totalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        progress.collector.emit(DownloadProgress(bytesDownloaded, progress.totalBytes, ratio))
    }
}
