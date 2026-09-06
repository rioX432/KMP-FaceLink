package io.github.kmpfacelink.capture

private val GUIDED_22_SCENARIOS = listOf(
    CaptureScenario.NEUTRAL,
    CaptureScenario.JAW_OPEN,
    CaptureScenario.CHEEK_PUFF_LIGHT,
    CaptureScenario.CHEEK_PUFF_MID,
    CaptureScenario.CHEEK_PUFF,
    CaptureScenario.CHEEK_PUFF_LEFT,
    CaptureScenario.CHEEK_PUFF_RIGHT,
    CaptureScenario.BLOW,
    CaptureScenario.BLOW,
    CaptureScenario.PUCKER,
    CaptureScenario.SMILE,
    CaptureScenario.TONGUE_OUT,
    CaptureScenario.NOSE_SNEER,
    CaptureScenario.SPEECH,
    CaptureScenario.CHEEK_PUFF_LIGHT,
    CaptureScenario.CHEEK_PUFF_MID,
    CaptureScenario.CHEEK_PUFF,
    CaptureScenario.CHEEK_PUFF_YAW_RIGHT,
    CaptureScenario.CHEEK_PUFF_YAW_LEFT,
    CaptureScenario.EYE_WIDEN,
    CaptureScenario.JAW_FORWARD,
    CaptureScenario.CLENCH,
)

private val IOS_REGISTERED_COEFFICIENT_17_SCENARIOS = GUIDED_22_SCENARIOS.take(17)

private val LEARNED_PATCH_SESSION_C_12_SCENARIOS = listOf(
    CaptureScenario.NEUTRAL,
    CaptureScenario.JAW_OPEN,
    CaptureScenario.CHEEK_PUFF_LIGHT,
    CaptureScenario.CHEEK_PUFF_MID,
    CaptureScenario.CHEEK_PUFF,
    CaptureScenario.BLOW,
    CaptureScenario.PUCKER,
    CaptureScenario.CHEEK_PUFF_LIGHT,
    CaptureScenario.CHEEK_PUFF_MID,
    CaptureScenario.CHEEK_PUFF,
    CaptureScenario.BLOW,
    CaptureScenario.PUCKER,
)

/**
 * One immutable recording plan.
 *
 * The protocol ID, storage contract, and ordered scenarios travel as one value so a host cannot
 * show one study in the UI while opening storage for another. A repeated scenario is a distinct
 * registered slot; callers must preserve [scenarios] exactly rather than de-duplicating it.
 */
public class CaptureStudyProtocol(
    public val id: String,
    public val storageProfile: CaptureStorageProfile,
    scenarios: List<CaptureScenario>,
) {
    /** Defensive copy: mutating a caller-owned list cannot alter an active run. */
    public val scenarios: List<CaptureScenario> = scenarios.toList()

    init {
        require(id.isNotBlank() && id != StudyRunIdentity.LEGACY_PROTOCOL_ID) {
            "study protocol ID is missing"
        }
        require(storageProfile != CaptureStorageProfile.LEGACY_DIAGNOSTIC_ONLY) {
            "legacy storage cannot define an active study protocol"
        }
        require(scenarios.isNotEmpty()) { "study protocol has no scenarios" }
        when (id) {
            StudyRunIdentity.GUIDED_PROTOCOL_ID -> require(
                storageProfile == CaptureStorageProfile.COEFFICIENTS_ONLY_V1 &&
                    this.scenarios == GUIDED_22_SCENARIOS,
            ) { "guided-22 identity requires its exact storage profile and ordered scenarios" }
            IOS_REGISTERED_COEFFICIENT_17_ID -> require(
                storageProfile == CaptureStorageProfile.COEFFICIENTS_ONLY_V1 &&
                    this.scenarios == IOS_REGISTERED_COEFFICIENT_17_SCENARIOS,
            ) { "iOS registered-17 identity requires its exact storage profile and ordered scenarios" }
            LEARNED_PATCH_SESSION_C_ID -> require(
                storageProfile == CaptureStorageProfile.CANONICAL_RGBA_240X320_V1 &&
                    this.scenarios == LEARNED_PATCH_SESSION_C_12_SCENARIOS,
            ) { "Session-C identity requires its exact storage profile and ordered scenarios" }
        }
    }

    override fun equals(other: Any?): Boolean = this === other ||
        other is CaptureStudyProtocol &&
        id == other.id &&
        storageProfile == other.storageProfile &&
        scenarios == other.scenarios

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + storageProfile.hashCode()
        result = 31 * result + scenarios.hashCode()
        return result
    }

    override fun toString(): String =
        "CaptureStudyProtocol(id=$id, storageProfile=$storageProfile, scenarios=$scenarios)"

    public companion object {
        public const val IOS_REGISTERED_COEFFICIENT_17_ID: String =
            "IOS_REGISTERED_COEFFICIENT_17_V1"
        public const val LEARNED_PATCH_SESSION_C_ID: String =
            "CHEEK_PUFF_LEARNED_PATCH_CONFIRMATORY_12_V1"

        /** Existing broad cheek-puff study and diagnostic extras. */
        public fun guided22(): CaptureStudyProtocol = CaptureStudyProtocol(
            id = StudyRunIdentity.GUIDED_PROTOCOL_ID,
            storageProfile = CaptureStorageProfile.COEFFICIENTS_ONLY_V1,
            scenarios = GUIDED_22_SCENARIOS,
        )

        /** Minimal coefficient-only iOS retake registered before device execution. */
        public fun iosRegisteredCoefficient17(): CaptureStudyProtocol = CaptureStudyProtocol(
            id = IOS_REGISTERED_COEFFICIENT_17_ID,
            storageProfile = CaptureStorageProfile.COEFFICIENTS_ONLY_V1,
            scenarios = IOS_REGISTERED_COEFFICIENT_17_SCENARIOS,
        )

        /** Frozen Android-only confirmation plan for the Session-B learned appearance candidate. */
        public fun learnedPatchSessionC12(): CaptureStudyProtocol = CaptureStudyProtocol(
            id = LEARNED_PATCH_SESSION_C_ID,
            storageProfile = CaptureStorageProfile.CANONICAL_RGBA_240X320_V1,
            scenarios = LEARNED_PATCH_SESSION_C_12_SCENARIOS,
        )
    }
}
