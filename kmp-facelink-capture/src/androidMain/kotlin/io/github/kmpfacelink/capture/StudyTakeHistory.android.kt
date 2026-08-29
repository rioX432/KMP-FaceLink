package io.github.kmpfacelink.capture

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import java.io.File

internal actual fun studyTakeRecords(session: CaptureSession): StateFlow<List<StudyTakeRecord>> =
    requireNotNull(session as? AndroidCaptureSession) {
        "study take state requires AndroidCaptureSession"
    }.studyTakeRecords

internal actual suspend fun deleteStudyTake(
    session: CaptureSession,
    take: StudyTakeRecord,
): Unit = deleteStudyTakeOnIo(session, take)

// The expect/actual API intentionally keeps dispatcher selection inside each platform adapter.
@Suppress("InjectDispatcher")
private suspend fun deleteStudyTakeOnIo(session: CaptureSession, take: StudyTakeRecord): Unit =
    withContext(Dispatchers.IO) {
        val android = requireNotNull(session as? AndroidCaptureSession) {
            "study retake requires AndroidCaptureSession"
        }
        val published = android.takesDirectory.listFiles()
            ?.filter(File::isDirectory)
            ?: emptyList()
        check(containsPublishedTake(published.map(File::getName), take.takeId)) {
            "published take ${take.takeId} does not exist"
        }
        val target = requireNotNull(published.firstOrNull { it.name == take.takeId })
        check(target.deleteRecursively()) { "could not delete ${target.path}" }
        android.removeStudyTake(take.takeId)
    }

internal fun readAndroidStudyTakeRecords(root: File): List<StudyTakeRecord> = root.listFiles()
    ?.filter(File::isDirectory)
    ?.mapNotNull(::readStudyTakeRecord)
    ?.sortedWith(compareBy(StudyTakeRecord::scenario, StudyTakeRecord::attempt))
    ?: emptyList()

// Detekt's Android analysis treats the generic kotlinx decode call as non-returning; it is not.
@Suppress("UnreachableCode")
private fun readStudyTakeRecord(directory: File): StudyTakeRecord? {
    val manifest = try {
        rawFrameJson.decodeFromString<TakeManifest>(
            File(directory, InferenceRecordFormat.TAKE_MANIFEST_FILE).readText(),
        )
    } catch (_: Exception) {
        return null
    }
    val scenario = CaptureScenario.byId(manifest.scenarioId)
    if (scenario == null) return null
    return StudyTakeRecord(manifest.takeId, scenario, manifest.attempt)
}
