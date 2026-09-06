@file:Suppress("LongParameterList", "TooManyFunctions")

package io.github.kmpfacelink.capture

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Durable identity for one atomically published H2 block. */
internal data class H2PublishedStreamingBlock(
    val blockId: String,
    val attempt: Int,
    val manifestSha256: String,
    val totalBytes: Long,
)

/** Narrow platform seam for syncing directory entries after create/rename operations. */
internal fun interface H2DirectoryDurability {
    fun sync(directory: File)
}

/** Internal fault seam used to prove storage-state transitions without a device or private input. */
internal fun interface H2StreamingFaultInjection {
    fun before(point: H2StreamingFaultPoint, logicalName: String?)
}

internal enum class H2StreamingFaultPoint {
    STREAM_WRITE,
    PUBLISH_FINALIZE,
    PUBLISH_MANIFEST_WRITE,
    PUBLISH_MANIFEST_SYNCED,
    PUBLISH_ATOMIC_MOVE,
    PUBLISH_MOVED,
    TERMINATE_FINALIZE,
    TERMINATE_ATOMIC_MOVE,
}

/**
 * H2-only integration point beside the existing Android capture manager.
 *
 * Payloads are streamed into a bounded same-directory partial block, individually synced, and
 * published by one atomic directory rename. Audio and cloud destinations are not representable.
 * Directory fsync is exposed through an injected platform seam. The current locked build uses the
 * no-op default, so parent-directory durability remains an explicit claim limitation until a
 * physical branch supplies and tests a platform implementation.
 */
internal class AndroidH2StreamingCaptureManager(
    private val root: File,
    private val maxBlockBytes: Long,
    private val directoryDurability: H2DirectoryDurability = H2DirectoryDurability { },
    private val faultInjection: H2StreamingFaultInjection = H2StreamingFaultInjection { _, _ -> },
) {
    private var active: H2StreamingBlockWriter? = null

    init {
        require(maxBlockBytes > 0)
        requireSafeRoot(root)
        recoverInterruptedBlocks()
    }

    @Synchronized
    fun beginBlock(blockId: String, attempt: Int): H2StreamingBlockWriter {
        require(blockId in H2ConfirmationContract.blocks.map(H2ConfirmationBlock::id))
        require(attempt in 1..2)
        check(active == null) { "another H2 block is active" }
        val partial = child(".$blockId.$attempt.partial")
        val published = child("$blockId.$attempt")
        val terminated = child(".$blockId.$attempt.terminated")
        check(!partial.exists() && !published.exists() && !terminated.exists()) { "H2 block path collision" }
        check(partial.mkdir()) { "cannot create H2 partial block" }
        refuseSymlink(partial)
        directoryDurability.sync(root)
        return H2StreamingBlockWriter(
            partial,
            published,
            blockId,
            attempt,
            maxBlockBytes,
            directoryDurability,
            faultInjection,
        ).also { active = it }
    }

    @Synchronized
    fun publishBlock(writer: H2StreamingBlockWriter): H2PublishedStreamingBlock {
        check(active === writer) { "H2 block writer is not the active writer" }
        return try {
            writer.publish()
        } catch (failure: H2ControllerStorageFailure) {
            throw failure
        } catch (cause: H2StreamingHashOrLengthFailure) {
            failPublication(writer, H2FailureCode.UNPUBLISHED_HASH_OR_LENGTH_MISMATCH, cause)
        } catch (cause: Exception) {
            val code = if (writer.publicationCommitted) {
                H2FailureCode.POST_PUBLICATION_MUTATION
            } else {
                H2FailureCode.UNPUBLISHED_ATOMIC_PUBLICATION_PRECONDITION_FAILURE
            }
            failPublication(writer, code, cause)
        } finally {
            active = null
        }
    }

    private fun failPublication(
        writer: H2StreamingBlockWriter,
        code: H2FailureCode,
        cause: Exception,
    ): Nothing {
        if (writer.retainedPartial) {
            try {
                writer.terminate(code)
            } catch (cleanup: Exception) {
                throw H2ControllerStorageFailure(H2FailureCode.POST_PUBLICATION_MUTATION, cleanup)
            }
        }
        throw H2ControllerStorageFailure(code, cause)
    }

    @Synchronized
    fun terminateActive(code: H2FailureCode) {
        try {
            active?.terminate(code)
        } finally {
            active = null
        }
    }

    fun recoverInterruptedBlocks(): Int {
        var recovered = 0
        root.listFiles().orEmpty().filter { it.name.endsWith(".partial") }.forEach { partial ->
            refuseSymlink(partial)
            val terminated = child(partial.name.removeSuffix(".partial") + ".terminated")
            check(!terminated.exists()) { "H2 recovery path collision" }
            finalizeRecoveredAttempt(partial, H2FailureCode.RESTART_DURING_ACTIVE_ATTEMPT)
            Files.move(partial.toPath(), terminated.toPath(), StandardCopyOption.ATOMIC_MOVE)
            directoryDurability.sync(root)
            recovered++
        }
        return recovered
    }

    private fun child(name: String): File = File(root, name).also { candidate ->
        require(checkNotNull(candidate.parentFile).canonicalFile == root.canonicalFile) { "H2 path escaped its root" }
    }

    private fun finalizeRecoveredAttempt(partial: File, code: H2FailureCode) {
        partial.listFiles().orEmpty().forEach(::refuseSymlink)
        val streamFiles = H2StreamingBlockWriter.REQUIRED_STREAMS.mapNotNull { name ->
            File(partial, name).takeIf(File::isFile)
        }
        val records = streamFiles.joinToString("") { file ->
            "${file.name}\t${file.length()}\t${sha256(file.readBytes())}\n"
        }
        syncFile(File(partial, H2StreamingBlockWriter.TERMINATED_STREAM_MANIFEST), records.encodeToByteArray())
        syncFile(File(partial, H2StreamingBlockWriter.FAILURE_CODE_FILE), "${code.name}\n".encodeToByteArray())
    }
}

