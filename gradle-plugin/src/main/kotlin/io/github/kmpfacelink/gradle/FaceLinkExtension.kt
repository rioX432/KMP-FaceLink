package io.github.kmpfacelink.gradle

import org.gradle.api.provider.Property

/**
 * DSL extension for configuring KMP-FaceLink model downloads and tracking features.
 *
 * Usage in consumer build script:
 * ```
 * faceLink {
 *     faceEnabled.set(true)
 *     handEnabled.set(true)
 *     bodyEnabled.set(false)
 *     precision.set("float16")
 * }
 * ```
 */
public interface FaceLinkExtension {

    /** Enable face tracking model download. Default: true. */
    public val faceEnabled: Property<Boolean>

    /** Enable hand tracking model download. Default: true. */
    public val handEnabled: Property<Boolean>

    /** Enable body/pose tracking model download. Default: false. */
    public val bodyEnabled: Property<Boolean>

    /** Model precision variant. Default: "float16". */
    public val precision: Property<String>

    /** Output directory for downloaded models, relative to project dir. */
    public val outputDir: Property<String>

    /** MediaPipe model version tag used in download URLs. Default: "latest". */
    public val mediapipeVersion: Property<String>
}
