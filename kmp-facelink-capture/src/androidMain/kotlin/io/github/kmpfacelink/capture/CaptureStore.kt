package io.github.kmpfacelink.capture

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Where takes live, and how one becomes visible.
 *
 * A take is recorded into `tmp/` and moved into `takes/` only once its manifest has been written, so
 * an interrupted recording cannot be mistaken for a finished one by anything that lists the
 * directory. Nothing is ever written over: a take id is the timestamp plus the scenario plus the
 * attempt, and a collision means a bug rather than a retake.
 */
internal class CaptureStore(context: Context, private val modelAssetPath: String) {

    private val assets = context.assets
    private val root = File(context.filesDir, ROOT_DIR)
    val takesRoot: File = File(root, TAKES_DIR).apply { mkdirs() }
    private val tmpRoot: File = File(root, TMP_DIR).apply { mkdirs() }

    /**
     * SHA-256 of the model asset as shipped in this build.
     *
     * The one field that makes two takes comparable at all. It is read from the asset rather than
     * declared, because a declared version is what let a study attribute numbers to a model it was
     * not actually running.
     */
    val modelSha256: String by lazy {
        val digest = MessageDigest.getInstance("SHA-256")
        assets.open(modelAssetPath).use { stream ->
            val buffer = ByteArray(HASH_BUFFER_BYTES)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it.toInt() and BYTE_MASK) }
    }

    fun newTakeId(scenario: CaptureScenario, attempt: Int, startedAtMs: Long): String =
        "${timestamp(startedAtMs)}_${scenario.id}_$attempt"

    fun createTmpDir(takeId: String): File {
        val dir = File(tmpRoot, takeId)
        check(!dir.exists()) { "take $takeId already exists in ${tmpRoot.path}" }
        check(dir.mkdirs()) { "could not create ${dir.path}" }
        return dir
    }

    /** Moves a finished take where readers look. Only ever called after the manifest is written. */
    fun publish(tmpDir: File, takeId: String): File {
        val target = File(takesRoot, takeId)
        check(!target.exists()) { "take $takeId is already published" }
        check(tmpDir.renameTo(target)) { "could not move ${tmpDir.path} to ${target.path}" }
        return target
    }

    fun discard(tmpDir: File) {
        tmpDir.deleteRecursively()
    }

    fun publishedTakes(): List<File> =
        takesRoot.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: emptyList()

    fun timestamp(millis: Long): String = FORMAT.get()!!.format(Date(millis))

    private companion object {
        const val ROOT_DIR = "capture"
        const val TAKES_DIR = "takes"
        const val TMP_DIR = "tmp"
        const val HASH_BUFFER_BYTES = 1 shl 16
        const val BYTE_MASK = 0xFF

        // SimpleDateFormat is not thread-safe and takes are stamped from more than one thread.
        val FORMAT: ThreadLocal<SimpleDateFormat> = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat =
                SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
        }
    }
}

/** ISO-8601 in UTC, for the manifest. */
internal fun isoTimestamp(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date(millis))