internal class H2StreamingBlockWriter(
    private val partial: File,
    private val published: File,
    private val blockId: String,
    private val attempt: Int,
    private val limitBytes: Long,
    private val directoryDurability: H2DirectoryDurability,
    private val faultInjection: H2StreamingFaultInjection,
) {
    private val streams = linkedMapOf<String, BoundedDigestStream>()
    private var totalBytes = 0L
    private var state = WriterState.OPEN

    val publicationCommitted: Boolean
        get() = state == WriterState.PUBLISHED || published.exists()

    val retainedPartial: Boolean
        get() = partial.isDirectory

    fun open(logicalName: String): OutputStream {
        check(state == WriterState.OPEN)
        require(logicalName in REQUIRED_STREAMS)
        check(logicalName !in streams) { "H2 stream already opened" }
        val file = File(partial, logicalName)
        require(checkNotNull(file.parentFile).canonicalFile == partial.canonicalFile)
        check(!file.exists()) { "H2 stream path collision" }
        return BoundedDigestStream(
            FileOutputStream(file),
            limitBytes,
            logicalName,
            faultInjection,
        ) { count ->
            totalBytes += count
            require(totalBytes <= limitBytes) { "H2 block byte ceiling exceeded" }
        }.also { streams[logicalName] = it }
    }

    fun publish(): H2PublishedStreamingBlock {
        check(state == WriterState.OPEN)
        faultInjection.before(H2StreamingFaultPoint.PUBLISH_FINALIZE, null)
        state = WriterState.FINALIZING
        streams.values.forEach(BoundedDigestStream::finish)
        check(streams.keys == REQUIRED_STREAMS) { "H2 stream inventory is incomplete" }
        partial.listFiles().orEmpty().forEach(::refuseSymlink)
        val records = REQUIRED_STREAMS.map { name ->
            val stream = checkNotNull(streams[name])
            val retained = File(partial, name)
            if (retained.length() != stream.bytesWritten || sha256(retained.readBytes()) != stream.sha256) {
                throw H2StreamingHashOrLengthFailure(name)
            }
            "$name\t${stream.bytesWritten}\t${stream.sha256}\n"
        }.joinToString("")
        val manifest = File(partial, STREAM_MANIFEST)
        faultInjection.before(H2StreamingFaultPoint.PUBLISH_MANIFEST_WRITE, null)
        FileOutputStream(manifest).use { output ->
            output.write(records.encodeToByteArray())
            output.fd.sync()
        }
        faultInjection.before(H2StreamingFaultPoint.PUBLISH_MANIFEST_SYNCED, null)
        check(!published.exists()) { "H2 published block collision" }
        faultInjection.before(H2StreamingFaultPoint.PUBLISH_ATOMIC_MOVE, null)
        Files.move(partial.toPath(), published.toPath(), StandardCopyOption.ATOMIC_MOVE)
        state = WriterState.PUBLISHED
        faultInjection.before(H2StreamingFaultPoint.PUBLISH_MOVED, null)
        directoryDurability.sync(checkNotNull(published.parentFile))
        return H2PublishedStreamingBlock(blockId, attempt, sha256(records.encodeToByteArray()), totalBytes)
    }

    fun terminate(code: H2FailureCode) {
        if (state == WriterState.TERMINATED || state == WriterState.PUBLISHED) return
        faultInjection.before(H2StreamingFaultPoint.TERMINATE_FINALIZE, null)
        state = WriterState.FINALIZING
        streams.values.forEach(BoundedDigestStream::finish)
        val publicationManifest = File(partial, STREAM_MANIFEST)
        check(!publicationManifest.exists() || publicationManifest.delete()) {
            "cannot remove incomplete H2 publication manifest"
        }
        if (code == H2FailureCode.UNPUBLISHED_EVENT_LOG_WRITE_FAILURE) sanitizeEventPrefix()
        val records = streams.keys.joinToString("") { name ->
            val file = File(partial, name)
            "$name\t${file.length()}\t${sha256(file.readBytes())}\n"
        }
        syncFile(File(partial, TERMINATED_STREAM_MANIFEST), records.encodeToByteArray())
        syncFile(File(partial, FAILURE_CODE_FILE), "${code.name}\n".encodeToByteArray())
        val terminated = File(checkNotNull(partial.parentFile), partial.name.removeSuffix(".partial") + ".terminated")
        check(!terminated.exists())
        faultInjection.before(H2StreamingFaultPoint.TERMINATE_ATOMIC_MOVE, null)
        Files.move(partial.toPath(), terminated.toPath(), StandardCopyOption.ATOMIC_MOVE)
        state = WriterState.TERMINATED
        directoryDurability.sync(checkNotNull(terminated.parentFile))
    }

    private fun sanitizeEventPrefix() {
        val eventFile = File(partial, EVENT_STREAM)
        if (!eventFile.isFile) return
        val bytes = eventFile.readBytes()
        val completeLength = bytes.indexOfLast { it == '\n'.code.toByte() } + 1
        if (completeLength == bytes.size) return
        RandomAccessFile(eventFile, "rw").use { file ->
            file.setLength(completeLength.toLong())
            file.fd.sync()
        }
    }

    private enum class WriterState { OPEN, FINALIZING, PUBLISHED, TERMINATED }

    companion object {
        val REQUIRED_STREAMS: LinkedHashSet<String> = linkedSetOf(
            "camera.rgba",
            "participant-action.rgba",
            "avatar-a.rgba",
            "avatar-b.rgba",
            "controller-events.jsonl",
        )
        internal const val STREAM_MANIFEST = "streams.sha256"
        internal const val TERMINATED_STREAM_MANIFEST = "terminated-streams.sha256"
        internal const val FAILURE_CODE_FILE = "failure-code.txt"
        private const val EVENT_STREAM = "controller-events.jsonl"
    }
}

