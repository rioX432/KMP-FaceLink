package io.github.kmpfacelink.capture

import kotlinx.serialization.Serializable

/**
 * Everything a finished take needs for its numbers to mean something later.
 *
 * Written last, so its presence is what marks the take complete — the same rule the frame payload
 * uses. Two takes are only comparable through [condition], which is why it is not optional and not
 * a free-text note.
 *
 * @property scenarioId [CaptureScenario.id], the stable value the analysis reads
 * @property attempt which run of this scenario in the session. Recorded because a retake is a
 *   different performance, not a repeat of the same one
 * @property analysisFramesAdmitted frames the analyzer accepted. In LIVE_STREAM this is larger than
 *   [inferenceResults] by however many the engine declined to run
 * @property framesStored frames written to the payload. Equal to [analysisFramesAdmitted] when the
 *   take recorded frames at all, and zero when it did not
 * @property spans the phase timeline actually used, so the analysis does not have to reconstruct it
 *   from timestamps
 * @property blendshapeNames the names MediaPipe returned at runtime, at their category index.
 *   Authoritative over any hardcoded order
 */
@Serializable
public data class TakeManifest(
    public val schemaVersion: Int,
    public val takeId: String,
    public val scenarioId: String,
    public val attempt: Int,
    public val startedAtIso: String,
    public val condition: CaptureCondition,
    public val analysisFramesAdmitted: Int,
    public val inferenceResults: Int,
    public val noFaceResults: Int,
    public val inferenceFailedResults: Int,
    public val inferenceErrors: Int,
    public val framesStored: Int,
    public val softValidationPassed: Boolean,
    public val softValidationReason: String,
    public val medianIod: Float,
    public val outOfDistanceBandFraction: Float,
    public val spans: List<TakeSpan>,
    public val blendshapeNames: List<String>,
    public val resultIndexSha256: String,
    public val landmarksSha256: String,
    public val blendshapesSha256: String,
    public val transformSha256: String,
    /** Participant workflow that initiated this take; missing v1 data decodes as manual. */
    public val captureWorkflow: CaptureWorkflow = CaptureWorkflow.LEGACY_MANUAL_V1,
    /** Required for schema 4; legacy schema remains readable only for diagnostics. */
    public val storageReceipt: CaptureStorageReceipt = CaptureStorageReceipt.legacyDiagnosticOnly(),
    /** Required for schema 5; older takes are deliberately unscoped diagnostics. */
    public val studyRun: StudyRunIdentity = StudyRunIdentity.legacyUnscoped(),
) {
    init {
        require(schemaVersion <= SCHEMA_VERSION) {
            "unsupported take manifest schema $schemaVersion"
        }
        if (schemaVersion >= STORAGE_RECEIPT_SCHEMA_VERSION) {
            storageReceipt.requireSchema4Consistency(framesStored)
        }
        if (schemaVersion >= SCHEMA_VERSION) {
            require(studyRun.schemaVersion == StudyRunIdentity.SCHEMA_VERSION) {
                "schema-5 takes require a current study run identity"
            }
            require(studyRun.storageProfile == storageReceipt.profile) {
                "take storage profile differs from its study run"
            }
        }
    }
    public companion object {
        /** Bumped whenever this file's layout changes in a way the analysis must know about. */
        public const val SCHEMA_VERSION: Int = 5
        private const val STORAGE_RECEIPT_SCHEMA_VERSION: Int = 4
    }
}

/** Interaction protocol is recorded separately from the camera/estimator condition. */
@Serializable
public enum class CaptureWorkflow {
    /** Original per-take Start and Next buttons. */
    LEGACY_MANUAL_V1,

    /** One introduction, gate-stable countdown, automatic advance, and explicit pause/retry. */
    GUIDED_GATE_AUTOSTART_V1,
}

/**
 * One stretch of the take, as it was actually run.
 *
 * Stored by wall time and by sequence. The times say what the subject was asked to do; the
 * sequences say which frames were in it, and only the sequences survive frames the engine dropped.
 */
@Serializable
public data class TakeSpan(
    public val cue: String,
    public val phaseCode: Int,
    public val startMs: Long,
    public val endMs: Long,
    public val firstFrameSeq: Int,
    public val lastFrameSeq: Int,
)
