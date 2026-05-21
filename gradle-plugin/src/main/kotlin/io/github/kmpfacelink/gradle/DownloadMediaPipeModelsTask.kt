package io.github.kmpfacelink.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.IOException
import java.net.URI
import java.security.MessageDigest

/**
 * Gradle task that downloads MediaPipe model files and verifies SHA-256 checksums.
 *
 * Skips download when the file already exists and its checksum matches.
 * Deletes incomplete or corrupted files before throwing on failure.
 */
public abstract class DownloadMediaPipeModelsTask : DefaultTask() {

    /** Model entries to download. */
    @get:Input
    public abstract val models: ListProperty<MediaPipeModelRegistry.ModelEntry>

    /** Model precision variant (e.g. "float16"). */
    @get:Input
    public abstract val precision: Property<String>

    /** MediaPipe model version tag (e.g. "latest"). */
    @get:Input
    public abstract val version: Property<String>

    /** Output directory for downloaded model files. */
    @get:OutputDirectory
    public abstract val outputDir: DirectoryProperty

    @TaskAction
    public fun download() {
        val dir = outputDir.get().asFile
        dir.mkdirs()
        val p = precision.get()
        val v = version.get()
        for (model in models.get()) {
            val destFile = File(dir, model.filename)
            if (destFile.exists() && verifyChecksum(destFile, model.sha256)) {
                logger.lifecycle("Skipping ${model.name}: already downloaded and checksum matches")
                continue
            }
            downloadModel(model, p, v, destFile)
        }
    }

    private fun downloadModel(
        model: MediaPipeModelRegistry.ModelEntry,
        precision: String,
        version: String,
        destFile: File,
    ) {
        val url = MediaPipeModelRegistry.resolveUrl(model, precision, version)
        logger.lifecycle("Downloading ${model.name} from $url")
        try {
            URI(url).toURL().openStream().use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: IOException) {
            destFile.delete()
            throw IOException(
                "Failed to download ${model.name}. " +
                    "If offline, manually download from:\n  $url\n" +
                    "and place it at: ${destFile.absolutePath}",
                e,
            )
        }
        validateDownloadedFile(destFile, model)
    }

    private fun validateDownloadedFile(destFile: File, model: MediaPipeModelRegistry.ModelEntry) {
        if (model.sha256.isNotEmpty() && !verifyChecksum(destFile, model.sha256)) {
            val actual = computeSha256(destFile)
            destFile.delete()
            error(
                "Checksum mismatch for ${model.name}. " +
                    "Expected: ${model.sha256}, Got: $actual. " +
                    "The file has been deleted. Re-run the build to retry.",
            )
        }
        logger.lifecycle("Downloaded ${model.name} to ${destFile.path}")
    }

    internal companion object {

        internal fun verifyChecksum(file: File, expectedSha256: String): Boolean {
            if (expectedSha256.isEmpty()) return true
            return computeSha256(file).equals(expectedSha256, ignoreCase = true)
        }

        internal fun computeSha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var bytesRead = input.read(buffer)
                while (bytesRead != -1) {
                    digest.update(buffer, 0, bytesRead)
                    bytesRead = input.read(buffer)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