private class H2StreamingHashOrLengthFailure(logicalName: String) :
    IllegalStateException("H2 retained stream hash or length mismatch: $logicalName")

private class BoundedDigestStream(
    private val output: FileOutputStream,
    private val ceiling: Long,
    private val logicalName: String,
    private val faultInjection: H2StreamingFaultInjection,
    private val account: (Long) -> Unit,
) : OutputStream() {
    private val digest = MessageDigest.getInstance("SHA-256")
    var bytesWritten: Long = 0
        private set
    var sha256: String? = null
        private set

    override fun write(value: Int) = write(byteArrayOf(value.toByte()))

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        check(sha256 == null) { "H2 stream is closed" }
        require(length >= 0 && bytesWritten + length <= ceiling) { "H2 stream byte ceiling exceeded" }
        faultInjection.before(H2StreamingFaultPoint.STREAM_WRITE, logicalName)
        account(length.toLong())
        output.write(bytes, offset, length)
        digest.update(bytes, offset, length)
        bytesWritten += length
    }

    fun finish() {
        if (sha256 != null) return
        output.flush()
        output.fd.sync()
        output.close()
        sha256 = digest.digest().joinToString("") {
            "%02x".format(it.toInt() and SHA_BYTE_MASK)
        }
    }

    override fun close() = finish()
}

private fun requireSafeRoot(root: File) {
    if (!root.exists()) check(root.mkdirs()) { "cannot create H2 root" }
    require(root.isDirectory) { "H2 root is not a directory" }
    refuseSymlink(root)
    require(root.canonicalFile == root.absoluteFile) { "H2 root is not canonical" }
}

private fun refuseSymlink(file: File) {
    require(!Files.isSymbolicLink(file.toPath())) { "H2 paths cannot be symbolic links" }
}

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString("") { "%02x".format(it.toInt() and SHA_BYTE_MASK) }

private const val SHA_BYTE_MASK = 0xff

private fun syncFile(file: File, bytes: ByteArray) {
    FileOutputStream(file).use { output ->
        output.write(bytes)
        output.fd.sync()
    }
}
