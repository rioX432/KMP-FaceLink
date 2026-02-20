package io.github.kmpfacelink.stream

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FlowExtensionsTest {

    @Test
    fun rateLimitRequiresPositiveFps() {
        assertFailsWith<IllegalArgumentException> {
            flowOf(1).rateLimit(0)
        }
    }

    @Test
    fun rateLimitEmitsFirstItem() = runTest {
        val items = flowOf(1, 2, 3).rateLimit(1).toList()
        assertTrue(items.isNotEmpty(), "Should emit at least one item")
        assertEquals(1, items.first())
    }

    @Test
    fun rateLimitHighFpsPassesAll() = runTest {
        // With a very high FPS limit, all items should pass through
        val items = flowOf(1, 2, 3, 4, 5).rateLimit(10000).toList()
        assertEquals(5, items.size)
    }
}
