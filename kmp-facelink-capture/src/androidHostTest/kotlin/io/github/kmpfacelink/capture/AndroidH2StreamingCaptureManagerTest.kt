package io.github.kmpfacelink.capture

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AndroidH2StreamingCaptureManagerTest {
    @Test
    fun streamsExactInventoryAndPublishesOneDirectory() {
        val root = Files.createTempDirectory("h2-streaming").toFile().canonicalFile
        try {
            var directorySyncCount = 0
            val manager = AndroidH2StreamingCaptureManager(
                root,
                1_024,
                H2DirectoryDurability { directory ->
                    assertEquals(root, directory.canonicalFile)
                    directorySyncCount++
                },
            )
            val writer = manager.beginBlock(H2ConfirmationContract.blocks[1].id, 1)
            H2StreamingBlockWriter.REQUIRED_STREAMS.forEachIndexed { index, name ->
                writer.open(name).use { it.write(byteArrayOf(index.toByte(), 7)) }
            }
            val published = manager.publishBlock(writer)
            assertEquals(10, published.totalBytes)
            assertTrue(published.manifestSha256.matches(Regex("[0-9a-f]{64}")))
            assertEquals(
                H2StreamingBlockWriter.REQUIRED_STREAMS + "streams.sha256",
                root.resolve("${published.blockId}.1").listFiles().orEmpty().map { it.name }.toSet(),
            )
            assertEquals(2, directorySyncCount)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun aggregateByteCeilingAndCollisionFailClosed() {
        val root = Files.createTempDirectory("h2-streaming-ceiling").toFile().canonicalFile
        try {
            val manager = AndroidH2StreamingCaptureManager(root, 5)
            val writer = manager.beginBlock(H2ConfirmationContract.blocks[1].id, 1)
            writer.open("camera.rgba").use { it.write(byteArrayOf(1, 2, 3)) }
            assertFailsWith<IllegalArgumentException> {
                writer.open("participant-action.rgba").use { it.write(byteArrayOf(4, 5, 6)) }
            }
            manager.terminateActive(H2FailureCode.RESTART_DURING_ACTIVE_ATTEMPT)
            assertTrue(root.resolve(".${H2ConfirmationContract.blocks[1].id}.1.terminated").isDirectory)
            assertFailsWith<IllegalStateException> {
                manager.beginBlock(H2ConfirmationContract.blocks[1].id, 1)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun restartRecoversPartialAndSymlinkRootIsRejected() {
        val root = Files.createTempDirectory("h2-streaming-recovery").toFile().canonicalFile
        val linkParent = Files.createTempDirectory("h2-streaming-link").toFile().canonicalFile
        try {
            root.resolve(".B02_OPENING_NEUTRAL_READY.1.partial").mkdir()
            AndroidH2StreamingCaptureManager(root, 100)
            assertTrue(root.resolve(".B02_OPENING_NEUTRAL_READY.1.terminated").isDirectory)

            val link = linkParent.resolve("linked")
            Files.createSymbolicLink(link.toPath(), root.toPath())
            assertFailsWith<IllegalArgumentException> { AndroidH2StreamingCaptureManager(link, 100) }
        } finally {
            linkParent.deleteRecursively()
            root.deleteRecursively()
        }
    }

    @Test
    fun controllerStoreSeparatesActionVisualFromAvatarAndRetainsScoreFreeEnvelope() {
        val root = Files.createTempDirectory("h2-controller-streams").toFile().canonicalFile
        try {
            val store = AndroidH2ControllerStore(AndroidH2StreamingCaptureManager(root, 1_000_000))
            val blockId = H2ConfirmationContract.blocks[1].id
            store.beginBlock(blockId, 1)
            store.appendEvent(
                H2ControllerEvent(
                    0,
                    blockId,
                    H2ControllerEventKind.BLOCK_SCHEDULE_OPENED,
                    20_000_000_000,
                    "1".repeat(64),
                    participantVisibility = H2SurfaceVisibilityMode.PRIMARY_ONLY,
                ),
            )
            val clock = H2ClockMapping(
                H2ClockMapping.MONOTONIC_CLOCK_ID,
                20_000_000_000,
                20_000_000_000,
                20_000_000_000,
                20_000_000_000,
            )
            val primary = checkNotNull(
                H2BlindAvatarRenderer(H2CandidateRole.PRIMARY).render(
                    H2AvatarRouteState(false, true),
                    clock,
                    1,
                    blockId,
                    23_001_000_000,
                ),
            )
            val comparator = checkNotNull(
                H2BlindAvatarRenderer(H2CandidateRole.COMPARATOR).render(
                    H2AvatarRouteState(false, true),
                    clock,
                    2,
                    blockId,
                    23_001_000_000,
                ),
            )
            store.append(
                H2RetainedControllerFrame(
                    40,
                    3_001,
                    23_001_000_000,
                    byteArrayOf(1, 2),
                    byteArrayOf(9, 8),
                    primary,
                    comparator,
                    primary,
                    listOf(primary.event, comparator.event),
                    listOf(
                        H2ControllerEvent(
                            3,
                            blockId,
                            H2ControllerEventKind.FRAME_JOINED,
                            23_001_000_000,
                            "1".repeat(64),
                            40,
                            3_001,
                        ),
                        H2ControllerEvent(
                            4,
                            blockId,
                            H2ControllerEventKind.CLOCK_MAPPING,
                            23_001_000_000,
                            "1".repeat(64),
                            40,
                            3_001,
                            clockMapping = clock,
                        ),
                    ),
                ),
            )
            val published = store.publishBlock()
            val directory = root.resolve("${published.blockId}.1")
            assertEquals(
                listOf(9.toByte(), 8.toByte()),
                directory.resolve("participant-action.rgba").readBytes().takeLast(2),
            )
            val events = directory.resolve("controller-events.jsonl").readText()
            listOf("BLOCK_SCHEDULE_OPENED", "FRAME_JOINED", "CLOCK_MAPPING", "VISIBLE_OUTPUT").forEach {
                assertTrue(it in events)
            }
            assertTrue("frame_index" in events && "take_elapsed_millis" in events)
            assertTrue("run_identity_sha256" in events)
            assertTrue("score" !in events.lowercase() && "result" !in events.lowercase())
            val sequences = Regex("\\\"sequence\\\":(\\d+)").findAll(events)
                .map { it.groupValues[1].toLong() }
                .toList()
            assertEquals((0L..4L).toList(), sequences)
        } finally {
            root.deleteRecursively()
        }
    }
}
