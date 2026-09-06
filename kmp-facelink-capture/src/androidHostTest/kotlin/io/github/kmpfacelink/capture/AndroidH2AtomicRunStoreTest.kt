package io.github.kmpfacelink.capture

import kotlinx.serialization.encodeToString
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AndroidH2AtomicRunStoreTest {
    @Test
    fun exclusivePrepareAndAtomicBlockPublicationRetainExactFiveStreams() {
        val root = Files.createTempDirectory("h2-atomic-test").toFile()
        try {
            val store = AndroidH2AtomicFixtureRunStore(root)
            var state = store.open(store.prepare("1".repeat(64)))
            state = store.begin(state, H2ConfirmationContract.blocks.first().id, true)
            state = store.publishBlock(state, 15, 0, 0, streams())

            val published = root.resolve("blocks/B01_STARTUP_NEUTRAL_READINESS-attempt-1")
            assertTrue(published.isDirectory)
            assertEquals(
                setOf(
                    "canonical-camera.bin",
                    "participant-action-visual.bin",
                    "primary-avatar-render.bin",
                    "comparator-avatar-render.bin",
                    "clocked-events.jsonl",
                    "streams.sha256",
                ),
                published.listFiles().orEmpty().map { it.name }.toSet(),
            )
            assertEquals(1, state.nextBlockIndex)
            assertFailsWith<IllegalArgumentException> {
                AndroidH2AtomicFixtureRunStore(root).prepare("2".repeat(64))
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun wrongStreamInventoryFailsBeforePublication() {
        val root = Files.createTempDirectory("h2-atomic-fault-test").toFile()
        try {
            val store = AndroidH2AtomicFixtureRunStore(root)
            var state = store.open(store.prepare("3".repeat(64)))
            state = store.begin(state, H2ConfirmationContract.blocks.first().id, true)
            assertFailsWith<IllegalArgumentException> {
                store.publishBlock(state, 15, 1, 1, mapOf("canonical-camera.bin" to byteArrayOf(1)))
            }
            assertTrue(root.resolve("blocks").listFiles().orEmpty().isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun restartDuringAnOpenAttemptTerminatesAndNeverResumes() {
        val root = Files.createTempDirectory("h2-restart-fault-test").toFile()
        try {
            val store = AndroidH2AtomicFixtureRunStore(root)
            var state = store.open(store.prepare("4".repeat(64)))
            state = store.begin(state, H2ConfirmationContract.blocks.first().id, true)

            val recovered = AndroidH2AtomicFixtureRunStore(root).loadOrRecover()
            assertEquals(H2RunPhase.TERMINATED, recovered.phase)
            assertEquals(H2FailureCode.RESTART_DURING_ACTIVE_ATTEMPT, recovered.terminalCode)
            assertEquals(1, recovered.failures.size)
            assertFailsWith<IllegalStateException> {
                beginH2Block(recovered, H2ConfirmationContract.blocks.first().id, false)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun controllerJournalTerminatesPublishedProgressInsteadOfRestartingAtB01() {
        val root = Files.createTempDirectory("h2-controller-journal-restart").toFile()
        try {
            val identity = "5".repeat(64)
            val journal = AndroidH2ControllerRunJournal(root)
            var state = journal.openOrRecover(identity)
            state = beginH2Block(state, H2ConfirmationContract.blocks.first().id, false)
            journal.persist(state)
            state = completeH2Block(state, receipt(state, H2ConfirmationContract.STARTUP_TOTAL_SECONDS))
            journal.persist(state)

            val recovered = AndroidH2ControllerRunJournal(root).openOrRecover(identity)
            assertEquals(H2RunPhase.TERMINATED, recovered.phase)
            assertEquals(H2FailureCode.STALE_OPEN_OR_PREPARED_RUN, recovered.terminalCode)
            assertEquals(1, recovered.nextBlockIndex)
            assertFailsWith<IllegalStateException> {
                beginH2Block(recovered, H2ConfirmationContract.blocks[1].id, true)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun controllerJournalPersistsExactTwentyTwoBlockOrderAndTerminalSeal() {
        withSealedJournal { root, identity, journal, state, seal ->
            assertEquals(H2RunPhase.SEALED, state.phase)
            assertEquals(
                H2ConfirmationContract.blocks.map(H2ConfirmationBlock::id),
                state.receipts.map(H2BlockReceipt::blockId),
            )
            val readback = journal.readVerifiedSealedRun(identity)
            assertEquals(state, readback.state)
            assertEquals(seal.runIdentitySha256, readback.seal.runIdentitySha256)
            assertEquals(seal.orderedBlockReceiptsSha256, readback.seal.orderedBlockReceiptsSha256)
            assertEquals(seal.controllerEventStreamsSha256, readback.seal.controllerEventStreamsSha256)
            assertEquals(seal.clockMappingReceiptSha256, readback.seal.clockMappingReceiptSha256)
            assertEquals(seal.retainedStreamManifestsSha256, readback.seal.retainedStreamManifestsSha256)
            assertEquals(root.resolve("h2-terminal-seal-v1.sha256").readText().trim(), readback.terminalSealSha256)
            assertEquals(
                H2RunPhase.SEALED,
                AndroidH2ControllerRunJournal(root).openOrRecover(identity).phase,
            )
        }
    }

    @Test
    fun sealedJournalMissingTruncatedHashAndIdentityMutationsFailClosed() {
        withSealedJournal { root, identity, _, _, _ ->
            root.resolve("h2-run-state-v1.json").delete()
            assertFailsWith<IllegalArgumentException> {
                AndroidH2ControllerRunJournal(root).openOrRecover(identity)
            }
        }
        withSealedJournal { root, identity, _, _, _ ->
            root.resolve("h2-terminal-seal-v1.json").delete()
            assertFailsWith<IllegalArgumentException> {
                AndroidH2ControllerRunJournal(root).openOrRecover(identity)
            }
        }
        withSealedJournal { root, identity, _, _, _ ->
            root.resolve("h2-terminal-seal-v1.json").writeText("{")
            assertFailsWith<IllegalArgumentException> {
                AndroidH2ControllerRunJournal(root).openOrRecover(identity)
            }
        }
        withSealedJournal { root, identity, _, _, _ ->
            root.resolve("h2-run-state-v1.json").writeText("{")
            assertFailsWith<Exception> {
                AndroidH2ControllerRunJournal(root).openOrRecover(identity)
            }
        }
        withSealedJournal { root, identity, _, _, _ ->
            root.resolve("h2-terminal-seal-v1.sha256").writeText("0".repeat(64) + "\n")
            assertFailsWith<IllegalArgumentException> {
                AndroidH2ControllerRunJournal(root).openOrRecover(identity)
            }
        }
        withSealedJournal { _, _, journal, _, _ ->
            assertFailsWith<IllegalArgumentException> {
                journal.readVerifiedSealedRun("7".repeat(64))
            }
        }
    }

    @Test
    fun sealedJournalRejectsStateRollbackAndDuplicateSeal() {
        withSealedJournal { root, identity, journal, state, seal ->
            val rolledBack = state.copy(
                phase = H2RunPhase.OPEN,
                nextBlockIndex = state.nextBlockIndex - 1,
                receipts = state.receipts.dropLast(1),
            )
            root.resolve("h2-run-state-v1.json").writeText(rawFrameJson.encodeToString(rolledBack))
            assertFailsWith<IllegalArgumentException> {
                AndroidH2ControllerRunJournal(root).openOrRecover(identity)
            }
            assertFailsWith<IllegalArgumentException> {
                journal.publishTerminalSeal(state.copy(phase = H2RunPhase.OPEN), seal)
            }
        }
    }

    private fun receipt(state: H2RunState, observableSeconds: Int): H2BlockReceipt = H2BlockReceipt(
        blockId = checkNotNull(state.activeBlockId),
        captureAttempt = checkNotNull(state.activeAttempt),
        observableSeconds = observableSeconds,
        primaryVisibleEventCount = if (state.nextBlockIndex == 0) 0 else 1,
        comparatorVisibleEventCount = if (state.nextBlockIndex == 0) 0 else 1,
        retainedStreamManifestSha256 = "a".repeat(64),
        publishedAtomically = true,
    )

    private fun seal(state: H2RunState): H2PublicSafeSeal {
        val hasher = H2SealSha256(::sha256)
        val canonical = state.orderedBlockReceiptsCanonicalUtf8Lf()
        val digests = H2VerifiedSealDigestBundle(
            state.runIdentitySha256,
            canonical,
            sha256(canonical.encodeToByteArray()),
            sha256("events\n".encodeToByteArray()),
            sha256("clock\n".encodeToByteArray()),
            sha256("streams\n".encodeToByteArray()),
        )
        return buildH2PublicSafeSeal(
            state,
            H2SealAuthorityChain.fromRetainedBytes(
                "source".encodeToByteArray(), "patch".encodeToByteArray(),
                "apk".encodeToByteArray(), "build".encodeToByteArray(), "1".repeat(40),
                "binary".encodeToByteArray(), "device".encodeToByteArray(),
                "privacy".encodeToByteArray(), "capture".encodeToByteArray(), hasher,
            ),
            digests,
            missingExtraDuplicateMutationChecksPassed = true,
            sealedAtIso = "2026-09-03T00:00:00Z",
        )
    }

    private fun withSealedJournal(
        block: (
            root: java.io.File,
            identity: String,
            journal: AndroidH2ControllerRunJournal,
            sealedState: H2RunState,
            seal: H2PublicSafeSeal,
        ) -> Unit,
    ) {
        val root = Files.createTempDirectory("h2-controller-journal-seal").toFile()
        try {
            val identity = "6".repeat(64)
            val journal = AndroidH2ControllerRunJournal(root)
            var state = journal.openOrRecover(identity)
            H2ConfirmationContract.blocks.forEach { confirmationBlock ->
                state = beginH2Block(state, confirmationBlock.id, providerPolicyRouteRenderAvailable = true)
                journal.persist(state)
                state = completeH2Block(
                    state,
                    receipt(state, confirmationBlock.minimumObservableSeconds),
                )
                journal.persist(state)
            }
            val terminalSeal = seal(state)
            state = journal.publishTerminalSeal(state, terminalSeal)
            block(root, identity, journal, state, terminalSeal)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun streams(): Map<String, ByteArray> = mapOf(
        "canonical-camera.bin" to byteArrayOf(1),
        "participant-action-visual.bin" to byteArrayOf(2),
        "primary-avatar-render.bin" to byteArrayOf(3),
        "comparator-avatar-render.bin" to byteArrayOf(4),
        "clocked-events.jsonl" to "{}\n".encodeToByteArray(),
    )
}
