package io.github.kmpfacelink.capture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CaptureStoreRecoveryTest {
    @Test
    fun recoveryRemovesOnlyIncompleteChildren() {
        val root = tempDir("capture-store-recovery")
        val tmp = File(root, "tmp").apply { mkdirs() }
        val takes = File(root, "takes").apply { mkdirs() }
        val incomplete = File(tmp, "take-a").apply { mkdirs() }
        File(incomplete, "results.idx").writeText("partial")
        val published = File(takes, "take-b").apply { mkdirs() }
        File(published, "take.json").writeText("complete")

        assertEquals(1, recoverAndroidIncompleteTakes(tmp))

        assertFalse(incomplete.exists())
        assertTrue(published.exists())
        assertEquals(0, recoverAndroidIncompleteTakes(tmp))
        root.deleteRecursively()
    }
}
