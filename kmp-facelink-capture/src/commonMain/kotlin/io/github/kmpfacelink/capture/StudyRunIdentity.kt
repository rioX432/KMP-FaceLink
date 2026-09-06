package io.github.kmpfacelink.capture

import kotlinx.serialization.Serializable

/** Non-biometric identity that prevents published takes from leaking across studies. */
@Serializable
public data class StudyRunIdentity(
    public val schemaVersion: Int,
    public val runId: String,
    public val protocolId: String,
    public val storageProfile: CaptureStorageProfile,
    public val createdAtIso: String,
) {
    init {
        if (schemaVersion >= SCHEMA_VERSION) {
            require(runId.isNotBlank() && runId != LEGACY_RUN_ID) { "study run ID is missing" }
            require(protocolId.isNotBlank() && protocolId != LEGACY_PROTOCOL_ID) {
                "study protocol ID is missing"
            }
            require(storageProfile != CaptureStorageProfile.LEGACY_DIAGNOSTIC_ONLY) {
                "legacy storage cannot identify an active study run"
            }
            require(createdAtIso.isNotBlank()) { "study run creation time is missing" }
        }
    }

    public fun isExactMatch(other: StudyRunIdentity): Boolean = this == other

    public companion object {
        public const val SCHEMA_VERSION: Int = 1
        public const val GUIDED_PROTOCOL_ID: String = "CHEEK_PUFF_GUIDED_22_V1"
        public const val LEGACY_RUN_ID: String = "LEGACY_UNSCOPED"
        public const val LEGACY_PROTOCOL_ID: String = "LEGACY_UNSCOPED"

        public fun legacyUnscoped(): StudyRunIdentity = StudyRunIdentity(
            schemaVersion = 0,
            runId = LEGACY_RUN_ID,
            protocolId = LEGACY_PROTOCOL_ID,
            storageProfile = CaptureStorageProfile.LEGACY_DIAGNOSTIC_ONLY,
            createdAtIso = "",
        )
    }
}

internal inline fun activeOrNewStudyRun(
    restored: StudyRunIdentity?,
    protocolId: String,
    storageProfile: CaptureStorageProfile,
    createNew: () -> StudyRunIdentity,
): StudyRunIdentity {
    require(protocolId.isNotBlank()) { "study protocol ID is missing" }
    require(storageProfile != CaptureStorageProfile.LEGACY_DIAGNOSTIC_ONLY) {
        "legacy storage cannot start a study run"
    }
    if (restored?.matches(protocolId, storageProfile) == true) {
        return restored
    }

    return createNew().also { created ->
        require(created.schemaVersion == StudyRunIdentity.SCHEMA_VERSION)
        require(created.protocolId == protocolId)
        require(created.storageProfile == storageProfile)
    }
}

private fun StudyRunIdentity.matches(
    protocolId: String,
    storageProfile: CaptureStorageProfile,
): Boolean = schemaVersion == StudyRunIdentity.SCHEMA_VERSION &&
    this.protocolId == protocolId &&
    this.storageProfile == storageProfile
