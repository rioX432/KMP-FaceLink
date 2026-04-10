package io.github.kmpfacelink.llm.internal

import io.github.kmpfacelink.networking.currentTimeMs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val METADATA_FILE_NAME = "cache_metadata.json"

/**
 * Tracks last-used timestamps for cached models to support LRU eviction.
 */
@Serializable
internal data class CacheMetadata(
    @SerialName("entries")
    private val mutableEntries: MutableMap<String, CacheEntry> = mutableMapOf(),
) {
    /** Read-only view of cached model entries. */
    internal val entries: Map<String, CacheEntry> get() = mutableEntries

    /** Adds or updates an entry for the given [key]. */
    internal fun put(key: String, entry: CacheEntry) {
        mutableEntries[key] = entry
    }

    /** Removes the entry for the given [key]. Returns `true` if the key was present. */
    internal fun remove(key: String): Boolean = mutableEntries.remove(key) != null

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
        } catch (_: SerializationException) {
            // Corrupted or schema-incompatible cache metadata — reset to defaults
            CacheMetadata()
        }
    }

    internal fun save(metadata: CacheMetadata) {
        FileSystem.mkdirs(cacheDir)
        FileSystem.writeText(metadataPath, json.encodeToString(metadata))
    }

    internal fun recordUsage(modelId: String, fileName: String, sizeBytes: Long) {
        val metadata = load()
        metadata.put(
            modelId,
            CacheMetadata.CacheEntry(
                modelId = modelId,
                fileName = fileName,
                sizeBytes = sizeBytes,
                lastUsedTimestamp = currentTimeMs(),
            ),
        )
        save(metadata)
    }

    internal fun remove(modelId: String) {
        val metadata = load()
        metadata.remove(modelId)
        save(metadata)
    }

    internal fun lruOrder(): List<CacheMetadata.CacheEntry> {
        return load().entries.values.sortedBy { it.lastUsedTimestamp }
    }
}
