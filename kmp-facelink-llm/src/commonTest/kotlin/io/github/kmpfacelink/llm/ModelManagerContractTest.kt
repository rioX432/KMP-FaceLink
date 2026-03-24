package io.github.kmpfacelink.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contract tests for the [ModelManager] interface.
 *
 * Uses an in-memory fake to verify that model download, cache, and
 * delete operations conform to the expected behavioral contracts.
 */
class ModelManagerContractTest {

    private val testModel = ModelInfo(
        id = "test-model-int4",
        displayName = "Test Model",
        sizeBytes = 1024L,
        quantization = Quantization.INT4,
        downloadUrl = "https://example.com/model.gguf",
        sha256Checksum = "abc123",
    )

    private fun createManager(): FakeModelManager = FakeModelManager()

    @Test
    fun initiallyNothingIsCached() = runTest {
        val manager = createManager()
        assertTrue(manager.listCached().isEmpty())
    }

    @Test
    fun isDownloadedReturnsFalseForUnknownModel() = runTest {
        val manager = createManager()
        assertFalse(manager.isDownloaded("nonexistent"))
    }

    @Test
    fun getModelPathReturnsNullForUnknownModel() = runTest {
        val manager = createManager()
        assertNull(manager.getModelPath("nonexistent"))
    }

    @Test
    fun downloadAddsModelToCache() = runTest {
        val manager = createManager()
        val progress = manager.download(testModel).toList()
        assertTrue(progress.isNotEmpty())
        assertTrue(manager.isDownloaded(testModel.id))
    }

    @Test
    fun downloadEmitsProgressFromZeroToOne() = runTest {
        val manager = createManager()
        val progress = manager.download(testModel).toList()
        assertEquals(0f, progress.first().progress)
        assertEquals(1f, progress.last().progress)
    }

    @Test
    fun listCachedReturnsDownloadedModels() = runTest {
        val manager = createManager()
        manager.download(testModel).toList()
        val cached = manager.listCached()
        assertEquals(1, cached.size)
        assertTrue(cached.contains(testModel.id))
    }

    @Test
    fun getModelPathReturnsPathAfterDownload() = runTest {
        val manager = createManager()
        manager.download(testModel).toList()
        val path = manager.getModelPath(testModel.id)
        assertTrue(path != null && path.contains(testModel.id))
    }

    @Test
    fun deleteRemovesModelFromCache() = runTest {
        val manager = createManager()
        manager.download(testModel).toList()
        assertTrue(manager.delete(testModel.id))
        assertFalse(manager.isDownloaded(testModel.id))
    }

    @Test
    fun deleteReturnsFalseForUnknownModel() = runTest {
        val manager = createManager()
        assertFalse(manager.delete("nonexistent"))
    }
}

private class FakeModelManager : ModelManager {
    private val cache = mutableMapOf<String, String>()

    override fun download(modelInfo: ModelInfo): Flow<DownloadProgress> = flow {
        emit(DownloadProgress(0L, modelInfo.sizeBytes, 0f))
        emit(DownloadProgress(modelInfo.sizeBytes / 2, modelInfo.sizeBytes, 0.5f))
        emit(DownloadProgress(modelInfo.sizeBytes, modelInfo.sizeBytes, 1f))
        cache[modelInfo.id] = "/models/${modelInfo.id}.gguf"
    }

    override suspend fun delete(modelId: String): Boolean = cache.remove(modelId) != null

    override suspend fun listCached(): List<String> = cache.keys.toList()

    override suspend fun isDownloaded(modelId: String): Boolean = cache.containsKey(modelId)

    override suspend fun getModelPath(modelId: String): String? = cache[modelId]
}
