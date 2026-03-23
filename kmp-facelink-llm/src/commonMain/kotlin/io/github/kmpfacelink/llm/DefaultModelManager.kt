package io.github.kmpfacelink.llm

import io.github.kmpfacelink.llm.internal.CacheMetadataStore
import io.github.kmpfacelink.llm.internal.FileSystem
import io.github.kmpfacelink.llm.internal.executeDownload
import io.github.kmpfacelink.llm.internal.sha256Hex
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

private const val DEFAULT_MAX_CACHE_BYTES = 4_000_000_000L
private const val MODEL_FILE_EXTENSION = ".gguf"

/**
 * Default implementation of [ModelManager] that downloads models via Ktor,
 * verifies checksums, and manages a local cache with LRU eviction.
 *
 * @param httpClient Ktor [HttpClient] used for downloading model files
 * @param cacheDir Absolute path to the directory for storing cached models
 * @param maxCacheBytes Maximum total cache size in bytes before LRU eviction triggers
 */
public class DefaultModelManager(
    private val httpClient: HttpClient,
    private val cacheDir: String,
    private val maxCacheBytes: Long = DEFAULT_MAX_CACHE_BYTES,
) : ModelManager {

    private val metadataStore = CacheMetadataStore(cacheDir)

    override fun download(modelInfo: ModelInfo): Flow<DownloadProgress> = flow {
        FileSystem.mkdirs(cacheDir)
        val filePath = modelFilePath(modelInfo.id)
        val partialPath = "$filePath.part"
        val existingSize = partialFileSize(partialPath)

        executeDownload(httpClient, modelInfo, filePath, existingSize, this)
        verifyAndRecord(modelInfo, filePath)
    }

    override suspend fun delete(modelId: String): Boolean {
        val filePath = modelFilePath(modelId)
        FileSystem.delete("$filePath.part")
        val deleted = FileSystem.delete(filePath)
        if (deleted) {
            metadataStore.remove(modelId)
        }
        return deleted
    }

    override suspend fun listCached(): List<String> {
        return metadataStore.lruOrder()
            .filter { FileSystem.exists(modelFilePath(it.modelId)) }
            .map { it.modelId }
    }

    override suspend fun isDownloaded(modelId: String): Boolean {
        return FileSystem.exists(modelFilePath(modelId))
    }

    override suspend fun getModelPath(modelId: String): String? {
        val path = modelFilePath(modelId)
        return if (FileSystem.exists(path)) {
            val fileName = "$modelId$MODEL_FILE_EXTENSION"
            metadataStore.recordUsage(modelId, fileName, FileSystem.fileSize(path))
            path
        } else {
            null
        }
    }

    /**
     * Evicts least-recently-used models until total cache size is within [maxCacheBytes].
     */
    public suspend fun evictIfNeeded() {
        val entries = metadataStore.lruOrder()
        var totalSize = entries.sumOf { it.sizeBytes }
        for (entry in entries) {
            if (totalSize <= maxCacheBytes) break
            delete(entry.modelId)
            totalSize -= entry.sizeBytes
        }
    }

    private suspend fun verifyAndRecord(modelInfo: ModelInfo, filePath: String) {
        if (!modelInfo.sha256Checksum.startsWith("placeholder")) {
            val actual = sha256Hex(filePath)
            check(actual == modelInfo.sha256Checksum) {
                "Checksum mismatch for ${modelInfo.id}: expected ${modelInfo.sha256Checksum}, got $actual"
            }
        }
        val fileName = "${modelInfo.id}$MODEL_FILE_EXTENSION"
        metadataStore.recordUsage(modelInfo.id, fileName, FileSystem.fileSize(filePath))
    }

    private fun modelFilePath(modelId: String): String = "$cacheDir/$modelId$MODEL_FILE_EXTENSION"

    private fun partialFileSize(partialPath: String): Long {
        return if (FileSystem.exists(partialPath)) FileSystem.fileSize(partialPath) else 0L
    }
}
