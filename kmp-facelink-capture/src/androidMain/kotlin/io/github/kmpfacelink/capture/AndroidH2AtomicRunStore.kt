@file:Suppress("Filename", "MatchingDeclarationName", "TooManyFunctions")

package io.github.kmpfacelink.capture

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Local-only persistence for the score-free H2 route.
 *
 * Private streams are accepted as opaque bytes. Their contents never enter the public-safe state;
 * only logical names, lengths, and hashes are used to bind an atomic block publication. This is a
 * bounded host-fixture sink, not production-scale stream plumbing. Android does not expose the
 * parent-directory fsync used by the final capture durability contract, so this implementation
 * cannot make H2 executable and is limited to public synthetic fault checks.
 */
internal class AndroidH2AtomicFixtureRunStore(private val root: File) {
    private val guardFile = File(root, RUN_GUARD_FILE)
    private val stateFile = File(root, RUN_STATE_FILE)
    private val blocksRoot = File(root, BLOCKS_DIR)

    @Synchronized
    fun prepare(runIdentitySha256: String): H2RunState {
        require(root.isDirectory || root.mkdirs()) { "H2 root is unavailable" }
        requireSafeDirectory(root, root.parentFile)
        require(!stateFile.exists() && !File(root, TERMINAL_SEAL_FILE).exists()) {
            "H2 root already contains a run"
        }
        require(guardFile.createNewFile()) { "another H2 run holds the exclusive lease" }
        val prepared = H2RunState(runIdentitySha256)
        writeState(prepared)
        return prepared
    }

    @Synchronized
    fun hasRunState(): Boolean = stateFile.exists()

    @Synchronized
    fun persistTransition(state: H2RunState) {
        require(state.phase != H2RunPhase.PREPARED) { "prepared state must be created by prepare" }
        writeState(state)
    }

    /**
     * Host-fixture recovery rule. Any unresolved restart terminates; the only forward recovery is
     * completing a terminal-seal/state write pair after the immutable seal already exists.
     */
    @Synchronized
    fun loadOrRecover(): H2RunState {
        requireSafeDirectory(root, root.parentFile)
        val state = readState()
        if (state.phase == H2RunPhase.SEALED) return readVerifiedSealedRun(state.runIdentitySha256).state
        if (state.phase == H2RunPhase.TERMINATED) {
            require(!File(root, TERMINAL_SEAL_FILE).exists() && !File(root, TERMINAL_SEAL_HASH_FILE).exists()) {
                "terminated H2 state contradicts terminal seal artifacts"
            }
            return state
        }
        val terminalFile = File(root, TERMINAL_SEAL_FILE)
        if (terminalFile.exists() || File(root, TERMINAL_SEAL_HASH_FILE).exists()) {
            val seal = readVerifiedSeal()
            val recovered = sealH2Run(state, seal)
            writeState(recovered)
            return recovered
        }
        val publishedOrPartialExists = blocksRoot.listFiles().orEmpty().isNotEmpty()
        val code = when {
            state.phase == H2RunPhase.PREPARED -> H2FailureCode.STALE_OPEN_OR_PREPARED_RUN
            state.activeBlockId != null -> H2FailureCode.RESTART_DURING_ACTIVE_ATTEMPT
            publishedOrPartialExists -> H2FailureCode.POST_PUBLICATION_MUTATION
            else -> H2FailureCode.STALE_OPEN_OR_PREPARED_RUN
        }
        val terminated = terminateH2Run(state, code)
        writeState(terminated)
        return terminated
    }

    @Synchronized
    fun open(prepared: H2RunState): H2RunState {
        require(guardFile.isFile) { "H2 exclusive lease is missing" }
        val opened = openPreparedH2Run(
            prepared,
            exclusiveLeaseAcquired = true,
            noPriorOpenOrTerminalRun = true,
        )
        writeState(opened)
        return opened
    }

    @Synchronized
    fun begin(state: H2RunState, blockId: String, providerPolicyRouteRenderAvailable: Boolean): H2RunState {
        val active = beginH2Block(state, blockId, providerPolicyRouteRenderAvailable)
        writeState(active)
        return active
    }

