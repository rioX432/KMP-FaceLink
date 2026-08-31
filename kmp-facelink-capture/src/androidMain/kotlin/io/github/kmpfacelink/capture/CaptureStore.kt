@file:Suppress(
    // Root ownership must be released for every constructor failure, regardless of whether the
    // failing operation was directory I/O, manifest decoding, or stale-take recovery.
    "TooGenericExceptionCaught",
    "InstanceOfCheckForException",
)

package io.github.kmpfacelink.capture

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Where takes live, and how one becomes visible.
 *
 * A take is recorded into `tmp/` and moved into `takes/` only once its manifest has been written, so
 * an interrupted recording cannot be mistaken for a finished one by anything that lists the
 * directory. Nothing is ever written over: a take id is the timestamp plus the scenario plus the
 * attempt, and a collision means a bug rather than a retake.
 */
// The seven are the identity of a run: where it lives, what model produced it, which protocol and
// storage profile it must match, and whether an existing run may be rotated. A holder would carry
// the same seven and hide which ones a caller is allowed to vary.
@Suppress("LongParameterList")
internal class CaptureStore(
    context: Context,
    private val modelAssetPath: String,
    protocolId: String,
    storageProfile: CaptureStorageProfile,
    requiredStudyRunId: String? = null,
    rootDirectoryName: String = ROOT_DIR,
    refuseActiveRunRotation: Boolean = false,
) {

    internal val root = try {
        require(rootDirectoryName.matches(Regex("[a-z0-9][a-z0-9-]{0,63}"))) {
            "capture root name is invalid"
        }
        File(context.filesDir, rootDirectoryName).apply {
            check(exists() || mkdirs()) { "could not create capture root $path" }
        }
    } catch (failure: Throwable) {
        if (failure is Error) throw failure
        throw captureFailure(CaptureFailureCategory.STORAGE_SETUP, failure)
    }
    private val rootLease = acquireCaptureRootLease(root.canonicalPath)
    val studyRun: StudyRunIdentity
    val takesRoot: File
    private val tmpRoot: File
    internal val recoveredIncompleteTakeCount: Int

    init {
        try {
            studyRun = loadOrCreateAndroidStudyRun(
                root,
                protocolId,
                storageProfile,
                requiredStudyRunId,
                refuseActiveRunRotation,
            )
            takesRoot = File(root, TAKES_DIR).apply {
                check(exists() || mkdirs()) { "could not create takes root $path" }
            }
            tmpRoot = File(root, TMP_DIR).apply {
                check(exists() || mkdirs()) { "could not create tmp root $path" }
            }
            recoveredIncompleteTakeCount = recoverAndroidIncompleteTakes(tmpRoot)
        } catch (failure: Throwable) {
            runCatching { rootLease.release() }.onFailure(failure::addSuppressed)
            if (failure is Error || failure is CaptureFailureException) throw failure
            throw captureFailure(CaptureFailureCategory.STORAGE_SETUP, failure)
        }
    }

    /**
     * SHA-256 of the model asset as shipped in this build.
     *
     * The one field that makes two takes comparable at all. It is read from the asset rather than
     * declared, because a declared version is what let a study attribute numbers to a model it was
     * not actually running.
     */
    val modelSha256: String by lazy { modelAssetSha256(context, modelAssetPath) }

    fun newTakeId(scenario: CaptureScenario, attempt: Int, startedAtMs: Long): String =
        "${timestamp(startedAtMs)}_${scenario.id}_$attempt"

    fun createTmpDir(takeId: String): File {
        try {
            val dir = File(tmpRoot, takeId)
            check(!dir.exists()) { "take $takeId already exists in ${tmpRoot.path}" }
            check(dir.mkdirs()) { "could not create ${dir.path}" }
            return dir
        } catch (failure: Throwable) {
            if (failure is Error || failure is CaptureFailureException) throw failure
            throw captureFailure(CaptureFailureCategory.STORAGE_SETUP, failure)
        }
    }

    /** Moves a finished take where readers look. Only ever called after the manifest is written. */
    fun publish(tmpDir: File, takeId: String): File {
        try {
            val target = File(takesRoot, takeId)
            check(!target.exists()) { "take $takeId is already published" }
            check(tmpDir.renameTo(target)) { "could not move ${tmpDir.path} to ${target.path}" }
            return target
        } catch (failure: Throwable) {
            if (failure is Error || failure is CaptureFailureException) throw failure
            throw captureFailure(CaptureFailureCategory.STREAM_WRITE, failure)
        }
    }

    fun discard(tmpDir: File) {
        try {
            check(tmpDir.deleteRecursively()) { "could not discard incomplete take ${tmpDir.path}" }
        } catch (failure: Throwable) {
            if (failure is Error || failure is CaptureFailureException) throw failure
            throw captureFailure(CaptureFailureCategory.CLEANUP, failure)
        }
    }

    fun publishedTakes(): List<File> =
        takesRoot.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: emptyList()

    fun close() {
        rootLease.release()
    }

    fun timestamp(millis: Long): String = FORMAT.get()!!.format(Date(millis))

    private companion object {
        const val ROOT_DIR = "capture"
        const val TAKES_DIR = "takes"
        const val TMP_DIR = "tmp"

        // SimpleDateFormat is not thread-safe and takes are stamped from more than one thread.
        val FORMAT: ThreadLocal<SimpleDateFormat> = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat =
                SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
        }
    }
}

