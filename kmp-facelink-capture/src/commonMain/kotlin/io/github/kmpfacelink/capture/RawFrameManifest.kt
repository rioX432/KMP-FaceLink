package io.github.kmpfacelink.capture

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What a finished take says about itself. Its presence is what marks the take complete.
 *
 * One declaration for the writer, the reader and the offline tooling, and parsed rather than
 * pattern-matched. Picking fields out of the text with regular expressions accepted a manifest that
 * had been cut off part way through, and would accept one missing [payloadSha256] entirely — which
 * is the field that decides whether the pixels are still the pixels.
 *
 * [phaseCodes] is carried in the file so a reader that does not share this enum — the Python
 * re-inference tooling — can map the stored codes without hardcoding them.
 *
 * @property payloadSha256 lower-case hex, over the whole payload file as it was left on disk
 * @property indexSha256 lower-case hex, over the whole index file
 */
@Serializable
public data class RawFrameManifest(
    public val schemaVersion: Int,
    public val pixelFormat: String,
    public val bytesPerPixel: Int,
    public val width: Int,
    public val height: Int,
    public val frameCount: Int,
    public val bytesPerFrame: Int,
    public val indexRecordBytes: Int,
    public val compressed: Boolean,
    public val payloadSha256: String,
    public val indexSha256: String,
    public val phaseCodes: Map<String, Int>,
)

/**
 * How a manifest is written and read.
 *
 * Unknown keys are refused, which is the default: a take written by a later build carries fields
 * this one does not understand, and reading it as if those fields did not matter is how a format
 * change turns into wrong numbers instead of an error.
 */
internal val rawFrameJson: Json = Json {
    prettyPrint = true
}

/** The phase codes as this build defines them, for [RawFrameManifest.phaseCodes]. */
internal fun capturePhaseCodes(): Map<String, Int> =
    CapturePhase.entries.associate { it.name to it.code }