    @Synchronized
    fun fail(
        state: H2RunState,
        code: H2FailureCode,
    ): Pair<H2RunState, H2FailureDecision> {
        val result = failH2Block(state, requireNotNull(state.activeBlockId), code)
        writeState(result.first)
        return result
    }

    @Synchronized
    fun publishBlock(
        state: H2RunState,
        observableSeconds: Int,
        primaryVisibleEventCount: Int,
        comparatorVisibleEventCount: Int,
        privateStreams: Map<String, ByteArray>,
    ): H2RunState {
        require(privateStreams.keys == REQUIRED_PRIVATE_STREAMS) { "H2 private stream inventory mismatch" }
        val blockId = requireNotNull(state.activeBlockId)
        val attempt = requireNotNull(state.activeAttempt)
        require(blocksRoot.isDirectory || blocksRoot.mkdirs()) { "H2 block root is unavailable" }
        requireSafeDirectory(blocksRoot, root)
        val publicationName = "$blockId-attempt-$attempt"
        val staging = File(blocksRoot, "$publicationName.partial")
        val published = File(blocksRoot, publicationName)
        require(!staging.exists() && !published.exists()) { "H2 block attempt already exists" }
        require(staging.mkdir()) { "H2 staging directory is unavailable" }
        val manifestLines = privateStreams.toSortedMap().map { (logicalName, bytes) ->
            val streamFile = File(staging, logicalName)
            require(!Files.isSymbolicLink(streamFile.toPath())) { "H2 stream path is unsafe" }
            FileOutputStream(streamFile).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            "$logicalName\t${bytes.size}\t${sha256(bytes)}"
        }
        val manifestBytes = (manifestLines.joinToString("\n") + "\n").encodeToByteArray()
        FileOutputStream(File(staging, STREAM_MANIFEST_FILE)).use { output ->
            output.write(manifestBytes)
            output.fd.sync()
        }
        Files.move(staging.toPath(), published.toPath(), StandardCopyOption.ATOMIC_MOVE)
        val receipt = H2BlockReceipt(
            blockId = blockId,
            captureAttempt = attempt,
            observableSeconds = observableSeconds,
            primaryVisibleEventCount = primaryVisibleEventCount,
            comparatorVisibleEventCount = comparatorVisibleEventCount,
            retainedStreamManifestSha256 = sha256(manifestBytes),
            publishedAtomically = true,
        )
        val completed = completeH2Block(state, receipt)
        writeState(completed)
        return completed
    }

    @Synchronized
    fun publishTerminalSeal(state: H2RunState, seal: H2PublicSafeSeal): H2RunState {
        val terminal = File(root, TERMINAL_SEAL_FILE)
        val staging = File(root, "$TERMINAL_SEAL_FILE.partial")
        val hash = File(root, TERMINAL_SEAL_HASH_FILE)
        val hashStaging = File(root, "$TERMINAL_SEAL_HASH_FILE.partial")
        require(!terminal.exists() && !staging.exists() && !hash.exists() && !hashStaging.exists()) {
            "H2 terminal seal already exists"
        }
        val sealedState = sealH2Run(state, seal)
        val sealBytes = rawFrameJson.encodeToString(seal).encodeToByteArray()
        FileOutputStream(staging).use { output ->
            output.write(sealBytes)
            output.fd.sync()
        }
        FileOutputStream(hashStaging).use { output ->
            output.write("${sha256(sealBytes)}\n".encodeToByteArray())
            output.fd.sync()
        }
        Files.move(staging.toPath(), terminal.toPath(), StandardCopyOption.ATOMIC_MOVE)
        Files.move(hashStaging.toPath(), hash.toPath(), StandardCopyOption.ATOMIC_MOVE)
        writeState(sealedState)
        return sealedState
    }

    @Synchronized
    fun readVerifiedSealedRun(expectedRunIdentitySha256: String): H2DurableJournalReadback {
        val stateBytes = readSafeFile(stateFile, "H2 state")
        val state = rawFrameJson.decodeFromString<H2RunState>(stateBytes.decodeToString())
        require(state.phase == H2RunPhase.SEALED) { "H2 state is not sealed" }
        require(state.runIdentitySha256 == expectedRunIdentitySha256) {
            "H2 run identity changed across sealed readback"
        }
        val sealBytes = readSafeFile(File(root, TERMINAL_SEAL_FILE), "H2 terminal seal")
        val seal = readVerifiedSeal(sealBytes)
        requireSealMatchesState(state, seal)
        return H2DurableJournalReadback(
            state,
            seal,
            sha256(stateBytes),
            sha256(sealBytes),
        )
    }

