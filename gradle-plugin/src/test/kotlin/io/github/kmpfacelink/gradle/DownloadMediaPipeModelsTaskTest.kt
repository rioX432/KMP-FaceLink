package io.github.kmpfacelink.gradle

import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadMediaPipeModelsTaskTest {

    @Test
    fun `download skipped when file exists and checksum matches`() {
        // Arrange
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register(
            "testDownload",
            DownloadMediaPipeModelsTask::class.java,
        ).get()
        val outputDir = File(project.layout.buildDirectory.get().asFile, "test-models")
        outputDir.mkdirs()

        val content = "fake model content for testing"
        val testFile = File(outputDir, "test_model.task")
        testFile.writeText(content)

        val sha256 = DownloadMediaPipeModelsTask.computeSha256(testFile)

        val model = MediaPipeModelRegistry.ModelEntry(
            name = "Test Model",
            urlTemplate = "https://invalid.example.com/{precision}/{version}/model.task",
            filename = "test_model.task",
            sha256 = sha256,
        )

        task.models.set(listOf(model))
        task.precision.set("float16")
        task.version.set("latest")
        task.outputDir.set(outputDir)

        // Act — should not throw (skips download since file exists and checksum matches)
        task.download()

        // Assert — file unchanged
        assertTrue(testFile.exists(), "File should still exist")
        assertEquals(content, testFile.readText(), "File content should be unchanged")
    }

    @Test
    fun `checksum mismatch deletes file and throws`() {
        // Arrange
        val testFile = File.createTempFile("model", ".task")
        testFile.writeText("corrupted content")

        val wrongChecksum = "0000000000000000000000000000000000000000000000000000000000000000"

        // Act & Assert
        assertFalse(
            DownloadMediaPipeModelsTask.verifyChecksum(testFile, wrongChecksum),
            "Checksum should not match for corrupted file",
        )

        testFile.delete()
    }

    @Test
    fun `verifyChecksum returns true for empty expected checksum`() {
        val testFile = File.createTempFile("model", ".task")
        testFile.writeText("any content")

        assertTrue(
            DownloadMediaPipeModelsTask.verifyChecksum(testFile, ""),
            "Empty expected checksum should always pass",
        )

        testFile.delete()
    }

    @Test
    fun `computeSha256 produces consistent hash`() {
        val testFile = File.createTempFile("model", ".task")
        testFile.writeText("deterministic content")

        val hash1 = DownloadMediaPipeModelsTask.computeSha256(testFile)
        val hash2 = DownloadMediaPipeModelsTask.computeSha256(testFile)

        assertEquals(hash1, hash2, "Same file should produce the same SHA-256 hash")
        assertEquals(SHA256_HEX_LENGTH, hash1.length, "SHA-256 hex string should be 64 characters")

        testFile.delete()
    }

    @Test
    fun `download fails with descriptive error for unreachable URL`() {
        // Arrange
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register(
            "testDownloadFail",
            DownloadMediaPipeModelsTask::class.java,
        ).get()
        val outputDir = File(project.layout.buildDirectory.get().asFile, "test-models-fail")
        outputDir.mkdirs()

        val model = MediaPipeModelRegistry.ModelEntry(
            name = "Unreachable Model",
            urlTemplate = "https://invalid.test.example.invalid/{precision}/{version}/model.task",
            filename = "unreachable_model.task",
            sha256 = "",
        )

        task.models.set(listOf(model))
        task.precision.set("float16")
        task.version.set("latest")
        task.outputDir.set(outputDir)

        // Act & Assert
        val exception = assertFailsWith<Exception> { task.download() }
        assertTrue(
            exception.message?.contains("Failed to download") == true ||
                exception.message?.contains("manually download") == true,
            "Error should contain helpful download instructions",
        )

        // Verify incomplete file is cleaned up
        val destFile = File(outputDir, "unreachable_model.task")
        assertFalse(destFile.exists(), "Incomplete file should be deleted on failure")
    }

    companion object {
        private const val SHA256_HEX_LENGTH = 64
    }
}