/** Reclaims only direct children of the capture-owned tmp root; published takes are never scanned. */
internal fun recoverAndroidIncompleteTakes(tmpRoot: File): Int {
    val ownedRoot = tmpRoot.canonicalFile
    val incomplete = tmpRoot.listFiles().orEmpty()
    incomplete.forEach { child ->
        check(child.canonicalFile.parentFile == ownedRoot) {
            "refusing to remove incomplete take outside ${ownedRoot.path}: ${child.path}"
        }
        check(child.deleteRecursively()) { "could not recover incomplete take ${child.path}" }
    }
    return incomplete.size
}

internal fun loadOrCreateAndroidStudyRun(
    root: File,
    protocolId: String,
    storageProfile: CaptureStorageProfile,
    requiredRunId: String? = null,
    refuseActiveRunRotation: Boolean = false,
): StudyRunIdentity {
    require(requiredRunId == null || requiredRunId.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}"))) {
        "required study run ID is invalid"
    }
    val active = File(root, ACTIVE_STUDY_RUN_FILE)
    val restored = runCatching {
        rawFrameJson.decodeFromString<StudyRunIdentity>(active.readText())
    }.getOrNull()
    // Rotation is refused only when every part of the restored identity still matches; listing the
    // parts separately is what makes a mismatch readable in the failure message below.
    @Suppress("ComplexCondition")
    if (
        refuseActiveRunRotation && restored != null &&
        !(
            restored.schemaVersion == StudyRunIdentity.SCHEMA_VERSION &&
                restored.protocolId == protocolId &&
                restored.storageProfile == storageProfile
            )
    ) {
        throw captureFailure(CaptureFailureCategory.CONFIGURATION)
    }
    if (requiredRunId != null && restored != null) {
        require(
            restored.schemaVersion == StudyRunIdentity.SCHEMA_VERSION &&
                restored.protocolId == protocolId &&
                restored.storageProfile == storageProfile &&
                restored.runId == requiredRunId,
        ) {
            "active study run does not match Session-C authorization"
        }
    }
    return activeOrNewStudyRun(restored, protocolId, storageProfile) {
        val created = StudyRunIdentity(
            schemaVersion = StudyRunIdentity.SCHEMA_VERSION,
            runId = requiredRunId ?: UUID.randomUUID().toString(),
            protocolId = protocolId,
            storageProfile = storageProfile,
            createdAtIso = isoTimestamp(System.currentTimeMillis()),
        )
        val staging = File(root, "$ACTIVE_STUDY_RUN_FILE.partial")
        FileOutputStream(staging).use { stream ->
            stream.write(rawFrameJson.encodeToString(created).encodeToByteArray())
            stream.fd.sync()
        }
        Files.move(
            staging.toPath(),
            active.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
        created
    }
}

internal fun modelAssetSha256(context: Context, modelAssetPath: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    context.assets.open(modelAssetPath).use { stream ->
        val buffer = ByteArray(1 shl 16)
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and UNSIGNED_BYTE_MASK) }
}

/** ISO-8601 in UTC, for the manifest. */
internal fun isoTimestamp(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date(millis))

private const val ACTIVE_STUDY_RUN_FILE = "active-study-run-v1.json"
private const val UNSIGNED_BYTE_MASK = 0xFF