    private fun writeState(state: H2RunState) {
        requireSafeDirectory(root, root.parentFile)
        val staging = File(root, "$RUN_STATE_FILE.partial")
        FileOutputStream(staging).use { output ->
            output.write(rawFrameJson.encodeToString(state).encodeToByteArray())
            output.fd.sync()
        }
        Files.move(
            staging.toPath(),
            stateFile.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    private fun readState(): H2RunState = rawFrameJson.decodeFromString(
        readSafeFile(stateFile, "H2 state").decodeToString(),
    )

    private fun readVerifiedSeal(): H2PublicSafeSeal = readVerifiedSeal(
        readSafeFile(File(root, TERMINAL_SEAL_FILE), "H2 terminal seal"),
    )

    private fun readVerifiedSeal(sealBytes: ByteArray): H2PublicSafeSeal {
        val hashBytes = readSafeFile(File(root, TERMINAL_SEAL_HASH_FILE), "H2 terminal seal hash")
        val retainedHash = hashBytes.decodeToString()
        require(retainedHash.matches(Regex("[0-9a-f]{64}\\n"))) {
            "H2 terminal seal hash is malformed"
        }
        require(retainedHash.dropLast(1) == sha256(sealBytes)) { "H2 terminal seal hash mismatch" }
        return rawFrameJson.decodeFromString(sealBytes.decodeToString())
    }

    private fun requireSealMatchesState(state: H2RunState, seal: H2PublicSafeSeal) {
        require(seal.runIdentitySha256 == state.runIdentitySha256) { "H2 sealed run identity mismatch" }
        require(seal.protocolSha256 == H2ConfirmationContract.PROTOCOL_PREREG_SHA256) {
            "H2 sealed protocol mismatch"
        }
        val canonicalReceipts = state.orderedBlockReceiptsCanonicalUtf8Lf()
        require(seal.orderedBlockReceiptsCanonicalUtf8Lf == canonicalReceipts) {
            "H2 sealed receipt bytes mismatch"
        }
        require(seal.orderedBlockReceiptsSha256 == sha256(canonicalReceipts.encodeToByteArray())) {
            "H2 sealed receipt digest mismatch"
        }
        require(seal.failureLedger == state.failures) { "H2 sealed failure ledger mismatch" }
    }

    private fun readSafeFile(file: File, label: String): ByteArray {
        require(file.isFile && !Files.isSymbolicLink(file.toPath())) { "$label is missing or unsafe" }
        return file.readBytes()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte.toInt() and BYTE_MASK) }

    private fun requireSafeDirectory(directory: File, expectedParent: File?) {
        require(directory.isDirectory && !Files.isSymbolicLink(directory.toPath())) {
            "H2 directory is missing or symbolic"
        }
        if (expectedParent != null && directory != expectedParent) {
            require(directory.canonicalFile.parentFile == expectedParent.canonicalFile) {
                "H2 directory escaped its parent"
            }
        }
    }

    companion object {
        const val BYTE_MASK: Int = 0xff
        internal const val RUN_GUARD_FILE: String = "h2-exclusive-run.guard"
        internal const val RUN_STATE_FILE: String = "h2-run-state-v1.json"
        const val BLOCKS_DIR: String = "blocks"
        const val STREAM_MANIFEST_FILE: String = "streams.sha256"
        internal const val TERMINAL_SEAL_FILE: String = "h2-terminal-seal-v1.json"
        internal const val TERMINAL_SEAL_HASH_FILE: String = "h2-terminal-seal-v1.sha256"
        val REQUIRED_PRIVATE_STREAMS: Set<String> = setOf(
            "canonical-camera.bin",
            "participant-action-visual.bin",
            "primary-avatar-render.bin",
            "comparator-avatar-render.bin",
            "clocked-events.jsonl",
        )
    }
}

internal data class H2DurableJournalReadback(
    val state: H2RunState,
    val seal: H2PublicSafeSeal,
    val stateSha256: String,
    val terminalSealSha256: String,
)
