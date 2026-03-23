package io.github.kmpfacelink.pipeline.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SentenceBufferTest {

    private val buffer = SentenceBuffer()

    @Test
    fun singleSentenceWithPeriod() {
        val result = buffer.append("Hello world.")
        assertEquals(listOf("Hello world."), result)
    }

    @Test
    fun singleSentenceWithExclamation() {
        val result = buffer.append("Hello world!")
        assertEquals(listOf("Hello world!"), result)
    }

    @Test
    fun singleSentenceWithQuestion() {
        val result = buffer.append("How are you?")
        assertEquals(listOf("How are you?"), result)
    }

    @Test
    fun tokenByTokenInput() {
        assertEquals(emptyList(), buffer.append("Hello"))
        assertEquals(emptyList(), buffer.append(" world"))
        val result = buffer.append(".")
        assertEquals(listOf("Hello world."), result)
    }

    @Test
    fun multipleSentencesInOneChunk() {
        val result = buffer.append("First. Second! Third?")
        assertEquals(listOf("First.", "Second!", "Third?"), result)
    }

    @Test
    fun flushReturnsRemainingText() {
        buffer.append("Incomplete sentence")
        val flushed = buffer.flush()
        assertEquals("Incomplete sentence", flushed)
    }

    @Test
    fun flushReturnsNullWhenEmpty() {
        assertNull(buffer.flush())
    }

    @Test
    fun flushAfterCompleteSentence() {
        buffer.append("Complete.")
        assertNull(buffer.flush())
    }

    @Test
    fun abbreviationDrNotBoundary() {
        assertEquals(emptyList(), buffer.append("Dr."))
        assertEquals(emptyList(), buffer.append(" Smith is"))
        val result = buffer.append(" here.")
        assertEquals(listOf("Dr. Smith is here."), result)
    }

    @Test
    fun abbreviationMrNotBoundary() {
        val result = buffer.append("Mr. Jones arrived.")
        assertEquals(listOf("Mr. Jones arrived."), result)
    }

    @Test
    fun abbreviationEtcNotBoundary() {
        assertEquals(emptyList(), buffer.append("cats, dogs, etc."))
        val result = buffer.append(" They are cute!")
        assertEquals(listOf("cats, dogs, etc. They are cute!"), result)
    }

    @Test
    fun fullWidthJapanesePeriod() {
        val result = buffer.append("\u3053\u3093\u306B\u3061\u306F\u3002")
        assertEquals(1, result.size)
    }

    @Test
    fun fullWidthExclamation() {
        val result = buffer.append("\u3059\u3054\u3044\uFF01")
        assertEquals(1, result.size)
    }

    @Test
    fun fullWidthQuestion() {
        val result = buffer.append("\u5143\u6C17\uFF1F")
        assertEquals(1, result.size)
    }

    @Test
    fun emptyChunkProducesNothing() {
        assertEquals(emptyList(), buffer.append(""))
    }

    @Test
    fun multipleFlushesReturnNull() {
        buffer.append("text")
        buffer.flush()
        assertNull(buffer.flush())
    }

    @Test
    fun whitespaceOnlyFlushReturnsNull() {
        buffer.append("   ")
        assertNull(buffer.flush())
    }
}
