package io.github.kmpfacelink.llm

import kotlinx.coroutines.flow.Flow

/**
 * Manages downloading, caching, and deleting on-device LLM models.
 *
 * Platform implementations handle storage location and download mechanics.
 */
public interface ModelManager {

    /**
     * Downloads a model to local storage.
     *
     * @param modelInfo Model metadata including download URL and checksum
     * @return Flow emitting download progress as a fraction (0.0 to 1.0)
     */
    public fun download(modelInfo: ModelInfo): Flow<Float>

    /**
     * Deletes a previously downloaded model from local storage.
     *
     * @param modelId Unique model identifier
     * @return true if the model was deleted, false if it was not found
     */
    public suspend fun delete(modelId: String): Boolean

    /**
     * Lists all models currently cached on the device.
     *
     * @return List of model identifiers that are available locally
     */
    public suspend fun listCached(): List<String>

    /**
     * Checks whether a model is downloaded and available for inference.
     *
     * @param modelId Unique model identifier
     * @return true if the model is available locally
     */
    public suspend fun isDownloaded(modelId: String): Boolean

    /**
     * Returns the absolute file path for a cached model.
     *
     * @param modelId Unique model identifier
     * @return Absolute path to the model file, or null if not downloaded
     */
    public suspend fun getModelPath(modelId: String): String?
}
