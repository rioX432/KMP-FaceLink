@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.kmpfacelink.capture

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import platform.Foundation.NSFileManager

internal actual fun studyTakeRecords(session: CaptureSession): StateFlow<List<StudyTakeRecord>> =
    requireNotNull(session as? IosCaptureSession) {
        "study take state requires IosCaptureSession"
    }.studyTakeRecords

internal actual suspend fun deleteStudyTake(
    session: CaptureSession,
    take: StudyTakeRecord,
): Unit = withContext(Dispatchers.Default) {
    val ios = requireNotNull(session as? IosCaptureSession) {
        "study retake requires IosCaptureSession"
    }
    check(containsPublishedTake(publishedTakeNames(ios.takesDirectory), take.takeId)) {
        "published take ${take.takeId} does not exist"
    }
    val path = joinCapturePath(ios.takesDirectory, take.takeId)
    check(removeCapturePath(path)) { "could not delete $path" }
    ios.removeStudyTake(take.takeId)
}

private fun publishedTakeNames(root: String): List<String> =
    (NSFileManager.defaultManager.contentsOfDirectoryAtPath(root, error = null) as? List<*>)
        ?.filterIsInstance<String>()
        ?: emptyList()

internal fun readIosStudyTakeRecords(root: String): List<StudyTakeRecord> = publishedTakeNames(root)
    .mapNotNull { readStudyTakeRecord(root, it) }
    .sortedWith(compareBy(StudyTakeRecord::scenario, StudyTakeRecord::attempt))

private fun readStudyTakeRecord(root: String, name: String): StudyTakeRecord? {
    val manifestPath = joinCapturePath(
        joinCapturePath(root, name),
        InferenceRecordFormat.TAKE_MANIFEST_FILE,
    )
    val manifest = runCatching {
        rawFrameJson.decodeFromString<TakeManifest>(readCaptureText(manifestPath))
    }.getOrNull() ?: return null
    val scenario = CaptureScenario.byId(manifest.scenarioId) ?: return null
    return StudyTakeRecord(manifest.takeId, scenario, manifest.attempt)
}
