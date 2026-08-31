package io.github.kmpfacelink.capture

import kotlinx.coroutines.flow.StateFlow

/** The non-biometric fields the recording workflow needs from one published take. */
internal data class StudyTakeRecord(
    val takeId: String,
    val scenario: CaptureScenario,
    val attempt: Int,
    val studyRun: StudyRunIdentity = StudyRunIdentity.legacyUnscoped(),
)

internal enum class StudyTakeGroup { PROTOCOL, EXTRA }

/** One position in the ordered recording plan, including repeated scenarios. */
internal data class StudyTakeSlot(
    val scenario: CaptureScenario,
    val attempt: Int,
    val group: StudyTakeGroup,
) {
    val key: String get() = "${scenario.id}#$attempt"
}

/** Reactive published-take state. Initial restoration reads manifests, never biometric payloads. */
internal expect fun studyTakeRecords(session: CaptureSession): StateFlow<List<StudyTakeRecord>>

/** Deletes the published take whose ID exactly matches [take]. */
internal expect suspend fun deleteStudyTake(
    session: CaptureSession,
    take: StudyTakeRecord,
)

internal fun acceptedStudyTake(
    records: List<StudyTakeRecord>,
    slot: StudyTakeSlot,
): StudyTakeRecord? = records.firstOrNull {
    it.scenario == slot.scenario && it.attempt == slot.attempt
}

internal fun studyTakeSlots(scenarios: List<CaptureScenario>): List<StudyTakeSlot> {
    val attempts = mutableMapOf<CaptureScenario, Int>()
    return scenarios.map { scenario ->
        val attempt = attempts.getOrElse(scenario) { 0 } + 1
        attempts[scenario] = attempt
        StudyTakeSlot(
            scenario = scenario,
            attempt = attempt,
            group = if (scenario in EXTRA_STUDY_SCENARIOS) StudyTakeGroup.EXTRA else StudyTakeGroup.PROTOCOL,
        )
    }
}

internal fun completedStudyTakeCount(
    slots: List<StudyTakeSlot>,
    records: List<StudyTakeRecord>,
): Int = slots.count { acceptedStudyTake(records, it) != null }

internal fun nextStudyAttempt(
    records: List<StudyTakeRecord>,
    scenario: CaptureScenario,
): Int {
    val used = records.filter { it.scenario == scenario }.mapTo(hashSetOf(), StudyTakeRecord::attempt)
    return generateSequence(1) { it + 1 }.first { it !in used }
}

/** Returns the next registered slot and rejects scenarios or repetitions outside the active plan. */
internal fun nextProtocolAttempt(
    records: List<StudyTakeRecord>,
    protocol: CaptureStudyProtocol,
    scenario: CaptureScenario,
): Int {
    val allowed = protocol.scenarios.count { it == scenario }
    require(allowed > 0) { "scenario ${scenario.id} is not registered in protocol ${protocol.id}" }
    val attempt = nextStudyAttempt(records, scenario)
    require(attempt <= allowed) {
        "scenario ${scenario.id} has no remaining slot in protocol ${protocol.id}"
    }
    return attempt
}

internal fun containsPublishedTake(takeIds: List<String>, takeId: String): Boolean =
    takeId in takeIds

internal fun withPublishedStudyTake(
    records: List<StudyTakeRecord>,
    take: StudyTakeRecord,
): List<StudyTakeRecord> = records + take

internal fun withoutStudyTake(
    records: List<StudyTakeRecord>,
    takeId: String,
): List<StudyTakeRecord> = records.filterNot { it.takeId == takeId }

internal fun requireUniqueStudySlots(records: List<StudyTakeRecord>): List<StudyTakeRecord> {
    val duplicate = records.groupBy { "${it.scenario.id}#${it.attempt}" }
        .entries.firstOrNull { it.value.size > 1 }
    require(duplicate == null) { "duplicate published study slot ${duplicate?.key}" }
    return records
}

internal fun scopedStudyTakeRecords(
    records: List<StudyTakeRecord>,
    activeStudyRun: StudyRunIdentity,
): List<StudyTakeRecord> = requireUniqueStudySlots(
    records.filter { it.studyRun.isExactMatch(activeStudyRun) },
)

/**
 * Takes recorded alongside the protocol but outside it.
 *
 * Membership here is what keeps a take out of the registered judgement, so adding a scenario to the
 * plan without adding it here silently promotes it into the set the pre-registration scores.
 */
private val EXTRA_STUDY_SCENARIOS = setOf(
    CaptureScenario.CHEEK_PUFF_YAW_RIGHT,
    CaptureScenario.CHEEK_PUFF_YAW_LEFT,
    CaptureScenario.EYE_WIDEN,
    CaptureScenario.CLENCH,
    CaptureScenario.JAW_FORWARD,
)
