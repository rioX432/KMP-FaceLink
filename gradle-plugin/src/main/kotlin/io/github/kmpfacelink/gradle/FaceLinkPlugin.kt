package io.github.kmpfacelink.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Gradle plugin that registers the `faceLink` DSL extension with convention defaults.
 *
 * Apply in a consumer project:
 * ```
 * plugins {
 *     id("io.github.rioX432.facelink")
 * }
 * ```
 */
public class FaceLinkPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create(
            EXTENSION_NAME,
            FaceLinkExtension::class.java,
        )
        applyConventionDefaults(extension)
    }

    private fun applyConventionDefaults(extension: FaceLinkExtension) {
        extension.faceEnabled.convention(DEFAULT_FACE_ENABLED)
        extension.handEnabled.convention(DEFAULT_HAND_ENABLED)
        extension.bodyEnabled.convention(DEFAULT_BODY_ENABLED)
        extension.precision.convention(DEFAULT_PRECISION)
        extension.outputDir.convention(DEFAULT_OUTPUT_DIR)
        extension.mediapipeVersion.convention(DEFAULT_MEDIAPIPE_VERSION)
    }

    internal companion object {
        const val EXTENSION_NAME: String = "faceLink"
        const val DEFAULT_FACE_ENABLED: Boolean = true
        const val DEFAULT_HAND_ENABLED: Boolean = true
        const val DEFAULT_BODY_ENABLED: Boolean = false
        const val DEFAULT_PRECISION: String = "float16"
        const val DEFAULT_OUTPUT_DIR: String = "src/main/assets/models"
        const val DEFAULT_MEDIAPIPE_VERSION: String = "latest"
    }
}
