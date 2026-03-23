package io.github.kmpfacelink.llm.internal

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val METADATA_FILE_NAME = "cache_metadata.json"

/**
 * Tracks last-used timestamps for cached models to support LRU eviction.
 */
@Serializable
internal data class CacheMetadata(
    val entries: MutableMap<String, CacheEntry> = mutableMapOf(),
) {
    @Serializable
    internal data class CacheEntry(
        val modelId: String,
        val fileName: String,
        val sizeBytes: Long,
        val lastUsedTimestamp: Long,
    )
}

/**
 * Manages reading and writing of cache metadata from a JSON file.
 */
internal class CacheMetadataStore(private val cacheDir: String) {

    private val json = Json { ignoreUnknownKeys = true }
    private val metadataPath: String get() = "$cacheDir/$METADATA_FILE_NAME"

    internal fun load(): CacheMetadata {
        val text = FileSystem.readText(metadataPath) ?: return CacheMetadata()
        return try {
            json.decodeFromString<CacheMetadata>(text)
        } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
            CacheMetadata()
        }
    }

    internal fun save(metadata: CacheMetadata) {
        FileSystem.mkdirs(cacheDir)
        FileSystem.writeText(metadataPath, json.encodeToString(metadata))
    }

    internal fun recordUsage(modelId: String, fileName: String, sizeBytes: Long) {
        val metadata = load()
        metadata.entries[modelId] = CacheMetadata.CacheEntry(
            modelId = modelId,
            fileName = fileName,
            sizeBytes = sizeBytes,
            lastUsedTimestamp = currentTimeMillis(),
        )
        save(metadata)
    }

    internal fun remove(modelId: String) {
        val metadata = load()
        metadata.entries.remove(modelId)
        save(metadata)
    }

    internal fun lruOrder(): List<CacheMetadata.CacheEntry> {
        return load().entries.values.sortedBy { it.lastUsedTimestamp }
    }
}

internal expect fun currentTimeMillis(): Long
